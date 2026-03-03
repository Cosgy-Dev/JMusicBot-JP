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
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class SetvcCmd extends AdminCommand {
    public SetvcCmd(Bot bot) {
        this.name = "setvc";
        this.help = "再生に使用する音声チャンネルを固定します。";
        this.arguments = "<チャンネル名|NONE|なし>";
        this.aliases = bot.getConfig().getAliases(this.name);

        this.children = new SlashCommand[]{new Set(), new None()};
    }

    @Override
    protected void execute(SlashCommandEvent slashCommandEvent) {
    }

    @Override
    protected void execute(CommandEvent event) {
        Logger log = LoggerFactory.getLogger("SetVcCmd");
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + "音声チャンネルまたはNONEを含めてください。");
            return;
        }
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        if (event.getArgs().toLowerCase().matches("(none|なし)")) {
            s.setVoiceChannel(null);
            event.reply(event.getClient().getSuccess() + "音楽はどの音声チャンネルでも再生できます。");
        } else {
            List<VoiceChannel> list = FinderUtil.findVoiceChannels(event.getArgs(), event.getGuild());
            if (list.isEmpty())
                event.reply(event.getClient().getWarning() + "一致する音声チャンネルが見つかりませんでした \"" + event.getArgs() + "\"");
            else if (list.size() > 1)
                event.reply(event.getClient().getWarning() + FormatUtil.listOfVChannels(list, event.getArgs()));
            else {
                s.setVoiceChannel(list.get(0));
                log.info("音楽チャンネルを設定しました。");
                event.reply(event.getClient().getSuccess() + "音楽は**" + list.get(0).getAsMention() + "**でのみ再生できるようになりました。");
            }
        }
    }

    private static class Set extends AdminCommand {
        public Set() {
            this.name = "set";
            this.help = "再生に使用する音声チャンネルを設定";

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.CHANNEL, "channel", "音声チャンネル", true));

            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (checkAdminPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            Settings s = event.getClient().getSettingsFor(event.getGuild());
            Long channel = event.getOption("channel").getAsLong();

            if (event.getOption("channel").getChannelType() != ChannelType.VOICE) {
                event.reply(event.getClient().getError() + "音声チャンネルを設定して下さい").queue();
            }

            VoiceChannel vc = event.getGuild().getVoiceChannelById(channel);
            s.setVoiceChannel(vc);
            event.reply(event.getClient().getSuccess() + "音楽は**" + vc.getAsMention() + "**でのみ再生できるようになりました。").queue();
        }
    }

    private static class None extends AdminCommand {
        public None() {
            this.name = "none";
            this.help = "再生に使用する音声チャンネルの設定をリセットします。";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (checkAdminPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            Settings s = event.getClient().getSettingsFor(event.getGuild());
            s.setVoiceChannel(null);
            event.reply(event.getClient().getSuccess() + "音楽はどの音声チャンネルでも再生できます。").queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            Settings s = event.getClient().getSettingsFor(event.getGuild());
            s.setVoiceChannel(null);
            event.replySuccess("音楽はどの音声チャンネルでも再生できます。");
        }
    }
}
