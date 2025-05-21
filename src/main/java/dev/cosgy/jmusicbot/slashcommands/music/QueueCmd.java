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
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueueCmd extends MusicCommand {
    private final static String REPEAT_ALL = "\uD83D\uDD01"; // 🔁
    private final static String REPEAT_SINGLE = "\uD83D\uDD02"; // 🔂

    private final Paginator.Builder builder;

    public QueueCmd(Bot bot) {
        super(bot);
        this.name = "queue";
        this.help = "再生待ちの楽曲一覧を表示します";
        this.arguments = "[ページ]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS};
        builder = new Paginator.Builder()
                .setColumns(1)
                .setFinalAction(m -> {
                    try {
                        m.clearReactions().queue();
                    } catch (PermissionException ignore) {
                    }
                })
                .setItemsPerPage(10)
                .waitOnSinglePage(false)
                .useNumberedItems(true)
                .showPageNumbers(true)
                .wrapPageEnds(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event) {
        int pagenum = 1;
        try {
            pagenum = Integer.parseInt(event.getArgs());
        } catch (NumberFormatException ignore) {
        }
        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> list = ah.getQueue().getList();
        if (list.isEmpty()) {
            MessageCreateData nowp = null;
            try {
                nowp = ah.getNowPlaying(event.getJDA());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            MessageCreateData nonowp = ah.getNoMusicPlaying(event.getJDA());
            MessageCreateData built = new MessageCreateBuilder()
                    .setContent(event.getClient().getWarning() + " 再生待ちの楽曲はありません。")
                    .setEmbeds((nowp == null ? nonowp : nowp).getEmbeds().get(0)).build();
            MessageCreateData finalNowp = nowp;
            event.reply(built, m ->
            {
                if (finalNowp != null)
                    bot.getNowplayingHandler().setLastNPMessage(m);
            });
            return;
        }
        String[] songs = new String[list.size()];
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            total += list.get(i).getTrack().getDuration();
            songs[i] = list.get(i).toString();
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        long finTotal = total;
        builder.setText((i1, i2) -> getQueueTitle(ah, event.getClient().getSuccess(), songs.length, finTotal, settings.getRepeatMode()))
                .setItems(songs)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColor());

        builder.build().paginate(event.getChannel(), pagenum);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        InteractionHook m = event.reply("再生待ちを取得しています。").complete();
        int pagenum = 1;
        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> list = ah.getQueue().getList();
        if (list.isEmpty()) {
            MessageCreateData nowp = null;
            try {
                nowp = ah.getNowPlaying(event.getJDA());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            MessageCreateData nonowp = ah.getNoMusicPlaying(event.getJDA());
            MessageEditData built = new MessageEditBuilder()
                    .setContent(event.getClient().getWarning() + " 再生待ちの楽曲はありません。")
                    .setEmbeds((nowp == null ? nonowp : nowp).getEmbeds().get(0)).build();
            m.editOriginal(built).queue();
            return;
        }
        String[] songs = new String[list.size()];
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            total += list.get(i).getTrack().getDuration();
            songs[i] = list.get(i).toString();
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        long finTotal = total;
        builder.setText((i1, i2) -> getQueueTitle(ah, event.getClient().getSuccess(), songs.length, finTotal, settings.getRepeatMode()))
                .setItems(songs)
                .setUsers(event.getUser())
                .setColor(event.getGuild().getSelfMember().getColor());
        builder.build().paginate(event.getChannel(), pagenum);
        m.deleteOriginal().queue();
    }

    private String getQueueTitle(AudioHandler ah, String success, int songslength, long total, RepeatMode repeatmode) {
        StringBuilder sb = new StringBuilder();
        if (ah.getPlayer().getPlayingTrack() != null) {
            sb.append(ah.getPlayer().isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI).append(" **")
                    .append(
                            ah.getPlayer().getPlayingTrack().getInfo().uri.matches(".*stream.gensokyoradio.net/.*") ? "幻想郷ラジオ" :
                                    ah.getPlayer().getPlayingTrack().getInfo().title).append("**\n");
        }
        return FormatUtil.filter(sb.append(success).append(" 再生待ち楽曲一覧 | ").append(songslength)
                .append(" エントリー | `").append(FormatUtil.formatTime(total)).append("` ")
                // RepeatMode.OFF - ""
                // RepeatMode.ALL - QueueCmd.REPEAT_ALL
                // RepeatMode.SINGLE = QueueCmd.REPEAT_SINGLE
                .append(repeatmode != RepeatMode.OFF ? "| " + (repeatmode == RepeatMode.ALL ? REPEAT_ALL : REPEAT_SINGLE) : "").toString());
    }
}
