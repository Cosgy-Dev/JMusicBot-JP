package com.sedmelluq.discord.lavaplayer.source.nico;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio track that handles processing NicoNico tracks.
 */
public class NicoAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NicoAudioTrack.class);
    private final MediaContainerDescriptor containerTrackFactory;
    private final NicoAudioSourceManager sourceManager;

    /**
     * @param trackInfo     Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public NicoAudioTrack(AudioTrackInfo trackInfo, NicoAudioSourceManager sourceManager, MediaContainerDescriptor containerTrackFactory) {
        super(trackInfo);

        this.containerTrackFactory = containerTrackFactory;
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        File playbackUrl = downloadAudio();

        log.debug("Starting NicoNico track from URL: {}", playbackUrl);
        try (LocalSeekableInputStream inputStream = new LocalSeekableInputStream(playbackUrl)) {
            processDelegate((InternalAudioTrack) containerTrackFactory.createTrack(trackInfo, inputStream), localExecutor);
        }
    }

    //
    private @NotNull File downloadAudio() {
        String path = new File(".").getAbsoluteFile().getParent();
        Path file = Path.of(path, "cache" + File.separator + getIdentifier() + ".wav");

        if (Files.notExists(file)) {
            try {
                log.info("Downloading NicoNico track from: {}", getIdentifier());
                Runtime runtime = Runtime.getRuntime();

                String login = "";
                if (NicoAudioSourceManager.userName != null && NicoAudioSourceManager.password != null) {
                    login += " --username " + NicoAudioSourceManager.userName + " --password " + NicoAudioSourceManager.password;
                    log.info("ニコニコのログイン情報を使用しました。");
                    if(NicoAudioSourceManager.twofactor != null) {
                        String code = TOTPGenerator.getCode(NicoAudioSourceManager.twofactor);

                        login += " --twofactor " + code;
                        log.info("二段階認証を行いました:{}", code);
                    }
                }

                String command = "yt-dlp" + login + " --extract-audio --audio-format wav https://www.nicovideo.jp/watch/" + getIdentifier() + " --output cache/" + getIdentifier() + ".wav";

                Process process = runtime.exec(command);

                // エラーストリームを読み取るためのスレッドを作成
                new Thread(() -> {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            log.error(line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading process error stream", e);
                    }
                }).start();

                // 標準出力ストリームを読み取るためのスレッドを作成
                new Thread(() -> {
                    try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = inputReader.readLine()) != null) {
                            log.debug(line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading process input stream", e);
                    }
                }).start();

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("yt-dlp command failed with exit code " + exitCode);
                }
                process.destroy();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return file.toFile();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NicoAudioTrack(trackInfo, sourceManager, containerTrackFactory);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
