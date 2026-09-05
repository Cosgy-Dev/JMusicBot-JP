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
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.cosgy.agent.GensokyoInfoAgent;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author John Grosh
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler {
    /** 幻想郷ラジオ再生中の Embed に使う色。 */
    private static final Color GENSOKYO_COLOR = new Color(66, 16, 80);

    private final FairQueue<QueuedTrack> queue = new FairQueue<>();
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final String stringGuildId;
    private AudioFrame lastFrame;

    // ==== 再生失敗時の yt-dlp フォールバック状態 ====

    /** フォールバックの契機 */
    public enum FallbackOrigin {
        /** 再生中に例外が発生し、トラックは（間もなく）終了する */
        EXCEPTION,
        /** トラックがスタックしているが、まだ再生中扱いのまま */
        STUCK
    }

    /** {@link #beginFallback} の結果 */
    public enum FallbackBegin {
        /** 新たにフォールバックを開始した */
        STARTED,
        /** 同じトラックのフォールバックが既に進行中 */
        ALREADY_PENDING,
        /** 別トラックのフォールバックが進行中のため開始できない */
        BUSY
    }

    private enum FallbackPhase {
        /** ダウンロード中。終了イベントはまだ処理していない */
        PENDING,
        /** 対象トラックの終了イベントを処理済み */
        ENDED,
        /** フォールバック側が結果を確定した */
        RESOLVED
    }

    /**
     * 進行中のフォールバック 1 件分の状態。
     * <p>
     * 終了イベント（lavaplayer の送信スレッド）とダウンロード完了（ワーカースレッド）は
     * どちらが先に来るか分からないため、{@link #phase} の CAS でどちらか一方だけが
     * 「キューを進める責任」を持つようにしている。
     */
    private static final class FallbackState {
        final AudioTrack track;
        volatile FallbackOrigin origin;
        final AtomicReference<FallbackPhase> phase = new AtomicReference<>(FallbackPhase.PENDING);

        FallbackState(AudioTrack track, FallbackOrigin origin) {
            this.track = track;
            this.origin = origin;
        }
    }

    /** 進行中のフォールバック（ギルドごとに同時に 1 つ） */
    private final AtomicReference<FallbackState> fallback = new AtomicReference<>();

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.stringGuildId = guild.getId();
    }

    /**
     * 再生に失敗した（またはスタックした）トラックのフォールバックを開始する。
     * <p>
     * {@link FallbackOrigin#EXCEPTION} の場合、以降に来る対象トラックの終了イベントでは
     * キューを進めず、退出もしない。結果は {@link #completeFallback} か {@link #failFallback} で
     * 必ず確定させること。
     */
    public FallbackBegin beginFallback(AudioTrack track, FallbackOrigin origin) {
        FallbackState fresh = new FallbackState(track, origin);
        while (true) {
            FallbackState current = fallback.get();
            if (current != null) {
                if (current.track == track) {
                    // スタック中のトラックが例外で終了した → 終了イベントを抑制する側へ昇格
                    if (origin == FallbackOrigin.EXCEPTION) current.origin = FallbackOrigin.EXCEPTION;
                    return FallbackBegin.ALREADY_PENDING;
                }
                return FallbackBegin.BUSY;
            }
            if (fallback.compareAndSet(null, fresh)) break;
        }
        // lavaplayer は例外イベントより先に終了イベントを配送することがある。
        // その場合は既にキューが進んでいるので、抑制済み扱いにしておく。
        if (origin == FallbackOrigin.EXCEPTION && audioPlayer.getPlayingTrack() != track) {
            fresh.phase.compareAndSet(FallbackPhase.PENDING, FallbackPhase.ENDED);
        }
        return FallbackBegin.STARTED;
    }

    /**
     * フォールバックで取得した差し替えトラックを再生する。
     *
     * @return 差し替え再生またはキュー投入した場合 true。停止・スキップ等で不要になり破棄した場合 false
     */
    public boolean completeFallback(AudioTrack failedTrack, AudioTrack replacement) {
        FallbackState state = fallback.get();
        if (state == null || state.track != failedTrack) return false; // stop / skip 等で破棄済み
        fallback.compareAndSet(state, null);

        boolean ended = !state.phase.compareAndSet(FallbackPhase.PENDING, FallbackPhase.RESOLVED);
        AudioTrack current = audioPlayer.getPlayingTrack();

        if (current == failedTrack) {
            // まだ再生中扱い（スタック中、またはバッファ残りを再生中）→ その場で差し替え（REPLACED）
            audioPlayer.startTrack(replacement, false);
            return true;
        }
        if (state.origin == FallbackOrigin.STUCK) {
            // スタックしていたトラックは自力で終わった（または操作された）ので差し替え不要
            return false;
        }
        if (current != null) {
            // 別のトラックが再生中（利用者の操作など）→ 次に再生されるようキュー先頭へ
            queue.addAt(0, new QueuedTrack(replacement, extractRequestMetadata(replacement)));
            return true;
        }
        if (ended) {
            // 終了イベントを抑制して待たせていた → ここから再開する
            Guild guild = guild(manager.getBot().getJDA());
            if (guild == null || !guild.getSelfMember().getVoiceState().inAudioChannel()) return false;
            audioPlayer.playTrack(replacement);
            return true;
        }
        // 終了イベントを処理中 → 通常の進行がキュー先頭から拾う
        queue.addAt(0, new QueuedTrack(replacement, extractRequestMetadata(replacement)));
        return true;
    }

    /**
     * フォールバックに失敗したトラックを諦め、利用者へ通知して次の曲へ進める。
     */
    public void failFallback(AudioTrack failedTrack, String reason) {
        FallbackState state = fallback.get();
        if (state == null || state.track != failedTrack) return; // stop / skip 等で破棄済み
        fallback.compareAndSet(state, null);

        boolean ended = !state.phase.compareAndSet(FallbackPhase.PENDING, FallbackPhase.RESOLVED);
        notifyTrackFailed(failedTrack, reason);

        AudioTrack current = audioPlayer.getPlayingTrack();
        if (current == failedTrack) {
            // スタック中 / バッファ残り再生中 → 停止し、通常の終了処理（STOPPED）に次を任せる
            audioPlayer.stopTrack();
        } else if (ended && current == null) {
            // 終了イベントを抑制していた → 明示的にキューを進める
            playNextOrStop();
        }
        // それ以外: 終了イベントが通常どおりキューを進める（または別トラックが再生中）
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
        RepeatMode mode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
        if (mode != RepeatMode.OFF) {
            AudioTrack cloned = track.makeClone();
            cloned.setUserData(track.getUserData());
            queue.add(new QueuedTrack(cloned, extractRequestMetadata(track)), toEnt);
        }
    }

    private static RequestMetadata extractRequestMetadata(AudioTrack track) {
        if (track == null) return RequestMetadata.EMPTY;
        Object ud = track.getUserData();
        if (ud instanceof RequestMetadata) return (RequestMetadata) ud;
        if (ud instanceof PlayerManager.TrackContext) {
            PlayerManager.TrackContext tc = (PlayerManager.TrackContext) ud;
            if (tc.userData instanceof RequestMetadata) {
                return (RequestMetadata) tc.userData;
            }
        }
        return RequestMetadata.EMPTY; // NPE防止用のダミー(EMPTY)
    }

    public FairQueue<QueuedTrack> getQueue() {
        return queue;
    }

    public void stopAndClear() {
        queue.clear();
        defaultQueue.clear();
        fallback.set(null); // 進行中のフォールバック結果は破棄する
        audioPlayer.stopTrack();

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
        Object ud = audioPlayer.getPlayingTrack().getUserData();
        if (ud instanceof RequestMetadata) return (RequestMetadata) ud;
        if (ud instanceof PlayerManager.TrackContext) {
            PlayerManager.TrackContext tc = (PlayerManager.TrackContext) ud;
            if (tc.userData instanceof RequestMetadata) {
                return (RequestMetadata) tc.userData;
            }
        }
        return RequestMetadata.EMPTY;
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
        // ★ 置き換え（REPLACED）時は退出ロジックをスキップ
        if (endReason == AudioTrackEndReason.REPLACED) {
            return;
        }

        FallbackState state = fallback.get();
        if (state != null && state.track == track) {
            if (!endReason.mayStartNext) {
                // 利用者の skip / stop やプレイヤー破棄。フォールバック結果は破棄し、通常どおり進める
                fallback.compareAndSet(state, null);
            } else if (state.phase.compareAndSet(FallbackPhase.PENDING, FallbackPhase.ENDED)) {
                if (state.origin == FallbackOrigin.EXCEPTION) {
                    // 差し替え再生の準備中。キューを進めず、退出もしない
                    return;
                }
                // スタックしていたトラックが自力で再生を終えた → フォールバックは不要
                fallback.compareAndSet(state, null);
            }
        }

        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();

        // 完走時のリピート処理
        if (endReason == AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF) {
            // 元トラックの userData をそのままクローンへ引き継ぐ
            AudioTrack cloned = track.makeClone();
            cloned.setUserData(track.getUserData());
            RequestMetadata rm = extractRequestMetadata(track);

            if (repeatMode == RepeatMode.ALL) {
                queue.add(new QueuedTrack(cloned, rm), true);
            } else if (repeatMode == RepeatMode.SINGLE) {
                queue.addAt(0, new QueuedTrack(cloned, rm));
            }
        }

        playNextOrStop();
    }

    /**
     * キューの次の曲を再生する。キューが空の場合はデフォルトプレイリストへ、
     * それも無ければ再生を停止する。
     * <p>
     * 通常は {@link #onTrackEnd} から呼ばれるが、フォールバックに失敗したトラックを
     * スキップする際（{@link #failFallback}）にも使用する。
     */
    public void playNextOrStop() {
        if (queue.isEmpty()) {
            if (!playFromDefault()) {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
                if (!manager.getBot().getConfig().getStay()) manager.getBot().closeAudioConnection(guildId);

                audioPlayer.setPaused(false);

                Guild guild = guild(manager.getBot().getJDA());
                Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
            }
        } else {
            QueuedTrack qt = queue.pull();
            audioPlayer.playTrack(qt.getTrack());
        }
    }

    /**
     * 年齢制限や地域制限などで再生できなかったトラックを、設定されたテキストチャンネルへ通知する。
     * 通知先が無い場合や権限が無い場合は何もしない。
     */
    public void notifyTrackFailed(AudioTrack track, String reason) {
        Guild guild = guild(manager.getBot().getJDA());
        if (guild == null) return;

        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        TextChannel tchan = settings == null ? null : settings.getTextChannel(guild);
        if (tchan == null || !guild.getSelfMember().hasPermission(tchan, Permission.MESSAGE_SEND)) return;

        String title = (track == null || track.getInfo().title == null) ? "不明なトラック" : track.getInfo().title;
        StringBuilder sb = new StringBuilder(manager.getBot().getConfig().getWarning())
                .append(" **").append(title).append("** を再生できなかったため、スキップしました。");
        if (reason != null && !reason.isBlank()) {
            sb.append("\n理由: ").append(reason);
        }

        try {
            tchan.sendMessage(FormatUtil.filter(sb.toString())).queue(null, t -> {});
        } catch (Exception ignored) {
            // 通知に失敗してもスキップ自体は続行する
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        votes.clear();
        // 幻想郷ラジオの場合は、表示に使う曲情報の取得を先に開始させておく
        if (GensokyoInfoAgent.isGensokyoRadio(track)) {
            GensokyoInfoAgent.markActive();
        }
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

            if (!GensokyoInfoAgent.isGensokyoRadio(track)) {
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }

                // ★ 置き換え時は元のメタ（タイトル/URI/作者）を優先
                AudioTrackInfo info = track.getInfo();
                String title = info.title;
                String uri   = info.uri;
                String author = info.author;

                Object udAll = track.getUserData();
                if (udAll instanceof PlayerManager.TrackContext) {
                    PlayerManager.TrackContext tc = (PlayerManager.TrackContext) udAll;
                    if (tc.originalInfo != null) {
                        if (tc.originalInfo.title != null) title = tc.originalInfo.title;
                        if (tc.originalInfo.uri != null) uri = tc.originalInfo.uri;
                        if (tc.originalInfo.author != null) author = tc.originalInfo.author;
                    }
                }

                try { eb.setTitle(title, uri); } catch (Exception e) { eb.setTitle(title); }

                // サムネ
                if (manager.getBot().getConfig().useNPImages()) {
                    if (track instanceof YoutubeAudioTrack) {
                        eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/maxresdefault.jpg");
                    } else {
                        String ytId = extractYoutubeId(uri);
                        if (ytId != null) {
                            eb.setThumbnail("https://img.youtube.com/vi/" + ytId + "/maxresdefault.jpg");
                        }
                    }
                }

                if (author != null && !author.isEmpty())
                    eb.setFooter("出典: " + author, null);

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
                buildGensokyoRadioEmbed(eb);
            }

            return mb.addEmbeds(eb.build()).build();
        } else return null;
    }

    /**
     * 幻想郷ラジオ再生中の Embed を組み立てる。曲情報が未取得の場合でも
     * 最低限の表示になるようにフォールバックする。
     */
    private void buildGensokyoRadioEmbed(EmbedBuilder eb) {
        GensokyoInfoAgent.Snapshot info = GensokyoInfoAgent.getInfo();
        eb.setColor(GENSOKYO_COLOR)
                .setFooter("コンテンツはgensokyoradio.netによって提供されています。"
                        + "\nGRロゴはGensokyo Radioの商標です。"
                        + "\nGensokyo Radio is © LunarSpotlight.", null);

        if (info == null || info.title() == null) {
            eb.setTitle(GensokyoInfoAgent.DISPLAY_NAME, GensokyoInfoAgent.SITE_URL)
                    .setDescription((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                            + " [LIVE] " + FormatUtil.volumeIcon(audioPlayer.getVolume()));
            return;
        }

        try {
            eb.setTitle(info.title(), GensokyoInfoAgent.SITE_URL);
        } catch (IllegalArgumentException e) {
            eb.setTitle(info.title());
        }
        if (info.album() != null) eb.addField("アルバム", info.album(), true);
        if (info.artist() != null) eb.addField("アーティスト", info.artist(), true);
        if (info.circle() != null) eb.addField("サークル", info.circle(), true);
        if (info.year() != null) eb.addField("リリース", info.year(), true);

        eb.setDescription((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                + " " + FormatUtil.progressBar(info.progress())
                + " `[" + FormatUtil.formatTime(info.playedSeconds() * 1000L)
                + "/" + FormatUtil.formatTime(info.durationSeconds() * 1000L) + "]` "
                + FormatUtil.volumeIcon(audioPlayer.getVolume()));

        if (manager.getBot().getConfig().useNPImages() && info.albumArtUrl() != null) {
            try {
                eb.setImage(info.albumArtUrl());
            } catch (IllegalArgumentException ignored) {
                // アルバムアートのURLが不正でも再生情報の表示は続行する
            }
        }
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

            if (GensokyoInfoAgent.isGensokyoRadio(track)) {
                return "**" + GensokyoInfoAgent.DISPLAY_NAME + "** [" + (userid == 0 ? "自動再生" : "<@" + userid + ">") + "]"
                        + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                        + "[LIVE] "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume());
            }

            // 置き換え時でも元のタイトルを優先表示
            AudioTrackInfo info = track.getInfo();
            String title = info.title;
            String uri   = info.uri;
            Object udAll = track.getUserData();
            if (udAll instanceof PlayerManager.TrackContext) {
                PlayerManager.TrackContext tc = (PlayerManager.TrackContext) udAll;
                if (tc.originalInfo != null) {
                    if (tc.originalInfo.title != null) title = tc.originalInfo.title;
                    if (tc.originalInfo.uri != null) uri = tc.originalInfo.uri;
                }
            }
            if (title == null || title.equals("不明なタイトル"))
                title = uri;

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

    // 元URLから YouTube のIDを抽出（置き換え表示用）
    private static String extractYoutubeId(String url) {
        if (url == null) return null;
        try {
            int vIndex = url.indexOf("v=");
            if (vIndex >= 0) {
                String v = url.substring(vIndex + 2);
                int amp = v.indexOf('&');
                return amp > 0 ? v.substring(0, amp) : v;
            }
            int idx = url.indexOf("youtu.be/");
            if (idx >= 0) {
                String v = url.substring(idx + "youtu.be/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
            idx = url.indexOf("/shorts/");
            if (idx >= 0) {
                String v = url.substring(idx + "/shorts/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
        } catch (Exception ignored) {}
        return null;
    }
}