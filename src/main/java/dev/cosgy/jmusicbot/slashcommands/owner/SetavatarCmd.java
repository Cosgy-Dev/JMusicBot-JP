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
package dev.cosgy.jmusicbot.slashcommands.owner;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetavatarCmd extends OwnerCommand {
    public SetavatarCmd(Bot bot) {
        this.name = "setavatar";
        this.help = "ボットのアバターを設定します";
        this.arguments = "<url>";
        this.aliases = bot.getConfig().getAliases(this.name);
        //this.guildOnly = false;
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "image", "画像のURL", true));
        this.options = options;

    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String url = event.getOption("image").getAsString();
        InputStream s = OtherUtil.imageFromUrl(url);
        if (s == null) {
            event.reply(event.getClient().getError() + " 無効または見つからないURL").queue();
        } else {
            try {
                event.getJDA().getSelfUser().getManager().setAvatar(Icon.from(s)).queue(
                        v -> event.reply(event.getClient().getSuccess() + "アバターを変更しました。").queue(),
                        t -> event.reply(event.getClient().getError() + "アバターを設定できませんでした。").queue());
            } catch (IOException e) {
                event.reply(event.getClient().getError() + " 提供されたURLから読み込めませんでした。").queue();
            }
        }
    }

    @Override
    protected void execute(CommandEvent event) {
        String url;
        if (event.getArgs().isEmpty())
            if (!event.getMessage().getAttachments().isEmpty() && event.getMessage().getAttachments().get(0).isImage())
                url = event.getMessage().getAttachments().get(0).getUrl();
            else
                url = null;
        else
            url = event.getArgs();
        InputStream s = OtherUtil.imageFromUrl(url);
        if (s == null) {
            event.reply(event.getClient().getError() + " 無効または見つからないURL");
        } else {
            try {
                event.getSelfUser().getManager().setAvatar(Icon.from(s)).queue(
                        v -> event.reply(event.getClient().getSuccess() + "アバターを変更しました。"),
                        t -> event.reply(event.getClient().getError() + "アバターを設定できませんでした。"));
            } catch (IOException e) {
                event.reply(event.getClient().getError() + " 提供されたURLから読み込めませんでした。");
            }
        }
    }
}
