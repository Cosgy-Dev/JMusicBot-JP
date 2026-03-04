package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import java.util.stream.Collectors;

public class SlashCommandEvent {
    private final CommandClient client;
    private final SlashCommandInteractionEvent event;

    public SlashCommandEvent(CommandClient client, SlashCommandInteractionEvent event) {
        this.client = client;
        this.event = event;
    }

    public CommandClient getClient() {
        return client;
    }

    public SlashCommandInteractionEvent getEvent() {
        return event;
    }

    public JDA getJDA() {
        return event.getJDA();
    }

    public Guild getGuild() {
        return event.getGuild();
    }

    public User getUser() {
        return event.getUser();
    }

    public SelfUser getSelfUser() {
        return event.getJDA().getSelfUser();
    }

    public Member getMember() {
        return event.getMember();
    }

    public boolean isOwner() {
        return event.getUser().getId().equals(client.getOwnerId()) || client.getCoOwnerIds().contains(event.getUser().getId());
    }

    public boolean isFromGuild() {
        return event.isFromGuild();
    }

    public ChannelType getChannelType() {
        return event.getChannelType();
    }

    public GuildMessageChannel getGuildChannel() {
        return event.getGuildChannel();
    }

    public MessageChannelUnion getChannel() {
        return event.getChannel();
    }

    public TextChannel getTextChannel() {
        return event.getChannelType() == ChannelType.TEXT ? event.getChannel().asTextChannel() : null;
    }

    public OptionMapping getOption(String name) {
        return event.getOption(name);
    }

    public String getArgs() {
        if (event.getOptions().isEmpty()) return "";
        return event.getOptions().stream()
                .map(OptionMapping::getAsString)
                .collect(Collectors.joining(" "));
    }

    public ReplyCallbackAction reply(String content) {
        return event.reply(content);
    }

    public ReplyCallbackAction reply(MessageCreateData data) {
        return event.reply(data);
    }

    public ReplyCallbackAction replyEmbeds(net.dv8tion.jda.api.entities.MessageEmbed embed) {
        return event.replyEmbeds(embed);
    }

    public ReplyCallbackAction deferReply() {
        return event.deferReply();
    }

    public InteractionHook getHook() {
        return event.getHook();
    }

    public void replySuccess(String content) {
        reply(client.getSuccess() + content).queue();
    }

    public void replyWarning(String content) {
        reply(client.getWarning() + content).queue();
    }

    public void replyError(String content) {
        reply(client.getError() + content).queue();
    }
}
