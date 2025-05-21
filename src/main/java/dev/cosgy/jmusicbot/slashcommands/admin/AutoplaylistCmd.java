/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package dev.cosgy.jmusicbot.slashcommands.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.slashcommands.AdminCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kosugi_kun
 */
public class AutoplaylistCmd extends AdminCommand {
    private final Bot bot;

    public AutoplaylistCmd(Bot bot) {
        this.bot = bot;
        this.guildOnly = true;
        this.name = "autoplaylist";
        this.arguments = "<name|NONE|なし>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.help = "サーバーの自動再生リストを設定";
        this.ownerCommand = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "name", "プレイリストの名前", true));

        this.options = options;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (checkAdminPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
            return;
        }
        
        String pName = event.getOption("name").getAsString();
        if (pName.toLowerCase().matches("(none|なし)")) {
            Settings settings = event.getClient().getSettingsFor(event.getGuild());
            settings.setDefaultPlaylist(null);
            event.reply(event.getClient().getSuccess() + "**" + event.getGuild().getName() + "** での自動再生リストを、なしに設定しました。").queue();
            return;
        }
        if (bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), pName) == null) {
            event.reply(event.getClient().getError() + "`" + pName + "`を見つけることができませんでした!").queue();
        } else {
            Settings settings = event.getClient().getSettingsFor(event.getGuild());
            settings.setDefaultPlaylist(pName);
            event.reply(event.getClient().getSuccess() + "**" + event.getGuild().getName() + "** での自動再生リストを、`" + pName + "`に設定しました。\n"
                    + "再生待ちに曲がないときは、自動再生リストの曲が再生されます。").queue();
        }
    }

    @Override
    public void execute(CommandEvent event) {
        if (!event.isOwner() || !event.getMember().isOwner()) return;
        String guildId = event.getGuild().getId();

        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + " 再生リスト名、またはNONEを含めてください。");
            return;
        }
        if (event.getArgs().toLowerCase().matches("(none|なし)")) {
            Settings settings = event.getClient().getSettingsFor(event.getGuild());
            settings.setDefaultPlaylist(null);
            event.reply(event.getClient().getSuccess() + "**" + event.getGuild().getName() + "** での自動再生リストを、なしに設定しました。");
            return;
        }
        String pName = event.getArgs().replaceAll("\\s+", "_");
        if (bot.getPlaylistLoader().getPlaylist(guildId, pName) == null) {
            event.reply(event.getClient().getError() + "`" + pName + "`を見つけることができませんでした!");
        } else {
            Settings settings = event.getClient().getSettingsFor(event.getGuild());
            settings.setDefaultPlaylist(pName);
            event.reply(event.getClient().getSuccess() + "**" + event.getGuild().getName() + "** での自動再生リストを、`" + pName + "`に設定しました。\n"
                    + "再生待ちに曲がないときは、自動再生リストの曲が再生されます。");
        }
    }
}