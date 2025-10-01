package dev.cosgy.jmusicbot.slashcommands.general;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;

public class InviteCommand extends SlashCommand {
    public InviteCommand() {
        this.name = "invite";
        this.help = "Botの招待用URLを表示します。";
        //this.guildOnly = false;
        this.aliases = new String[]{"share"};
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        long botId = event.getJDA().getSelfUser().getIdLong();
        Permission[] permissions = new Permission[]
                {
                        Permission.MANAGE_CHANNEL,
                        Permission.MANAGE_ROLES,
                        Permission.MESSAGE_MANAGE,
                        Permission.NICKNAME_CHANGE,
                        Permission.MESSAGE_SEND,
                        Permission.VOICE_CONNECT,
                        Permission.VOICE_SPEAK,
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_EXT_EMOJI
                };

        event.reply(String.format("https://discord.com/oauth2/authorize?client_id=%s&scope=bot%20applications.commands&permissions=%s", botId, Permission.getRaw(permissions))).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        long botId = event.getSelfUser().getIdLong();
        Permission[] permissions = new Permission[]
                {
                        Permission.MANAGE_CHANNEL,
                        Permission.MANAGE_ROLES,
                        Permission.MESSAGE_MANAGE,
                        Permission.NICKNAME_CHANGE,
                        Permission.MESSAGE_SEND,
                        Permission.VOICE_CONNECT,
                        Permission.VOICE_SPEAK,
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_EXT_EMOJI
                };

        event.replyFormatted("https://discord.com/oauth2/authorize?client_id=%s&scope=bot&permissions=%s", botId, Permission.getRaw(permissions));
    }


}
