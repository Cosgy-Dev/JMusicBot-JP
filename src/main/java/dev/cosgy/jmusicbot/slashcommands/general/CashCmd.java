package dev.cosgy.jmusicbot.slashcommands.general;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.util.Cache;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Kosugi_kun
 */
public class CashCmd extends SlashCommand {
    private final Paginator.Builder builder;
    public Bot bot;

    public CashCmd(Bot bot) {
        this.bot = bot;
        this.name = "cache";
        this.help = "キャッシュに保存されている曲を表示します。";
        //this.guildOnly = true;
        this.category = new Category("General");
        this.aliases = bot.getConfig().getAliases(this.name);
        this.children = new SlashCommand[]{new DeleteCmd(bot), new ShowCmd(bot)};
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
    protected void execute(SlashCommandEvent slashCommandEvent) {
    }

    @Override
    protected void execute(CommandEvent event) {
        if (!bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
            event.reply("キャッシュに保存された曲がありませんでした。");
            return;
        }
        int pagenum = 1;
        try {
            pagenum = Integer.parseInt(event.getArgs());
        } catch (NumberFormatException ignore) {
        }

        List<Cache> cache = bot.getCacheLoader().GetCache(event.getGuild().getId());

        String[] songs = new String[cache.size()];
        long total = 0;
        for (int i = 0; i < cache.size(); i++) {
            total += Long.parseLong(cache.get(i).getLength());
            songs[i] = "`[" + FormatUtil.formatTime(Long.parseLong(cache.get(i).getLength())) + "]` **" + cache.get(i).getTitle() + "** - <@" + cache.get(i).getUserId() + ">";
        }
        long finTotal = total;
        builder.setText((i1, i2) -> getQueueTitle(event.getClient().getSuccess(), songs.length, finTotal))
                .setItems(songs)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColor())
        ;
        builder.build().paginate(event.getChannel(), pagenum);
    }

    private String getQueueTitle(String success, int songsLength, long total) {
        StringBuilder sb = new StringBuilder();

        return FormatUtil.filter(sb.append(success).append(" キャッシュに保存された曲一覧 | ").append(songsLength)
                .append(" 曲 | `").append(FormatUtil.formatTime(total)).append("` ")
                .toString());
    }

    public static class DeleteCmd extends DJCommand {
        public DeleteCmd(Bot bot) {
            super(bot);
            this.name = "delete";
            this.aliases = new String[]{"dl", "clear"};
            this.help = "保存されているキャッシュを削除します。";
            //this.guildOnly = true;
        }

        @Override
        public void doCommand(CommandEvent event) {
            if (!bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                event.reply("キャッシュが存在しません。");
                return;
            }

            try {
                bot.getCacheLoader().deleteCache(event.getGuild().getId());
            } catch (IOException e) {
                event.reply("キャッシュを削除する際にエラーが発生しました。");
                e.printStackTrace();
                return;
            }
            event.reply("キャッシュを削除しました。");
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            if (!bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                event.reply("キャッシュが存在しません。").queue();
                return;
            }

            try {
                bot.getCacheLoader().deleteCache(event.getGuild().getId());
            } catch (IOException e) {
                event.reply("キャッシュを削除する際にエラーが発生しました。").queue();
                e.printStackTrace();
                return;
            }
            event.reply("キャッシュを削除しました。").queue();
        }
    }

    public class ShowCmd extends SlashCommand {
        private final Paginator.Builder builder;

        public ShowCmd(Bot bot) {
            this.name = "show";
            this.help = "キャッシュされている楽曲を一覧表示します。";
            //this.guildOnly = true;
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
        protected void execute(SlashCommandEvent event) {
            if (!bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                event.reply("キャッシュに保存された曲がありませんでした。").queue();
                return;
            }
            int pagenum = 1;
            event.reply("キャッシュを取得します。").queue();

            List<Cache> cache = bot.getCacheLoader().GetCache(event.getGuild().getId());

            String[] songs = new String[cache.size()];
            long total = 0;
            for (int i = 0; i < cache.size(); i++) {
                total += Long.parseLong(cache.get(i).getLength());
                songs[i] = "`[" + FormatUtil.formatTime(Long.parseLong(cache.get(i).getLength())) + "]` **" + cache.get(i).getTitle() + "** - <@" + cache.get(i).getUserId() + ">";
            }
            long finTotal = total;
            builder.setText((i1, i2) -> getQueueTitle(event.getClient().getSuccess(), songs.length, finTotal))
                    .setItems(songs)
                    .setUsers(event.getUser())
                    .setColor(event.getMember().getColor());
            builder.build().paginate(event.getChannel(), pagenum);
        }

    }
}
