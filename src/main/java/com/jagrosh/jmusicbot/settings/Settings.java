/*
 * Copyright 2018 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jdautilities.command.GuildSettingsProvider;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.Collection;
import java.util.Collections;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class Settings implements GuildSettingsProvider {
    private final SettingsManager manager;
    protected long textId;
    protected long voiceId;
    protected long roleId;
    private int volume, announce;
    private String defaultPlaylist;
    private RepeatMode repeatMode;
    private String prefix;
    private boolean bitrateWarningReaded;
    private double skipRatio;
    private boolean vcStatus;
    private boolean ForceToEndQue;


    public Settings(SettingsManager manager, String textId, String voiceId, String roleId, int volume, String defaultPlaylist, RepeatMode repeatMode, String prefix, boolean bitrateWarningReaded, int announce, double skipRatio, boolean vcStatus, boolean forceToEndQue) {
        this.manager = manager;
        try {
            this.textId = Long.parseLong(textId);
        } catch (NumberFormatException e) {
            this.textId = 0;
        }
        try {
            this.voiceId = Long.parseLong(voiceId);
        } catch (NumberFormatException e) {
            this.voiceId = 0;
        }
        try {
            this.roleId = Long.parseLong(roleId);
        } catch (NumberFormatException e) {
            this.roleId = 0;
        }
        this.volume = volume;
        this.defaultPlaylist = defaultPlaylist;
        this.repeatMode = repeatMode;
        this.prefix = prefix;
        this.bitrateWarningReaded = bitrateWarningReaded;
        this.announce = announce;
        this.skipRatio = skipRatio;
        this.vcStatus = vcStatus;
        this.ForceToEndQue = forceToEndQue;
    }

    public Settings(SettingsManager manager, long textId, long voiceId, long roleId, int volume, String defaultPlaylist, RepeatMode repeatMode, String prefix, boolean bitrateWarningReaded, int announce, double skipRatio, boolean vcStatus, boolean forceToEndQue) {
        this.manager = manager;
        this.textId = textId;
        this.voiceId = voiceId;
        this.roleId = roleId;
        this.volume = volume;
        this.defaultPlaylist = defaultPlaylist;
        this.repeatMode = repeatMode;
        this.prefix = prefix;
        this.bitrateWarningReaded = bitrateWarningReaded;
        this.announce = announce;
        this.skipRatio = skipRatio;
        this.vcStatus = vcStatus;
        this.ForceToEndQue = forceToEndQue;
    }

    // Getters
    public TextChannel getTextChannel(Guild guild) {
        return guild == null ? null : guild.getTextChannelById(textId);
    }

    public VoiceChannel getVoiceChannel(Guild guild) {
        return guild == null ? null : guild.getVoiceChannelById(voiceId);
    }

    public Role getRole(Guild guild) {
        return guild == null ? null : guild.getRoleById(roleId);
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
        this.manager.writeSettings();
    }

    public String getDefaultPlaylist() {
        return defaultPlaylist;
    }

    public void setDefaultPlaylist(String defaultPlaylist) {
        this.defaultPlaylist = defaultPlaylist;
        this.manager.writeSettings();
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        this.manager.writeSettings();
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
        this.manager.writeSettings();
    }

    public double getSkipRatio() {
        return skipRatio;
    }

    public void setSkipRatio(double skipRatio) {
        this.skipRatio = skipRatio;
        this.manager.writeSettings();
    }

    public int getAnnounce() {
        return announce;
    }

    public void setAnnounce(int announce) {
        this.announce = announce;
        this.manager.writeSettings();
    }

    public boolean getVCStatus() {
        return vcStatus;
    }

    public void setVCStatus(boolean vcStatus) {
        this.vcStatus = vcStatus;
        this.manager.writeSettings();
    }

    public boolean isBitrateWarningReaded() {
        return bitrateWarningReaded;
    }

    public void setBitrateWarning(boolean readied) {
        this.bitrateWarningReaded = readied;
    }

    @Override
    public Collection<String> getPrefixes() {
        return prefix == null ? Collections.EMPTY_SET : Collections.singleton(prefix);
    }

    // Setters
    public void setTextChannel(TextChannel tc) {
        this.textId = tc == null ? 0 : tc.getIdLong();
        this.manager.writeSettings();
    }

    public void setVoiceChannel(AudioChannel vc) {
        this.voiceId = vc == null ? 0 : vc.getIdLong();
        this.manager.writeSettings();
    }

    public void setDJRole(Role role) {
        this.roleId = role == null ? 0 : role.getIdLong();
        this.manager.writeSettings();
    }

    public void setForceToEndQue(boolean forceToEndQue) {
        this.ForceToEndQue = forceToEndQue;
        this.manager.writeSettings();
    }

    public boolean isForceToEndQue() {
        return ForceToEndQue;
    }
}