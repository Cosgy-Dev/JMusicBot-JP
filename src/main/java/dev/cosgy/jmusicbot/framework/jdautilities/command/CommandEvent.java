package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Objects;
import java.util.function.Consumer;

public class CommandEvent {
    private final CommandClient client;
    private final Message message;
    private String args;

    public CommandEvent(CommandClient client, Message message, String args) {
        this.client = client;
        this.message = message;
        this.args = args == null ? "" : args;
    }

    public CommandClient getClient() {
        return client;
    }

    public Message getMessage() {
        return message;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args == null ? "" : args;
    }

    public JDA getJDA() {
        return message.getJDA();
    }

    public Guild getGuild() {
        return message.getGuild();
    }

    public User getAuthor() {
        return message.getAuthor();
    }

    public Member getMember() {
        return message.getMember();
    }

    public Member getSelfMember() {
        Guild guild = getGuild();
        return guild == null ? null : guild.getSelfMember();
    }

    public SelfUser getSelfUser() {
        return getJDA().getSelfUser();
    }

    public MessageChannelUnion getChannel() {
        return message.getChannel();
    }

    public GuildMessageChannel getGuildChannel() {
        return message.getGuildChannel();
    }

    public ChannelType getChannelType() {
        return message.getChannelType();
    }

    public TextChannel getTextChannel() {
        return message.isFromGuild() && message.getChannelType().isMessage() && message.getChannelType() == ChannelType.TEXT
                ? message.getChannel().asTextChannel()
                : null;
    }

    public boolean isFromType(ChannelType type) {
        return message.getChannelType() == type;
    }

    public boolean isOwner() {
        return Objects.equals(getAuthor().getId(), client.getOwnerId()) || client.getCoOwnerIds().contains(getAuthor().getId());
    }

    public void reply(String content) {
        getChannel().sendMessage(content).queue();
    }

    public void reply(String content, Consumer<Message> success) {
        getChannel().sendMessage(content).queue(success);
    }

    public void reply(MessageEmbed embed) {
        getChannel().sendMessageEmbeds(embed).queue();
    }

    public void reply(MessageCreateData data, Consumer<Message> success) {
        getChannel().sendMessage(data).queue(success);
    }

    public void reply(MessageCreateData data) {
        getChannel().sendMessage(data).queue();
    }

    public void replySuccess(String content) {
        reply(client.getSuccess() + content);
    }

    public void replyWarning(String content) {
        reply(client.getWarning() + content);
    }

    public void replyError(String content) {
        reply(client.getError() + content);
    }

    public void replyInDm(String content) {
        getAuthor().openPrivateChannel().flatMap(channel -> channel.sendMessage(content)).queue();
    }

    public void replyInDm(String content, Consumer<Message> success, Consumer<Throwable> failure) {
        RestAction<Message> action = getAuthor().openPrivateChannel().flatMap(channel -> channel.sendMessage(content));
        if (failure != null) {
            action.queue(success, failure::accept);
        } else {
            action.queue(success);
        }
    }

    public void reactSuccess() {
        message.addReaction(Emoji.fromUnicode("✅")).queue();
    }

    public void replyFormatted(String format, Object... args) {
        reply(String.format(format, args));
    }
}
