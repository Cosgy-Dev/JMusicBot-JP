package dev.cosgy.jmusicbot.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TimeUtilTest {
    @Test
    public void formatTimeForLiveStream() {
        assertEquals("LIVE", TimeUtil.formatTime(Long.MAX_VALUE));
    }

    @Test
    public void formatTimeRoundsMilliseconds() {
        assertEquals("01:02", TimeUtil.formatTime(61_500));
    }

    @Test
    public void parseColonTimeSupportsHoursMinutesSeconds() {
        assertEquals(3_723_000L, TimeUtil.parseColonTime("1:2:3"));
    }

    @Test
    public void parseColonTimeRejectsTooManyParts() {
        assertEquals(-1L, TimeUtil.parseColonTime("1:2:3:4"));
    }

    @Test
    public void parseUnitTimeSupportsWordsAndSpaces() {
        assertEquals(4_800_000L, TimeUtil.parseUnitTime("1h and 20m"));
    }

    @Test
    public void parseTimeSupportsRelativeBackwardSeek() {
        TimeUtil.SeekTime seekTime = TimeUtil.parseTime("-1:30");
        assertNotNull(seekTime);
        assertTrue(seekTime.relative);
        assertEquals(-90_000L, seekTime.milliseconds);
    }

    @Test
    public void parseTimeSupportsAbsoluteSeek() {
        TimeUtil.SeekTime seekTime = TimeUtil.parseTime("2m10s");
        assertNotNull(seekTime);
        assertFalse(seekTime.relative);
        assertEquals(130_000L, seekTime.milliseconds);
    }

    @Test
    public void parseTimeReturnsNullForInvalidInput() {
        assertNull(TimeUtil.parseTime("abc"));
    }
}
