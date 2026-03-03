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

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SearchCmd extends MusicCommand {
    private final OrderedMenu.Builder builder;
    private final String searchingEmoji;
    protected String searchPrefix = "ytsearch:";

    public SearchCmd(Bot bot) {
        super(bot);
        this.searchingEmoji = bot.getConfig().getSearching();
        this.name = "search";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "<query>";
        this.help = "指定した文字列を使用してYouTube上の動画を検索します。";
        this.beListening = true;
        this.bePlaying = false;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "検索ワード", true));
        this.options = options;

        builder = new OrderedMenu.Builder()
                .allowTextInput(true)
                .useNumbers()
                .useCancelButton(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.replyError("文字列を指定してください。");
            return;
        }
        event.reply(searchingEmoji + "`[" + event.getArgs() + "]`を検索中... ",
                m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchPrefix + event.getArgs(), new ResultHandler(m, event)));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        event.reply(searchingEmoji + "`[" + event.getOption("input").getAsString() + "]`を検索中... ").queue(
                m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchPrefix + event.getOption("input").getAsString(), new SlashResultHandler(m, event)));
    }

    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**`は許可されている最大長より長いです。"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
            m.editOriginal(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "を追加しました。"
                    : "を" + pos + "番目の再生待ちに追加しました。"))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            builder.setColor(event.getGuild().getSelfMember().getColor())
                    .setText(FormatUtil.filter(event.getClient().getSuccess() + " `" + event.getOption("input").getAsString() + "`の検索結果:"))
                    .setChoices()
                    .setSelection((msg, i) ->
                    {
                        AudioTrack track = playlist.getTracks().get(i - 1);
                        if (bot.getConfig().isTooLong(track)) {
                            event.reply(event.getClient().getWarning() + "**" + track.getInfo().title + "**`は許可されている最大長よりも長いです。"
                                    + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`").queue();
                            return;
                        }
                        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                        int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
                        event.reply(event.getClient().getSuccess() + "**" + track.getInfo().title
                                + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "を追加しました。"
                                : " を" + pos + "番目の再生待ちに追加しました。 ")).queue();
                    })
                    .setCancel((msg) -> {
                    })
                    .setUsers(event.getUser())
            ;
            for (int i = 0; i < 4 && i < playlist.getTracks().size(); i++) {
                AudioTrack track = playlist.getTracks().get(i);
                builder.addChoices("`[" + FormatUtil.formatTime(track.getDuration()) + "]` [**" + track.getInfo().title + "**](" + track.getInfo().uri + ")");
            }
            builder.build().display(event.getChannel());
        }

        @Override
        public void noMatches() {
            m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " の検索結果はありません。 `" + event.getOption("input").getAsString() + "`.")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {

            if (throwable.severity == Severity.COMMON)
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + throwable.getMessage()).queue();
            else
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました").queue();
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;

        private ResultHandler(Message m, CommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " この曲 (**" + track.getInfo().title + "**) は許可されている最大長よりも長いです。 `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + " **" + track.getInfo().title
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "の再生を開始します。"
                    : "を" + pos + "番目の再生待ちに追加しました。"))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            builder.setColor(event.getSelfMember().getColor())
                    .setText(FormatUtil.filter(event.getClient().getSuccess() + " `" + event.getArgs() + "`の検索結果:"))
                    .setChoices()
                    .setSelection((msg, i) ->
                    {
                        AudioTrack track = playlist.getTracks().get(i - 1);
                        if (bot.getConfig().isTooLong(track)) {
                            event.replyWarning("この曲 (**" + track.getInfo().title + "**) は、許容される最大長より長いです。: `"
                                    + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`");
                            return;
                        }
                        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                        int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
                        event.replySuccess("**" + FormatUtil.filter(track.getInfo().title)
                                + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "の再生を開始します。"
                                : "を" + pos + "番目の再生待ちに追加しました。"));
                    })
                    .setCancel((msg) -> {
                    })
                    .setUsers(event.getAuthor())
            ;
            for (int i = 0; i < 4 && i < playlist.getTracks().size(); i++) {
                AudioTrack track = playlist.getTracks().get(i);
                builder.addChoices("`[" + FormatUtil.formatTime(track.getDuration()) + "]` [**" + track.getInfo().title + "**](" + track.getInfo().uri + ")");
            }
            builder.build().display(m);
        }

        @Override
        public void noMatches() {
            m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " `" + event.getArgs() + "`に該当する結果は見つかりませんでした。")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON)
                m.editMessage(event.getClient().getError() + " 読み込みエラー: " + throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError() + " 曲の読み込みに失敗しました。").queue();
        }
    }
}
