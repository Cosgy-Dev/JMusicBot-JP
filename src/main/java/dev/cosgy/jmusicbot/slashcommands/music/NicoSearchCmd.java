package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.niconicoSearchAPI.nicoSearchAPI;
import dev.cosgy.niconicoSearchAPI.nicoVideoSearchResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NicoSearchCmd extends MusicCommand {
    public static final nicoSearchAPI niconicoAPI = new nicoSearchAPI(true, 100);

    public NicoSearchCmd(Bot bot) {
        super(bot);
        this.name = "ncsearch";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
        this.arguments = "<検索語句>";
        this.help = "指定した文字列を使用してニコニコ動画上の動画を検索します。";
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "検索語句", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        boolean isOwner = event.getAuthor().getIdLong() == bot.getConfig().getOwnerId();
        if (!bot.getConfig().isNicoNicoEnabled()) {
            event.reply("ニコニコ動画の機能が有効になっていません。\n" +
                    (isOwner ? "" : "Botの作成者に") + "config.txtの`useniconico = false`を`useniconico = true`に変更" + (isOwner ? "してください" : "するよう頼んでください") + "。");
            return;
        }

        if (event.getArgs().isEmpty()) {
            event.reply("使用法: **`" + event.getClient().getPrefix() + this.name + " " + this.arguments + "`**");
        } else {
            Message m = event.getChannel().sendMessage(bot.getConfig().getSearching() + " ニコニコ動画で " + event.getArgs() + " を検索しています\n" +
                    "**(注: 一部再生できない動画があります。)**").complete();
            LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(event.getArgs(), 5, true);
            if (results.size() == 0) {
                m.editMessage(event.getArgs() + " の検索結果はありません。").queue();
                return;
            }

            OrderedMenu.Builder builder = new OrderedMenu.Builder()
                    .allowTextInput(true)
                    .useNumbers()
                    .useCancelButton(true)
                    .setEventWaiter(bot.getWaiter())
                    .setTimeout(1, TimeUnit.MINUTES)
                    .setCancel(msg -> msg.delete().complete())
                    .setText(FormatUtil.filter(event.getClient().getSuccess() + "`" + event.getArgs() + "` の検索結果:"))
                    .setSelection((msg, sel) -> {
                        nicoVideoSearchResult selectedResultVideo = results.get((sel - 1));
                        System.out.println("URL = " + selectedResultVideo.getWatchUrl() + ", title = " + selectedResultVideo.getTitle());
                        bot.getPlayerManager().loadItemOrdered(event.getGuild(), selectedResultVideo.getWatchUrl(), new ResultHandler(m, event, bot));
                    });

            results.forEach(result -> builder.addChoice("`[" + result.getInfo().getLengthFormatted() + "]` [**" + result.getTitle() + "**](" + result.getWatchUrl() + ")"));
            builder.build().display(m);
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        boolean isOwner = event.getUser().getIdLong() == bot.getConfig().getOwnerId();
        if (!bot.getConfig().isNicoNicoEnabled()) {
            event.reply("ニコニコ動画の機能が有効になっていません。\n" +
                    (isOwner ? "" : "Botの作成者に") + "config.txtの`useniconico = false`を`useniconico = true`に変更" + (isOwner ? "してください" : "するよう頼んでください") + "。").queue();
            return;
        }

        String input = event.getOption("input").getAsString();

        event.reply(bot.getConfig().getSearching() + " ニコニコ動画で " + input + " を検索しています\n" +
                "**(注: 一部再生できない動画があります。)**").queue(m -> {
            LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(input, 5, true);
            if (results.size() == 0) {
                m.editOriginal(input + " の検索結果はありません。").queue();
                return;
            }

            OrderedMenu.Builder builder = new OrderedMenu.Builder()
                    .allowTextInput(true)
                    .useNumbers()
                    .useCancelButton(true)
                    .setEventWaiter(bot.getWaiter())
                    .setTimeout(1, TimeUnit.MINUTES)
                    .setCancel(msg -> msg.delete().complete())
                    .setText(FormatUtil.filter(event.getClient().getSuccess() + "`" + input + "` の検索結果:"))
                    .setSelection((msg, sel) -> {
                        nicoVideoSearchResult selectedResultVideo = results.get((sel - 1));
                        System.out.println("URL = " + selectedResultVideo.getWatchUrl() + ", title = " + selectedResultVideo.getTitle());
                        bot.getPlayerManager().loadItemOrdered(event.getGuild(), selectedResultVideo.getWatchUrl(), new SlashResultHandler(m, event, bot));
                    });

            results.forEach(result -> builder.addChoice("`[" + result.getInfo().getLengthFormatted() + "]` [**" + result.getTitle() + "**](" + result.getWatchUrl() + ")"));
            builder.build().display(event.getChannel());
        });
    }

    private static class ResultHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        private final Bot bot;

        private ResultHandler(Message m, CommandEvent event, Bot bot) {
            this.bot = bot;
            this.event = event;
        }

        /**
         * Called when the requested item is a track and it was successfully loaded.
         *
         * @param track The loaded track
         */
        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                event.reply(FormatUtil.filter(event.getClient().getWarning() + " 楽曲 (**" + track.getInfo().title + "**) は許容されている動画の長さを超えています: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`"));
                return;
            }

            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;

            event.reply(FormatUtil.filter(String.format("%s %s **%s** (`%s`) を追加しました", event.getClient().getSuccess(), (pos == 0 ? "再生待ちに" : "再生待ち #" + pos + " に"), track.getInfo().title, FormatUtil.formatTime(track.getDuration()))));
        }

        /**
         * Called when the requested item is a playlist and it was successfully loaded.
         *
         * @param playlist The loaded playlist
         */
        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        /**
         * Called when there were no items found by the specified identifier.
         */
        @Override
        public void noMatches() {
            event.reply(FormatUtil.filter(event.getClient().getWarning() + " `" + event.getArgs() + "`の検索結果はありません."));
        }

        /**
         * Called when loading an item failed with an exception.
         *
         * @param exception The exception that was thrown
         */
        @Override
        public void loadFailed(FriendlyException exception) {
            if (exception.severity == FriendlyException.Severity.COMMON)
                event.reply(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + exception.getMessage());
            else
                event.reply(event.getClient().getError() + " 楽曲を読み込み中にエラーが発生しました");
        }
    }

    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;
        private final Bot bot;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event, Bot bot) {
            this.m = m;
            this.bot = bot;
            this.event = event;
        }

        /**
         * Called when the requested item is a track and it was successfully loaded.
         *
         * @param track The loaded track
         */
        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " 楽曲 (**" + track.getInfo().title + "**) は許容されている動画の長さを超えています: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }

            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;

            m.editOriginal(FormatUtil.filter(String.format("%s %s **%s** (`%s`) を追加しました", event.getClient().getSuccess(), (pos == 0 ? "再生待ちに" : "再生待ち #" + pos + " に"), track.getInfo().title, FormatUtil.formatTime(track.getDuration())))).queue();
        }

        /**
         * Called when the requested item is a playlist and it was successfully loaded.
         *
         * @param playlist The loaded playlist
         */
        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        /**
         * Called when there were no items found by the specified identifier.
         */
        @Override
        public void noMatches() {
            m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " `" + event.getOption("input").getAsString() + "`の検索結果はありません.")).queue();
        }

        /**
         * Called when loading an item failed with an exception.
         *
         * @param exception The exception that was thrown
         */
        @Override
        public void loadFailed(FriendlyException exception) {
            if (exception.severity == FriendlyException.Severity.COMMON)
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + exception.getMessage()).queue();
            else
                m.editOriginal(event.getClient().getError() + " 楽曲を読み込み中にエラーが発生しました").queue();
        }
    }
}
