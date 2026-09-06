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

import java.util.function.Consumer;

/**
 * ストリームのURLを入力しなくても幻想郷ラジオを再生できるようにするコマンド。
 */
public class GensokyoCmd extends MusicCommand {
    /**
     * 再生に使用する幻想郷ラジオのストリーム（4番＝ロスレス配信・Ogg FLAC）。
     * <p>
     * 短時間に何本も接続すると配信側が 401 を返すことがあるが、
     * 時間をおけば復帰する一時的なものなので番号は変更しない。
     */
    public static final String STREAM_URL = "https://stream.gensokyoradio.net/4/";

    public GensokyoCmd(Bot bot) {
        super(bot);
        this.name = "gensokyo";
        this.help = "幻想郷ラジオを再生します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(CommandEvent event) {
        request(event.getGuild(), event.getAuthor(), event::reply);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        event.deferReply().queue(hook -> request(event.getGuild(), event.getUser(),
                message -> hook.editOriginal(message).queue()));
    }

    /**
     * 幻想郷ラジオのストリームを読み込んで再生待ちに追加する。
     */
    private void request(Guild guild, User user, Consumer<String> reply) {
        // 表示に使う曲情報の取得を先に開始させておく
        GensokyoInfoAgent.markActive();
        String success = bot.getConfig().getSuccess();
        String error = bot.getConfig().getError();

        bot.getPlayerManager().loadItemOrdered(guild, STREAM_URL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                int pos = handler.addTrack(new QueuedTrack(track, user)) + 1;
                reply.accept(FormatUtil.filter(success + " **" + GensokyoInfoAgent.DISPLAY_NAME + "** "
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
