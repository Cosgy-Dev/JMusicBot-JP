/*
 *  Copyright 2024 Cosgy Dev (info@cosgy.dev).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.cosgy.jmusicbot.slashcommands.dj;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public class ForceToEnd extends DJCommand {
    public ForceToEnd(Bot bot) {
        super(bot);
        this.name = "forcetoend";
        this.help = "楽曲追加設定をフェア追加モードか通常追加モードを使用するかを切り替えます。設定を`TRUE`にすると通常追加モードになります。";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.options = List.of(new OptionData(OptionType.BOOLEAN, "value", "通常追加モードを使用するか", true));
    }

    @Override
    public void doCommand(CommandEvent event) {
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        boolean nowSetting = settings.isForceToEndQue();
        boolean newSetting;

        if (event.getArgs().isEmpty()) {
            newSetting = !nowSetting;
        } else if (event.getArgs().equalsIgnoreCase("true")
                || event.getArgs().equalsIgnoreCase("on")
                || event.getArgs().equalsIgnoreCase("有効")) {
            newSetting = true;
        } else if (event.getArgs().equalsIgnoreCase("false")
                || event.getArgs().equalsIgnoreCase("off")
                || event.getArgs().equalsIgnoreCase("無効")) {
            newSetting = false;
        } else {
            newSetting = nowSetting;
        }

        settings.setForceToEndQue(newSetting);
        event.replySuccess(buildMessage(newSetting));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        boolean newSetting = event.getOption("value") != null && event.getOption("value").getAsBoolean();
        settings.setForceToEndQue(newSetting);
        event.reply(buildMessage(newSetting)).queue();
    }

    private String buildMessage(boolean enabled) {
        if (enabled) {
            return "再生待ちへの追加方法を変更しました。\n設定:通常追加モード\nリクエストした曲を再生待ちの最後に追加します。";
        }
        return "再生待ちへの追加方法を変更しました。\n設定:フェア追加モード\nリクエストした曲をフェアな順序で再生待ちに追加します。";
    }
}
