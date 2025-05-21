package com.sedmelluq.discord.lavaplayer.source.nico;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.container.*;
import com.sedmelluq.discord.lavaplayer.container.wav.WavContainerProbe;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager that implements finding NicoNico tracks based on URL.
 */
public class NicoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:(?:www\\.|sp\\.|)nicovideo\\.jp/watch/|nico\\.ms/)((?:sm|nm|so)[0-9]+)(?:\\?.*|)$";

    private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);
    private static final Logger log = LoggerFactory.getLogger(NicoAudioSourceManager.class);
    private final HttpInterfaceManager httpInterfaceManager;
    private final AtomicBoolean loggedIn;
    public static String userName = null;
    public static String password = null;
    public static String twofactor = null;

    public NicoAudioSourceManager() {
        this(null, null);
    }

    /**
     * @param email    Site account email
     * @param password Site account password
     */
    public NicoAudioSourceManager(String email, String password) {
        updateYtDlp();

        File cacheDir = new File("cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        loggedIn = new AtomicBoolean();
        // Log in at the start
        if (!DataFormatTools.isNullOrEmpty(email) && !DataFormatTools.isNullOrEmpty(password)) {
            logIn(email, password);
            NicoAudioSourceManager.userName = email;
            NicoAudioSourceManager.password = password;
        }

        if(!BotConfig.getNicoNicoTwoFactor().isEmpty()){
            NicoAudioSourceManager.twofactor = BotConfig.getNicoNicoTwoFactor();
        }
    }

    private static String getWatchUrl(String videoId) {
        return "https://www.nicovideo.jp/watch/" + videoId;
    }

    /**
     * This method attempts to update the <code>`yt-dlp`</code> Python package on the system.
     * <p>
     * The process involves checking whether Python 3 is available by first trying
     * to execute the <code>`python3 --version`</code> command. If `python3` is not found,
     * the method checks if the `python` command corresponds to Python version 3.x.
     * <p>
     * If a suitable Python 3 interpreter is found, the method then attempts to
     * update `yt-dlp` using the command <code>`python3 -m pip install -U --pre yt-dlp`</code>
     * or <code>`python -m pip install -U --pre yt-dlp`</code>, depending on which interpreter
     * is available.
     * <p>
     * If neither `python3` nor `python` corresponds to a Python 3.x interpreter,
     * the method logs an error and aborts the update process.
     * <p>
     * In case of any exception during this process, an error is logged, and the user
     * is prompted to run the appropriate command manually.
     */

    public void updateYtDlp() {
        Runtime runtime = Runtime.getRuntime();
        try {
            log.info("Checking if python3 is available.");
            Process checkPython3 = runtime.exec("python3 --version");
            int python3ExitCode = checkPython3.waitFor();

            String pythonCommand = "python3";
            if (python3ExitCode != 0) {
                log.info("python3 not found. Checking if python is Python 3.");
                Process checkPython = runtime.exec("python --version");
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkPython.getInputStream()));
                String pythonVersion = reader.readLine();
                checkPython.waitFor();

                if (pythonVersion != null && pythonVersion.startsWith("Python 3")) {
                    log.info("Python is version 3.x.");
                    pythonCommand = "python";
                } else {
                    log.error("Neither python3 nor python version 3.x found. Aborting yt-dlp update.");
                    return;
                }
            }

            log.info("Updating yt-dlp using {}.", pythonCommand);
            Process process = runtime.exec(pythonCommand + " -m pip install -U --pre yt-dlp");
            process.waitFor();
            process.destroy();
            log.info("yt-dlp update completed.");
        } catch (Exception e) {
            log.error("Failed to update yt-dlp. Please run \"python3 -m pip install -U --pre yt-dlp\" or \"python -m pip install -U --pre yt-dlp\" manually to update.");
        }
    }


    @Override
    public String getSourceName() {
        return "niconico";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        Matcher trackMatcher = trackUrlPattern.matcher(reference.identifier);

        if (trackMatcher.matches()) {
            return loadTrack(trackMatcher.group(1));
        }

        return null;
    }

    private AudioTrack loadTrack(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://ext.nicovideo.jp/api/getthumbinfo/" + videoId))) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Unexpected response code from video info: " + statusCode);
                }

                Document document = Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
                return extractTrackFromXml(videoId, document);
            }
        } catch (IOException e) {
            throw new FriendlyException("Error occurred when extracting video info.", SUSPICIOUS, e);
        }
    }


    /**
     * This method extracts an `AudioTrack` object from the provided XML document
     * based on the given video ID.
     * <p>
     * The method iterates over the elements in the document that match the CSS selector
     * `:root > thumb`. For each matching element, it extracts relevant information such as:
     * - Uploader: If the video ID starts with "so", the uploader's name is retrieved
     *   from the `ch_name` element. Otherwise, it is retrieved from the `user_nickname` element.
     * - Title: The track title is retrieved from the `title` element.
     * - Thumbnail URL: The URL of the video's thumbnail is retrieved from the `thumbnail_url` element.
     * - Duration: The video's duration is parsed from the `length` element using
     *   `DataFormatTools.durationTextToMillis`.
     * <p>
     * Once these details are collected, a new `NicoAudioTrack` object is created with
     * the extracted information and is returned. If no matching elements are found
     * in the XML document, the method returns `null`.
     *
     * @param videoId  The ID of the video for which the track information is being extracted.
     * @param document The XML document containing the track's metadata.
     * @return A `NicoAudioTrack` object with the extracted track information, or `null` if no matching element is found.
     */

    private AudioTrack extractTrackFromXml(String videoId, Document document) {
        for (Element element : document.select(":root > thumb")) {

            String uploader = "";
            if (videoId.matches("so.*")) {
                uploader = element.select("ch_name").first().text();
            } else {
                Elements userNicknameElements = element.select("user_nickname");
                if (!userNicknameElements.isEmpty()) {
                    uploader = userNicknameElements.first().text();
                } else {
                    // user_nicknameが存在しない場合の処理
                    uploader = "情報なし";
                }
            }
            String title = element.selectFirst("title").text();
            String thumbnailUrl = element.selectFirst("thumbnail_url").text();
            long duration = DataFormatTools.durationTextToMillis(element.selectFirst("length").text());

            return new NicoAudioTrack(new AudioTrackInfo(title,
                    uploader,
                    duration,
                    videoId,
                    false,
                    getWatchUrl(videoId),
                    thumbnailUrl,
                    null
            ), this, new MediaContainerDescriptor(new WavContainerProbe(), null));
        }

        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // No extra information to save
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new NicoAudioTrack(trackInfo, this, new MediaContainerDescriptor(new WavContainerProbe(), null));
    }

    @Override
    public void shutdown() {
        // Nothing to shut down
    }

    /**
     * @return Get an HTTP interface for a playing track.
     */
    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    void logIn(String email, String password) {
        synchronized (loggedIn) {
            if (loggedIn.get()) {
                return;
            }

            String url = "https://account.nicovideo.jp/login/redirector".trim();
            URI uri = URI.create(url);
            HttpPost loginRequest = new HttpPost(uri);

            loginRequest.setEntity(new UrlEncodedFormEntity(Arrays.asList(
                    new BasicNameValuePair("mail_tel", email),
                    new BasicNameValuePair("password", password)
            ), StandardCharsets.UTF_8));

            try (HttpInterface httpInterface = getHttpInterface()) {
                try (CloseableHttpResponse response = httpInterface.execute(loginRequest)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode != 302) {
                        throw new IOException("Unexpected response code " + statusCode);
                    }

                    Header location = response.getFirstHeader("Location");

                    if (location == null || location.getValue().contains("message=cant_login")) {
                        throw new FriendlyException("Login details for NicoNico are invalid.", COMMON, null);
                    }

                    loggedIn.set(true);
                }
            } catch (IOException e) {
                throw new FriendlyException("Exception when trying to log into NicoNico", SUSPICIOUS, e);
            }
        }
    }
}
