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
package dev.cosgy.JMusicBot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.JMusicBot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ShuffleCmd extends MusicCommand {
    public ShuffleCmd(Bot bot) {
        super(bot);
        this.name = "shuffle";
        this.help = "追加した曲をシャッフル";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        int s = handler.getQueue().shuffle(event.getAuthor().getIdLong());
        switch (s) {
            case 0:
                event.replyError("再生待ちに曲がありません!");
                break;
            case 1:
                event.replyWarning("再生待ちには現在1曲しかありません!");
                break;
            default:
                event.replySuccess("" + s + "曲をシャッフルしました。");
                break;
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        int s = handler.getQueue().shuffle(event.getUser().getIdLong());
        switch (s) {
            case 0:
                event.reply(client.getError() + "再生待ちに曲がありません!").queue();
                break;
            case 1:
                event.reply(client.getWarning() + "再生待ちには現在1曲しかありません!").queue();
                break;
            default:
                event.reply(client.getSuccess() + "" + s + "曲をシャッフルしました。").queue();
                break;
        }
    }
}
