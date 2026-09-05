/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
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

package dev.cosgy.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 幻想郷ラジオ (Gensokyo Radio) の再生情報を収集するエージェント。
 * <p>
 * 再生情報は WebSocket ({@code wss://gensokyoradio.net/wss}) から受け取る。接続時と曲の
 * 切り替わりに合わせてサーバーから push されるため、こちらから問い合わせる必要は無い。
 * <p>
 * コマンド側は {@link #getInfo()} でキャッシュ済みのスナップショットを参照するだけなので、
 * 表示処理が通信でブロックすることはない。誰も情報を要求していない間は接続を閉じる。
 * <p>
 * WebSocket の手順は以下の通り:
 * <ol>
 *     <li>接続後 {@code {"message":"grInitialConnection"}} を送る</li>
 *     <li>{@code {"message":"welcome","id":...}} でクライアントIDが返る</li>
 *     <li>{@code {"message":"ping"}} には {@code {"message":"pong","id":...}} を返す</li>
 *     <li>曲情報は {@code songid} を持つ JSON として push される</li>
 * </ol>
 */
public class GensokyoInfoAgent extends Thread {
    /** 幻想郷ラジオのストリーム/サイト URL に共通して含まれるホスト名。 */
    public static final String RADIO_HOST = "gensokyoradio.net";
    /** 曲情報が取得できない場合などに使用する表示名。 */
    public static final String DISPLAY_NAME = "幻想郷ラジオ";
    /** 公式サイト。曲名のリンク先に使う。 */
    public static final String SITE_URL = "https://gensokyoradio.net/";

    private static final Logger log = LoggerFactory.getLogger(GensokyoInfoAgent.class);

    private static final String WS_URL = "wss://gensokyoradio.net/wss";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36";
    private static final String INITIAL_MESSAGE = "{\"message\":\"grInitialConnection\"}";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 監視ループの刻み幅。 */
    private static final long TICK_MS = 1_000L;
    /** 情報の要求が途絶えてから接続を閉じるまでの時間。 */
    private static final long IDLE_TIMEOUT_MS = 300_000L;
    /**
     * この時間なにも受信しなければ接続が死んだとみなす。
     * サーバーからは20秒程度の間隔で ping が届くため、無通信は異常を意味する。
     */
    private static final long STALE_TIMEOUT_MS = 90_000L;
    private static final long INITIAL_BACKOFF_MS = 10_000L;
    private static final long MAX_BACKOFF_MS = 300_000L;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 最後に情報が要求された時刻。要求が無い間は接続を張らないために使う。 */
    private static final AtomicLong lastDemandAt = new AtomicLong(Long.MIN_VALUE);
    /** 直近の再生情報。 */
    private static volatile Snapshot snapshot = null;

    /** 接続中の WebSocket。未接続なら {@code null}。 */
    private volatile WebSocket socket = null;
    /** 接続処理が進行中かどうか。 */
    private volatile boolean connecting = false;
    /** 次に接続を試みてよい時刻。 */
    private volatile long nextConnectAt = 0L;
    /** ping への応答に使うクライアントID。 */
    private volatile int clientId = 0;
    /** 最後に何らかのメッセージを受け取った時刻。 */
    private volatile long lastMessageAt = 0L;
    private volatile long backoffMs = INITIAL_BACKOFF_MS;
    /** 情報が要求されている状態かどうか。監視スレッドだけが読み書きする。 */
    private boolean active = false;

    public GensokyoInfoAgent() {
        setDaemon(true);
        setName("GensokyoInfoAgent");
    }

    /**
     * トラックが幻想郷ラジオのストリームかどうかを判定する。
     */
    public static boolean isGensokyoRadio(AudioTrack track) {
        return track != null && track.getInfo() != null
                && track.getInfo().uri != null && track.getInfo().uri.contains(RADIO_HOST);
    }

    /**
     * 直近の再生情報を返す。通信は行わないため即座に返る。
     * まだ一度も受信できていない場合は {@code null}。
     */
    public static Snapshot getInfo() {
        lastDemandAt.set(System.currentTimeMillis());
        return snapshot;
    }

    /**
     * 幻想郷ラジオの再生を開始したことをエージェントに伝え、情報の受信を再開させる。
     * 利用者が {@code nowplaying} を実行する前に情報を用意しておくために呼ぶ。
     */
    public static void markActive() {
        getInfo();
    }

    /**
     * トラックの表示名。幻想郷ラジオであれば受信済みの曲名を、それ以外はトラックのタイトルを返す。
     */
    public static String displayTitle(AudioTrack track) {
        if (isGensokyoRadio(track)) {
            Snapshot current = getInfo();
            String title = current == null ? null : current.title();
            return title == null ? DISPLAY_NAME : DISPLAY_NAME + ": " + title;
        }
        return track == null || track.getInfo() == null ? "不明なトラック" : track.getInfo().title;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String trimmed = value.asText().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int number(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? fallback : value.asInt();
    }

    @Override
    public void run() {
        log.info("GensokyoInfoAgentを開始しました");
        while (!isInterrupted()) {
            try {
                sleep(TICK_MS);
            } catch (InterruptedException e) {
                interrupt();
                break;
            }

            long now = System.currentTimeMillis();
            // 誰も情報を見ていない間は幻想郷ラジオへ接続しない
            if (now - lastDemandAt.get() > IDLE_TIMEOUT_MS) {
                if (active) {
                    active = false;
                    disconnect();
                }
                continue;
            }
            if (!active) {
                active = true;
                lastMessageAt = now;
            }

            WebSocket current = socket;
            // ping すら届かなくなった接続は繋ぎ直す
            if (current != null && now - lastMessageAt > STALE_TIMEOUT_MS) {
                log.warn("幻想郷ラジオのWebSocketから応答が無いため接続し直します");
                if (!current.isOutputClosed()) {
                    current.sendClose(WebSocket.NORMAL_CLOSURE, "");
                }
                onDisconnected(current);
            }
            connect(now);
        }
        disconnect();
        log.info("GensokyoInfoAgentを停止しました");
    }

    /** WebSocket が繋がっていなければ接続する。 */
    private void connect(long now) {
        WebSocket current = socket;
        // コールバックを取りこぼした場合に備え、閉じた接続はここでも破棄する
        if (current != null && (current.isInputClosed() || current.isOutputClosed())) {
            onDisconnected(current);
        }
        if (socket != null || connecting || now < nextConnectAt) return;
        connecting = true;
        CLIENT.newWebSocketBuilder()
                .connectTimeout(TIMEOUT)
                .header("user-agent", USER_AGENT)
                .buildAsync(URI.create(WS_URL), new InfoListener())
                .whenComplete((ws, error) -> {
                    connecting = false;
                    if (error != null) {
                        // ハンドシェイクに失敗した場合は onError が呼ばれないためここで処理する
                        log.warn("幻想郷ラジオのWebSocketに接続できませんでした: {}", error.toString());
                        scheduleReconnect();
                        return;
                    }
                    lastMessageAt = System.currentTimeMillis();
                    socket = ws;
                    backoffMs = INITIAL_BACKOFF_MS;
                    log.debug("幻想郷ラジオのWebSocketに接続しました");
                });
    }

    /** 接続を閉じ、次に要求されるまで待機する。 */
    private void disconnect() {
        WebSocket ws = socket;
        socket = null;
        nextConnectAt = 0L;
        backoffMs = INITIAL_BACKOFF_MS;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
        }
    }

    private void scheduleReconnect() {
        nextConnectAt = System.currentTimeMillis() + backoffMs;
        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }

    /** 接続が失われたことを記録する。既に別の接続へ差し替わっていれば何もしない。 */
    private void onDisconnected(WebSocket ws) {
        if (socket != ws) return;
        socket = null;
        scheduleReconnect();
    }

    /** WebSocket から push される再生情報を受け取る。 */
    private class InfoListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            webSocket.sendText(INITIAL_MESSAGE, true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            lastMessageAt = System.currentTimeMillis();
            buffer.append(data);
            if (!last) return null;

            String message = buffer.toString();
            buffer.setLength(0);
            try {
                handle(webSocket, MAPPER.readTree(message));
            } catch (Exception e) {
                log.warn("幻想郷ラジオのWebSocketメッセージを処理できませんでした: {}", e.toString());
            }
            return null;
        }

        private void handle(WebSocket webSocket, JsonNode json) {
            String type = text(json, "message");
            if (type != null) {
                switch (type) {
                    case "welcome" -> clientId = number(json, "id", 0);
                    // ping に応答しないと接続を切られるため必ず返す
                    case "ping" -> webSocket.sendText("{\"message\":\"pong\",\"id\":" + clientId + "}", true);
                    default -> log.debug("幻想郷ラジオから未知のメッセージを受信しました: {}", type);
                }
                return;
            }
            if (json.has("songid")) {
                snapshot = Snapshot.of(json);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.debug("幻想郷ラジオのWebSocketが切断されました({} {})", statusCode, reason);
            onDisconnected(webSocket);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("幻想郷ラジオのWebSocketでエラーが発生しました: {}", error.toString());
            onDisconnected(webSocket);
        }
    }

    /**
     * ある時点の再生情報。値は不変で、再生位置だけ受信時刻からの経過で補正する。
     * 不明な項目は {@code null} になる。
     *
     * @param playedAtReceipt 受信した時点での再生位置(秒)
     */
    public record Snapshot(String title, String artist, String album, String circle, String year,
                           String albumArtUrl, int durationSeconds, int playedAtReceipt, long receivedAtMs) {

        /** push された曲情報を読み取る。 */
        static Snapshot of(JsonNode json) {
            int year = number(json, "year", 0);
            return new Snapshot(
                    text(json, "title"),
                    text(json, "artist"),
                    text(json, "album"),
                    text(json, "circle"),
                    year <= 0 ? null : Integer.toString(year),
                    text(json, "albumart"),
                    Math.max(0, number(json, "duration", 0)),
                    Math.max(0, number(json, "played", 0)),
                    System.currentTimeMillis());
        }

        /** 現在の再生位置(秒)。受信時刻からの経過を加算して求める。 */
        public int playedSeconds() {
            long elapsed = Math.max(0L, System.currentTimeMillis() - receivedAtMs) / 1000L;
            long position = playedAtReceipt + elapsed;
            return (int) (durationSeconds > 0 ? Math.min(position, durationSeconds) : position);
        }

        /** 再生の進捗 (0.0 - 1.0)。長さが不明な場合は 0。 */
        public double progress() {
            return durationSeconds <= 0 ? 0.0 : (double) playedSeconds() / durationSeconds;
        }
    }
}
