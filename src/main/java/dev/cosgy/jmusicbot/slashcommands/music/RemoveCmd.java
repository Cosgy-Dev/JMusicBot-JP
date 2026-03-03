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
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class RemoveCmd extends MusicCommand {
    public RemoveCmd(Bot bot) {
        super(bot);
        this.name = "remove";
        this.help = "再生待ちから曲を削除します";
        this.arguments = "<再生待ち番号|すべて|ALL>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "再生待ち番号|すべて|ALL", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getQueue().isEmpty()) {
            event.replyError("再生待ちには何もありません。");
            return;
        }
        if (event.getArgs().toLowerCase().matches("(all|すべて)")) {
            int count = handler.getQueue().removeAll(event.getAuthor().getIdLong());
            if (count == 0)
                event.replyWarning("再生待ちに曲がありません。");
            else
                event.replySuccess(count + "曲を削除しました。");
            return;
        }
        int pos;
        try {
            pos = Integer.parseInt(event.getArgs());
        } catch (NumberFormatException e) {
            pos = 0;
        }
        if (pos < 1 || pos > handler.getQueue().size()) {
            event.replyError(String.format("1から%sまでの有効な数字を入力してください!", handler.getQueue().size()));
            return;
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        boolean isDJ = event.getMember().hasPermission(Permission.MANAGE_SERVER);
        if (!isDJ)
            isDJ = event.getMember().getRoles().contains(settings.getRole(event.getGuild()));
        QueuedTrack qt = handler.getQueue().get(pos - 1);
        if (qt.getIdentifier() == event.getAuthor().getIdLong()) {
            handler.getQueue().remove(pos - 1);
            event.replySuccess("**" + (qt.getTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : qt.getTrack().getInfo().title) + "**をキューから削除しました。");
        } else if (isDJ) {
            handler.getQueue().remove(pos - 1);
            User u;
            try {
                u = event.getJDA().getUserById(qt.getIdentifier());
            } catch (Exception e) {
                u = null;
            }
            event.replySuccess("**" + qt.getTrack().getInfo().title
                    + "**を再生待ちから削除しました。\n(この曲は" + (u == null ? "誰かがリクエストしました。" : "**" + u.getName() + "**がリクエストしました。") + ")");
        } else {
            event.replyError("**" + (qt.getTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : qt.getTrack().getInfo().title) + "** を削除できませんでした。理由: DJ権限を持っていますか？自分のリクエスト以外は削除できません。");
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getQueue().isEmpty()) {
            event.reply(event.getClient().getError() + "再生待ちには何もありません。").queue();
            return;
        }

        if (event.getOption("input").getAsString().toLowerCase().matches("(all|すべて)")) {
            int count = handler.getQueue().removeAll(event.getUser().getIdLong());
            if (count == 0)
                event.reply(event.getClient().getWarning() + "再生待ちに曲がありません。").queue();
            else
                event.reply(event.getClient().getSuccess() + count + "曲を削除しました。").queue();
            return;
        }
        int pos;
        try {
            pos = Integer.parseInt(event.getOption("input").getAsString());
        } catch (NumberFormatException e) {
            pos = 0;
        }
        if (pos < 1 || pos > handler.getQueue().size()) {
            event.reply(event.getClient().getError() + String.format("1から%sまでの有効な数字を入力してください!", handler.getQueue().size())).queue();
            return;
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        boolean isDJ = event.getMember().hasPermission(Permission.MANAGE_SERVER);
        if (!isDJ)
            isDJ = event.getMember().getRoles().contains(settings.getRole(event.getGuild()));
        QueuedTrack qt = handler.getQueue().get(pos - 1);
        if (qt.getIdentifier() == event.getUser().getIdLong()) {
            handler.getQueue().remove(pos - 1);
            event.reply(event.getClient().getSuccess() + "**" + qt.getTrack().getInfo().title + "**をキューから削除しました。").queue();
        } else if (isDJ) {
            handler.getQueue().remove(pos - 1);
            User u;
            try {
                u = event.getJDA().getUserById(qt.getIdentifier());
            } catch (Exception e) {
                u = null;
            }
            event.reply(event.getClient().getSuccess() + "**" + qt.getTrack().getInfo().title
                    + "**を再生待ちから削除しました。\n(この曲は" + (u == null ? "誰かがリクエストしました。" : "**" + u.getName() + "**がリクエストしました。") + ")").queue();
        } else {
            event.reply(event.getClient().getError() + "**" + qt.getTrack().getInfo().title + "** を削除できませんでした。理由: DJ権限を持っていますか？自分のリクエスト以外は削除できません。").queue();
        }
    }
}
