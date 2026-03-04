package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandClientBuilder {
    String prefix = "!";
    String altPrefix;
    String ownerId;
    String success = "✅ ";
    String warning = "⚠️ ";
    String error = "❌ ";
    int linkedCacheSize = 0;
    GuildSettingsManager guildSettingsManager;
    CommandListener listener;
    String serverInvite;
    OnlineStatus status;
    Activity activity;
    final List<Command> commands = new ArrayList<>();
    final List<SlashCommand> slashCommands = new ArrayList<>();

    public CommandClientBuilder setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public CommandClientBuilder setAlternativePrefix(String altPrefix) {
        this.altPrefix = altPrefix;
        return this;
    }

    public CommandClientBuilder setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public CommandClientBuilder setEmojis(String success, String warning, String error) {
        this.success = success;
        this.warning = warning;
        this.error = error;
        return this;
    }

    public CommandClientBuilder useHelpBuilder(boolean ignored) {
        return this;
    }

    public CommandClientBuilder setLinkedCacheSize(int linkedCacheSize) {
        this.linkedCacheSize = linkedCacheSize;
        return this;
    }

    public CommandClientBuilder setGuildSettingsManager(GuildSettingsManager guildSettingsManager) {
        this.guildSettingsManager = guildSettingsManager;
        return this;
    }

    public CommandClientBuilder setListener(CommandListener listener) {
        this.listener = listener;
        return this;
    }

    public CommandClientBuilder setServerInvite(String serverInvite) {
        this.serverInvite = serverInvite;
        return this;
    }

    public CommandClientBuilder setStatus(OnlineStatus status) {
        this.status = status;
        return this;
    }

    public CommandClientBuilder setActivity(Activity activity) {
        this.activity = activity;
        return this;
    }

    public CommandClientBuilder addCommand(Command command) {
        this.commands.add(command);
        if (command instanceof SlashCommand) {
            this.slashCommands.add((SlashCommand) command);
        }
        return this;
    }

    public CommandClientBuilder addCommands(Command... commands) {
        this.commands.addAll(Arrays.asList(commands));
        for (Command command : commands) {
            if (command instanceof SlashCommand) {
                this.slashCommands.add((SlashCommand) command);
            }
        }
        return this;
    }

    public CommandClientBuilder addSlashCommands(SlashCommand... commands) {
        this.slashCommands.addAll(Arrays.asList(commands));
        return this;
    }

    public CommandClient build() {
        return new CommandClient(this);
    }
}
