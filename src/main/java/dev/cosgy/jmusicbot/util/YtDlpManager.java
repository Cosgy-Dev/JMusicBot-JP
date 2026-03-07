package dev.cosgy.jmusicbot.util;

import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YtDlpManager {
    private static final Logger log = LoggerFactory.getLogger(YtDlpManager.class);

    private static final String GITHUB_LATEST_BASE =
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final String SHA256_FILE = "SHA2-256SUMS";
    private static final int PROC_TIMEOUT_SEC = 120;

    // 自動更新の既定設定（24h・起動分散のため初回 5–30 分ランダム遅延）
    private static final Duration DEFAULT_UPDATE_INTERVAL = Duration.ofDays(1);
    private static final boolean AUTO_UPDATE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("jmusicbot.ytdlp.autoUpdate", "true"));
    private static final String UPDATE_TO = System.getProperty("jmusicbot.ytdlp.updateTo", "").trim(); // "", "stable", "nightly", "2025.XX"

    // 準備済みフラグとパス（複数回実行を防ぐ）
    private static final AtomicBoolean prepared = new AtomicBoolean(false);
    private static volatile Path preparedPath = null;

    // 自動更新スケジューラ（JVM内で1本のみ）
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "yt-dlp-auto-update");
                t.setDaemon(true);
                return t;
            });
    private static volatile ScheduledFuture<?> updateFuture = null;
    private static final AtomicBoolean updatingNow = new AtomicBoolean(false);

    private final Path binDir;
    private final Path exePath;
    private final String assetName;

    public YtDlpManager(Path botDir) {
        log.debug("YtDlpManagerを初期化中: botDir={}", botDir);
        this.binDir = botDir.resolve("bin");
        this.assetName = pickAssetForCurrentPlatform();
        this.exePath = binDir.resolve(assetNameForLocal(assetName));
        log.debug("実行ファイルパス: {}", exePath);
    }

    /** yt-dlpの準備を行い、実行可能なパスを返す（ダウンロード/検証/権限設定/自己更新1回） */
    public Path prepare() throws Exception {
        if (prepared.get() && preparedPath != null) {
            log.debug("yt-dlpは既に準備済みです: {}", preparedPath);
            // 自動更新が未起動なら起動
            if (AUTO_UPDATE_ENABLED) startAutoUpdateIfNeeded(DEFAULT_UPDATE_INTERVAL);
            return preparedPath;
        }

        synchronized (YtDlpManager.class) {
            if (prepared.get() && preparedPath != null) {
                if (AUTO_UPDATE_ENABLED) startAutoUpdateIfNeeded(DEFAULT_UPDATE_INTERVAL);
                return preparedPath;
            }

            log.info("yt-dlpの準備を開始します");
            Files.createDirectories(binDir);
            log.debug("binディレクトリを作成/確認: {}", binDir);

            boolean needDownload = !Files.isRegularFile(exePath);
            if (!needDownload) {
                log.debug("既存のyt-dlp実行ファイルを検証中...");
                if (!isExecutableOk(exePath)) {
                    log.warn("既存のyt-dlpが破損/実行不可。再ダウンロードします。");
                    needDownload = true;
                }
            }

            if (needDownload) {
                log.info("yt-dlpをダウンロードしています...");
                downloadAndVerify();
                grantExecuteIfNeeded(exePath);
                log.info("yt-dlpのダウンロードと検証が完了しました");
            } else {
                log.info("既存のyt-dlpを使用します");
            }

            // 起動確認
            log.debug("yt-dlpのバージョンを確認中...");
            String version = runAndCapture(exePath.toString(), "--version").trim();
            if (version.isEmpty()) throw new IllegalStateException("yt-dlp launch failed");
            log.info("yt-dlpのバージョン: {}", version);

            // 一度だけ手動自己更新（失敗しても続行）
            try {
                log.info("yt-dlpの自己更新を実行中...");
                runUpdateCommandWithTimeout(300, exePath);
                log.info("yt-dlpの自己更新が完了しました");
            } catch (Exception e) {
                log.warn("yt-dlpの自己更新に失敗しましたが、続行します: {}", e.getMessage());
            }

            preparedPath = exePath;
            prepared.set(true);

            // 自動更新を開始
            if (AUTO_UPDATE_ENABLED) startAutoUpdateIfNeeded(DEFAULT_UPDATE_INTERVAL);
            else log.info("yt-dlp自動更新は無効化されています（-Djmusicbot.ytdlp.autoUpdate=false）");

            log.info("yt-dlpの準備が完了しました: {}", exePath);
            return exePath;
        }
    }

    // ————— 自動更新制御 —————

    /** 既定間隔（24h）で自動更新を開始。既に開始済みなら何もしない */
    public synchronized void startAutoUpdate() {
        startAutoUpdateIfNeeded(DEFAULT_UPDATE_INTERVAL);
    }

    /** 任意の間隔で自動更新を開始（例：Duration.ofHours(6)）。既に開始済みなら何もしない */
    public synchronized void startAutoUpdate(Duration interval) {
        startAutoUpdateIfNeeded(interval != null ? interval : DEFAULT_UPDATE_INTERVAL);
    }

    /** 自動更新を停止 */
    public synchronized void stopAutoUpdate() {
        if (updateFuture != null) {
            updateFuture.cancel(false);
            updateFuture = null;
            log.info("yt-dlp自動更新を停止しました");
        }
    }

    private void startAutoUpdateIfNeeded(Duration interval) {
        if (updateFuture != null && !updateFuture.isCancelled()) return;

        long periodSec = Math.max(60, interval.getSeconds()); // 最短60秒
        long initialDelaySec = ThreadLocalRandom.current().nextLong(300, 1800); // 5〜30分分散

        // シャットダウン時の後片付け
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopAutoUpdate, "yt-dlp-auto-update-shutdown"));
        } catch (IllegalStateException ignored) { /* 既に終了中 */ }

        updateFuture = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (!prepared.get()) {
                    // まだ準備完了していない場合は準備（他スレッドと競合しない）
                    try { prepare(); } catch (Exception e) {
                        log.warn("自動更新前の準備に失敗: {}", e.toString());
                        return;
                    }
                }
                performSelfUpdate(); // 実処理
            } catch (Throwable t) {
                log.warn("yt-dlp自動更新ループでエラー: {}", t.toString());
            }
        }, initialDelaySec, periodSec, TimeUnit.SECONDS);

        log.info("yt-dlp自動更新を開始しました: 初回遅延={}秒, 周期={}秒", initialDelaySec, periodSec);
    }

    /** 実際の自己更新（同時実行防止付き） */
    private void performSelfUpdate() {
        if (!updatingNow.compareAndSet(false, true)) {
            log.debug("別の更新処理が実行中のためスキップ");
            return;
        }
        try {
            Path target = (preparedPath != null) ? preparedPath : exePath;
            if (!Files.isRegularFile(target)) {
                log.warn("更新対象のyt-dlpが見つかりません: {}", target);
                return;
            }
            log.info("[yt-dlp] 自動更新チェックを実行します...");
            runUpdateCommandWithTimeout(600, target); // 自動更新は余裕を持って最大600秒
        } catch (Exception e) {
            log.warn("yt-dlp自動更新に失敗: {}", e.toString());
        } finally {
            updatingNow.set(false);
        }
    }

    private void runUpdateCommandWithTimeout(int timeoutSec, Path exe) throws Exception {
        if (UPDATE_TO.isEmpty()) {
            runAndCaptureWithProgressTimeout(timeoutSec, exe.toString(), "-U");
        } else {
            runAndCaptureWithProgressTimeout(timeoutSec, exe.toString(), "--update-to", UPDATE_TO);
        }
    }

    // ————— helpers —————

    /** 現在のプラットフォームに適したアセット名を選択 */
    private static String pickAssetForCurrentPlatform() {
        return pickAssetForPlatform(
                System.getProperty("os.name"),
                System.getProperty("os.arch")
        );
    }

    static String pickAssetForPlatform(String osName, String archName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = archName.toLowerCase(Locale.ROOT);

        log.debug("プラットフォームを検出: OS={}, Arch={}", os, arch);
        if (os.contains("mac") || os.contains("darwin")) {
            log.debug("macOS版を選択");
            return "yt-dlp_macos";
        } else if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                log.debug("Windows ARM64版を選択");
                return "yt-dlp_arm64.exe";
            }
            log.debug("Windows版を選択");
            return "yt-dlp.exe";
        } else {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                log.debug("Linux ARM64版を選択");
                return "yt-dlp_linux_aarch64";
            }
            log.debug("Linux版を選択");
            return "yt-dlp_linux";
        }
    }

    /** ローカル配置名（固定） */
    private static String assetNameForLocal(String asset) {
        return asset.endsWith(".exe") ? "yt-dlp.exe" : "yt-dlp";
    }

    /** ダウンロード＆SHA256検証＆配置 */
    private void downloadAndVerify() throws Exception {
        log.info("yt-dlpのダウンロードを開始: {}", assetName);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        // 1) 本体DL
        URI binUri = URI.create(GITHUB_LATEST_BASE + assetName);
        log.debug("ダウンロードURL: {}", binUri);
        // 同一ドライブ/ディレクトリ上に一時ファイルを作成して、別ドライブ間移動による失敗（例: AppData→D:\ 等）を回避
        Path tmp = Files.createTempFile(binDir, "yt-dlp-", ".dl");
        log.debug("一時ファイル: {}", tmp);

        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(binUri).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );
        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            downloadWithProgress(in, out, totalBytes);
            log.info("ダウンロード完了");
        }

        // 2) SHA256検証
        log.info("SHA256チェックサムを検証中...");
        URI sumsUri = URI.create(GITHUB_LATEST_BASE + SHA256_FILE);
        String sums = client.send(HttpRequest.newBuilder(sumsUri).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        String expected = parseSha256ForAsset(sums, assetName);

        if (expected != null) {
            log.debug("期待されるSHA256: {}", expected);
            String actual = sha256Hex(tmp);
            log.debug("実際のSHA256: {}", actual);
            if (!expected.equalsIgnoreCase(actual)) {
                Files.deleteIfExists(tmp);
                log.error("SHA256不一致。期待: {}, 実際: {}", expected, actual);
                throw new SecurityException("SHA-256 mismatch for " + assetName);
            }
            log.info("✓ SHA256検証成功");
        } else {
            log.warn("SHA256チェックサムが見つからず、検証をスキップします。");
        }

        // 3) 配置
        log.debug("yt-dlpを最終位置に移動: {} -> {}", tmp, exePath);
        try {
            // 可能ならアトミックに置き換え（同一ファイルシステム前提）。
            Files.move(tmp, exePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 異なるドライブ/ファイルシステム等でATOMIC_MOVEが不可能な場合は通常のMOVEで再試行
            log.debug("ATOMIC_MOVEが未サポートのため通常MOVEで再試行します: {}", e.getMessage());
            Files.move(tmp, exePath, StandardCopyOption.REPLACE_EXISTING);
        }
        grantExecuteIfNeeded(exePath);
        log.info("yt-dlpの配置が完了しました");
    }

    /** 進捗表示付きでダウンロード */
    private void downloadWithProgress(InputStream in, OutputStream out, long totalBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long downloadedBytes = 0;
        int bytesRead;
        long lastProgressTime = System.currentTimeMillis();
        int lastProgress = -1;

        log.info("ダウンロード開始: {} (サイズ: {})", "yt-dlp", formatBytes(totalBytes));

        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            downloadedBytes += bytesRead;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastProgressTime >= 500) {
                if (totalBytes > 0) {
                    int progress = (int) ((downloadedBytes * 100) / totalBytes);
                    if (progress != lastProgress) {
                        String progressBar = createProgressBar(downloadedBytes, totalBytes);
                        log.info("ダウンロード進捗: {} {}% ({}/{})",
                                progressBar, progress, formatBytes(downloadedBytes), formatBytes(totalBytes));
                        lastProgress = progress;
                    }
                } else {
                    log.info("ダウンロード進捗: {} (サイズ不明)", formatBytes(downloadedBytes));
                }
                lastProgressTime = currentTime;
            }
        }

        if (totalBytes > 0) {
            log.info("ダウンロード完了: 100% ({}/{})", formatBytes(downloadedBytes), formatBytes(totalBytes));
        } else {
            log.info("ダウンロード完了: {}", formatBytes(downloadedBytes));
        }
    }

    private String createProgressBar(long current, long total) {
        if (total <= 0) return "[??????????]";
        int barLength = 20;
        int filled = (int) ((current * barLength) / total);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) bar.append("=");
            else if (i == filled) bar.append(">");
            else bar.append(" ");
        }
        bar.append("]");
        return bar.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "不明";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String parseSha256ForAsset(String sumsText, String asset) {
        log.debug("SHA256SUMSから{}のハッシュを検索中", asset);
        for (String line : sumsText.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int sp = t.indexOf(' ');
            if (sp > 0) {
                String hash = t.substring(0, sp).trim();
                String file = t.substring(t.lastIndexOf(' ') + 1).trim();
                if (file.equals(asset)) {
                    log.debug("一致するハッシュを発見: {}", hash);
                    return hash;
                }
            }
        }
        log.debug("{}のハッシュが見つかりませんでした", asset);
        return null;
    }

    private static String sha256Hex(Path p) throws Exception {
        log.debug("SHA256を計算中: {}", p);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        }
        byte[] b = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static void grantExecuteIfNeeded(Path p) throws IOException {
        try {
            log.debug("実行権限を付与中: {}", p);
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(p);
            if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(p, perms);
                log.debug("✓ 実行権限を付与しました");
            } else {
                log.debug("既に実行権限があります");
            }
        } catch (UnsupportedOperationException ignored) {
            log.debug("POSIX権限非対応（Windowsなど）");
        }
    }

    private static boolean isExecutableOk(Path exe) {
        log.debug("実行ファイルの動作確認: {}", exe);
        try {
            String out = runAndCapture(exe.toString(), "--version");
            boolean ok = !out.isBlank();
            log.debug("動作確認結果: {}", ok ? "OK" : "NG");
            return ok;
        } catch (Exception e) {
            log.debug("動作確認で例外発生: {}", e.getMessage());
            return false;
        }
    }

    private static String runAndCapture(String... cmd) throws Exception {
        log.debug("コマンド実行: {}", Arrays.toString(cmd));
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = proc.getInputStream()) {
            in.transferTo(bos);
        }
        if (!proc.waitFor(PROC_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            log.error("プロセスがタイムアウトしました: {}", Arrays.toString(cmd));
            throw new RuntimeException("Process timeout: " + Arrays.toString(cmd));
        }
        String output = bos.toString(StandardCharsets.UTF_8);
        log.debug("コマンド実行完了。出力サイズ: {} バイト", output.length());
        return output;
    }

    /** 旧API（既定=120秒） */
    private static String runAndCaptureWithProgress(String... cmd) throws Exception {
        return runAndCaptureWithProgressTimeout(PROC_TIMEOUT_SEC, cmd);
    }

    /** タイムアウト指定付き */
    private static String runAndCaptureWithProgressTimeout(int timeoutSec, String... cmd) throws Exception {
        log.debug("コマンド実行: {}", Arrays.toString(cmd));
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (!line.trim().isEmpty()) log.info("  {}", line.trim());
            }
        }

        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            log.error("プロセスがタイムアウトしました: {}", Arrays.toString(cmd));
            throw new RuntimeException("Process timeout: " + Arrays.toString(cmd));
        }

        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            log.warn("コマンドが終了コード {} で終了しました", exitCode);
        }

        log.debug("コマンド実行完了。出力サイズ: {} バイト", output.length());
        return output.toString();
    }
}
