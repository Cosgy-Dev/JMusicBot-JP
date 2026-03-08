package com.jagrosh.jmusicbot.utils;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OtherUtilTest {
    @Test
    public void makeNonEmptyReturnsZeroWidthForEmptyInput() {
        assertEquals("\u200B", OtherUtil.makeNonEmpty(""));
        assertEquals("\u200B", OtherUtil.makeNonEmpty(null));
    }

    @Test
    public void parseStatusFallsBackToOnline() {
        assertEquals(OnlineStatus.ONLINE, OtherUtil.parseStatus(null));
        assertEquals(OnlineStatus.ONLINE, OtherUtil.parseStatus("invalid"));
        assertEquals(OnlineStatus.IDLE, OtherUtil.parseStatus("idle"));
    }

    @Test
    public void parseGameReturnsNullForDefault() {
        assertNull(OtherUtil.parseGame("default"));
        assertNull(OtherUtil.parseGame(" "));
    }

    @Test
    public void parseGameParsesPlayingPrefix() {
        Activity activity = OtherUtil.parseGame("playing Tetris");
        assertEquals(Activity.ActivityType.PLAYING, activity.getType());
        assertEquals("Tetris", activity.getName());
    }

    @Test
    public void parseGameParsesStreamingPrefix() {
        Activity activity = OtherUtil.parseGame("streaming cosgy live");
        assertEquals(Activity.ActivityType.STREAMING, activity.getType());
        assertEquals("live", activity.getName());
        assertEquals("https://twitch.tv/cosgy", activity.getUrl());
    }

    @Test
    public void parseGameUsesCustomStatusAsFallback() {
        Activity activity = OtherUtil.parseGame("status text");
        assertEquals(Activity.ActivityType.CUSTOM_STATUS, activity.getType());
        assertEquals("status text", activity.getName());
    }
}
