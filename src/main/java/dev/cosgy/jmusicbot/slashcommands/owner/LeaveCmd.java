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

package dev.cosgy.jmusicbot.slashcommands.owner;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

public class LeaveCmd extends OwnerCommand {
    private final Bot bot;

    public LeaveCmd(Bot bot) {
        this.bot = bot;
        this.name = "leave";
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "serverid", "サーバーID", true));
        this.options = options;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String id = event.getOption("serverid").getAsString();
        event.getJDA().getGuildById(id).leave().queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + "役割の名前、またはNONEなどを付けてください。");
            return;
        }

        event.getJDA().getGuildById(event.getArgs()).leave().queue();
    }
}
