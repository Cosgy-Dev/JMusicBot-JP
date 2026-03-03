/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package dev.cosgy.jmusicbot.slashcommands.dj;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PauseCmd extends DJCommand {
    Logger log = LoggerFactory.getLogger("Pause");

    public PauseCmd(Bot bot) {
        super(bot);
        this.name = "pause";
        this.help = "現在の曲を一時停止します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().isPaused()) {
            event.replyWarning("曲はすでに一時停止しています。 `" + event.getClient().getPrefix() + " play` を使用して一時停止を解除する事ができます。");
            return;
        }
        handler.getPlayer().setPaused(true);
        log.info(event.getGuild().getName() + "で" + handler.getPlayer().getPlayingTrack().getInfo().title + "を一時停止しました。");
        event.replySuccess("**" + handler.getPlayer().getPlayingTrack().getInfo().title + "**を一時停止にしました。 `" + event.getClient().getPrefix() + " play` を使用すると一時停止を解除できます。");

        Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PAUSED);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        if (!checkDJPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
            return;
        }
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().isPaused()) {
            event.reply(event.getClient().getWarning() + "曲はすでに一時停止しています。 `" + event.getClient().getPrefix() + " play` を使用して一時停止を解除する事ができます。").queue();
            return;
        }
        handler.getPlayer().setPaused(true);
        log.info(event.getGuild().getName() + "で" + handler.getPlayer().getPlayingTrack().getInfo().title + "を一時停止しました。");
        event.reply(event.getClient().getSuccess() + "**" + handler.getPlayer().getPlayingTrack().getInfo().title + "**を一時停止にしました。 `" + event.getClient().getPrefix() + " play` を使用すると一時停止を解除できます。").queue();

        Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PAUSED);
    }
}
