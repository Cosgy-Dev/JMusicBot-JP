/*
 *  Copyright 2025 Cosgy Dev (info@cosgy.dev).
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

package dev.cosgy.jmusicbot.slashcommands.listeners.buttons;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.util.QueuePaginatorManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class QueueButtonListener extends ListenerAdapter {
    private final Bot bot;

    public QueueButtonListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId(); // e.g., queue:next:123456
        if (!id.startsWith("queue:")) return;

        String[] parts = id.split(":");
        if (parts.length < 3 || !event.getUser().getId().equals(parts[2])) {
            event.reply("このボタンはあなた専用です。").setEphemeral(true).queue();
            return;
        }

        String action = parts[1];

        MessageEmbed embed = event.getMessage().getEmbeds().get(0);
        String footer = embed.getFooter() != null ? embed.getFooter().getText() : "";
        int current = 1, max = 1;
        try {
            String[] nums = footer.replace("ページ", "").trim().split("/");
            current = Integer.parseInt(nums[0].trim());
            max = Integer.parseInt(nums[1].trim().split(" ")[0]); // "5 🔁" のような場合対応
        } catch (Exception ignored) {
        }

        int newPage;
        if (action.equals("prev")) {
            newPage = (current <= 1) ? max : current - 1;
        } else if (action.equals("next")) {
            newPage = (current >= max) ? 1 : current + 1;
        } else {
            newPage = -1;
        }

        if (action.equals("close")) {
            event.getMessage().delete().queue();
            return;
        }
        if (newPage == -1) {
            event.deferEdit().queue();
            return;
        }

        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> queue = ah.getQueue().getList();
        RepeatMode repeatMode = bot.getSettingsManager().getSettings(event.getGuild()).getRepeatMode();
        MessageEmbed newEmbed = QueuePaginatorManager.createQueuePageEmbed(ah, queue,
                bot.getConfig().getSuccess(), repeatMode, newPage, max);

        event.editMessageEmbeds(newEmbed).queue();
    }
}