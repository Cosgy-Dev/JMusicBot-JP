package dev.cosgy.jmusicbot.util;

import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
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

    // 準備済みフラグ（複数回実行を防ぐ）
    private static final AtomicBoolean prepared = new AtomicBoolean(false);
    private static Path preparedPath = null;

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

    /** 
     * yt-dlpの準備を行い、実行可能なパスを返す
     * ダウンロード/検証/権限設定/自己更新を実行
     */
    public Path prepare() throws Exception {
        // 既に準備済みの場合は、キャッシュされたパスを返す
        if (prepared.get() && preparedPath != null) {
            log.debug("yt-dlpは既に準備済みです: {}", preparedPath);
            return preparedPath;
        }

        // 同期ブロックで準備処理を1回だけ実行
        synchronized (YtDlpManager.class) {
            // ダブルチェックロッキング
            if (prepared.get() && preparedPath != null) {
                log.debug("yt-dlpは既に準備済みです（同期後確認）: {}", preparedPath);
                return preparedPath;
            }

            log.info("yt-dlpの準備を開始します");
            
            // binディレクトリを作成
            Files.createDirectories(binDir);
            log.debug("binディレクトリを作成/確認: {}", binDir);

            // ダウンロードの必要性をチェック
            boolean needDownload = !Files.isRegularFile(exePath);
            if (!needDownload) {
                log.debug("既存のyt-dlp実行ファイルを検証中...");
                if (!isExecutableOk(exePath)) {
                    // 破損/古すぎる等で再取得
                    log.warn("既存のyt-dlpが破損しているか実行できません。再ダウンロードします。");
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
            if (version.isEmpty()) {
                log.error("yt-dlpの起動に失敗しました");
                throw new IllegalStateException("yt-dlp launch failed");
            }
            log.info("yt-dlpのバージョン: {}", version);

            // 自己更新（失敗しても致命ではないのでログのみ）
            try {
                log.info("yt-dlpの自己更新を実行中...");
                runAndCaptureWithProgress(exePath.toString(), "-U");
                log.info("yt-dlpの自己更新が完了しました");
            } catch (Exception e) {
                log.warn("yt-dlpの自己更新に失敗しましたが、続行します: {}", e.getMessage());
            }

            log.info("yt-dlpの準備が完了しました: {}", exePath);
            
            // 準備完了フラグを設定
            preparedPath = exePath;
            prepared.set(true);
            
            return exePath;
        }
    }

    // ————— helpers —————

    /**
     * 現在のプラットフォームに適したyt-dlpのアセット名を選択
     */
    private static String pickAssetForCurrentPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        
        log.debug("プラットフォームを検出: OS={}, Arch={}", os, arch);

        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                log.debug("Windows ARM64版を選択");
                return "yt-dlp_arm64.exe";
            }
            log.debug("Windows版を選択");
            return "yt-dlp.exe";
        } else if (os.contains("mac") || os.contains("darwin")) {
            log.debug("macOS版を選択");
            return "yt-dlp_macos";
        } else {
            // Linux/BSD 他 → Python同梱のスタンドアロン
            log.debug("Linux版を選択");
            return "yt-dlp_linux";
        }
    }

    /**
     * ローカル配置用のファイル名を決定
     * 配置名を固定にして、パス指定を簡単にする
     */
    private static String assetNameForLocal(String asset) {
        // ローカル配置名は固定にする（後でパス指定しやすい）
        if (asset.endsWith(".exe")) return "yt-dlp.exe";
        return "yt-dlp";
    }

    /**
     * yt-dlpバイナリをダウンロードしてSHA256で検証
     */
    private void downloadAndVerify() throws Exception {
        log.info("yt-dlpのダウンロードを開始: {}", assetName);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        // 1) バイナリ本体DL（進捗表示付き）
        URI binUri = URI.create(GITHUB_LATEST_BASE + assetName);
        log.debug("ダウンロードURL: {}", binUri);
        Path tmp = Files.createTempFile("yt-dlp-", ".dl");
        log.debug("一時ファイルに保存: {}", tmp);
        
        HttpResponse<InputStream> response = client.send(
            HttpRequest.newBuilder(binUri).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        );
        
        // Content-Lengthを取得して進捗表示
        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            downloadWithProgress(in, out, totalBytes);
            log.info("ダウンロード完了");
        }

        // 2) SHA256一覧取得 & 照合（一致しなければ中止）
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
                log.error("SHA256が一致しません。期待: {}, 実際: {}", expected, actual);
                throw new SecurityException("SHA-256 mismatch for " + assetName);
            }
            log.info("✓ SHA256検証成功");
        } else {
            log.warn("SHA256チェックサムが見つかりませんでした。検証をスキップします。");
        }

        // 3) 配置
        log.debug("yt-dlpを最終位置に移動: {} -> {}", tmp, exePath);
        Files.move(tmp, exePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        grantExecuteIfNeeded(exePath);
        log.info("yt-dlpの配置が完了しました");
    }

    /**
     * 進捗表示付きでダウンロード
     */
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
            
            // 500ms毎に進捗を更新
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
        
        // 最終進捗
        if (totalBytes > 0) {
            log.info("ダウンロード完了: 100% ({}/{})", formatBytes(downloadedBytes), formatBytes(totalBytes));
        } else {
            log.info("ダウンロード完了: {}", formatBytes(downloadedBytes));
        }
    }

    /**
     * プログレスバーを生成
     */
    private String createProgressBar(long current, long total) {
        if (total <= 0) return "[??????????]";
        
        int barLength = 20;
        int filled = (int) ((current * barLength) / total);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("=");
            } else if (i == filled) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        
        return bar.toString();
    }

    /**
     * バイト数を人間が読みやすい形式にフォーマット
     */
    private String formatBytes(long bytes) {
        if (bytes < 0) return "不明";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * SHA256SUMSファイルから指定アセットのハッシュ値を抽出
     */
    private static String parseSha256ForAsset(String sumsText, String asset) {
        // フォーマット: "<sha256>  <filename>"
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
        // 見つからなければ検証スキップ（まれにアセットが追加で増えるタイミング）
        log.debug("{}のハッシュが見つかりませんでした", asset);
        return null;
    }

    /**
     * ファイルのSHA256ハッシュ値を16進数文字列で計算
     */
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

    /**
     * Unix系OSで実行権限を付与（Windowsでは無視される）
     */
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
            // WindowsなどPOSIXでないFSは無視
            log.debug("POSIXファイル権限をサポートしていないOS (Windowsなど)");
        }
    }

    /**
     * 既存のyt-dlp実行ファイルが正常に動作するかテスト
     */
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

    /**
     * コマンドを実行して標準出力を取得
     */
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

    /**
     * コマンドを実行して標準出力をリアルタイムで表示（更新コマンド用）
     */
    private static String runAndCaptureWithProgress(String... cmd) throws Exception {
        log.debug("コマンド実行（進捗表示付き）: {}", Arrays.toString(cmd));
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                // 更新の進捗をログに出力
                if (!line.trim().isEmpty()) {
                    log.info("  {}", line.trim());
                }
            }
        }
        
        if (!proc.waitFor(PROC_TIMEOUT_SEC, TimeUnit.SECONDS)) {
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
