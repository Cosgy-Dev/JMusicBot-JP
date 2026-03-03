package dev.cosgy.jmusicbot.slashcommands.music;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.QueuePaginatorManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.List;

/**
 * 再生中・キュー中の楽曲情報をページ表示するコマンド。
 */
public class QueueCmd extends MusicCommand {
    public QueueCmd(Bot bot) {
        super(bot);
        this.name = "queue";
        this.help = "再生待ちの楽曲一覧を表示します";
        this.arguments = "[ページ]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event) {
        int page = parsePage(event.getArgs());
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> queue = handler.getQueue().getList();

        if (queue.isEmpty()) {
            MessageCreateData response = createNoQueueCreateMessage(event, handler);
            event.reply(response, m -> {
                try {
                    MessageCreateData np = handler.getNowPlaying(event.getJDA());
                    if (np != null) bot.getNowplayingHandler().setLastNPMessage(m);
                } catch (Exception ignored) {
                }
            });
            return;
        }

        MessageEmbed embed = createQueueEmbed(event.getClient().getSuccess(), event.getClient().getSettingsFor(event.getGuild()), handler, queue, page);
        ActionRow row = createRow(event.getAuthor().getId());
        event.getChannel().sendMessageEmbeds(embed).setComponents(row).queue();
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        event.deferReply().queue();
        int page = 1;

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> queue = handler.getQueue().getList();

        if (queue.isEmpty()) {
            MessageEditData response = createNoQueueEditMessage(event, handler);
            event.getHook().editOriginal(response).queue();
            return;
        }

        MessageEmbed embed = createQueueEmbed(event.getClient().getSuccess(), event.getClient().getSettingsFor(event.getGuild()), handler, queue, page);
        ActionRow row = createRow(event.getUser().getId());
        event.getHook().editOriginalEmbeds(embed).setComponents(row).queue();
    }

    private int parsePage(String raw) {
        try {
            return Integer.parseInt(raw == null ? "1" : raw.trim());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private MessageEmbed createQueueEmbed(String successEmoji, Settings settings, AudioHandler handler, List<QueuedTrack> queue, int requestedPage) {
        int totalPages = Math.max(1, (int) Math.ceil(queue.size() / 10.0));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        RepeatMode repeatMode = settings.getRepeatMode();
        return QueuePaginatorManager.createQueuePageEmbed(handler, queue, successEmoji, repeatMode, page, totalPages);
    }

    private ActionRow createRow(String userId) {
        Button btnPrev = Button.secondary("queue:prev:" + userId, "⏮ 前へ");
        Button btnNext = Button.secondary("queue:next:" + userId, "⏭ 次へ");
        Button btnClose = Button.danger("queue:close:" + userId, "❌ 閉じる");
        return ActionRow.of(btnPrev, btnNext, btnClose);
    }

    private MessageCreateData createNoQueueCreateMessage(CommandEvent event, AudioHandler handler) {
        MessageCreateData display = createNoQueueBase(event.getClient().getWarning(), event.getJDA(), handler);
        return new MessageCreateBuilder()
                .setContent(display.getContent())
                .setEmbeds(display.getEmbeds())
                .build();
    }

    private MessageEditData createNoQueueEditMessage(SlashCommandEvent event, AudioHandler handler) {
        MessageCreateData display = createNoQueueBase(event.getClient().getWarning(), event.getJDA(), handler);
        return new MessageEditBuilder()
                .setContent(display.getContent())
                .setEmbeds(display.getEmbeds())
                .build();
    }

    private MessageCreateData createNoQueueBase(String warningEmoji, net.dv8tion.jda.api.JDA jda, AudioHandler handler) {
        MessageCreateData nowPlaying;
        try {
            nowPlaying = handler.getNowPlaying(jda);
        } catch (Exception e) {
            nowPlaying = null;
        }
        MessageCreateData noMusic = handler.getNoMusicPlaying(jda);
        return new MessageCreateBuilder()
                .setContent(warningEmoji + " 再生待ちの楽曲はありません。")
                .setEmbeds((nowPlaying == null ? noMusic : nowPlaying).getEmbeds().get(0))
                .build();
    }
}
