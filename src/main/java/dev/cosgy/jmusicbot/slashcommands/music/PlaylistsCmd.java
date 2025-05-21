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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.StackTraceUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.cosgy.jmusicbot.slashcommands.DJCommand.checkDJPermission;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlaylistsCmd extends MusicCommand {

    public PlaylistsCmd(Bot bot) {
        super(bot);
        this.name = "playlists";
        this.help = "利用可能な再生リストを表示します";
        this.arguments = "<play|append|delete|make|show>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
        this.beListening = false;
        this.children = new MusicCommand[]{
                new PlayCmd(bot),
                new ListCmd(bot),
                new AppendlistCmd(bot),
                new DeletelistCmd(bot),
                new MakelistCmd(bot),
                new ShowTracksCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event) {
        handlePlaylistsCommand(event.getGuild().getId(), event, null);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        handlePlaylistsCommand(event.getGuild().getId(), null, event);
    }

    private void handlePlaylistsCommand(String guildID, CommandEvent cmdEvent, SlashCommandEvent slashEvent) {
        ensureFoldersExist(guildID);

        List<String> playlists = bot.getPlaylistLoader().getPlaylistNames(guildID);

        String prefix = (cmdEvent != null ? cmdEvent.getClient().getTextualPrefix() : slashEvent.getClient().getTextualPrefix());
        String message;
        if (playlists == null) {
            message = (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " 利用可能な再生リストを読み込めませんでした。";
        } else if (playlists.isEmpty()) {
            message = (cmdEvent != null ? cmdEvent.getClient().getWarning() : slashEvent.getClient().getWarning()) + " 再生リストフォルダにプレイリストがありません。";
        } else {
            StringBuilder builder = new StringBuilder((cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess()) + " 利用可能な再生リスト:\n");
            playlists.forEach(name -> builder.append("`").append(name).append("` "));
            builder.append("\n`").append(prefix).append("playlists play <name>` と入力することで再生リストを再生できます。");
            message = builder.toString();
        }

        reply(cmdEvent, slashEvent, message);
    }

    private void ensureFoldersExist(String guildID) {
        if (!bot.getPlaylistLoader().folderExists()) {
            bot.getPlaylistLoader().createFolder();
        }
        if (!bot.getPlaylistLoader().folderGuildExists(guildID)) {
            bot.getPlaylistLoader().createGuildFolder(guildID);
        }
    }

    private void reply(CommandEvent cmdEvent, SlashCommandEvent slashEvent, String message) {
        if (cmdEvent != null) {
            cmdEvent.reply(message); // CommandEventにはqueue()を付けない
        } else if (slashEvent != null) {
            slashEvent.reply(message).queue(); // SlashCommandEventにはqueue()を付ける
        }
    }

    public class PlayCmd extends MusicCommand {
        public PlayCmd(Bot bot) {
            super(bot);
            this.name = "play";
            this.aliases = new String[]{"play"};
            this.arguments = "<name>";
            this.help = "指定された再生リストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "マイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            playPlaylist(event.getGuild().getId(), event.getArgs(), event, null);
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String playlistName = event.getOption("name").getAsString();
            playPlaylist(event.getGuild().getId(), playlistName, null, event);
        }

        private void playPlaylist(String guildID, String playlistName, CommandEvent cmdEvent, SlashCommandEvent slashEvent) {
            if (playlistName == null || playlistName.isEmpty()) {
                reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " 再生リスト名を指定してください。");
                return;
            }

            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildID, playlistName);
            if (playlist == null) {
                reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " 再生リスト `" + playlistName + "` が見つかりませんでした。");
                return;
            }

            reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess()) + ":calling: 再生リスト **" + playlistName + "** を読み込んでいます... (" + playlist.getItems().size() + " 曲)");

            AudioHandler handler = (AudioHandler) (cmdEvent != null
                    ? cmdEvent.getGuild().getAudioManager().getSendingHandler()
                    : slashEvent.getGuild().getAudioManager().getSendingHandler());

            playlist.loadTracks(bot.getPlayerManager(), track -> handler.addTrack(new QueuedTrack(track, cmdEvent != null ? cmdEvent.getAuthor() : slashEvent.getUser())), () -> {
                StringBuilder builder = new StringBuilder();
                if (playlist.getTracks().isEmpty()) {
                    builder.append((cmdEvent != null ? cmdEvent.getClient().getWarning() : slashEvent.getClient().getWarning())).append(" 楽曲がロードされていません。");
                } else {
                    builder.append((cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess())).append(" **").append(playlist.getTracks().size()).append("** 曲をロードしました。");
                }
                if (!playlist.getErrors().isEmpty()) {
                    builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(error -> builder.append("\n`[").append(error.getIndex() + 1).append("]` **").append(error.getItem()).append("**: ").append(error.getReason()));
                }

                String result = FormatUtil.filter(builder.toString());
                if (result.length() > 2000) {
                    result = result.substring(0, 1994) + " (以下略)";
                }

                reply(cmdEvent, slashEvent, result);
            });
        }
    }

    public class ShowTracksCmd extends MusicCommand {
        public ShowTracksCmd(Bot bot) {
            super(bot);
            this.name = "show";
            this.help = "指定した再生リスト内の曲を表示";
            this.arguments = "<name>";
            this.guildOnly = true;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();
            String playlistName = event.getArgs().trim();

            if (playlistName.isEmpty()) {
                event.reply(event.getClient().getError() + " プレイリスト名を指定してください。");
                return;
            }

            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " 再生リスト `" + playlistName + "` が見つかりませんでした。");
                return;
            }

            if (playlist.getItems().isEmpty()) {
                event.reply(event.getClient().getWarning() + " 再生リスト `" + playlistName + "` に曲がありません。");
                return;
            }

            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 再生リスト `" + playlistName + "` 内の曲:\n");
            for (int i = 0; i < playlist.getItems().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
            }

            if (builder.length() > 2000) {
                builder.setLength(1997); // Discordのメッセージ制限に対応
                builder.append("...");
            }

            event.reply(builder.toString());
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String guildId = event.getGuild().getId();
            String playlistName = event.getOption("name").getAsString();

            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " 再生リスト `" + playlistName + "` が見つかりませんでした。").queue();
                return;
            }

            if (playlist.getItems().isEmpty()) {
                event.reply(event.getClient().getWarning() + " 再生リスト `" + playlistName + "` に曲がありません。").queue();
                return;
            }

            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 再生リスト `" + playlistName + "` 内の曲:\n");
            for (int i = 0; i < playlist.getItems().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
            }

            if (builder.length() > 2000) {
                builder.setLength(1997); // Discordのメッセージ制限に対応
                builder.append("...");
            }

            event.reply(builder.toString()).queue();
        }
    }

    public class MakelistCmd extends MusicCommand {
        public MakelistCmd(Bot bot) {
            super(bot);
            this.name = "make";
            this.aliases = new String[]{"create"};
            this.help = "再生リストを新規作成";
            this.arguments = "<name>";
            this.guildOnly = true;
            this.ownerCommand = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {

            String pName = event.getArgs().replaceAll("\\s+", "_");
            String guildId = event.getGuild().getId();

            if (pName == null || pName.isEmpty()) {
                event.replyError("プレイリストの名前を入力してください。");
            } else if (bot.getPlaylistLoader().getPlaylist(guildId, pName) == null) {
                try {
                    bot.getPlaylistLoader().createPlaylist(guildId, pName);
                    event.reply(event.getClient().getSuccess() + "再生リスト `" + pName + "` を作成しました");
                } catch (IOException e) {
                    if (event.isOwner() || event.getMember().isOwner()) {
                        event.replyError("曲の読み込み中にエラーが発生しました。\n" +
                                "**エラーの内容: " + e.getLocalizedMessage() + "**");
                        StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                        return;
                    }

                    event.reply(event.getClient().getError() + " 再生リストを作成できませんでした。:" + e.getLocalizedMessage());
                }
            } else {
                event.reply(event.getClient().getError() + " 再生リスト `" + pName + "` は既に存在します");
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            if (!checkDJPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            String pname = event.getOption("name").getAsString();
            String guildId = event.getGuild().getId();
            if (pname == null || pname.isEmpty()) {
                event.reply(event.getClient().getError() + "プレイリストの名前を入力してください。").queue();
            } else if (bot.getPlaylistLoader().getPlaylist(guildId, pname) == null) {
                try {
                    bot.getPlaylistLoader().createPlaylist(guildId, pname);
                    event.reply(event.getClient().getSuccess() + "再生リスト `" + pname + "` を作成しました").queue();
                } catch (IOException e) {
                    if (event.getClient().getOwnerId() == event.getMember().getId() || event.getMember().isOwner()) {
                        event.reply(event.getClient().getError() + "曲の読み込み中にエラーが発生しました。\n" +
                                "**エラーの内容: " + e.getLocalizedMessage() + "**").queue();
                        StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                        return;
                    }

                    event.reply(event.getClient().getError() + " 再生リストを作成できませんでした。:" + e.getLocalizedMessage()).queue();
                }
            } else {
                event.reply(event.getClient().getError() + " 再生リスト `" + pname + "` は既に存在します").queue();
            }
        }
    }

    public class DeletelistCmd extends MusicCommand {
        public DeletelistCmd(Bot bot) {
            super(bot);
            this.name = "delete";
            this.aliases = new String[]{"remove"};
            this.help = "既存の再生リストを削除";
            this.arguments = "<name>";
            this.guildOnly = true;
            this.ownerCommand = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {

            String pname = event.getArgs().replaceAll("\\s+", "_");
            String guildid = event.getGuild().getId();
            if (!pname.equals("")) {
                if (bot.getPlaylistLoader().getPlaylist(guildid, pname) == null)
                    event.reply(event.getClient().getError() + " 再生リストは存在しません:`" + pname + "`");
                else {
                    try {
                        bot.getPlaylistLoader().deletePlaylist(guildid, pname);
                        event.reply(event.getClient().getSuccess() + " 再生リストを削除しました:`" + pname + "`");
                    } catch (IOException e) {
                        event.reply(event.getClient().getError() + " 再生リストを削除できませんでした: " + e.getLocalizedMessage());
                    }
                }
            } else {
                event.reply(event.getClient().getError() + "再生リストの名前を含めてください");
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            if (!checkDJPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            String pname = event.getOption("name").getAsString();
            String guildid = event.getGuild().getId();
            if (bot.getPlaylistLoader().getPlaylist(guildid, pname) == null)
                event.reply(event.getClient().getError() + " 再生リストは存在しません:`" + pname + "`").queue();
            else {
                try {
                    bot.getPlaylistLoader().deletePlaylist(guildid, pname);
                    event.reply(event.getClient().getSuccess() + " 再生リストを削除しました:`" + pname + "`").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " 再生リストを削除できませんでした: " + e.getLocalizedMessage()).queue();
                }
            }
        }
    }

    public class AppendlistCmd extends MusicCommand {
        public AppendlistCmd(Bot bot) {
            super(bot);
            this.name = "append";
            this.aliases = new String[]{"add"};
            this.help = "既存の再生リストに曲を追加";
            this.arguments = "<name> <URL>| <URL> | ...";
            this.guildOnly = true;
            this.ownerCommand = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            options.add(new OptionData(OptionType.STRING, "url", "URL", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {

            String[] parts = event.getArgs().split("\\s+", 2);
            String guildid = event.getGuild().getId();
            if (parts.length < 2) {
                event.reply(event.getClient().getError() + " 追加先の再生リスト名とURLを含めてください。");
                return;
            }
            String pname = parts[0];
            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildid, pname);
            if (playlist == null)
                event.reply(event.getClient().getError() + " 再生リストは存在しません:`" + pname + "`");
            else {
                StringBuilder builder = new StringBuilder();
                playlist.getItems().forEach(item -> builder.append("\r\n").append(item));
                String[] urls = parts[1].split("\\|");
                for (String url : urls) {
                    String u = url.trim();
                    if (u.startsWith("<") && u.endsWith(">"))
                        u = u.substring(1, u.length() - 1);
                    builder.append("\r\n").append(u);
                }
                try {
                    bot.getPlaylistLoader().writePlaylist(guildid, pname, builder.toString());
                    event.reply(event.getClient().getSuccess() + urls.length + " 項目を再生リストに追加しました:`" + pname + "`");
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " 再生リストに追加できませんでした: " + e.getLocalizedMessage());
                }
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            if (!checkDJPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }

            String guildid = event.getGuild().getId();
            String pname = event.getOption("name").getAsString();
            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildid, pname);
            if (playlist == null)
                event.reply(event.getClient().getError() + " 再生リストは存在しません:`" + pname + "`").queue();
            else {
                StringBuilder builder = new StringBuilder();
                playlist.getItems().forEach(item -> builder.append("\r\n").append(item));
                String[] urls = event.getOption("url").getAsString().split("\\|");
                for (String url : urls) {
                    String u = url.trim();
                    if (u.startsWith("<") && u.endsWith(">"))
                        u = u.substring(1, u.length() - 1);
                    builder.append("\r\n").append(u);
                }
                try {
                    bot.getPlaylistLoader().writePlaylist(guildid, pname, builder.toString());
                    event.reply(event.getClient().getSuccess() + urls.length + " 項目を再生リストに追加しました:`" + pname + "`").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " 再生リストに追加できませんでした: " + e.getLocalizedMessage()).queue();
                }
            }
        }
    }

    public class ListCmd extends MusicCommand {
        public ListCmd(Bot bot) {
            super(bot);
            this.name = "all";
            this.aliases = new String[]{"available", "list"};
            this.help = "利用可能なすべての再生リストを表示";
            this.guildOnly = true;
            this.ownerCommand = false;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();

            if (!bot.getPlaylistLoader().folderGuildExists(guildId))
                bot.getPlaylistLoader().createGuildFolder(guildId);
            if (!bot.getPlaylistLoader().folderGuildExists(guildId)) {
                event.reply(event.getClient().getWarning() + " 再生リストフォルダが存在しないため作成できませんでした。");
                return;
            }
            List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildId);
            if (list == null)
                event.reply(event.getClient().getError() + " 利用可能な再生リストを読み込めませんでした。");
            else if (list.isEmpty())
                event.reply(event.getClient().getWarning() + " 再生リストフォルダに再生リストがありません。");
            else {
                StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 利用可能な再生リスト:\n");
                list.forEach(str -> builder.append("`").append(str).append("` "));
                event.reply(builder.toString());
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            if (!checkDJPermission(event.getClient(), event)) {
                event.reply(event.getClient().getWarning() + "権限がないため実行できません。").queue();
                return;
            }
            String guildId = event.getGuild().getId();
            if (!bot.getPlaylistLoader().folderGuildExists(guildId))
                bot.getPlaylistLoader().createGuildFolder(guildId);
            if (!bot.getPlaylistLoader().folderGuildExists(guildId)) {
                event.reply(event.getClient().getWarning() + " 再生リストフォルダが存在しないため作成できませんでした。").queue();
                return;
            }
            List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildId);
            if (list == null)
                event.reply(event.getClient().getError() + " 利用可能な再生リストを読み込めませんでした。").queue();
            else if (list.isEmpty())
                event.reply(event.getClient().getWarning() + " 再生リストフォルダに再生リストがありません。").queue();
            else {
                StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 利用可能な再生リスト:\n");
                list.forEach(str -> builder.append("`").append(str).append("` "));
                event.reply(builder.toString()).queue();
            }
        }
    }
}