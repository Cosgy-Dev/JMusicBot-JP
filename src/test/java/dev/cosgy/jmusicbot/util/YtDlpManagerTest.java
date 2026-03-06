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
}
