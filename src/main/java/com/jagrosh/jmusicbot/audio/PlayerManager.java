/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.cosgy.jmusicbot.util.YtDlpManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.cipher.RemoteCipherManager;
import dev.lavalink.youtube.clients.*;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class PlayerManager extends DefaultAudioPlayerManager {
    /** yt-dlp フォールバックの対象となる提供元名 */
    /** ニコニコ動画の動画ID（sm/nm/so + 数字） */
    private static final Pattern NICO_ID_PATTERN = Pattern.compile("^(?:sm|nm|so)[0-9]+$");

    /** yt-dlp 1 回あたりの実行時間上限（秒）。-Djmusicbot.ytdlp.timeoutSec で変更可 */
    private static final long YTDLP_TIMEOUT_SEC = Math.max(30, Long.getLong("jmusicbot.ytdlp.timeoutSec", 300L));
    /** 同時に実行する yt-dlp プロセス数の上限。-Djmusicbot.ytdlp.maxConcurrent で変更可 */
    private static final int YTDLP_MAX_CONCURRENT = Math.max(1, Integer.getInteger("jmusicbot.ytdlp.maxConcurrent", 3));
    /** yt-dlp 取得失敗を記憶しておく時間（一時的な失敗） */
    private static final long FAILURE_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    /** yt-dlp 取得失敗を記憶しておく時間（再試行しても解決しない失敗） */
    private static final long PERMANENT_FAILURE_TTL_MS = TimeUnit.HOURS.toMillis(1);
    /** キャッシュ済みファイルとして探す拡張子（yt-dlp の出力形式順） */
    private static final List<String> CACHE_EXTENSIONS =
            List.of("webm", "m4a", "opus", "ogg", "mp4", "mp3", "aac", "flac", "wav");
    /** 抽出方法を変えて再試行しても結果が変わらない yt-dlp のエラー */
    private static final Pattern PERMANENT_ERROR = Pattern.compile(
            "(?i)video unavailable|private video|has been removed|this video is not available"
                    + "|sign in to confirm your age|age-restricted|members-only|join this channel"
                    + "|copyright|is not a valid url|unsupported url|premieres? in|this live event will begin");

    private final Bot bot;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** 直近で yt-dlp 取得に失敗した動画（キー → 記録の失効時刻 epoch millis） */
    private final ConcurrentHashMap<String, Long> recentFailures = new ConcurrentHashMap<>();
    /** 進行中の yt-dlp ダウンロード（キー → 完了 Future）。同じ動画の同時ダウンロードを 1 本にまとめる */
    private final ConcurrentHashMap<String, CompletableFuture<Path>> inFlightDownloads = new ConcurrentHashMap<>();
    /** 同時実行する yt-dlp プロセス数を制限する */
    private final Semaphore ytDlpSlots = new Semaphore(YTDLP_MAX_CONCURRENT);
    /** 再生中フォールバック用ワーカー。長時間ブロックする処理を common pool に載せない */
    private final ExecutorService fallbackExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "yt-dlp-fallback");
        t.setDaemon(true);
        return t;
    });

    // yt-dlp
    private Path ytDlpPath;
    private volatile String ytDlpVersion;
    private volatile boolean ffmpegAvailable;
    private volatile boolean ffprobeAvailable;

    public PlayerManager(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        verifyFfmpegAvailability();

        try {
            Path botDir = Paths.get("").toAbsolutePath();
            YtDlpManager y = new YtDlpManager(botDir);
            this.ytDlpPath = y.prepare();
            this.ytDlpVersion = probeYtDlpVersion();
            y.startAutoUpdate(Duration.ofHours(6));
            logger.info("yt-dlp ready at {}", ytDlpPath);
            if (ytDlpVersion != null) {
                logger.info("yt-dlp version detected: {}", ytDlpVersion);
            }
        } catch (Exception e) {
            logger.error("yt-dlp の初期化に失敗。yt-dlpフォールバックは無効化されます。", e);
            this.ytDlpPath = null;
            this.ytDlpVersion = null;
        }

        // ==== ソース登録 ====
        if (bot.getConfig().isNicoNicoEnabled()) {
            // ニコニコ側の仕様変更などで初期化に失敗しても Bot 全体を停止させない
            try {
                registerSourceManager(
                        new com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager(
                                bot.getConfig().getNicoNicoEmailAddress(),
                                bot.getConfig().getNicoNicoPassword())
                );
            } catch (Exception e) {
                logger.error("ニコニコ動画のソースマネージャーの初期化に失敗しました。"
                        + "ニコニコ動画の再生は無効化されますが、Botは起動を続行します。", e);
            }
        }

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(true);
        yt.setPlaylistPageCount(10);

        // YouTube の署名解読を外部サービスへ任せる。
        // YouTube 側の仕様変更で解読に失敗しても、Bot を更新せずに追従できる。
        String cipherUrl = bot.getConfig().getYouTubeCipherUrl();
        if (cipherUrl != null) {
            try {
                yt.setCipherManager(new RemoteCipherManager(cipherUrl));
                logger.info("YouTubeの署名解読に外部サービスを使用します: {}", cipherUrl);
            } catch (Exception e) {
                logger.error("YouTube署名解読サービスの設定に失敗しました。内蔵の解読処理を使用します: {}", cipherUrl, e);
            }
        }
        if (bot.getConfig().isYouTubeOauth2Enabled()) {
            String refreshToken = bot.getConfig().getYouTubeOauth2RefreshToken();
            yt.useOauth2(refreshToken == null || refreshToken.isBlank() ? null : refreshToken, false);
            logger.info("YouTube OAuth2 を有効化しました。");
        }
        registerSourceManager(yt);

        AudioSourceManagers.registerRemoteSources(this);
        AudioSourceManagers.registerLocalSource(this);

        // エンコード・リサンプリング品質
        if (getConfiguration().getOpusEncodingQuality() != 10) {
            logger.debug("OpusEncodingQuality を 10 に設定（旧: {}）", getConfiguration().getOpusEncodingQuality());
            getConfiguration().setOpusEncodingQuality(10);
        }
        if (getConfiguration().getResamplingQuality() != AudioConfiguration.ResamplingQuality.HIGH) {
            logger.debug("ResamplingQuality を HIGH に設定（旧: {}）", getConfiguration().getResamplingQuality().name());
            getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        }
    }

    private void verifyFfmpegAvailability() {
        boolean ffmpegOk = isCommandAvailable("ffmpeg");
        boolean ffprobeOk = isCommandAvailable("ffprobe");
        this.ffmpegAvailable = ffmpegOk;
        this.ffprobeAvailable = ffprobeOk;
        if (ffmpegOk && ffprobeOk) {
            logger.info("ffmpeg / ffprobe を検出しました。外部コマンドを使用します。");
            return;
        }

        if (!ffmpegOk) {
            logger.warn("ffmpeg が見つかりません。実行環境へ ffmpeg をインストールしてください。");
        }
        if (!ffprobeOk) {
            logger.warn("ffprobe が見つかりません。実行環境へ ffprobe をインストールしてください。");
        }
        logger.warn("一部ソースの音声変換・抽出が失敗する可能性があります。");
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    public boolean isFfprobeAvailable() {
        return ffprobeAvailable;
    }

    public String getYtDlpVersion() {
        String latest = probeYtDlpVersion();
        if (latest != null) {
            ytDlpVersion = latest;
        }
        return ytDlpVersion;
    }

    private String probeYtDlpVersion() {
        if (ytDlpPath == null || !Files.isRegularFile(ytDlpPath)) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(ytDlpPath.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String lastNonEmpty = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        lastNonEmpty = trimmed;
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (lastNonEmpty != null) {
                return lastNonEmpty;
            }
            return process.exitValue() == 0 ? ytDlpVersion : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public Bot getBot() { return bot; }

    public boolean hasHandler(Guild guild) {
        return guild.getAudioManager().getSendingHandler() != null;
    }

    public AudioHandler setUpHandler(Guild guild) {
        AudioHandler handler;
        if (guild.getAudioManager().getSendingHandler() == null) {
            AudioPlayer player = createPlayer();
            player.setVolume(bot.getSettingsManager().getSettings(guild).getVolume());
            handler = new AudioHandler(this, guild, player);
            player.addListener(handler);

            // 再生中例外 / スタック → フォールバック
            player.addListener(new YtDlpExceptionListener(this, handler));

            guild.getAudioManager().setSendingHandler(handler);
        } else {
            handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        }
        return handler;
    }

    // =========================
    //  ロード段階：Lavaplayer失敗→yt-dlp へフォールバック
    // =========================
    @Override
    public Future<Void> loadItemOrdered(Object orderingKey, String identifier, AudioLoadResultHandler handler) {
        return super.loadItemOrdered(orderingKey, identifier, new AudioLoadResultHandler() {
            @Override public void trackLoaded(AudioTrack track) { handler.trackLoaded(track); }
            @Override public void playlistLoaded(AudioPlaylist playlist) { handler.playlistLoaded(playlist); }

            @Override
            public void noMatches() {
                if (shouldFallbackToYtDlp(identifier)) {
                    tryFallbackDownload(orderingKey, identifier, handler, null);
                } else handler.noMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (shouldFallbackToYtDlp(identifier)) {
                    tryFallbackDownload(orderingKey, identifier, handler, exception);
                } else handler.loadFailed(exception);
            }
        });
    }

    /** 文字列が http(s) の URL かどうか。 */
    private static boolean isHttpUrl(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * yt-dlp に渡す URL を求める。トラックの URI をそのまま使うので、提供元を問わず
     * yt-dlp が対応しているサイトであればフォールバックできる。
     * <p>
     * 識別子（{@link AudioTrack#getIdentifier()}）は使わない。ニコニコ動画の ID
     * （例: so29416460）は YouTube の動画 ID と区別できず、誤って
     * {@code https://www.youtube.com/watch?v=so29416460} を取得しにいってしまうため。
     *
     * @return 渡せる URL。無ければ {@code null}
     */
    String downloadUrlOf(AudioTrack track) {
        if (track == null) return null;
        AudioTrackInfo info = track.getInfo();
        String uri = info == null ? null : info.uri;
        return isHttpUrl(uri) ? uri.trim() : null;
    }

    /**
     * yt-dlp フォールバックの対象トラックかどうかを判定する。
     */
    boolean shouldFallbackToYtDlp(AudioTrack track) {
        if (track == null || ytDlpPath == null) return false;
        // ライブ配信は yt-dlp でダウンロードしても終わらない（タイムアウトまで待たせるだけ）
        if (track.getInfo() != null && track.getInfo().isStream) return false;
        return downloadUrlOf(track) != null;
    }

    /**
     * ロード段階での判定。トラックがまだ無いため、利用者が入力した文字列だけで判断する。
     */
    boolean shouldFallbackToYtDlp(String identifier) {
        if (ytDlpPath == null || identifier == null) return false;
        String id = identifier.trim();
        if (id.toLowerCase(Locale.ROOT).startsWith("ytsearch:")) return false;
        // URL であれば提供元を問わず yt-dlp に任せる
        if (isHttpUrl(id)) return true;
        if (NICO_ID_PATTERN.matcher(id.toLowerCase(Locale.ROOT)).matches()) return false; // ニコニコ動画のID
        return id.matches("^[a-zA-Z0-9_-]{10,}$"); // 素のYouTube ID
    }

    private void tryFallbackDownload(Object orderingKey,
                                     String identifier,
                                     AudioLoadResultHandler handler,
                                     FriendlyException cause) {
        logger.warn("ロードに失敗。yt-dlpでフォールバックします: {}", identifier);
        try {
            Path out = downloadViaYtDlp(identifier);
            if (out == null || !Files.isRegularFile(out))
                throw new IllegalStateException("yt-dlp出力が見つかりません: " + out);

            // LocalSource は file:// ではなく絶対パス文字列を期待
            super.loadItemOrdered(orderingKey, out.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                @Override public void trackLoaded(AudioTrack track) { handler.trackLoaded(track); }
                @Override public void playlistLoaded(AudioPlaylist playlist) { handler.playlistLoaded(playlist); }
                @Override public void noMatches() {
                    // 壊れたキャッシュを次回も使い続けないよう削除する
                    invalidateCache(out);
                    handler.noMatches();
                }
                @Override public void loadFailed(FriendlyException e) {
                    invalidateCache(out);
                    handler.loadFailed(e);
                }
            });
        } catch (Exception ex) {
            logger.error("yt-dlpフォールバックに失敗: {}", ex.toString());
            if (cause != null) {
                handler.loadFailed(new FriendlyException(
                        "YouTubeロード失敗。yt-dlpフォールバックも失敗: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS, cause));
            } else {
                handler.loadFailed(new FriendlyException(
                        "読み込めず、yt-dlpフォールバックも失敗: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS, ex));
            }
        }
    }

    /**
     * yt-dlp で音声を取得し、ローカルファイルのパスを返す。
     * <ul>
     *   <li>キャッシュ済みならダウンロードしない</li>
     *   <li>同じ動画のダウンロードが進行中ならその完了を待つ（同じ出力先へ多重書き込みしない）</li>
     *   <li>直近に失敗した動画は即座に失敗させ、無駄な再実行で待たせない</li>
     * </ul>
     */
    Path downloadViaYtDlp(String input) throws Exception {
        if (ytDlpPath == null) throw new IllegalStateException("yt-dlp が利用できません");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("yt-dlp に渡す URL がありません");
        Path botRoot = Paths.get("").toAbsolutePath().normalize();
        Path cacheDir = botRoot.resolve("cache");
        Files.createDirectories(cacheDir);

        String url = toYoutubeUrl(input);
        String key = cacheKeyFor(url);

        Long failedUntil = recentFailures.get(key);
        if (failedUntil != null) {
            if (failedUntil > System.currentTimeMillis()) {
                throw new IllegalStateException("直近に yt-dlp での取得に失敗した動画のため、再試行を見送ります: " + key);
            }
            recentFailures.remove(key, failedUntil);
        }

        Path cached = findCachedFile(cacheDir, key);
        if (cached != null) {
            logger.info("yt-dlp のキャッシュを再利用: {}", cached);
            return cached;
        }

        CompletableFuture<Path> mine = new CompletableFuture<>();
        CompletableFuture<Path> existing = inFlightDownloads.putIfAbsent(key, mine);
        if (existing != null) {
            logger.info("同じ動画のダウンロードが進行中のため完了を待ちます: {}", key);
            try {
                return existing.get(YTDLP_TIMEOUT_SEC * 2 + 30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
            }
        }
        try {
            Path out = runDownload(botRoot, cacheDir, url, key);
            mine.complete(out);
            return out;
        } catch (Exception ex) {
            mine.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlightDownloads.remove(key, mine);
        }
    }

    /** 直近に yt-dlp 取得へ失敗した動画かどうか（フォールバック開始前の事前チェック用） */
    boolean isRecentlyFailed(String input) {
        if (input == null) return false;
        Long failedUntil = recentFailures.get(cacheKeyFor(toYoutubeUrl(input)));
        return failedUntil != null && failedUntil > System.currentTimeMillis();
    }

    private void markFailure(String key, boolean permanent) {
        long now = System.currentTimeMillis();
        if (recentFailures.size() > 256) recentFailures.values().removeIf(until -> until <= now);
        recentFailures.put(key, now + (permanent ? PERMANENT_FAILURE_TTL_MS : FAILURE_TTL_MS));
    }

    private static boolean isPermanentError(String outputTail) {
        return outputTail != null && PERMANENT_ERROR.matcher(outputTail).find();
    }

    private Path findCachedFile(Path cacheDir, String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        for (String ext : CACHE_EXTENSIONS) {
            Path candidate = cacheDir.resolve(videoId + "." + ext);
            try {
                if (Files.isRegularFile(candidate) && Files.size(candidate) > 0) return candidate;
            } catch (IOException ignored) {
                // 読めないファイルは無いものとして扱う
            }
        }
        return null;
    }

    /** 読み込めなかったキャッシュファイルを削除する（次回は再ダウンロードさせる） */
    void invalidateCache(Path file) {
        if (file == null) return;
        try {
            if (Files.deleteIfExists(file)) logger.warn("読み込めないキャッシュを削除しました: {}", file);
        } catch (IOException e) {
            logger.warn("キャッシュの削除に失敗: {} ({})", file, e.toString());
        }
    }

    private Path runDownload(Path botRoot, Path cacheDir, String url, String key) throws Exception {
        logger.info("yt-dlp でダウンロード: {}", url);

        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlpPath.toString());

        // 変換なしで Lavaplayer が読める形式を優先（webm/opus → m4a/aac → その他）
        Collections.addAll(cmd,
                "--no-playlist",
                "--ignore-config",
                "--no-progress",
                "--newline",
                "--restrict-filenames",
                "--force-overwrites",
                // 応答が止まった接続で長時間待たない
                "--socket-timeout", "30",
                "--retries", "5",
                "--fragment-retries", "5",
                "-f", "bestaudio[ext=webm][acodec=opus]/bestaudio[ext=m4a]/bestaudio",
                "--no-post-overwrites",
                "--output", cacheDir.resolve(key + ".%(ext)s").toString(),
                "--print", "after_move:filepath"
        );

        // YouTube の認証情報は YouTube にしか渡さない（他サイトへ送信してしまわないように）
        if (isYoutubeUrl(url)) {
            String ytEmail = bot.getConfig().getYouTubeEmailAddress();
            String ytPass = bot.getConfig().getYouTubePassword();
            if (ytEmail != null && !ytEmail.isBlank() && ytPass != null && !ytPass.isBlank()) {
                cmd.add("--username");
                cmd.add(ytEmail);
                cmd.add("--password");
                cmd.add(ytPass);
            }
        }

        cmd.add(url);

        YtDlpRunResult result = null;
        List<List<String>> retryExtras = isYoutubeUrl(url)
                ? List.of(
                        Collections.emptyList(),
                        List.of("--force-ipv4"),
                        List.of("--extractor-args", "youtube:player_client=tv,ios,web"))
                : List.of(
                        Collections.emptyList(),
                        List.of("--force-ipv4"));
        for (int i = 0; i < retryExtras.size(); i++) {
            List<String> extra = retryExtras.get(i);
            List<String> attempt = new ArrayList<>(cmd.size() + extra.size());
            attempt.addAll(cmd.subList(0, cmd.size() - 1));
            attempt.addAll(extra);
            attempt.add(cmd.get(cmd.size() - 1));

            result = runYtDlp(botRoot, attempt);
            if (result.exitCode == 0) break;
            if (result.timedOut) {
                logger.warn("yt-dlp がタイムアウト ({}秒, extra={}): {}", YTDLP_TIMEOUT_SEC, extra, result.outputTail);
                // IPv6 起因の停滞に備えて IPv4 強制で 1 回だけ再試行。それでも駄目なら諦める
                if (i >= 1) break;
                continue;
            }
            logger.warn("yt-dlp失敗 (exit={}, extra={}): {}", result.exitCode, extra, result.outputTail);
            if (isPermanentError(result.outputTail)) {
                logger.warn("再試行しても解決しないエラーのため中止: {}", key);
                break;
            }
        }

        if (result == null) throw new RuntimeException("yt-dlp実行に失敗: 実行結果なし");
        if (result.timedOut) {
            markFailure(key, false);
            throw new RuntimeException("yt-dlp timeout (" + YTDLP_TIMEOUT_SEC + "s)");
        }
        if (result.exitCode != 0) {
            markFailure(key, isPermanentError(result.outputTail));
            String msg = result.outputTail.isBlank() ? "" : " / output tail: " + result.outputTail;
            throw new RuntimeException("yt-dlp exit code=" + result.exitCode + msg);
        }

        String lastNonEmpty = result.lastNonEmptyLine;
        if (lastNonEmpty == null) {
            Path guess = findCachedFile(cacheDir, key);
            if (guess != null) return guess;
            markFailure(key, false);
            throw new FileNotFoundException("最終パス不明（printが空）。キャッシュからも見つかりません: " + key);
        }

        Path out = Paths.get(lastNonEmpty);
        if (!out.isAbsolute()) out = botRoot.resolve(out).normalize();
        if (!Files.isRegularFile(out)) {
            markFailure(key, false);
            throw new FileNotFoundException("出力が存在しません: " + out);
        }
        logger.info("yt-dlp 完了: {}", out);
        return out;
    }

    /**
     * yt-dlp を 1 回実行する。標準出力は別スレッドで読み取り、
     * プロセスが出力を止めたまま固まっても {@link #YTDLP_TIMEOUT_SEC} で確実に打ち切る。
     */
    private YtDlpRunResult runYtDlp(Path botRoot, List<String> cmd) throws Exception {
        ytDlpSlots.acquire();
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(botRoot.toFile());
            pb.redirectErrorStream(true);
            // 日本語パス対応：Python(yt-dlp)の出力を UTF-8 に固定
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            proc = pb.start();
            OutputCollector collector = new OutputCollector(proc);
            Thread reader = new Thread(collector, "yt-dlp-output");
            reader.setDaemon(true);
            reader.start();

            boolean finished = proc.waitFor(YTDLP_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                proc.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(5_000);
            return new YtDlpRunResult(finished ? proc.exitValue() : -1, !finished,
                    collector.lastNonEmpty(), collector.tail());
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly(); // 割り込み時にプロセスを残さない
            ytDlpSlots.release();
        }
    }

    /** yt-dlp の出力を読み取り、最後の非空行と末尾数行を保持する */
    private final class OutputCollector implements Runnable {
        private final Process proc;
        private String lastNonEmpty;
        private final Deque<String> tail = new ArrayDeque<>();

        OutputCollector(Process proc) {
            this.proc = proc;
        }

        @Override
        public void run() {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (this) {
                        if (!line.isBlank()) lastNonEmpty = line.trim();
                        if (tail.size() >= 12) tail.removeFirst();
                        tail.addLast(line);
                    }
                    logger.debug("[yt-dlp] {}", line);
                }
            } catch (IOException e) {
                logger.debug("yt-dlp の出力読み取りを終了: {}", e.toString());
            }
        }

        synchronized String lastNonEmpty() {
            return lastNonEmpty;
        }

        synchronized String tail() {
            return String.join(" | ", tail);
        }
    }

    private static final class YtDlpRunResult {
        final int exitCode;
        final boolean timedOut;
        final String lastNonEmptyLine;
        final String outputTail;

        YtDlpRunResult(int exitCode, boolean timedOut, String lastNonEmptyLine, String outputTail) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.lastNonEmptyLine = lastNonEmptyLine;
            this.outputTail = outputTail == null ? "" : outputTail;
        }
    }

    /** Bot 終了時に呼ぶ。進行中のフォールバックを中断する */
    public void shutdown() {
        fallbackExecutor.shutdownNow();
    }

    /** YouTube の URL かどうか。YouTube 専用の引数を出し分けるために使う。 */
    private static boolean isYoutubeUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com/") || lower.contains("youtu.be/");
    }

    /** URL でなければ素の YouTube ID とみなして URL 化する。 */
    private String toYoutubeUrl(String input) {
        String s = input == null ? "" : input.trim();
        if (isHttpUrl(s)) return s;
        return "https://www.youtube.com/watch?v=" + s;
    }

    /**
     * キャッシュファイル名と失敗記録に使うキー。
     * YouTube は従来どおり動画IDを使い、既存のキャッシュをそのまま活かす。
     * それ以外は URL から一意な名前を作る（yt-dlp の {@code %(id)s} は事前に分からないため）。
     */
    private String cacheKeyFor(String url) {
        String videoId = tryExtractYoutubeId(url);
        if (videoId != null && !videoId.isBlank()) return videoId;
        return "dl_" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private String tryExtractYoutubeId(String url) {
        try {
            int vIndex = url.indexOf("v=");
            if (vIndex >= 0) {
                String v = url.substring(vIndex + 2);
                int amp = v.indexOf('&');
                return amp > 0 ? v.substring(0, amp) : v;
            }
            int idx = url.indexOf("youtu.be/");
            if (idx >= 0) {
                String v = url.substring(idx + "youtu.be/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
            idx = url.indexOf("/shorts/");
            if (idx >= 0) {
                String v = url.substring(idx + "/shorts/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ======== 置き換え時のメタ引き継ぎ用 ========
    static final class TrackContext {
        final AudioTrackInfo originalInfo; // 元のYouTube等のメタ
        final Object userData;             // RequestMetadata など既存のユーザーデータ
        TrackContext(AudioTrackInfo info, Object userData) {
            this.originalInfo = info;
            this.userData = userData;
        }
    }

    // 新しいローカル・トラックへ、元トラックの情報を持たせる
    void applyReplacementContext(AudioTrack newTrack, AudioTrack oldTrack) {
        Object ud = oldTrack.getUserData(); // RequestMetadata 等
        newTrack.setUserData(new TrackContext(oldTrack.getInfo(), ud));
    }

    // ==========================================================
    // 再生中例外 / スタック → yt-dlp フォールバック
    // ==========================================================
    private static class YtDlpExceptionListener extends AudioEventAdapter {
        /** 差し替え時に再開位置を引き継ぐ際、先頭・末尾からこの範囲内なら引き継がない */
        private static final long RESUME_MARGIN_MS = 3_000;

        private final PlayerManager pm;
        private final AudioHandler handler;

        YtDlpExceptionListener(PlayerManager pm, AudioHandler handler) {
            this.pm = pm;
            this.handler = handler;
        }

        @Override
        public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
            String id = track != null ? track.getIdentifier() : null;
            pm.logger.warn("再生中に例外発生。id={} msg={}", id, exception.getMessage());
            startFallback(player, track, exception.getMessage(), AudioHandler.FallbackOrigin.EXCEPTION);
        }

        @Override
        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
            if (track == null) return;
            String id = track.getIdentifier();
            if (!pm.shouldFallbackToYtDlp(track)) {
                // ニコニコ動画などは再生前に yt-dlp でのダウンロードが入るため、
                // スタック扱いになっても待つ以外にできることはない
                pm.logger.debug("トラックがスタックしましたが、フォールバック対象外です: id={}, stuck={}ms", id, thresholdMs);
                return;
            }
            pm.logger.warn("トラックがスタック。yt-dlpへフォールバックを試行: id={}, stuck={}ms", id, thresholdMs);
            startFallback(player, track, "再生が " + (thresholdMs / 1000) + " 秒以上停止しました。",
                    AudioHandler.FallbackOrigin.STUCK);
        }

        private void startFallback(AudioPlayer player, AudioTrack track, String reason,
                                   AudioHandler.FallbackOrigin origin) {
            boolean fromException = origin == AudioHandler.FallbackOrigin.EXCEPTION;
            if (!pm.shouldFallbackToYtDlp(track)) {
                // フォールバックできないので、通常の onTrackEnd に任せて次の曲へ進む
                if (fromException) handler.notifyTrackFailed(track, reason);
                return;
            }
            String id = pm.downloadUrlOf(track);
            if (pm.isRecentlyFailed(id)) {
                pm.logger.info("直近に yt-dlp 取得へ失敗した動画のためフォールバックを見送り: {}", id);
                handler.notifyTrackFailed(track, reason);
                // スタック中のトラックは自然には終わらないので、ここで打ち切って次へ進める
                if (!fromException && player.getPlayingTrack() == track) player.stopTrack();
                return;
            }
            switch (handler.beginFallback(track, origin)) {
                case ALREADY_PENDING:
                    pm.logger.debug("このトラックは既にフォールバック中: {}", id);
                    return;
                case BUSY:
                    pm.logger.info("別トラックのフォールバックが進行中のため見送り: {}", id);
                    if (fromException) handler.notifyTrackFailed(track, reason);
                    return;
                case STARTED:
                    break;
            }
            try {
                pm.fallbackExecutor.execute(() -> runFallback(track, reason));
            } catch (RejectedExecutionException e) {
                // シャットダウン中。抑制した終了処理を取り消して通常どおり進める
                handler.failFallback(track, reason);
            }
        }

        private void runFallback(AudioTrack track, String reason) {
            Path out;
            try {
                out = pm.downloadViaYtDlp(pm.downloadUrlOf(track));
                if (out == null || !Files.isRegularFile(out))
                    throw new IllegalStateException("yt-dlp出力が見つからない: " + out);
            } catch (Exception ex) {
                pm.logger.error("yt-dlpフォールバック（再生中）に失敗: {}", ex.toString());
                handler.failFallback(track, reason);
                return;
            }

            pm.logger.info("yt-dlpフォールバック成功。ローカルへ差し替え再生: {}", out);
            Path file = out;
            // ローカルファイルのロードは順序保証が不要なので、ギルドの通常ロード列に並ばせない
            pm.loadItem(file.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                @Override public void trackLoaded(AudioTrack newTrack) {
                    replace(track, newTrack);
                }
                @Override public void playlistLoaded(AudioPlaylist playlist) {
                    AudioTrack t = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                    if (t != null) replace(track, t);
                    else noMatches();
                }
                @Override public void noMatches() {
                    pm.logger.error("ローカル差し替えのロードに失敗（noMatches）: {}", file);
                    pm.invalidateCache(file);
                    handler.failFallback(track, "ダウンロードしたファイルを読み込めませんでした。");
                }
                @Override public void loadFailed(FriendlyException e) {
                    pm.logger.error("ローカル差し替えのロードに失敗: {}", e.getMessage());
                    pm.invalidateCache(file);
                    handler.failFallback(track, e.getMessage());
                }
            });
        }

        private void replace(AudioTrack failed, AudioTrack replacement) {
            // メタ引き継ぎ
            pm.applyReplacementContext(replacement, failed);

            // 途中で止まった場合は、聞こえていた位置から再開する
            long position = failed.getPosition();
            long duration = replacement.getDuration();
            if (replacement.isSeekable() && position > RESUME_MARGIN_MS
                    && duration != Units.DURATION_MS_UNKNOWN && position < duration - RESUME_MARGIN_MS) {
                replacement.setPosition(position);
                pm.logger.info("差し替えトラックを {}ms から再開: {}", position, failed.getIdentifier());
            }

            String id = failed.getIdentifier();
            switch (handler.completeFallback(failed, replacement)) {
                case REPLACED, QUEUED, STARTED -> {
                    // 差し替え成功
                }
                case DISCARDED_CANCELLED ->
                        pm.logger.info("再生が停止/スキップされたため、差し替えを破棄: {}", id);
                case DISCARDED_RECOVERED ->
                        pm.logger.info("対象トラックが自力で再生を終えたため、差し替えを破棄: {}", id);
                case DISCARDED_DISCONNECTED ->
                        pm.logger.warn("ボイスチャンネルから退出済みのため、差し替えを破棄: {}", id);
            }
        }
    }
}
