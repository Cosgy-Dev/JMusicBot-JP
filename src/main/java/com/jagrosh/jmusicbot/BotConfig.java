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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author John Grosh (jagrosh)
 */
public class BotConfig {
    private final static String CONTEXT = "Config";
    private final static String START_TOKEN = "/// START OF JMUSICBOT-JP CONFIG ///";
    private final static String END_TOKEN = "/// END OF JMUSICBOT-JP CONFIG ///";
    private final Prompt prompt;
    private Path path = null;
    // [JMusicBot-JP] added nicoEmail, nicoPass
    private String token;
    private String prefix;
    private String altprefix;
    private String helpWord;
    private String playlistsFolder;
    private String mylistfolder;
    private String publistFolder;
    private String successEmoji;
    private String warningEmoji;
    private String errorEmoji;
    private String loadingEmoji;
    private String searchingEmoji;
    private String nicoEmail;
    private String nicoPass;
    private static String nicoTwoFactor;
    private String ytEmail;
    private String ytPass;
    private String spClientId;
    private String spClientSecret;
    // [JMusicBot-JP] added useNicoNico, changeNickName, pauseNoUsers, resumeJoined, stopNoUsers, cosgyDevHost, helpToDm, officialInvite
    private boolean useNicoNico, changeNickName, stayInChannel, pauseNoUsers, resumeJoined, stopNoUsers, songInGame, npImages, updatealerts, useEval, dbots, cosgyDevHost, helpToDm, autoStopQueueSave, auditCommands, officialInvite, useinvitecommand;
    private long owner, maxSeconds, aloneTimeUntilStop;
    private OnlineStatus status;
    private Activity game;
    private Config aliases, transforms;

    private boolean valid = false;

    public BotConfig(Prompt prompt) {
        this.prompt = prompt;
    }

