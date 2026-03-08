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
package com.jagrosh.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.playlist.MylistLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class PlaylistLoader {
    private final BotConfig config;

    public PlaylistLoader(BotConfig config) {
        this.config = config;
    }

    private static <T> void shuffle(List<T> list) {
        for (int first = 0; first < list.size(); first++) {
            int second = (int) (Math.random() * list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        }
    }

    public List<String> getPlaylistNames(String guildId) {
        if (folderExists()) {
            if (folderGuildExists(guildId)) {
                File folder = new File(OtherUtil.getPath(config.getPlaylistsFolder() + File.separator + guildId).toString());
                File[] files = folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"));
                if (files == null) {
                    return Collections.emptyList();
                }
                return Arrays.stream(files)
                        .map(f -> f.getName().substring(0, f.getName().length() - 4))
                        .collect(Collectors.toList());
            } else {
                createGuildFolder(guildId);
                return getPlaylistNames(guildId);
            }
        } else {
            createFolder();
            createGuildFolder(guildId);
            return getPlaylistNames(guildId);
        }
    }

    public void createGuildFolder(String guildId) {
        try {
            Files.createDirectory(OtherUtil.getPath(config.getPlaylistsFolder() + File.separator + guildId));
        } catch (IOException ignored) {
        }
    }

    public void createFolder() {
        try {
            Files.createDirectory(OtherUtil.getPath(config.getPlaylistsFolder()));
        } catch (IOException ignore) {
        }
    }

    public boolean folderGuildExists(String guildId) {
        return Files.exists(Paths.get(config.getPlaylistsFolder() + File.separator + guildId));
    }

    public boolean folderExists() {
        return Files.exists(Paths.get(config.getPlaylistsFolder()));
    }

    public void createPlaylist(String guildId, String name) throws IOException {
        Files.createFile(Paths.get(config.getPlaylistsFolder() + File.separator + guildId + File.separator + name + ".txt"));
    }

    public void deletePlaylist(String guildId, String name) throws IOException {
        Files.delete(Paths.get(config.getPlaylistsFolder() + File.separator + guildId + File.separator + name + ".txt"));
    }

    public void writePlaylist(String guildId, String name, String text) throws IOException {
        Files.write(Paths.get(config.getPlaylistsFolder() + File.separator + guildId + File.separator + name + ".txt"), text.trim().getBytes(StandardCharsets.UTF_8));
    }

    public Playlist getPlaylist(String guildId, String name) {
        if (!getPlaylistNames(guildId).contains(name))
            return null;
        try {
            if (folderExists()) {
                if (folderGuildExists(guildId)) {
                    boolean[] shuffle = {false};
                    List<String> list = new ArrayList<>();
                    Files.readAllLines(Paths.get(config.getPlaylistsFolder() + File.separator + guildId + File.separator + name + ".txt")).forEach(str ->
                    {
                        MylistLoader.Trim(shuffle, list, str);
                    });
                    if (shuffle[0])
                        shuffle(list);
                    return new Playlist(name, list, shuffle[0]);
                } else {
                    createGuildFolder(guildId);
                    return null;
                }
            } else {
                createFolder();
                createGuildFolder(guildId);
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    public class Playlist {
        private final String name;
        private final List<String> items;
        private final boolean shuffle;
        private final List<AudioTrack> tracks = new LinkedList<>();
        private final List<PlaylistLoadError> errors = new LinkedList<>();
        private boolean loaded = false;

        private Playlist(String name, List<String> items, boolean shuffle) {
            this.name = name;
            this.items = items;
            this.shuffle = shuffle;
        }

        public void loadTracks(AudioPlayerManager manager, Consumer<AudioTrack> consumer, Runnable callback) {
            if (loaded)
                return;
            loaded = true;
            for (int i = 0; i < items.size(); i++) {
                boolean last = i + 1 == items.size();
                int index = i;
                manager.loadItemOrdered(name, items.get(i), new AudioLoadResultHandler() {
                    private void done() {
                        if (last) {
                            if (shuffle)
                                shuffleTracks();
                            if (callback != null)
                                callback.run();
                        }
                    }

                    @Override
                    public void trackLoaded(AudioTrack at) {
                        if (config.isTooLong(at))
                            errors.add(new PlaylistLoadError(index, items.get(index), "このトラックは許可された最大長を超えています"));
                        else {
                            at.setUserData(0L);
                            tracks.add(at);
                            consumer.accept(at);
                        }
                        done();
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist ap) {
                        if (ap.isSearchResult()) {
                            trackLoaded(ap.getTracks().get(0));
                        } else if (ap.getSelectedTrack() != null) {
                            trackLoaded(ap.getSelectedTrack());
                        } else {
                            List<AudioTrack> loaded = new ArrayList<>(ap.getTracks());
                            if (shuffle)
                                for (int first = 0; first < loaded.size(); first++) {
                                    int second = (int) (Math.random() * loaded.size());
                                    AudioTrack tmp = loaded.get(first);
                                    loaded.set(first, loaded.get(second));
                                    loaded.set(second, tmp);
                                }
                            loaded.removeIf(config::isTooLong);
                            loaded.forEach(at -> at.setUserData(0L));
                            tracks.addAll(loaded);
                            loaded.forEach(consumer);
                        }
                        done();
                    }

                    @Override
                    public void noMatches() {
                        errors.add(new PlaylistLoadError(index, items.get(index), "一致するものが見つかりませんでした。"));
                        done();
                    }

                    @Override
                    public void loadFailed(FriendlyException fe) {
                        errors.add(new PlaylistLoadError(index, items.get(index), "トラックを読み込めませんでした: " + fe.getLocalizedMessage()));
                        done();
                    }
                });
            }
        }

        public void shuffleTracks() {
            shuffle(tracks);
        }

        public String getName() {
            return name;
        }

        public List<String> getItems() {
            return items;
        }

        public List<AudioTrack> getTracks() {
            return tracks;
        }

        public List<PlaylistLoadError> getErrors() {
            return errors;
        }
    }

    public class PlaylistLoadError {
        private final int number;
        private final String item;
        private final String reason;

        private PlaylistLoadError(int number, String item, String reason) {
            this.number = number;
            this.item = item;
            this.reason = reason;
        }

        public int getIndex() {
            return number;
        }

        public String getItem() {
            return item;
        }

        public String getReason() {
            return reason;
        }
    }
}
