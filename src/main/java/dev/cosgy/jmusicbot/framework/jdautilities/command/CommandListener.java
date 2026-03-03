package dev.cosgy.jmusicbot.framework.jdautilities.command;

public interface CommandListener {
    default void onCommand(CommandEvent event, Command command) {}

    default void onSlashCommand(SlashCommandEvent event, SlashCommand command) {}

    default void onCompletedCommand(CommandEvent event, Command command) {}

    default void onCompletedSlashCommand(SlashCommandEvent event, SlashCommand command) {}

    default void onTerminatedCommand(CommandEvent event, Command command) {}

    default void onTerminatedSlashCommand(SlashCommandEvent event, SlashCommand command) {}

    default void onCommandException(CommandEvent event, Command command, Throwable throwable) {}

    default void onSlashCommandException(SlashCommandEvent event, SlashCommand command, Throwable throwable) {}
}
