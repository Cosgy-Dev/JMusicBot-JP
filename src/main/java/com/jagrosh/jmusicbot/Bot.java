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
package com.jagrosh.jmusicbot;

import dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowplayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Bot {
    public static Bot INSTANCE;
    private final EventWaiter waiter;
    private final ScheduledExecutorService threadpool;
    private final BotConfig config;
    private final SettingsManager settings;
    private final PlayerManager players;
    private final PlaylistLoader playlists;
    private final MylistLoader mylists;
    private final PubliclistLoader publist;
    private final CacheLoader cache;
    private final NowplayingHandler nowplaying;
    private final AloneInVoiceHandler aloneInVoiceHandler;

    private boolean shuttingDown = false;
    private JDA jda;
    private GUI gui;

    public Bot(EventWaiter waiter, BotConfig config, SettingsManager settings) {
        this.waiter = waiter;
        this.config = config;
        this.settings = settings;
        this.playlists = new PlaylistLoader(config);
        this.mylists = new MylistLoader(config);
        this.publist = new PubliclistLoader(config);
        this.cache = new CacheLoader(config);
        this.threadpool = Executors.newSingleThreadScheduledExecutor();
        this.players = new PlayerManager(this);
        this.players.init();
        this.nowplaying = new NowplayingHandler(this);
        this.nowplaying.init();
        this.aloneInVoiceHandler = new AloneInVoiceHandler(this);
        this.aloneInVoiceHandler.init();
    }

    public static void updatePlayStatus(Guild guild, Member selfMember, PlayStatus status) {
        if (!INSTANCE.getConfig().getChangeNickName()) return;
        if (!selfMember.hasPermission(Permission.NICKNAME_CHANGE)) {
            LoggerFactory.getLogger("UpdName").error("ニックネームを変更できませんでした: 権限が不足しています。");
            return;
        }

        String name = selfMember.getEffectiveName().replaceAll("[⏯⏸⏹] ", "");
        switch (status) {
            case PLAYING:
                name = "⏯ " + name;
                break;
            case PAUSED:
                name = "⏸ " + name;
                break;
            case STOPPED:
                name = "⏹ " + name;
                break;
            default:
        }

        guild.modifyNickname(selfMember, name).queue();
    }

    public BotConfig getConfig() {
        return config;
    }

    public SettingsManager getSettingsManager() {
        return settings;
    }

    public EventWaiter getWaiter() {
        return waiter;
    }

    public ScheduledExecutorService getThreadpool() {
        return threadpool;
    }

    public PlayerManager getPlayerManager() {
        return players;
    }

    public PlaylistLoader getPlaylistLoader() {
        return playlists;
    }

    public MylistLoader getMylistLoader() {
        return mylists;
    }

    public PubliclistLoader getPublistLoader() {
        return publist;
    }

    public CacheLoader getCacheLoader() {
        return cache;
    }

    public NowplayingHandler getNowplayingHandler() {
        return nowplaying;
    }

    public AloneInVoiceHandler getAloneInVoiceHandler() {
        return aloneInVoiceHandler;
    }

    public JDA getJDA() {
        return jda;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public void closeAudioConnection(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null)
            threadpool.submit(() -> {
                nowplaying.clearVoiceChannelStatus(guildId, false);
                guild.getAudioManager().closeAudioConnection();
            });
    }

    public void resetGame() {
        Activity game = config.getGame() == null || config.getGame().getName().toLowerCase().matches("(none|なし)") ? null : config.getGame();
        if (!Objects.equals(jda.getPresence().getActivity(), game))
            jda.getPresence().setActivity(game);
    }

    public void shutdown() {
        if (shuttingDown)
            return;
        shuttingDown = true;
        threadpool.shutdownNow();
        if (jda.getStatus() != JDA.Status.SHUTTING_DOWN) {
            jda.getGuilds().forEach(g ->
            {
                nowplaying.clearVoiceChannelStatus(g.getIdLong(), true);
                g.getAudioManager().closeAudioConnection();
                AudioHandler ah = (AudioHandler) g.getAudioManager().getSendingHandler();
                if (ah != null) {
                    ah.stopAndClear();
                    ah.getPlayer().destroy();
                    nowplaying.updateTopic(g.getIdLong(), ah, true);
                }
            });
            jda.shutdown();
        }
        if (gui != null)
            gui.dispose();
        System.exit(0);
    }

    public void setGUI(GUI gui) {
        this.gui = gui;
    }
}
