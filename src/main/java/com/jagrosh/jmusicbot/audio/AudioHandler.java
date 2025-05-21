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
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler {
    private final FairQueue<QueuedTrack> queue = new FairQueue<>();
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final String stringGuildId;
    private AudioFrame lastFrame;

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.stringGuildId = guild.getId();
    }

    public int addTrackToFront(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            queue.addAt(0, qtrack);
            return 0;
        }
    }

    public int addTrack(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
            return queue.add(qtrack, toEnt);
        }
    }

    public void addTrackIfRepeat(AudioTrack track) {
        // リピートモードの場合は、キューの最後にトラックを追加します
        RepeatMode mode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
        if (mode != RepeatMode.OFF) {
            queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)), toEnt);
        }
    }

    public FairQueue<QueuedTrack> getQueue() {
        return queue;
    }

    public void stopAndClear() {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        //current = null;

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
    }

    public boolean isMusicPlaying(JDA jda) {
        return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && audioPlayer.getPlayingTrack() != null;
    }

    public Set<String> getVotes() {
        return votes;
    }

    public AudioPlayer getPlayer() {
        return audioPlayer;
    }

    public RequestMetadata getRequestMetadata() {
        if (audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }

    public boolean playFromDefault() {
        if (!defaultQueue.isEmpty()) {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if (settings == null || settings.getDefaultPlaylist() == null)
            return false;

        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(stringGuildId, settings.getDefaultPlaylist());
        if (pl == null || pl.getItems().isEmpty())
            return false;
        pl.loadTracks(manager, (at) -> {
            if (audioPlayer.getPlayingTrack() == null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> {
            if (pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }

    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();

        // もしも楽曲再生が通常通り終了し、リピートモードが有効(!OFF)ならばキューに再追加する
        if (endReason == AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF) {
            // in RepeatMode.ALL
            if (repeatMode == RepeatMode.ALL) {
                queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));

                // in RepeatMode.SINGLE
            } else if (repeatMode == RepeatMode.SINGLE) {
                queue.addAt(0, new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));
            }
        }

        if (queue.isEmpty()) {
            if (!playFromDefault()) {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
                if (!manager.getBot().getConfig().getStay()) manager.getBot().closeAudioConnection(guildId);

                player.setPaused(false);

                Guild guild = guild(manager.getBot().getJDA());
                Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
            }
        } else {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        votes.clear();
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track, this);

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.PLAYING);
    }


    // Formatting
    public MessageCreateData getNowPlaying(JDA jda) throws Exception {
        if (isMusicPlaying(jda)) {
            Guild guild = guild(jda);
            AudioTrack track = audioPlayer.getPlayingTrack();
            MessageCreateBuilder mb = new MessageCreateBuilder();
            mb.addContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **" + guild.getSelfMember().getVoiceState().getChannel().getAsMention() + "**で、再生中です..."));
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(guild.getSelfMember().getColor());
            RequestMetadata rm = getRequestMetadata();
            if (!track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                if (track instanceof YoutubeAudioTrack && manager.getBot().getConfig().useNPImages()) {
                    eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/maxresdefault.jpg");
                }

                if (track.getInfo().author != null && !track.getInfo().author.isEmpty())
                    eb.setFooter("出典: " + track.getInfo().author, null);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                eb.setDescription((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                        + " " + FormatUtil.progressBar(progress)
                        + " `[" + FormatUtil.formatTime(track.getPosition()) + "/" + FormatUtil.formatTime(track.getDuration()) + "]` "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume()));

            } else {
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                if (track instanceof YoutubeAudioTrack && manager.getBot().getConfig().useNPImages()) {
                    eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/maxresdefault.jpg");
                }

                if (track.getInfo().author != null && !track.getInfo().author.isEmpty())
                    eb.setFooter("出典: " + track.getInfo().author, null);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                eb.setDescription((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                        + " " + FormatUtil.progressBar(progress)
                        + " `[" + FormatUtil.formatTime(track.getPosition()) + "/" + FormatUtil.formatTime(track.getDuration()) + "]` "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume()));
                /*String titleUrl = GensokyoInfoAgent.getInfo().getMisc().getCirclelink().equals("") ?
                        "https://gensokyoradio.net/" :
                        GensokyoInfoAgent.getInfo().getMisc().getCirclelink();

                String albumArt = "";
                if(manager.getBot().getConfig().useNPImages()){
                    albumArt = GensokyoInfoAgent.getInfo().getMisc().getAlbumart().equals("") ?
                        "https://cdn.discordapp.com/attachments/240116420946427905/373019550725177344/gr-logo-placeholder.png" :
                        "https://gensokyoradio.net/images/albums/original/" + GensokyoInfoAgent.getInfo().getMisc().getAlbumart();
                }
                eb.setTitle(GensokyoInfoAgent.getInfo().getSonginfo().getTitle(), titleUrl)
                        .addField("アルバム", GensokyoInfoAgent.getInfo().getSonginfo().getAlbum(), true)
                        .addField("アーティスト", GensokyoInfoAgent.getInfo().getSonginfo().getArtist(), true)
                        .addField("サークル", GensokyoInfoAgent.getInfo().getSonginfo().getCircle(), true);

                if (Integer.parseInt(GensokyoInfoAgent.getInfo().getSonginfo().getYear()) != 0) {
                    eb.addField("リリース", GensokyoInfoAgent.getInfo().getSonginfo().getYear(), true);
                }

                double progress = (double) GensokyoInfoAgent.getInfo().getSongtimes().getPlayed() / GensokyoInfoAgent.getInfo().getSongtimes().getDuration();
                eb.setDescription((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                        + " " + FormatUtil.progressBar(progress)
                        + " `[" + FormatUtil.formatTime(GensokyoInfoAgent.getInfo().getSongtimes().getPlayed()) + "/" + FormatUtil.formatTime(GensokyoInfoAgent.getInfo().getSongtimes().getDuration()) + "]` "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume()));

                eb.addField("リスナー", Integer.toString(GensokyoInfoAgent.getInfo().getServerinfo().getListeners()), true)
                        .setColor(new Color(66, 16, 80))
                        .setFooter("コンテンツはgensokyoradio.netによって提供されています。\n" +
                                "GRロゴはGensokyo Radioの商標です。" +
                                "\nGensokyo Radio is © LunarSpotlight.", null);
                if(manager.getBot().getConfig().useNPImages()){
                    eb.setImage(albumArt);
                }*/
            }

            return mb.addEmbeds(eb.build()).build();
        } else return null;
    }

    public MessageCreateData getNoMusicPlaying(JDA jda) {
        Guild guild = guild(jda);
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **音楽を再生していません。**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("音楽を再生していません。")
                        .setDescription(JMusicBot.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(audioPlayer.getVolume()))
                        .setColor(guild.getSelfMember().getColor())
                        .build())
                .build();
    }

    public String getTopicFormat(JDA jda) {
        if (isMusicPlaying(jda)) {
            long userid = getRequestMetadata().getOwner();
            AudioTrack track = audioPlayer.getPlayingTrack();

            // 幻想郷ラジオを再生しているか確認
            if (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                return "**幻想郷ラジオ** [" + (userid == 0 ? "自動再生" : "<@" + userid + ">") + "]"
                        + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                        + "[LIVE] "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume());
            }

            String title = track.getInfo().title;
            if (title == null || title.equals("不明なタイトル"))
                title = track.getInfo().uri;
            return "**" + title + "** [" + (userid == 0 ? "自動再生" : "<@" + userid + ">") + "]"
                    + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                    + "[" + FormatUtil.formatTime(track.getDuration()) + "] "
                    + FormatUtil.volumeIcon(audioPlayer.getVolume());
        } else return "音楽を再生していません" + JMusicBot.STOP_EMOJI + " " + FormatUtil.volumeIcon(audioPlayer.getVolume());
    }

    // Audio Send Handler methods
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }


    // Private methods
    private Guild guild(JDA jda) {
        return jda.getGuildById(guildId);
    }
}
