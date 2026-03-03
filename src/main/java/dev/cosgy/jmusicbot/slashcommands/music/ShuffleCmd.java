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
package dev.cosgy.jmusicbot.slashcommands.music;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;

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
                event.reply(event.getClient().getError() + "再生待ちに曲がありません!").queue();
                break;
            case 1:
                event.reply(event.getClient().getWarning() + "再生待ちには現在1曲しかありません!").queue();
                break;
            default:
                event.reply(event.getClient().getSuccess() + "" + s + "曲をシャッフルしました。").queue();
                break;
        }
    }
}
