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

import com.jagrosh.jmusicbot.queue.Queueable;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.agent.GensokyoInfoAgent;
import net.dv8tion.jda.api.entities.User;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueuedTrack implements Queueable {
    private final AudioTrack track;

    public QueuedTrack(AudioTrack track, User owner) {
        this(track, new RequestMetadata(owner));
    }

    public QueuedTrack(AudioTrack track, RequestMetadata rm) {
        this.track = track;
        this.track.setUserData(rm);
    }

    @Override
    public long getIdentifier() {
        return track.getUserData(RequestMetadata.class).getOwner();
    }

    public AudioTrack getTrack() {
        return track;
    }

    @Override
    public String toString() {
        String owner = " - <@" + track.getUserData(RequestMetadata.class).getOwner() + ">";

        if (GensokyoInfoAgent.isGensokyoRadio(track)) {
            // 情報が未取得でもキューの表示は止めない
            GensokyoInfoAgent.Snapshot data = GensokyoInfoAgent.getInfo();
            String title = data == null || data.title() == null
                    ? GensokyoInfoAgent.DISPLAY_NAME : data.title();
            String length = data == null ? "LIVE" : FormatUtil.formatTime(data.durationSeconds() * 1000L);
            return "`[" + length + "]` [**" + title + "**](" + GensokyoInfoAgent.SITE_URL + ")" + owner;
        }

        AudioTrackInfo trackInfo = track.getInfo();
        String entry = "`[" + FormatUtil.formatTime(track.getDuration()) + "]` ";
        entry = entry + (trackInfo.uri.startsWith("http") ? "[**" + trackInfo.title + "**](" + trackInfo.uri + ")" : "**" + trackInfo.title + "**");
        return entry + owner;
    }
}
