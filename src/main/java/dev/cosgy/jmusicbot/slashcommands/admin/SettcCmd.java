/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package dev.cosgy.jmusicbot.slashcommands.admin;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.commons.utils.FinderUtil;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.AdminCommand;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class SettcCmd extends AdminCommand {
    public SettcCmd(Bot bot) {
        this.name = "settc";
        this.help = "ボットのコマンドチャンネルを設定します";
        this.arguments = "<チャンネル名|NONE|なし>";
        this.aliases = bot.getConfig().getAliases(this.name);

        this.children = new SlashCommand[]{new Set(), new None()};
    }

    @Override
    protected void execute(SlashCommandEvent event) {
    }

    // ここは普通のコマンド
    @Override
    protected void execute(CommandEvent event) {
        Logger log = LoggerFactory.getLogger("SettcCmd");
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + "チャンネルまたはNONEを含めてください。");
            return;
        }
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        if (event.getArgs().toLowerCase().matches("(none|なし)")) {
            s.setTextChannel(null);
            event.reply(event.getClient().getSuccess() + "音楽コマンドは現在どのチャンネルでも使用できます。");
        } else {
            List<TextChannel> list = FinderUtil.findTextChannels(event.getArgs(), event.getGuild());
            if (list.isEmpty())
                event.reply(event.getClient().getWarning() + "一致するチャンネルが見つかりませんでした \"" + event.getArgs() + "\"");
            else if (list.size() > 1)
                event.reply(event.getClient().getWarning() + FormatUtil.listOfTChannels(list, event.getArgs()));
            else {
                s.setTextChannel(list.get(0));
                log.info("音楽コマンド用のチャンネルを設定しました。");
                event.reply(event.getClient().getSuccess() + "音楽コマンドを<#" + list.get(0).getId() + ">のみで使用できるように設定しました。");
            }
        }
    }

    private static class Set extends AdminCommand {
        public Set() {
            this.name = "set";
            this.help = "音楽コマンド用のチャンネルを設定";

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.CHANNEL, "channel", "テキストチャンネル", true));

            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (checkAdminPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            Settings s = event.getClient().getSettingsFor(event.getGuild());


            if (event.getOption("channel").getChannelType() != ChannelType.TEXT) {
                event.reply(event.getClient().getError() + "テキストチャンネルを設定して下さい。").queue();
                return;
            }
            Long channelId = event.getOption("channel").getAsLong();
            TextChannel tc = event.getGuild().getTextChannelById(channelId);

            s.setTextChannel(tc);
            event.reply(client.getSuccess() + "音楽コマンドを<#" + tc.getId() + ">のみで使用できるように設定しました。").queue();

        }
    }

    private static class None extends AdminCommand {
        public None() {
            this.name = "none";
            this.help = "音楽コマンド用チャンネルの設定を無効にします。";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (checkAdminPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            Settings s = event.getClient().getSettingsFor(event.getGuild());
            s.setTextChannel(null);
            event.reply(event.getClient().getSuccess() + "音楽コマンドは現在どのチャンネルでも使用できます。").queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            Settings s = event.getClient().getSettingsFor(event.getGuild());
            s.setTextChannel(null);
            event.replySuccess("音楽コマンドは現在どのチャンネルでも使用できます。");
        }
    }

}
