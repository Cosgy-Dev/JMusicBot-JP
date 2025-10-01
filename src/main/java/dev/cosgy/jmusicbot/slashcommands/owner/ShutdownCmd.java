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
package dev.cosgy.jmusicbot.slashcommands.owner;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ShutdownCmd extends OwnerCommand {
    private final Bot bot;

    public ShutdownCmd(Bot bot) {
        this.bot = bot;
        this.name = "shutdown";
        this.help = "安全にシャットダウン";
        this.aliases = bot.getConfig().getAliases(this.name);
        //this.guildOnly = false;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply(event.getClient().getWarning() + "シャットダウンしています...\n不具合で正常に停止できないことがあります。その場合は強制的にボットを停止して下さい。").queue();
        bot.shutdown();
    }

    @Override
    protected void execute(CommandEvent event) {
        event.replyWarning("シャットダウンしています...\n不具合で正常に停止できないことがあります。その場合は強制的にボットを停止して下さい。");
        bot.shutdown();
    }
}
