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
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SkipCmd extends MusicCommand {
    public SkipCmd(Bot bot) {
        super(bot);
        this.name = "skip";
        this.help = "現在流れている曲のスキップをリクエスト";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        RequestMetadata rm = handler.getRequestMetadata();
        if (event.getAuthor().getIdLong() == rm.getOwner()) {
            event.reply(event.getClient().getSuccess() + "**" + (handler.getPlayer().getPlayingTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : handler.getPlayer().getPlayingTrack().getInfo().title) + "** をスキップしました。");
            handler.getPlayer().stopTrack();
        } else {
            // ボイチャにいる人数 (Bot, スピーカーミュートは含まず)
            int listeners = (int) event.getSelfMember().getVoiceState().getChannel().getMembers().stream()
                    .filter(m -> !m.getUser().isBot() && !m.getVoiceState().isDeafened() && m.getUser().getIdLong() != handler.getRequestMetadata().getOwner()).count();

            // 送信するメッセージ
            String msg;

            // 現在の投票を取得して、メッセージの送信者が含まれているかどうか
            if (handler.getVotes().contains(event.getAuthor().getId())) {
                msg = event.getClient().getWarning() + " 再生中の曲はスキップリクエスト済みです。 `[";
            } else {
                msg = event.getClient().getSuccess() + "現在の曲をスキップリクエストしました。`[";
                handler.getVotes().add(event.getAuthor().getId());
            }

            // ボイチャにいる人の中から、スキップすることに投票している人数を取得する
            int skippers = (int) event.getSelfMember().getVoiceState().getChannel().getMembers().stream()
                    .filter(m -> handler.getVotes().contains(m.getUser().getId())).count();

            int required = (int) Math.ceil(listeners * bot.getSettingsManager().getSettings(event.getGuild()).getSkipRatio());
            msg += skippers + " 票, " + required + "/" + listeners + " 必要]`";

            // 必要投票数が、ボイチャにいる人数と相違する場合
            if (required != listeners) {
                // メッセージを付加する
                msg += "スキップリクエスト数は、" + skippers + "です。スキップするには、" + required + "/" + listeners + "必要です。]`";
            } else {
                msg = "";
            }

            // 現在の投票者数が、必要投票数に達しているかどうか
            if (skippers >= required) {
                msg += "\n" + event.getClient().getSuccess() + "**" + (handler.getPlayer().getPlayingTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : handler.getPlayer().getPlayingTrack().getInfo().title)
                        + "**をスキップしました。 " + (rm.getOwner() == 0L ? "(自動再生)" : "(**" + rm.user.username + "**がリクエスト)");
                handler.getPlayer().stopTrack();
            }
            event.reply(msg);
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        RequestMetadata rm = handler.getRequestMetadata();
        if (event.getUser().getIdLong() == rm.getOwner()) {
            event.reply(event.getClient().getSuccess() + "**" + (handler.getPlayer().getPlayingTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : handler.getPlayer().getPlayingTrack().getInfo().title) + "** をスキップしました。").queue();
            handler.getPlayer().stopTrack();
        } else {
            // ボイチャにいる人数 (Bot, スピーカーミュートは含まず)
            int listeners = (int) event.getGuild().getSelfMember().getVoiceState().getChannel().getMembers().stream()
                    .filter(m -> !m.getUser().isBot() && !m.getVoiceState().isDeafened() && m.getUser().getIdLong() != handler.getRequestMetadata().getOwner()).count();

            // 送信するメッセージ
            String msg;

            // 現在の投票を取得して、メッセージの送信者が含まれているかどうか
            if (handler.getVotes().contains(event.getUser().getId())) {
                msg = event.getClient().getWarning() + " 再生中の曲はスキップリクエスト済みです。 `[";
            } else {
                msg = event.getClient().getSuccess() + "現在の曲をスキップリクエストしました。`[";
                handler.getVotes().add(event.getUser().getId());
            }

            // ボイチャにいる人の中から、スキップすることに投票している人数を取得する
            int skippers = (int) event.getGuild().getSelfMember().getVoiceState().getChannel().getMembers().stream()
                    .filter(m -> handler.getVotes().contains(m.getUser().getId())).count();

            // 必要な投票数 (ボイチャにいる人数 × 0.55)
            int required = (int) Math.ceil(listeners * .55);

            // 必要投票数が、ボイチャにいる人数と相違する場合
            if (required != listeners) {
                // メッセージを付加する
                msg += "スキップリクエスト数は、" + skippers + "です。スキップするには、" + required + "/" + listeners + "必要です。]`";
            } else {
                msg = "";
            }

            // 現在の投票者数が、必要投票数に達しているかどうか
            if (skippers >= required) {
                msg += "\n" + event.getClient().getSuccess() + "**" + (handler.getPlayer().getPlayingTrack().getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "幻想郷ラジオ" : handler.getPlayer().getPlayingTrack().getInfo().title)
                        + "**をスキップしました。 " + (rm.getOwner() == 0L ? "(自動再生)" : "(**" + rm.user.username + "**がリクエスト)");
                handler.getPlayer().stopTrack();
            }
            event.reply(msg).queue();
        }
    }
}
