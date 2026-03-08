package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author kosugi_kun
 */
public class PubliclistLoader {
    private final BotConfig config;

    public PubliclistLoader(BotConfig config) {
        this.config = config;
    }

    private static <T> void shuffle(List<T> list) {
        IntStream.range(0, list.size()).forEach(first -> {
            int second = (int) (Math.random() * list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        });
    }

    public List<String> getPlaylistNames() {
        if (folderExists()) {
            File folder = new File(config.getPublistFolder());
            File[] files = folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"));
            if (files == null) {
                return Collections.emptyList();
            }
            return Arrays.stream(files)
                    .map(f -> f.getName().substring(0, f.getName().length() - 4))
                    .collect(Collectors.toList());
        } else {
            createFolder();
            return Collections.emptyList();
        }
    }

    public void createFolder() {
        try {
            Files.createDirectory(OtherUtil.getPath(config.getPublistFolder()));
        } catch (IOException ignore) {
        }
    }

    public boolean folderExists() {
        return Files.exists(OtherUtil.getPath(config.getPublistFolder()));
    }

    public void createPlaylist(String name) throws IOException {
        Files.createFile(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"));
    }

    public void deletePlaylist(String name) throws IOException {
        Files.delete(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"));
    }

    public void writePlaylist(String name, String text) throws IOException {
        Files.write(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"), text.trim().getBytes(StandardCharsets.UTF_8));
    }

    public Playlist getPlaylist(String name) {
        if (!getPlaylistNames().contains(name))
            return null;
        try {
            if (folderExists()) {
                boolean[] shuffle = {false};
                List<String> list = new ArrayList<>();
                Files.readAllLines(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt")).forEach(str ->
                        MylistLoader.Trim(shuffle, list, str));
                if (shuffle[0])
                    shuffle(list);
                return new Playlist(name, list, shuffle[0]);
            } else {
                createFolder();
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    public static class PlaylistLoadError {
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
}
