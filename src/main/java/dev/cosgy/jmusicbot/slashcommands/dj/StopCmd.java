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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.queue.FairQueue;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class StopCmd extends DJCommand {
    Logger log = LoggerFactory.getLogger("Stop");

    public StopCmd(Bot bot) {
        super(bot);
        this.name = "stop";
        this.help = "現在の曲を停止して再生待ちを削除します。";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "option", "再生リストを保存する場合は`save`を入力", false));

        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        CacheLoader cache = bot.getCacheLoader();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        if (queue.size() > 0 && event.getArgs().matches("save")) {
            cache.Save(event.getGuild().getId(), handler.getQueue());
            event.reply(event.getClient().getSuccess() + " 再生待ちの" + queue.size() + "曲を保存して再生を停止しました。");
            log.info(event.getGuild().getName() + "で再生待ちを保存して,ボイスチャンネルから切断しました。");
        } else {
            event.reply(event.getClient().getSuccess() + " 再生待ちを削除して、再生を停止しました。");
        }
        handler.stopAndClear();
        bot.closeAudioConnection(event.getGuild().getIdLong());
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        if (!checkDJPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
            return;
        }
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        CacheLoader cache = bot.getCacheLoader();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        log.debug("再生待ちのサイズ：" + queue.size());

        if (event.getOption("option") == null) {
            event.reply(event.getClient().getSuccess() + " 再生待ちを削除して、再生を停止しました。").queue();
            log.info(event.getGuild().getName() + "で再生待ちを削除して,ボイスチャンネルから切断しました。");
            handler.stopAndClear();
            bot.closeAudioConnection(event.getGuild().getIdLong());
            return;
        }

        if (queue.size() > 0 && event.getOption("option").getAsString().equals("save")) {
            cache.Save(event.getGuild().getId(), handler.getQueue());
            event.reply(event.getClient().getSuccess() + " 再生待ちの" + queue.size() + "曲を保存して再生を停止しました。").queue();
            log.info(event.getGuild().getName() + "で再生待ちを保存して,ボイスチャンネルから切断しました。");
        } else {
            event.reply(event.getClient().getSuccess() + " 再生待ちを削除して、再生を停止しました。").queue();
            log.info(event.getGuild().getName() + "で再生待ちを削除して,ボイスチャンネルから切断しました。");
        }
        handler.stopAndClear();
        bot.closeAudioConnection(event.getGuild().getIdLong());
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String[] cmdOptions = {"save"};
        if (event.getName().equals("stop") && event.getFocusedOption().getName().equals("option")) {
            List<Command.Choice> options = Stream.of(cmdOptions)
                    .filter(word -> word.startsWith(event.getFocusedOption().getValue())) // only display words that start with the user's current input
                    .map(word -> new Command.Choice(word, word)) // map the words to choices
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }
}
