/*
 * Copyright 2021 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.util.LastSendTextChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Michaili K (mysteriouscursor+git@protonmail.com)
 */
public class AloneInVoiceHandler {
    private final Bot bot;
    private final HashMap<Long, Instant> aloneSince = new HashMap<>();
    Logger log = LoggerFactory.getLogger("AloneInVoiceHandler");
    private long aloneTimeUntilStop = 0;

    public AloneInVoiceHandler(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        aloneTimeUntilStop = bot.getConfig().getAloneTimeUntilStop();
        if (aloneTimeUntilStop > 0)
            bot.getThreadpool().scheduleWithFixedDelay(this::check, 0, 5, TimeUnit.SECONDS);
    }

    private void check() {
        Set<Long> toRemove = new HashSet<>();
        for (Map.Entry<Long, Instant> entrySet : aloneSince.entrySet()) {
            if (entrySet.getValue().getEpochSecond() > Instant.now().getEpochSecond() - aloneTimeUntilStop) continue;

            Guild guild = bot.getJDA().getGuildById(entrySet.getKey());

            if (guild == null) {
                toRemove.add(entrySet.getKey());
                continue;
            }
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();

            if (bot.getConfig().getAutoStopQueueSave()) {
                // キャッシュの保存処理
                CacheLoader cache = bot.getCacheLoader();
                cache.Save(guild.getId(), handler.getQueue());
                log.info("再生待ちを保存してボイスチャンネルから退出します。");
                LastSendTextChannel.SendMessage(guild, ":notes: 再生待ちを保存してボイスチャンネルから退出しました。");
            } else {
                // キャッシュを保存せずに退出する時の処理
                log.info("再生待ちを削除してボイスチャンネルから退出します。");
                LastSendTextChannel.SendMessage(guild, ":notes: 再生待ちを削除してボイスチャンネルから退出しました。");
            }

            handler.stopAndClear();
            bot.closeAudioConnection(guild.getIdLong());

            toRemove.add(entrySet.getKey());
        }
        toRemove.forEach(aloneSince::remove);
    }

    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (aloneTimeUntilStop <= 0) return;

        Guild guild = event.getEntity().getGuild();
        if (!bot.getPlayerManager().hasHandler(guild)) return;
        // ステージチャンネルにいる場合は退出しない。
        if (guild.getAudioManager().getConnectedChannel() != null) {
            if (guild.getAudioManager().getConnectedChannel().getType() == ChannelType.STAGE) return;
        }

        boolean alone = isAlone(guild);
        boolean inList = aloneSince.containsKey(guild.getIdLong());

        if (!alone && inList)
            aloneSince.remove(guild.getIdLong());
        else if (alone && !inList)
            aloneSince.put(guild.getIdLong(), Instant.now());
    }

    private boolean isAlone(Guild guild) {
        if (guild.getAudioManager().getConnectedChannel() == null) return false;
        return guild.getAudioManager().getConnectedChannel().getMembers().stream()
                .noneMatch(x ->
                        !x.getVoiceState().isDeafened()
                                && !x.getUser().isBot());
    }
}
