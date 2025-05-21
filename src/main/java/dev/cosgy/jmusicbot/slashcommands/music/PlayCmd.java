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

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.Cache;
import dev.cosgy.jmusicbot.util.StackTraceUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayCmd extends MusicCommand {
    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    private final String loadingEmoji;

    public PlayCmd(Bot bot) {
        super(bot);
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.arguments = "<title|URL|subcommand>";
        this.help = "指定された曲を再生します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "URLまたは曲名", false));
        this.options = options;

        //this.children = new SlashCommand[]{new PlaylistCmd(bot), new MylistCmd(bot), new PublistCmd(bot), new RequestCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event) {

        if (event.getArgs().isEmpty() && event.getMessage().getAttachments().isEmpty()) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                if (DJCommand.checkDJPermission(event)) {
                    handler.getPlayer().setPaused(false);
                    event.replySuccess("**" + handler.getPlayer().getPlayingTrack().getInfo().title + "**の再生を再開しました。");

                    Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                } else
                    event.replyError("再生を再開できるのはDJのみです！");
                return;
            }

            // キャッシュの読み込み機構
            if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                AtomicInteger count = new AtomicInteger();
                CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                event.getChannel().sendMessage(":calling: キャッシュファイルを読み込んでいます... (" + cache.getItems().size() + "曲)").queue(m -> {
                    cache.loadTracks(bot.getPlayerManager(), (at) -> {
                        handler.addTrack(new QueuedTrack(at, (User) User.fromId(data.get(count.get()).getUserId())));
                        count.getAndIncrement();
                    }, () -> {
                        StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                                : event.getClient().getSuccess() + " キャッシュファイルから、" + "**" + cache.getTracks().size() + "**曲読み込みました。");
                        if (!cache.getErrors().isEmpty())
                            builder.append("\n以下の楽曲をロードできませんでした:");
                        cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (以下略)";
                        m.editMessage(FormatUtil.filter(str)).queue();
                    });
                });
                try {
                    bot.getCacheLoader().deleteCache(event.getGuild().getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (handler.playFromDefault()) {
                Settings settings = event.getClient().getSettingsFor(event.getGuild());
                handler.stopAndClear();
                Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                if (playlist == null) {
                    event.replyError("プレイリストフォルダに`" + event.getArgs() + ".txt`が見つかりませんでした。");
                    return;
                }
                event.getChannel().sendMessage(loadingEmoji + " プレイリストを読み込んでいます**" + settings.getDefaultPlaylist() + " ** ...（ " + playlist.getItems().size() + "曲）").queue(m ->
                {

                    playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                        StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " 曲がロードされていません！"
                                : event.getClient().getSuccess() + " ** " + playlist.getTracks().size() + " **曲をロードしました！");
                        if (!playlist.getErrors().isEmpty())
                            builder.append("\n次の曲を読み込めませんでした。:");
                        playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (...)";
                        m.editMessage(FormatUtil.filter(str)).queue();
                    });
                });
                return;

            }

            StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play コマンド:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <曲名>` - YouTubeから最初の結果を再生");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - 指定された曲、再生リスト、またはストリームを再生します");
            for (Command cmd : children)
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            event.reply(builder.toString());
            return;
        }
        String args = event.getArgs().startsWith("<") && event.getArgs().endsWith(">")
                ? event.getArgs().substring(1, event.getArgs().length() - 1)
                : event.getArgs().isEmpty() ? event.getMessage().getAttachments().get(0).getUrl() : event.getArgs();
        event.reply(loadingEmoji + "`[" + args + "]`を読み込み中です…", m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), args, new ResultHandler(m, event, false)));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {

        if (event.getOption("input") == null) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                if (DJCommand.checkDJPermission(event.getClient(), event)) {

                    handler.getPlayer().setPaused(false);
                    event.reply(event.getClient().getSuccess() + "**" + handler.getPlayer().getPlayingTrack().getInfo().title + "**の再生を再開しました。").queue();

                    Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                } else
                    event.reply(event.getClient().getError() + "再生を再開できるのはDJのみです！").queue();
                return;
            }

            // キャッシュの読み込み機構
            if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                AtomicInteger count = new AtomicInteger();
                CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                event.reply(":calling: キャッシュファイルを読み込んでいます... (" + cache.getItems().size() + "曲)").queue(m -> {
                    cache.loadTracks(bot.getPlayerManager(), (at) -> {
                        // TODO:キャッシュに保存されているユーザーIDを使用する。
                        handler.addTrack(new QueuedTrack(at, event.getUser()));
                        count.getAndIncrement();
                    }, () -> {
                        StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                                : event.getClient().getSuccess() + " キャッシュファイルから、" + "**" + cache.getTracks().size() + "**曲読み込みました。");
                        if (!cache.getErrors().isEmpty())
                            builder.append("\n以下の楽曲をロードできませんでした:");
                        cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (以下略)";
                        m.editOriginal(FormatUtil.filter(str)).queue();
                    });
                });
                try {
                    bot.getCacheLoader().deleteCache(event.getGuild().getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (handler.playFromDefault()) {
                Settings settings = event.getClient().getSettingsFor(event.getGuild());
                handler.stopAndClear();
                Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                if (playlist == null) {
                    event.reply("プレイリストフォルダに`" + event.getOption("input").getAsString() + ".txt`が見つかりませんでした。").queue();
                    return;
                }
                event.reply(loadingEmoji + " プレイリストを読み込んでいます**" + settings.getDefaultPlaylist() + " ** ...（ " + playlist.getItems().size() + "曲）").queue(m ->
                {

                    playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                        StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " 曲がロードされていません！"
                                : event.getClient().getSuccess() + " ** " + playlist.getTracks().size() + " **曲をロードしました！");
                        if (!playlist.getErrors().isEmpty())
                            builder.append("\n次の曲を読み込めませんでした。:");
                        playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (...)";
                        m.editOriginal(FormatUtil.filter(str)).queue();
                    });
                });
                return;

            }

            StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play コマンド:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <曲名>` - YouTubeから最初の結果を再生");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - 指定された曲、再生リスト、またはストリームを再生します");
            for (Command cmd : children)
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            event.reply(builder.toString()).queue();
            return;
        }
        event.reply(loadingEmoji + "`[" + event.getOption("input").getAsString() + "]`を読み込み中です…").queue(m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), event.getOption("input").getAsString(), new SlashResultHandler(m, event, false)));

    }

    public class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;
        private final boolean ytsearch;

        SlashResultHandler(InteractionHook m, SlashCommandEvent event, boolean ytsearch) {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() +
                        " **" + (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*") ? "幻想郷ラジオ" : track.getInfo().title) + "**`(" + FormatUtil.formatTime(track.getDuration()) + ")` は設定された長さ`(" + FormatUtil.formatTime(bot.getConfig().getMaxSeconds() * 1000) + ")` を超えています。")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;

            // Output MSG ex:
            // <タイトル><(長さ)> を追加しました。
            // <タイトル><(長さ)> を再生待ちの<再生待ち番号>番目に追加しました。
            String addMsg = FormatUtil.filter(event.getClient().getSuccess() + " **" + (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*") ? "幻想郷ラジオ" : track.getInfo().title)
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "を追加しました。" : "を再生待ちの" + pos + "番目に追加しました。 "));
            if (playlist == null || !event.getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
                m.editOriginal(addMsg).queue();
            } else {
                new ButtonMenu.Builder()
                        .setText(addMsg + "\n" + event.getClient().getWarning() + " この曲の再生リストには他に**" + playlist.getTracks().size() + "**曲が付属しています。トラックを読み込むには " + LOAD + " を選択して下さい。")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re ->
                        {
                            if (re.getName().equals(LOAD))
                                m.editOriginal(addMsg + "\n" + event.getClient().getSuccess() + "**" + loadPlaylist(playlist, track) + "**曲を再生待ちに追加しました!").queue();
                            else
                                m.editOriginal(addMsg).queue();
                        }).setFinalAction(m ->
                        {
                            try {
                                m.clearReactions().queue();
                                m.delete().queue();
                            } catch (PermissionException ignore) {
                            }
                        }).build().display(event.getChannel());
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().forEach((track) -> {
                if (!bot.getConfig().isTooLong(track) && !track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, event.getUser()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            } else if (playlist.getSelectedTrack() != null) {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            } else {
                int count = loadPlaylist(playlist, null);
                if (count == 0) {
                    m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " このプレイリスト内" + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + "は、許可された最大長より長いです。(`" + bot.getConfig().getMaxTime() + "`)")).queue();
                } else {
                    m.editOriginal(FormatUtil.filter(event.getClient().getSuccess()
                            + (playlist.getName() == null ? "再生リスト" : "再生リスト **" + playlist.getName() + "**") + " の `"
                            + playlist.getTracks().size() + "` 曲を再生待ちに追加しました。"
                            + (count < playlist.getTracks().size() ? "\n" + event.getClient().getWarning() + " 許可されている最大長より長いトラック (`"
                            + bot.getConfig().getMaxTime() + "`) 省略されています。" : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches() {
            if (ytsearch)
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " の検索結果はありません `" + event.getOption("input").getAsString() + "`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + event.getOption("input").getAsString(), new SlashResultHandler(m, event, true));
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON) {
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + throwable.getMessage()).queue();
            } else {

                m.editOriginal(event.getClient().getError() + "曲の読み込み中にエラーが発生しました。").queue();
            }
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;
        private final boolean ytsearch;

        private ResultHandler(Message m, CommandEvent event, boolean ytsearch) {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() +
                        " **" + track.getInfo().title + "**`(" + FormatUtil.formatTime(track.getDuration()) + ")` は設定された長さ`(" + FormatUtil.formatTime(bot.getConfig().getMaxSeconds() * 1000) + ")` を超えています。")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;

            // Output MSG ex:
            // <タイトル><(長さ)> を追加しました。
            // <タイトル><(長さ)> を再生待ちの<再生待ち番号>番目に追加しました。
            String addMsg = FormatUtil.filter(event.getClient().getSuccess() + " **" + (track.getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : track.getInfo().title)
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "を追加しました。" : "を再生待ちの" + pos + "番目に追加しました。 "));
            if (playlist == null || !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION))
                m.editMessage(addMsg).queue();
            else {
                new ButtonMenu.Builder()
                        .setText(addMsg + "\n" + event.getClient().getWarning() + " この曲の再生リストには他に**" + playlist.getTracks().size() + "**曲が付属しています。トラックを読み込むには " + LOAD + " を選択して下さい。")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re ->
                        {
                            if (re.getName().equals(LOAD))
                                m.editMessage(addMsg + "\n" + event.getClient().getSuccess() + "**" + loadPlaylist(playlist, track) + "**曲を再生待ちに追加しました!").queue();
                            else
                                m.editMessage(addMsg).queue();
                        }).setFinalAction(m ->
                        {
                            try {
                                m.clearReactions().queue();
                            } catch (PermissionException ignore) {
                            }
                        }).build().display(m);
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().forEach((track) -> {
                if (!bot.getConfig().isTooLong(track) && !track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, event.getAuthor()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            } else if (playlist.getSelectedTrack() != null) {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            } else {
                int count = loadPlaylist(playlist, null);
                if (count == 0) {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " このプレイリスト内" + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + "は、許可された最大長より長いです。(`" + bot.getConfig().getMaxTime() + "`)")).queue();
                } else {
                    m.editMessage(FormatUtil.filter(event.getClient().getSuccess()
                            + (playlist.getName() == null ? "再生リスト" : "再生リスト **" + playlist.getName() + "**") + " の `"
                            + playlist.getTracks().size() + "` 曲を再生待ちに追加しました。"
                            + (count < playlist.getTracks().size() ? "\n" + event.getClient().getWarning() + " 許可されている最大長より長いトラック (`"
                            + bot.getConfig().getMaxTime() + "`) 省略されています。" : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches() {
            if (ytsearch)
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " の検索結果はありません `" + event.getArgs() + "`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + event.getArgs(), new ResultHandler(m, event, true));
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON) {
                m.editMessage(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + throwable.getMessage()).queue();
            } else {
                if (m.getAuthor().getIdLong() == bot.getConfig().getOwnerId() || m.getMember().isOwner()) {
                    m.editMessage(event.getClient().getError() + "曲の読み込み中にエラーが発生しました。\n" +
                            "**エラーの内容: " + throwable.getLocalizedMessage() + "**").queue();
                    StackTraceUtil.sendStackTrace(event.getTextChannel(), throwable);
                    return;
                }

                m.editMessage(event.getClient().getError() + "曲の読み込み中にエラーが発生しました。").queue();
            }
        }
    }

    public class RequestCmd extends MusicCommand {
        private final static String LOAD = "\uD83D\uDCE5"; // 📥
        private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

        private final String loadingEmoji;
        private final JDA jda;

        public RequestCmd(Bot bot) {
            super(bot);
            this.jda = bot.getJDA();
            this.loadingEmoji = bot.getConfig().getLoading();
            this.name = "request";
            this.arguments = "<title|URL>";
            this.help = "曲をリクエストします。";
            this.aliases = bot.getConfig().getAliases(this.name);
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "input", "URLまたは曲名", false));
            this.options = options;

        }

        @Override
        public void doCommand(CommandEvent event) {
        }

        @Override
        public void doCommand(SlashCommandEvent event) {

            if (event.getOption("input") == null) {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                    if (DJCommand.checkDJPermission(event.getClient(), event)) {

                        handler.getPlayer().setPaused(false);
                        event.reply(event.getClient().getSuccess() + "**" + handler.getPlayer().getPlayingTrack().getInfo().title + "**の再生を再開しました。").queue();

                        Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                    } else
                        event.reply(event.getClient().getError() + "再生を再開できるのはDJのみです！").queue();
                    return;
                }

                // キャッシュの読み込み機構
                if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                    List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                    AtomicInteger count = new AtomicInteger();
                    CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                    event.reply(":calling: キャッシュファイルを読み込んでいます... (" + cache.getItems().size() + "曲)").queue(m -> {
                        cache.loadTracks(bot.getPlayerManager(), (at) -> {
                            // TODO:キャッシュに保存されているユーザーIDを使用する。
                            handler.addTrack(new QueuedTrack(at, event.getUser()));
                            count.getAndIncrement();
                        }, () -> {
                            StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                    ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                                    : event.getClient().getSuccess() + " キャッシュファイルから、" + "**" + cache.getTracks().size() + "**曲読み込みました。");
                            if (!cache.getErrors().isEmpty())
                                builder.append("\n以下の楽曲をロードできませんでした:");
                            cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                            String str = builder.toString();
                            if (str.length() > 2000)
                                str = str.substring(0, 1994) + " (以下略)";
                            m.editOriginal(FormatUtil.filter(str)).queue();
                        });
                    });
                    try {
                        bot.getCacheLoader().deleteCache(event.getGuild().getId());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                if (handler.playFromDefault()) {
                    Settings settings = event.getClient().getSettingsFor(event.getGuild());
                    handler.stopAndClear();
                    Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                    if (playlist == null) {
                        event.reply("プレイリストフォルダに`" + event.getOption("input").getAsString() + ".txt`が見つかりませんでした。").queue();
                        return;
                    }
                    event.reply(loadingEmoji + " プレイリストを読み込んでいます**" + settings.getDefaultPlaylist() + " ** ...（ " + playlist.getItems().size() + "曲）").queue(m ->
                    {

                        playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                            StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                    ? event.getClient().getWarning() + " 曲がロードされていません！"
                                    : event.getClient().getSuccess() + " ** " + playlist.getTracks().size() + " **曲をロードしました！");
                            if (!playlist.getErrors().isEmpty())
                                builder.append("\n次の曲を読み込めませんでした。:");
                            playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                            String str = builder.toString();
                            if (str.length() > 2000)
                                str = str.substring(0, 1994) + " (...)";
                            m.editOriginal(FormatUtil.filter(str)).queue();
                        });
                    });
                    return;

                }

                StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play コマンド:\n");
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <曲名>` - YouTubeから最初の結果を再生");
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - 指定された曲、再生リスト、またはストリームを再生します");
                for (Command cmd : children)
                    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
                event.reply(builder.toString()).queue();
                return;
            }
            event.reply(loadingEmoji + "`[" + event.getOption("input").getAsString() + "]`を読み込み中です…").queue(m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), event.getOption("input").getAsString(), new SlashResultHandler(m, event, false)));
        }


    }


    public class PlaylistCmd extends MusicCommand {
        public PlaylistCmd(Bot bot) {
            super(bot);
            this.name = "playlist";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "提供された再生リストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + "再生リスト名を含めてください。");
                return;
            }
            Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, event.getArgs());
            if (playlist == null) {
                event.replyError("`" + event.getArgs() + ".txt`を見つけられませんでした ");
                return;
            }
            event.getChannel().sendMessage(":calling: 再生リスト **" + event.getArgs() + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String guildId = event.getGuild().getId();

            String name = event.getOption("name").getAsString();

            Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "`" + name + ".txt`を見つけられませんでした ").queue();
                return;
            }
            event.reply(":calling: 再生リスト **" + name + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }

    public class MylistCmd extends MusicCommand {
        public MylistCmd(Bot bot) {
            super(bot);
            this.name = "mylist";
            this.aliases = new String[]{"ml"};
            this.arguments = "<name>";
            this.help = "マイリストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "マイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String userId = event.getAuthor().getId();
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + " マイリスト名を含めてください。");
                return;
            }
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, event.getArgs());
            if (playlist == null) {
                event.replyError("`" + event.getArgs() + ".txt `を見つけられませんでした ");
                return;
            }
            event.getChannel().sendMessage(":calling: マイリスト**" + event.getArgs() + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲、読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String userId = event.getUser().getId();

            String name = event.getOption("name").getAsString();

            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "`" + name + ".txt `を見つけられませんでした ").queue();
                return;
            }
            event.reply(":calling: マイリスト**" + name + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲、読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }

    public class PublistCmd extends MusicCommand {
        public PublistCmd(Bot bot) {
            super(bot);
            this.name = "publist";
            this.aliases = new String[]{"pul"};
            this.arguments = "<name>";
            this.help = "公開リストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "公開リスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + " 再生リスト名を含めてください。");
                return;
            }
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(event.getArgs());
            if (playlist == null) {
                event.replyError("`" + event.getArgs() + ".txt `を見つけられませんでした ");
                return;
            }
            event.getChannel().sendMessage(":calling: 再生リスト**" + event.getArgs() + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲、読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String name = event.getOption("name").getAsString();
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "`" + name + ".txt `を見つけられませんでした ").queue();
                return;
            }
            event.reply(":calling: 再生リスト**" + name + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲、読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }
}
