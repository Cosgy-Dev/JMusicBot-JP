package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.api.Permission;

public abstract class Interaction {
    protected boolean ownerCommand = false;
    protected boolean guildOnly = false;
    protected Permission[] userPermissions = new Permission[0];
    protected Permission[] botPermissions = new Permission[0];
    protected int cooldown = 0;
    protected CooldownScope cooldownScope = CooldownScope.USER;
    protected String userMissingPermMessage = "%s You need the `%s` permission in this %s to use this command!";
    protected String botMissingPermMessage = "%s I need the `%s` permission in this %s to do that!";

    public Permission[] getUserPermissions() {
        return userPermissions;
    }

    public Permission[] getBotPermissions() {
        return botPermissions;
    }

    public boolean isOwnerCommand() {
        return ownerCommand;
    }
}
