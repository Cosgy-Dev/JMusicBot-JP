/*
 *  Copyright 2021 Cosgy Dev (info@cosgy.dev).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.Command;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HelpCmd extends SlashCommand {
    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4000;
    public Bot bot;

    public HelpCmd(Bot bot) {
        this.bot = bot;
        this.name = "help";
        this.help = "コマンド一覧を表示します。";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        List<MessageEmbed> embeds = buildHelpEmbeds(
                event.getJDA().getSelfUser().getName(),
                event.getClient().getCommands(),
                event.getClient().getTextualPrefix(),
                event.isOwner(),
                event.getClient().getServerInvite(),
                event.getMember() == null ? null : event.getMember().getColor()
        );

        if (embeds.size() == 1) {
            event.replyEmbeds(embeds.get(0)).queue();
            return;
        }

        event.deferReply().queue(hook -> {
            hook.editOriginalEmbeds(embeds.get(0)).queue();
            for (int i = 1; i < embeds.size(); i++) {
                hook.sendMessageEmbeds(embeds.get(i)).queue();
            }
        });
    }

    public void execute(CommandEvent event) {
        List<MessageEmbed> embeds = buildHelpEmbeds(
                event.getJDA().getSelfUser().getName(),
                event.getClient().getCommands(),
                event.getClient().getTextualPrefix(),
                event.isOwner(),
                event.getClient().getServerInvite(),
                event.getSelfMember() == null ? null : event.getSelfMember().getColor()
        );

        if (bot.getConfig().getHelpToDm()) {
            event.getAuthor().openPrivateChannel().queue(channel -> {
                for (MessageEmbed embed : embeds) {
                    channel.sendMessageEmbeds(embed).queue();
                }
                if (event.isFromType(ChannelType.TEXT))
                    event.reactSuccess();
            }, t -> event.replyWarning("ダイレクトメッセージをブロックしているため、ヘルプを送信できません。"));
        } else {
            for (MessageEmbed embed : embeds) {
                event.reply(embed);
            }
        }
    }

    private List<MessageEmbed> buildHelpEmbeds(String botName, List<Command> commands, String prefix, boolean isOwner, String serverInvite, Color color) {
        List<String> descriptions = splitHelpDescriptions(commands, prefix, isOwner, serverInvite);
        List<MessageEmbed> embeds = new ArrayList<>();
        for (int i = 0; i < descriptions.size(); i++) {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle(botName + " コマンド一覧")
                    .setDescription(descriptions.get(i));
            if (color != null)
                embedBuilder.setColor(color);
            if (descriptions.size() > 1)
                embedBuilder.setFooter("ページ " + (i + 1) + "/" + descriptions.size());
            embeds.add(embedBuilder.build());
        }
        return embeds;
    }

    private List<String> splitHelpDescriptions(List<Command> commands, String prefix, boolean isOwner, String serverInvite) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Category category = null;
        for (Command command : commands) {
            if (command.isHidden() || (command.isOwnerCommand() && !isOwner))
                continue;

            if (!Objects.equals(category, command.getCategory())) {
                category = command.getCategory();
                appendSection(pages, current, "\n\n__" + (category == null ? "カテゴリなし" : category.getName()) + "__:\n");
            }

            String visiblePrefix = prefix == null ? "" : prefix;
            String cmd = "\n`" + visiblePrefix + command.getName()
                    + (command.getArguments() == null ? "`" : " " + command.getArguments() + "`")
                    + " - " + command.getHelp();
            appendSection(pages, current, cmd);
        }

        if (serverInvite != null) {
            appendSection(pages, current, "\n\nさらにヘルプが必要な場合は、公式サーバーに参加することもできます: " + serverInvite);
        }

        if (pages.isEmpty() && current.length() == 0) {
            current.append("表示可能なコマンドがありません。");
        }
        if (current.length() > 0) {
            pages.add(current.toString().trim());
        }
        return pages;
    }

    private void appendSection(List<String> pages, StringBuilder current, String section) {
        if (section.length() > MAX_EMBED_DESCRIPTION_LENGTH) {
            int start = 0;
            while (start < section.length()) {
                int end = Math.min(start + MAX_EMBED_DESCRIPTION_LENGTH, section.length());
                appendSection(pages, current, section.substring(start, end));
                start = end;
            }
            return;
        }

        if (current.length() + section.length() > MAX_EMBED_DESCRIPTION_LENGTH) {
            pages.add(current.toString().trim());
            current.setLength(0);
        }
        current.append(section);
    }
}
