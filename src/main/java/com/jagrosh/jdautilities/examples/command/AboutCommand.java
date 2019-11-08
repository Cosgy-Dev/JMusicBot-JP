/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jdautilities.examples.command;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;
import com.jagrosh.jdautilities.examples.doc.Author;
import net.dv8tion.jda.bot.entities.ApplicationInfo;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * @author John Grosh (jagrosh)
 */
@CommandInfo(
        name = "About",
        description = "Gets information about the bot."
)
@Author("John Grosh (jagrosh)")
public class AboutCommand extends Command {
    private boolean IS_AUTHOR = true;
    private String REPLACEMENT_ICON = "+";
    private final Color color;
    private final String description;
    private final Permission[] perms;
    private String oauthLink;
    private final String[] features;

    public AboutCommand(Color color, String description, String[] features, Permission... perms) {
        this.color = color;
        this.description = description;
        this.features = features;
        this.name = "about";
        this.help = "ボットに関する情報を表示します";
        this.guildOnly = false;
        this.perms = perms;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    public void setIsAuthor(boolean value) {
        this.IS_AUTHOR = value;
    }

    public void setReplacementCharacter(String value) {
        this.REPLACEMENT_ICON = value;
    }

    @Override
    protected void execute(CommandEvent event) {
        if (oauthLink == null) {
            try {
                ApplicationInfo info = event.getJDA().asBot().getApplicationInfo().complete();
                oauthLink = info.isBotPublic() ? info.getInviteUrl(0L, perms) : "";
            } catch (Exception e) {
                Logger log = LoggerFactory.getLogger("OAuth2");
                log.error("招待リンクを生成できませんでした ", e);
                oauthLink = "";
            }
        }
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(event.getGuild() == null ? color : event.getGuild().getSelfMember().getColor());
        builder.setAuthor("" + event.getSelfUser().getName() + "について!", null, event.getSelfUser().getAvatarUrl());
        boolean join = !(event.getClient().getServerInvite() == null || event.getClient().getServerInvite().isEmpty());
        boolean inv = !oauthLink.isEmpty();
        String invline = "\n" + (join ? "公式Discordチャンネルは [`こちら`](" + event.getClient().getServerInvite() + ")" : (inv ? "からお願いします " : ""))
                + (inv ? (join ? ", または " : "") + "あなたのサーバーに[`招待リンク`](" + oauthLink + ") " : "で招待することができます。") + "!";
        String author = event.getJDA().getUserById(event.getClient().getOwnerId()) == null ? "<@" + event.getClient().getOwnerId() + ">"
                : event.getJDA().getUserById(event.getClient().getOwnerId()).getName();
        StringBuilder descr = new StringBuilder().append("こんにちは！ 私は **").append(event.getSelfUser().getName()).append("**です。 ")
                .append(description).append("\n私は").append(JDAUtilitiesInfo.AUTHOR + "の[コマンド拡張](" + JDAUtilitiesInfo.GITHUB + ") (")
                .append(JDAUtilitiesInfo.VERSION).append(")と[JDAライブラリ](https://github.com/DV8FromTheWorld/JDA) (")
                .append(JDAInfo.VERSION).append(")を使用しており、私は、").append(author).append(IS_AUTHOR ? "にJava言語で作られました。" : "が所有しています。")
                .append("\n`").append(event.getClient().getTextualPrefix()).append(event.getClient().getHelpWord())
                .append("`でコマンドを確認することができます。").append(join || inv ? invline : "").append("\n\n機能の特徴： ```css");
        for (String feature : features)
            descr.append("\n").append(event.getClient().getSuccess().startsWith("<") ? REPLACEMENT_ICON : event.getClient().getSuccess()).append(" ").append(feature);
        descr.append(" ```");
        builder.setDescription(descr);
        if (event.getJDA().getShardInfo() == null) {
            builder.addField("統計", event.getJDA().getGuilds().size() + " サーバー\n1 シャード", true);
            builder.addField("ユーザー", event.getJDA().getUsers().size() + " ユニーク\n" + event.getJDA().getGuilds().stream().mapToInt(g -> g.getMembers().size()).sum() + " トータル", true);
            builder.addField("チャンネル", event.getJDA().getTextChannels().size() + " テキスト\n" + event.getJDA().getVoiceChannels().size() + " ボイス", true);
        } else {
            builder.addField("ステータス", (event.getClient()).getTotalGuilds() + " サーバー\nシャード " + (event.getJDA().getShardInfo().getShardId() + 1)
                    + "/" + event.getJDA().getShardInfo().getShardTotal(), true);
            builder.addField("", event.getJDA().getUsers().size() + " ユーザーのシャード\n" + event.getJDA().getGuilds().size() + " サーバー", true);
            builder.addField("", event.getJDA().getTextChannels().size() + " テキストチャンネル\n" + event.getJDA().getVoiceChannels().size() + " ボイスチャンネル", true);
        }
        builder.setFooter("最後に行われた再起動", null);
        builder.setTimestamp(event.getClient().getStartTime());
        event.reply(builder.build());
    }

}
