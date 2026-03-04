package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.api.entities.Guild;

public interface GuildSettingsManager {
    GuildSettingsProvider getSettings(Guild guild);
}