    public void load() {
        valid = false;

        // ファイルからの設定の読み込み
        try {
            // 設定のパスを取得（デフォルトはconfig.txt)
            path = OtherUtil.getPath(System.getProperty("config.file", System.getProperty("config", "config.txt")));
            if (path.toFile().exists()) {
                if (System.getProperty("config.file") == null)
                    System.setProperty("config.file", System.getProperty("config", path.toAbsolutePath().toString()));
                ConfigFactory.invalidateCaches();
            }

            // 設定ファイルにロードされ、デフォルト値が追加される
            //Config config = ConfigFactory.parseFile(path.toFile()).withFallback(ConfigFactory.load());
            Config config = ConfigFactory.load();
            // 設定値
            token = config.getString("token");
            prefix = config.getString("prefix");
            altprefix = config.getString("altprefix");
            helpWord = config.getString("help");
            owner = (config.getAnyRef("owner") instanceof String ? 0L : config.getLong("owner"));
            successEmoji = config.getString("success");
            warningEmoji = config.getString("warning");
            errorEmoji = config.getString("error");
            loadingEmoji = config.getString("loading");
            searchingEmoji = config.getString("searching");
            game = OtherUtil.parseGame(config.getString("game"));
            status = OtherUtil.parseStatus(config.getString("status"));
            stayInChannel = config.getBoolean("stayinchannel");
            songInGame = config.getBoolean("songinstatus");
            npImages = config.getBoolean("npimages");
            updatealerts = config.getBoolean("updatealerts");
            useEval = config.getBoolean("eval");
            maxSeconds = config.getLong("maxtime");
            aloneTimeUntilStop = config.getLong("alonetimeuntilstop");
            playlistsFolder = config.getString("playlistsfolder");
            mylistfolder = config.getString("mylistfolder");
            publistFolder = config.getString("publistfolder");
            aliases = config.getConfig("aliases");
            transforms = config.getConfig("transforms");
            dbots = owner == 334091398263341056L;


            // [JMusicBot-JP]
            useNicoNico = config.getBoolean("useniconico");
            nicoEmail = config.getString("nicomail");
            nicoPass = config.getString("nicopass");
            nicoTwoFactor = config.getString("nicotwofactor");
            pauseNoUsers = config.getBoolean("pausenousers");
            resumeJoined = config.getBoolean("resumejoined");
            stopNoUsers = config.getBoolean("stopnousers");
            changeNickName = config.getBoolean("changenickname");
            helpToDm = config.getBoolean("helptodm");
            autoStopQueueSave = config.getBoolean("autostopqueuesave");
            auditCommands = config.getBoolean("auditcommands");
            officialInvite = config.getBoolean("officialinvite");
            useinvitecommand = config.getBoolean("useinvitecommand");
            ytEmail = config.getString("ytemail");
            ytPass = config.getString("ytpass");
            spClientId = config.getString("spclient");
            spClientSecret = config.getString("spsecret");


            cosgyDevHost = false;
            // [JMusicBot-JP] End

            // we may need to write a new config file
            boolean write = false;

            // validate bot token
            if (token == null || token.isEmpty() || token.matches("(BOT_TOKEN_HERE|Botトークンをここに貼り付け)")) {
                token = prompt.prompt("BOTトークンを入力してください。"
                        + "\nトークンを取得する方法はこちらから:"
                        + "\nhttps://github.com/jagrosh/MusicBot/wiki/Getting-a-Bot-Token."
                        + "\nBOTトークン: ");
                if (token == null) {
                    prompt.alert(Prompt.Level.WARNING, CONTEXT, "トークンが入力されていません！終了します。\n\n設定ファイルの場所: " + path.toAbsolutePath());
                    return;
                } else {
                    write = true;
                }
            }

            // validate bot owner
            if (owner <= 0) {
                try {
                    owner = Long.parseLong(prompt.prompt("所有者のユーザーIDが設定されていない、または有効なIDではありません。"
                            + "\nBOTの所有者のユーザーIDを入力してください。"
                            + "\nユーザーIDの入手方法はこちらから:"
                            + "\nhttps://github.com/jagrosh/MusicBot/wiki/Finding-Your-User-ID"
                            + "\n所有者のユーザーID: "));
                } catch (NumberFormatException | NullPointerException ex) {
                    owner = 0;
                }
                if (owner <= 0) {
                    prompt.alert(Prompt.Level.ERROR, CONTEXT, "無効なユーザーIDです！終了します。\n\n設定ファイルの場所: " + path.toAbsolutePath());
                    System.exit(0);
                } else {
                    write = true;
                }
            }

            if (write) {
                String original = OtherUtil.loadResource(this, "/reference.conf");
                String mod;
                if (original == null) {
                    mod = ("token = " + token + "\r\nowner = " + owner);
                } else {
                    mod = original.substring(original.indexOf(START_TOKEN) + START_TOKEN.length(), original.indexOf(END_TOKEN))
                            .replace("BOT_TOKEN_HERE", token).replace("Botトークンをここに貼り付け", token)
                            .replace("0 // OWNER ID", Long.toString(owner)).replace("所有者IDをここに貼り付け", Long.toString(owner))
                            .trim();
                }

                FileUtils.writeStringToFile(path.toFile(), mod, StandardCharsets.UTF_8);
            }

            // if we get through the whole config, it's good to go
            valid = true;
        } catch (ConfigException | IOException ex) {
            prompt.alert(Prompt.Level.ERROR, CONTEXT, ex + ": " + ex.getMessage() + "\n\n設定ファイルの場所: " + path.toAbsolutePath());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public String getConfigLocation() {
        return path.toFile().getAbsolutePath();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAltPrefix() {
        return "NONE".equalsIgnoreCase(altprefix) ? null : altprefix;
    }

    public String getToken() {
        return token;
    }

    public long getOwnerId() {
        return owner;
    }

    public String getSuccess() {
        return successEmoji;
    }

    public String getWarning() {
        return warningEmoji;
    }

    public String getError() {
        return errorEmoji;
    }

    public String getLoading() {
        return loadingEmoji;
    }

    public String getSearching() {
        return searchingEmoji;
    }

    public Activity getGame() {
        return game;
    }

    public OnlineStatus getStatus() {
        return status;
    }

    public String getHelp() {
        return helpWord;
    }

    public boolean getStay() {
        return stayInChannel;
    }

    public boolean getNoUserPause() {
        return pauseNoUsers;
    }

    public boolean getResumeJoined() {
        return resumeJoined;
    }

    public boolean getNoUserStop() {
        return stopNoUsers;
    }

    public long getAloneTimeUntilStop() {
        return aloneTimeUntilStop;
    }

    public boolean getChangeNickName() {
        return changeNickName;
    }

    public boolean getSongInStatus() {
        return songInGame;
    }

    public String getPlaylistsFolder() {
        return playlistsFolder;
    }

    public String getMylistfolder() {
        return mylistfolder;
    }

    public String getPublistFolder() {
        return publistFolder;
    }

    public boolean getDBots() {
        return dbots;
    }

    public boolean useUpdateAlerts() {
        return updatealerts;
    }

    public boolean useEval() {
        return useEval;
    }

    public boolean useNPImages() {
        return npImages;
    }

    public long getMaxSeconds() {
        return maxSeconds;
    }

    public String getMaxTime() {
        return FormatUtil.formatTime(maxSeconds * 1000);
    }

    public boolean isTooLong(AudioTrack track) {
        if (maxSeconds <= 0)
            return false;
        return Math.round(track.getDuration() / 1000.0) > maxSeconds;
    }

    public String[] getAliases(String command) {
        try {
            return aliases.getStringList(command).toArray(new String[0]);
        } catch (NullPointerException | ConfigException.Missing e) {
            return new String[0];
        }
    }

    // [JMusicBot-JP] new function: support niconico play
    public boolean isNicoNicoEnabled() {
        return useNicoNico;
    }

    public String getNicoNicoEmailAddress() {
        return nicoEmail;
    }

    public String getNicoNicoPassword() {
        return nicoPass;
    }

    public static String getNicoNicoTwoFactor(){ return nicoTwoFactor; }

    public String getYouTubeEmailAddress() {
        return ytEmail;
    }

    public String getYouTubePassword() {
        return ytPass;
    }

    public String getSpotifyClientId(){return spClientId;}

    public String getSpotifyClientSecret(){return spClientSecret;}

    // [JMusicBot-JP] End

    public boolean getCosgyDevHost() {
        return cosgyDevHost;
    }

    public boolean getHelpToDm() {
        return helpToDm;
    }

    public boolean getAutoStopQueueSave() {
        return autoStopQueueSave;
    }

    public boolean getAuditCommands() {
        return auditCommands;
    }

    public Config getTransforms() {
        return transforms;
    }

    public boolean isOfficialInvite() {
        return officialInvite;
    }

    public boolean isUseInviteCommand() {
        return useinvitecommand;
    }
}
