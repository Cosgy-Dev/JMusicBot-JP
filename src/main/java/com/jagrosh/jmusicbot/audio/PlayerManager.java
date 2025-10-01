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
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.cosgy.jmusicbot.util.YtDlpManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.*;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerManager extends DefaultAudioPlayerManager {
    private final Bot bot;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // yt-dlp
    private Path ytDlpPath;

    public PlayerManager(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        try {
            Path botDir = Paths.get("").toAbsolutePath();
            YtDlpManager y = new YtDlpManager(botDir);
            this.ytDlpPath = y.prepare();
            y.startAutoUpdate(Duration.ofHours(6));
            logger.info("yt-dlp ready at {}", ytDlpPath);
        } catch (Exception e) {
            logger.error("yt-dlp の初期化に失敗。YouTubeフォールバックは無効化されます。", e);
            this.ytDlpPath = null;
        }

        // ==== ソース登録 ====
        if (bot.getConfig().isNicoNicoEnabled()) {
            registerSourceManager(
                    new com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager(
                            bot.getConfig().getNicoNicoEmailAddress(),
                            bot.getConfig().getNicoNicoPassword())
            );
        }

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(true, new Music(), new TvHtml5Embedded(), new AndroidMusic(), new Web(), new WebEmbedded(), new Android(), new Ios());
        yt.setPlaylistPageCount(10);
        yt.useOauth2(null, false);
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


            player.addListener(new YtDlpExceptionListener(this, player, handler));

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


    boolean shouldFallbackToYtDlp(String identifier) {
        if (ytDlpPath == null || identifier == null) return false;
        String id = identifier.toLowerCase(Locale.ROOT);
        if (id.startsWith("ytsearch:")) return false;
        if (id.startsWith("http://") || id.startsWith("https://")) {
            return id.contains("youtube.com/") || id.contains("youtu.be/");
        }
        return id.matches("^[a-zA-Z0-9_-]{10,}$"); // 素のID
    }

    private void tryFallbackDownload(Object orderingKey,
                                     String identifier,
                                     AudioLoadResultHandler handler,
                                     FriendlyException cause) {
        logger.warn("YouTubeのロードに失敗。yt-dlpでフォールバックします: {}", identifier);
        try {
            Path out = downloadViaYtDlp(identifier);
            if (out == null || !Files.isRegularFile(out))
                throw new IllegalStateException("yt-dlp出力が見つかりません: " + out);

            super.loadItemOrdered(orderingKey, out.toUri().toString(), handler);
        } catch (Exception ex) {
            logger.error("yt-dlpフォールバックに失敗: {}", ex.toString());
            if (cause != null) {
                handler.loadFailed(new FriendlyException(
                        "YouTubeロード失敗。yt-dlpフォールバックも失敗: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS, cause));
            } else {
                handler.loadFailed(new FriendlyException(
                        "YouTube未マッチ。yt-dlpフォールバックも失敗: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS, ex));
            }
        }
    }

    Path downloadViaYtDlp(String input) throws Exception {
        Path botRoot = Paths.get("").toAbsolutePath().normalize();
        Path cacheDir = botRoot.resolve("cache");
        Files.createDirectories(cacheDir);

        String url = toYoutubeUrl(input);
        logger.info("yt-dlp でダウンロード: {}", url);

        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlpPath.toString());
        /*Collections.addAll(cmd,
                "--no-playlist",
                "--ignore-config",
                "--no-progress",
                "--newline",
                "--restrict-filenames",
                "--force-overwrites",
                "--extract-audio",
                "--audio-format", "wav",
                "--output", cacheDir.resolve("%(id)s.%(ext)s").toString(),
                "--print", "after_move:filepath"
        );*/

        Collections.addAll(cmd,
                "--no-playlist",
                "--ignore-config",
                "--no-progress",
                "--newline",
                "--restrict-filenames",
                "--force-overwrites",
                "-f", "bestaudio[ext=webm][acodec=opus]/bestaudio[ext=m4a]/bestaudio",
                "--no-post-overwrites",
                "--output", cacheDir.resolve("%(id)s.%(ext)s").toString(),
                "--print", "after_move:filepath"
        );

        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(botRoot.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String lastNonEmpty = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) lastNonEmpty = line.trim();
                logger.debug("[yt-dlp] {}", line);
            }
        }

        if (!proc.waitFor(600, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("yt-dlp timeout (600s)");
        }
        if (proc.exitValue() != 0) throw new RuntimeException("yt-dlp exit code=" + proc.exitValue());

        if (lastNonEmpty == null) {
            String id = tryExtractYoutubeId(url);
            if (id == null) throw new IllegalStateException("最終パス不明（printが空）。ID抽出も失敗");
            Path guess = cacheDir.resolve(id + ".wav");
            if (Files.isRegularFile(guess)) return guess;
            Path guessWebm = cacheDir.resolve(id + ".webm");
            if (Files.isRegularFile(guessWebm)) return guessWebm;
            throw new FileNotFoundException("出力不明");
        }

        Path out = Paths.get(lastNonEmpty);
        if (!out.isAbsolute()) out = botRoot.resolve(out).normalize();
        if (!Files.isRegularFile(out)) throw new FileNotFoundException("出力が存在しません: " + out);
        logger.info("yt-dlp 完了: {}", out);
        return out;
    }

    private String toYoutubeUrl(String input) {
        String s = input == null ? "" : input.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return "https://www.youtube.com/watch?v=" + s;
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

    // ==========================================================
    // 再生中例外 → yt-dlp フォールバック
    // ==========================================================
    private static class YtDlpExceptionListener extends AudioEventAdapter {
        private final PlayerManager pm;
        private final AudioPlayer player;
        private final AudioHandler handler;
        private final AtomicBoolean fallingBack = new AtomicBoolean(false);
        private final Set<String> attempted = Collections.synchronizedSet(new HashSet<>());

        YtDlpExceptionListener(PlayerManager pm, AudioPlayer player, AudioHandler handler) {
            this.pm = pm;
            this.player = player;
            this.handler = handler;
        }

        @Override
        public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
            String id = track != null ? track.getIdentifier() : null;
            pm.logger.warn("再生中に例外発生。id={} msg={}", id, exception.getMessage());

            if (track == null || !pm.shouldFallbackToYtDlp(id)) return;

            handler.suppressAutoLeaveOnce();

            if (!attempted.add(id)) {
                pm.logger.debug("このトラックは既にフォールバックを試行済み: {}", id);
                return;
            }
            if (!fallingBack.compareAndSet(false, true)) return;

            CompletableFuture.runAsync(() -> {
                try {
                    Path out = pm.downloadViaYtDlp(id);
                    if (out == null || !Files.isRegularFile(out))
                        throw new IllegalStateException("yt-dlp出力が見つからない: " + out);

                    pm.logger.info("yt-dlpフォールバック成功。ローカルへ差し替え再生: {}", out);

                    pm.loadItemOrdered(handler, out.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                        @Override public void trackLoaded(AudioTrack newTrack) {
                            player.startTrack(newTrack, false);
                        }
                        @Override public void playlistLoaded(AudioPlaylist playlist) {
                            AudioTrack t = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                            if (t != null) player.startTrack(t, false);
                            else noMatches();
                        }
                        @Override
                        public void noMatches() {
                            pm.logger.error("ローカル差し替えのロードに失敗（noMatches）: {}", out);
                        }
                        @Override public void loadFailed(FriendlyException e) {
                            pm.logger.error("ローカル差し替えのロードに失敗: {}", e.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    pm.logger.error("yt-dlpフォールバック（再生中）に失敗: {}", ex.toString());
                } finally {
                    fallingBack.set(false);
                }
            });
        }

        @Override
        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
            if (track == null) return;
            String id = track.getIdentifier();
            if (pm.shouldFallbackToYtDlp(id)) {
                pm.logger.warn("トラックがスタック。yt-dlpへフォールバックを試行: id={}, stuck={}ms", id, thresholdMs);
                onTrackException(player, track, new FriendlyException("stuck " + thresholdMs + "ms",
                        FriendlyException.Severity.SUSPICIOUS, null));
            }
        }
    }
}
