/*
 *  Copyright 2024 Cosgy Dev (info@cosgy.dev).
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

package dev.cosgy.jmusicbot.slashcommands.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.slashcommands.AdminCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Objects;

public class SetvcStatusCmd extends AdminCommand {
    public SetvcStatusCmd(Bot bot) {
        this.name = "setvcstatus";
        this.help = "VCステータスに再生中を表示するかを設定します。";
        this.arguments = "<true|false>";
        this.aliases = bot.getConfig().getAliases(this.name);

        this.options = List.of(
                new OptionData(OptionType.BOOLEAN, "status", "有効:true 無効:false", true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (checkAdminPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
            return;
        }

        var status = Objects.requireNonNull(event.getOption("status")).getAsBoolean();
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        s.setVCStatus(status);

        event.reply(event.getClient().getSuccess() + "VCステータスに再生中を表示するかを`" + status + "`に設定しました。").queue();
    }

    @Override
    protected void execute(CommandEvent event) {

        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + "trueかfalseを含めてください。");
            return;
        }

        Settings s = event.getClient().getSettingsFor(event.getGuild());

        if (event.getArgs().toLowerCase().matches("(false|無効)")) {
            s.setVCStatus(false);
            event.reply(event.getClient().getSuccess() + "VCステータスに再生中を表示しないように設定しました。");
        }else if(event.getArgs().toLowerCase().matches("(true|有効)")) {
            s.setVCStatus(true);
            event.reply(event.getClient().getSuccess() + "VCステータスに再生中を表示するように設定しました。");
        }else {
            event.reply(event.getClient().getError() + "trueかfalseを含めてください。");
        }
    }
}
