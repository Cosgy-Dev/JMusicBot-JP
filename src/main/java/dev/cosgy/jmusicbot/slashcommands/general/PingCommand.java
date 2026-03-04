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
package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.doc.standard.CommandInfo;
import dev.cosgy.jmusicbot.framework.jdautilities.examples.doc.Author;

import java.time.temporal.ChronoUnit;

/**
 * @author John Grosh (jagrosh)
 */
@CommandInfo(
        name = {"Ping", "Pong"},
        description = "ボットのレイテンシを確認します"
)
@Author("John Grosh (jagrosh)")
public class PingCommand extends SlashCommand {

    public PingCommand() {
        this.name = "ping";
        this.help = "ボットのレイテンシをチェックします";
        //this.guildOnly = false;
        this.aliases = new String[]{"pong"};
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply("応答速度を測定中...").queue(m -> {
            m.editOriginal("WebSocket: " + event.getJDA().getGatewayPing() + "ms").queue();
        });
    }

    @Override
    protected void execute(CommandEvent event) {
        event.reply("応答速度を測定中...", m -> {
            long ping = event.getMessage().getTimeCreated().until(m.getTimeCreated(), ChronoUnit.MILLIS);
            m.editMessage("応答速度: " + ping + "ms | WebSocket: " + event.getJDA().getGatewayPing() + "ms").queue();
        });
    }

}
