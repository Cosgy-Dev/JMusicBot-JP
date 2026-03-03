
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
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
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.List;

/**
 * 再生中・キュー中の楽曲情報をページ表示するコマンド。
 */
public class QueueCmd extends MusicCommand {
    private static final String REPEAT_ALL = "\uD83D\uDD01";   // 🔁
    private static final String REPEAT_SINGLE = "\uD83D\uDD02"; // 🔂

    public QueueCmd(Bot bot) {
        super(bot);
        this.name = "queue";
        this.help = "再生待ちの楽曲一覧を表示します";
        this.arguments = "[ページ]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        // ボットに必要な権限（メッセージ埋め込みの許可が必要。リアクションは不要）
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event) {
        // 引数からページ番号を取得（指定なしの場合は1ページ目）
        int page = 1;
        try {
            page = Integer.parseInt(event.getArgs().trim());
        } catch (NumberFormatException ignored) {
        }

        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> queue = ah.getQueue().getList();
        if (queue.isEmpty()) {
            // キューに楽曲がない場合、現在再生中の曲情報を表示して終了
            MessageCreateData nowPlayingData;
            try {
                nowPlayingData = ah.getNowPlaying(event.getJDA());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            MessageCreateData noMusicData = ah.getNoMusicPlaying(event.getJDA());
            MessageCreateData response = new MessageCreateBuilder()
                    .setContent(event.getClient().getWarning() + " 再生待ちの楽曲はありません。")
                    .setEmbeds((nowPlayingData == null ? noMusicData : nowPlayingData).getEmbeds().get(0))
                    .build();
            event.reply(response, m -> {
                // NowPlayingHandlerに最後の再生メッセージとして登録（現在曲の自動更新用）
                if (nowPlayingData != null) {
                    bot.getNowplayingHandler().setLastNPMessage(m);
                }
            });
            return;
        }

        // 総楽曲数および総再生時間を計算
        long totalDuration = 0;
        for (QueuedTrack qt : queue) {
            totalDuration += qt.getTrack().getDuration();
        }
        int totalPages = (int) Math.ceil(queue.size() / 10.0);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // ページ番号に対応するEmbedを生成
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        RepeatMode repeatMode = settings.getRepeatMode();
        // QueuePaginatorManager を使用して現在ページのEmbedを取得
        // （QueuePaginatorManager側で現在再生中の曲情報やエントリー数、総時間、繰り返しモードの表示を行う）
        net.dv8tion.jda.api.entities.MessageEmbed queueEmbed = QueuePaginatorManager.createQueuePageEmbed(
                ah, queue, event.getClient().getSuccess(), repeatMode, page, totalPages);

        // ページ切り替え用ボタンの作成（ユーザー限定のカスタムIDを付与）
        String userId = event.getAuthor().getId();
        Button btnPrev = Button.secondary("queue:prev:" + userId, "⏮ 前へ");
        Button btnNext = Button.secondary("queue:next:" + userId, "⏭ 次へ");
        Button btnClose = Button.danger("queue:close:" + userId, "❌ 閉じる");

        // Embedとボタンをチャンネルに送信
        event.getChannel().sendMessageEmbeds(queueEmbed)
                .setComponents(ActionRow.of(btnPrev, btnNext, btnClose))
                .queue();
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        // スラッシュコマンドの場合、まず応答を遅延させて思考中表示
        event.deferReply().queue();

        int page = 1;
        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> queue = ah.getQueue().getList();
        if (queue.isEmpty()) {
            // キューに楽曲がない場合、現在再生中の曲情報を表示して終了
            MessageCreateData nowPlayingData;
            try {
                nowPlayingData = ah.getNowPlaying(event.getJDA());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            MessageCreateData noMusicData = ah.getNoMusicPlaying(event.getJDA());
            MessageEditData response = new MessageEditBuilder()
                    .setContent(event.getClient().getWarning() + " 再生待ちの楽曲はありません。")
                    .setEmbeds((nowPlayingData == null ? noMusicData : nowPlayingData).getEmbeds().get(0))
                    .build();
            event.getHook().editOriginal(response).queue();
            return;
        }

        // 総楽曲数および総再生時間を計算
        long totalDuration = 0;
        for (QueuedTrack qt : queue) {
            totalDuration += qt.getTrack().getDuration();
        }
        int totalPages = (int) Math.ceil(queue.size() / 10.0);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        RepeatMode repeatMode = settings.getRepeatMode();
        net.dv8tion.jda.api.entities.MessageEmbed queueEmbed = QueuePaginatorManager.createQueuePageEmbed(
                ah, queue, event.getClient().getSuccess(), repeatMode, page, totalPages);

        // ページ切り替え用ボタン（カスタムIDにユーザーIDを含める）
        String userId = event.getUser().getId();
        Button btnPrev = Button.secondary("queue:prev:" + userId, "⏮ 前へ");
        Button btnNext = Button.secondary("queue:next:" + userId, "⏭ 次へ");
        Button btnClose = Button.danger("queue:close:" + userId, "❌ 閉じる");

        // Embedとボタンを初期応答に設定
        event.getHook().editOriginalEmbeds(queueEmbed)
                .setComponents(ActionRow.of(btnPrev, btnNext, btnClose))
                .queue();
    }
}
