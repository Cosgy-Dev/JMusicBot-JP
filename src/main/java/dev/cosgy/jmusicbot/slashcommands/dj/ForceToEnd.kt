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

package dev.cosgy.jmusicbot.slashcommands.dj

import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.SlashCommandEvent
import com.jagrosh.jmusicbot.Bot
import dev.cosgy.jmusicbot.slashcommands.DJCommand
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class ForceToEnd(bot: Bot) : DJCommand(bot) {
    init {
        this.name = "forcetoend"
        this.help = "楽曲追加設定をフェア追加モードか通常追加モードを使用するかを切り替えます。設定を`TRUE`にすると通常追加モードになります。"
        this.aliases = bot.config.getAliases(this.name)
        this.options = listOf(OptionData(OptionType.BOOLEAN, "value", "通常追加モードを使用するか", true));
    }

    override fun doCommand(event: CommandEvent) {

        val nowSetting = bot.settingsManager?.getSettings(event.guild)?.isForceToEndQue
        var newSetting = false

        if (event.args.isEmpty()) {
            newSetting = !nowSetting!!
        } else if (event.args.equals("true", ignoreCase = true) || event.args.equals("on", ignoreCase = true) || event.args.equals("有効", ignoreCase = true)) {
            newSetting = true
        } else if (event.args.equals("false", ignoreCase = true) || event.args.equals("off", ignoreCase = true) || event.args.equals("無効", ignoreCase = true)) {
            newSetting = false
        }

        bot.settingsManager.getSettings(event.guild)?.isForceToEndQue = newSetting

        var msg = "再生待ちへの追加方法を変更しました。\n設定:"
        if (newSetting == true) {
            msg += "通常追加モード\nリクエストした曲を再生待ちの最後に追加します。"
        } else if (newSetting == false) {
            msg += "フェア追加モード\nリクエストした曲をフェアな順序で再生待ちに追加します。"
        }

        event.replySuccess(msg)
    }


    override fun doCommand(event: SlashCommandEvent) {
        val nowSetting = bot.settingsManager?.getSettings(event.guild)?.isForceToEndQue
        var newSetting = false

        newSetting = event.getOption("value")?.asBoolean!!

        bot.settingsManager.getSettings(event.guild)?.isForceToEndQue = newSetting

        var msg = "再生待ちへの追加方法を変更しました。\n設定:"
        if (newSetting == true) {
            msg += "通常追加モード\nリクエストした曲を再生待ちの最後に追加します。"
        } else if (newSetting == false) {
            msg += "フェア追加モード\nリクエストした曲をフェアな順序で再生待ちに追加します。"
        }

        event.reply(msg).queue()
    }
}