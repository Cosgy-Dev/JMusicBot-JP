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

package dev.cosgy.jmusicbot.util;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * 楽曲キューのページをEmbedとして生成するユーティリティクラス。
 */
public class QueuePaginatorManager {

    /**
     * 指定されたページに対応するEmbedを生成します。
     *
     * @param ah          AudioHandler（現在の再生情報を含む）
     * @param queue       再生待ちのキューリスト
     * @param successIcon 成功時に表示するアイコン（例：✅）
     * @param repeatMode  リピートモード（ALL/SINGLE/OFF）
     * @param page        現在のページ番号（1から開始）
     * @param totalPages  全ページ数
     * @return MessageEmbed（Discordに表示可能な埋め込みメッセージ）
     */
    public static MessageEmbed createQueuePageEmbed(AudioHandler ah,
                                                    List<QueuedTrack> queue,
                                                    String successIcon,
                                                    RepeatMode repeatMode,
                                                    int page,
                                                    int totalPages) {

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, queue.size());

        StringBuilder description = new StringBuilder();
        long totalDuration = 0;
        for (int i = start; i < end; i++) {
            QueuedTrack track = queue.get(i);
            description.append("`").append(i + 1).append(".` ").append(track.toString()).append("\n");
            totalDuration += track.getTrack().getDuration();
        }

        String repeatEmoji;
        if (repeatMode == RepeatMode.ALL) {
            repeatEmoji = " 🔁";
        } else if (repeatMode == RepeatMode.SINGLE) {
            repeatEmoji = " 🔂";
        } else {
            repeatEmoji = "";
        }

        EmbedBuilder eb = getEmbedBuilder(ah, description);

        eb.setFooter("ページ " + page + "/" + totalPages + repeatEmoji
                + " | 総時間: " + FormatUtil.formatTime(totalDuration), null);

        return eb.build();
    }

    private static @NotNull EmbedBuilder getEmbedBuilder(AudioHandler ah, StringBuilder description) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.CYAN);
        eb.setDescription(description.toString());

        // 現在再生中の曲
        if (ah.getPlayer().getPlayingTrack() != null) {
            eb.setTitle(
                    (ah.getPlayer().isPaused() ? "⏸" : "▶️") + " 再生中: " +
                            (ah.getPlayer().getPlayingTrack().getInfo().uri.contains("gensokyoradio.net")
                                    ? "幻想郷ラジオ"
                                    : ah.getPlayer().getPlayingTrack().getInfo().title)
            );
        } else {
            eb.setTitle("再生中の曲はありません。");
        }
        return eb;
    }
}

