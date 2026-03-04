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
package dev.cosgy.jmusicbot.slashcommands;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandClient;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public abstract class DJCommand extends MusicCommand {
    static Logger log = LoggerFactory.getLogger("DJCommand");

    public DJCommand(Bot bot) {
        super(bot);
        this.category = new Category("DJ", DJCommand::checkDJPermission);
    }

    public static boolean checkDJPermission(CommandEvent event) {
        if (event.getAuthor().getId().equals(event.getClient().getOwnerId())) {
            return true;
        }
        if (event.getGuild() == null) {
            return true;
        }
        if (event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        Role dj = settings.getRole(event.getGuild());
        return dj != null && (event.getMember().getRoles().contains(dj) || dj.getIdLong() == event.getGuild().getIdLong());
    }

    public static boolean checkDJPermission(CommandClient client, SlashCommandEvent event) {
        if (event.getUser().getId().equals(client.getOwnerId())) {
            return true;
        }
        if (event.getGuild() == null) {
            return true;
        }
        if (event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        Settings settings = client.getSettingsFor(event.getGuild());
        Role dj = settings.getRole(event.getGuild());
        return dj != null && (event.getMember().getRoles().contains(dj) || dj.getIdLong() == event.getGuild().getIdLong());
    }
}
