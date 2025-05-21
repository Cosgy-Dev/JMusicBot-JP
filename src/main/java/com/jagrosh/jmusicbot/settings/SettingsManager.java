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
package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jdautilities.command.GuildSettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class SettingsManager implements GuildSettingsManager {
    private final static double SKIP_RATIO = .55;
    private final HashMap<Long, Settings> settings;

    public SettingsManager() {
        this.settings = new HashMap<>();
        try {
            JSONObject loadedSettings = new JSONObject(new String(Files.readAllBytes(OtherUtil.getPath("serversettings.json"))));
            loadedSettings.keySet().forEach((id) -> {
                JSONObject o = loadedSettings.getJSONObject(id);

                // 以前の(boolean型)バージョンをサポートするための
                try {
                    if (o.getBoolean("repeat")) {
                        o.put("repeat", RepeatMode.ALL);
                    } else {
                        o.put("repeat", RepeatMode.OFF);
                    }
                    //バグで誤った値を入れていたのでその数値を正しいものに変更するため
                    if (o.getInt("announce") == 50) {
                        o.put("announce", 0);
                    }
                } catch (JSONException ignored) { /* ignored */ }

                settings.put(Long.parseLong(id), new Settings(this,
                        o.has("text_channel_id") ? o.getString("text_channel_id") : null,
                        o.has("voice_channel_id") ? o.getString("voice_channel_id") : null,
                        o.has("dj_role_id") ? o.getString("dj_role_id") : null,
                        o.has("volume") ? o.getInt("volume") : 10,
                        o.has("default_playlist") ? o.getString("default_playlist") : null,
                        o.has("repeat") ? o.getEnum(RepeatMode.class, "repeat") : RepeatMode.OFF,
                        o.has("prefix") ? o.getString("prefix") : null,
                        o.has("bitrate_warnings_readied") && o.getBoolean("bitrate_warnings_readied"),
                        o.has("announce") ? o.getInt("announce") : 0,
                        o.has("skip_ratio") ? o.getDouble("skip_ratio") : SKIP_RATIO,
                        o.has("vc_status") && o.getBoolean("vc_status"),
                        o.has("force_to_end_que") && o.getBoolean("force_to_end_que")));
            });
        } catch (NoSuchFileException e) {
            // ignore, it just means no settings have been saved yet
            // create an empty json file
            try {
                LoggerFactory.getLogger("Settings").info("serversettings.json を" + OtherUtil.getPath("serversettings.json").toAbsolutePath() + "に作成しました。");
                Files.write(OtherUtil.getPath("serversettings.json"), new JSONObject().toString(4).getBytes());
            } catch(IOException ex) {
                LoggerFactory.getLogger("Settings").warn("サーバー設定ファイルの作成に失敗しました:"+ex);
            }
            return;
        } catch(IOException | JSONException e) {
            LoggerFactory.getLogger("Settings").warn("サーバー設定ファイルの読み込みに失敗しました: "+e);
        }
    }

    /**
     * ギルドのnull以外の設定を取得します
     *
     * @param guild 設定を取得するギルド
     * @return the 既存の設定、またはそのギルドの新しい設定
     */
    @Override
    public Settings getSettings(Guild guild) {
        return getSettings(guild.getIdLong());
    }

    public Settings getSettings(long guildId) {
        return settings.computeIfAbsent(guildId, id -> createDefaultSettings());
    }

    private Settings createDefaultSettings() {
        return new Settings(this, 0, 0, 0, 10, null, RepeatMode.OFF, null, false, 0, SKIP_RATIO, true, false);
    }

    protected void writeSettings() {
        var obj = new JSONObject();
        for (Long key : settings.keySet()) {
            var o = new JSONObject();
            Settings s = settings.get(key);
            if (s.textId != 0)
                o.put("text_channel_id", Long.toString(s.textId));
            if (s.voiceId != 0)
                o.put("voice_channel_id", Long.toString(s.voiceId));
            if (s.roleId != 0)
                o.put("dj_role_id", Long.toString(s.roleId));
            if (s.getVolume() != 50)
                o.put("volume", s.getVolume());
            if (s.getDefaultPlaylist() != null)
                o.put("default_playlist", s.getDefaultPlaylist());
            if (s.getRepeatMode() != RepeatMode.OFF)
                o.put("repeat", s.getRepeatMode());
            if (s.getPrefix() != null)
                o.put("prefix", s.getPrefix());
            if (s.getAnnounce() != 0)
                o.put("announce", s.getAnnounce());
            if (s.getSkipRatio() != SKIP_RATIO)
                o.put("skip_ratio", s.getSkipRatio());
            if (!s.getVCStatus())
                o.put("vc_status", s.getVCStatus());
            if(s.isForceToEndQue())
                o.put("force_to_end_que", s.isForceToEndQue());

            obj.put(Long.toString(key), o);
        }
        try {
            Files.write(OtherUtil.getPath("serversettings.json"), obj.toString(4).getBytes());
        } catch (IOException ex) {
            LoggerFactory.getLogger("Settings").warn("ファイルへの書き込みに失敗しました： " + ex);
        }
    }
}
