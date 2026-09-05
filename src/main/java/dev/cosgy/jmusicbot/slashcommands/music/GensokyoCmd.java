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
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.agent.GensokyoInfoAgent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * ストリームのURLを入力しなくても幻想郷ラジオを再生できるようにするコマンド。
 */
public class GensokyoCmd extends MusicCommand {
    private static final String QUALITY_OPTION = "quality";

    /**
     * 幻想郷ラジオが配信しているストリーム。
     */
    private enum Quality {
        STANDARD("標準", "128kbps MP3", "https://stream.gensokyoradio.net/1", "standard", "normal", "128", "標準"),
        HIGH("高音質", "256kbps MP3", "https://stream.gensokyoradio.net/3", "high", "hq", "256", "高音質"),
        LOW("低音質", "64kbps Ogg", "https://stream.gensokyoradio.net/2", "low", "lq", "64", "低音質");

        private final String label;
        private final String description;
        private final String url;
        private final String[] keywords;

        Quality(String label, String description, String url, String... keywords) {
            this.label = label;
            this.description = description;
            this.url = url;
            this.keywords = keywords;
        }

        /** 引数を音質に変換する。空文字なら既定の音質、該当が無ければ {@code null}。 */
        static Quality parse(String input) {
            String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) return STANDARD;
            for (Quality quality : values()) {
                for (String keyword : quality.keywords) {
                    if (keyword.equals(normalized)) return quality;
                }
            }
            return null;
        }

        String choiceName() {
            return label + " (" + description + ")";
        }
    }

    public GensokyoCmd(Bot bot) {
        super(bot);
        this.name = "gensokyo";
        this.arguments = "[音質]";
        this.help = "幻想郷ラジオを再生します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;

        OptionData quality = new OptionData(OptionType.STRING, QUALITY_OPTION, "ストリームの音質 (既定: 標準)", false);
        for (Quality value : Quality.values()) {
            quality.addChoice(value.choiceName(), value.name());
        }
        List<OptionData> options = new ArrayList<>();
        options.add(quality);
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        Quality quality = Quality.parse(event.getArgs());
        if (quality == null) {
            event.replyError("音質の指定が正しくありません。使用できる値: " + keywordHelp());
            return;
        }
        request(event.getGuild(), event.getAuthor(), quality, event::reply);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        OptionMapping option = event.getOption(QUALITY_OPTION);
        Quality quality = option == null ? Quality.STANDARD : Quality.valueOf(option.getAsString());
        event.deferReply().queue(hook -> request(event.getGuild(), event.getUser(), quality,
                message -> hook.editOriginal(message).queue()));
    }

    private static String keywordHelp() {
        StringBuilder builder = new StringBuilder();
        for (Quality value : Quality.values()) {
            if (!builder.isEmpty()) builder.append(" / ");
            builder.append("`").append(value.keywords[0]).append("`");
        }
        return builder.toString();
    }

    /**
     * 幻想郷ラジオのストリームを読み込んで再生待ちに追加する。
     */
    private void request(Guild guild, User user, Quality quality, Consumer<String> reply) {
        // 表示に使う曲情報の取得を先に開始させておく
        GensokyoInfoAgent.markActive();
        String success = bot.getConfig().getSuccess();
        String error = bot.getConfig().getError();

        bot.getPlayerManager().loadItemOrdered(guild, quality.url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                // ストリームなので再生時間の上限は適用しない
                int pos = handler.addTrack(new QueuedTrack(track, user)) + 1;
                reply.accept(FormatUtil.filter(success + " **" + GensokyoInfoAgent.DISPLAY_NAME + "** ("
                        + quality.description + ") "
                        + (pos == 0 ? "の再生を開始しました。" : "を再生待ちの" + pos + "番目に追加しました。")));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    noMatches();
                    return;
                }
                trackLoaded(playlist.getTracks().get(0));
            }

            @Override
            public void noMatches() {
                reply.accept(error + " 幻想郷ラジオのストリームを読み込めませんでした。");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                reply.accept(error + " 幻想郷ラジオに接続できませんでした: " + exception.getMessage());
            }
        });
    }
}
