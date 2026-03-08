package dev.cosgy.jmusicbot.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YtDlpManagerTest {
    @Test
    public void picksLinuxAarch64BinaryOnArm64() {
        String asset = YtDlpManager.pickAssetForPlatform("Linux", "aarch64");
        assertEquals("yt-dlp_linux_aarch64", asset);
    }

    @Test
    public void picksLinuxDefaultBinaryOnX64() {
        String asset = YtDlpManager.pickAssetForPlatform("Linux", "amd64");
        assertEquals("yt-dlp_linux", asset);
    }

    @Test
    public void picksWindowsBinaryOnX64() {
        String asset = YtDlpManager.pickAssetForPlatform("Windows 11", "amd64");
        assertEquals("yt-dlp.exe", asset);
    }

    @Test
    public void picksWindowsArmBinaryOnArm64() {
        String asset = YtDlpManager.pickAssetForPlatform("Windows 11", "arm64");
        assertEquals("yt-dlp_arm64.exe", asset);
    }

    @Test
    public void picksMacBinaryOnDarwin() {
        String asset = YtDlpManager.pickAssetForPlatform("Darwin", "x86_64");
        assertEquals("yt-dlp_macos", asset);
    }
}
