package com.jagrosh.jmusicbot.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FormatUtilTest {
    @Test
    public void formatTimeLongSupportsLive() {
        assertEquals("LIVE", FormatUtil.formatTime(Long.MAX_VALUE));
    }

    @Test
    public void formatTimeIntSupportsHours() {
        assertEquals("1:01:01", FormatUtil.formatTime(3661));
    }

    @Test
    public void progressBarPlacesCursorAtExpectedPosition() {
        String bar = FormatUtil.progressBar(0.5);
        assertEquals(13, bar.length());
        assertTrue(bar.contains("\uD83D\uDD18"));
        assertEquals(6, bar.indexOf("\uD83D\uDD18"));
    }

    @Test
    public void volumeIconChangesByRange() {
        assertEquals("\uD83D\uDD07", FormatUtil.volumeIcon(0));
        assertEquals("\uD83D\uDD08", FormatUtil.volumeIcon(10));
        assertEquals("\uD83D\uDD09", FormatUtil.volumeIcon(40));
        assertEquals("\uD83D\uDD0A", FormatUtil.volumeIcon(100));
    }

    @Test
    public void filterProtectsMassMentions() {
        String filtered = FormatUtil.filter("  @everyone @here  ");
        assertFalse(filtered.contains("@everyone"));
        assertFalse(filtered.contains("@here"));
        assertTrue(filtered.startsWith("@"));
    }
}
