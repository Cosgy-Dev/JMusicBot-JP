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
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VolumeCmd extends MusicCommand {
    Logger log = LoggerFactory.getLogger("Volume");

    public VolumeCmd(Bot bot) {
        super(bot);
        this.name = "volume";
        this.aliases = new String[]{"vol"};
        this.help = "音量を設定または表示します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "[0-150]";

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.INTEGER, "vol", "音量は0から150までの整数", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        int volume = Objects.requireNonNull(handler).getPlayer().getVolume();
        if (event.getArgs().isEmpty()) {
            event.reply(FormatUtil.volumeIcon(volume) + " 現在の音量は`" + volume + "`です。");
        } else {
            int nvolume;
            try {
                nvolume = Integer.parseInt(event.getArgs());
            } catch (NumberFormatException e) {
                nvolume = -1;
            }
            if (nvolume < 0 || nvolume > 150)
                event.reply(event.getClient().getError() + " 音量は0から150までの整数でないといけません。");
            else {
                handler.getPlayer().setVolume(nvolume);
                settings.setVolume(nvolume);
                event.reply(FormatUtil.volumeIcon(nvolume) + " 音量を`" + volume + "`から`" + nvolume + "`に変更しました。");
                log.info(event.getGuild().getName() + "での音量が" + volume + "から" + nvolume + "に変更されました。");
            }
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        int volume = handler.getPlayer().getVolume();
        int nvolume;
        try {
            nvolume = Integer.parseInt(event.getOption("vol").getAsString());
        } catch (NumberFormatException e) {
            nvolume = -1;
        }
        if (nvolume < 0 || nvolume > 150)
            event.reply(event.getClient().getError() + " 音量は0から150までの整数でないといけません。").queue();
        else {
            handler.getPlayer().setVolume(nvolume);
            settings.setVolume(nvolume);
            event.reply(FormatUtil.volumeIcon(nvolume) + " 音量を`" + volume + "`から`" + nvolume + "`に変更しました。").queue();
            log.info(event.getGuild().getName() + "での音量が" + volume + "から" + nvolume + "に変更されました。");
        }
    }
}
