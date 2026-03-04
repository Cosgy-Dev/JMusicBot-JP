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
package dev.cosgy.jmusicbot.slashcommands.owner;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetgameCmd extends OwnerCommand {
    public SetgameCmd(Bot bot) {
        this.name = "setgame";
        this.help = "ボットがプレイしているゲームを設定します";
        this.arguments = "[action] [game]";
        this.aliases = bot.getConfig().getAliases(this.name);
        //this.guildOnly = false;
        this.children = new OwnerCommand[]{
                new PlayingCmd(),
                new SetlistenCmd(),
                new SetstreamCmd(),
                new SetwatchCmd(),
                new SetCompetingCmd(),
                new NoneCmd()
        };
    }

    @Override
    protected void execute(SlashCommandEvent slashCommandEvent) {
    }

    @Override
    protected void execute(CommandEvent event) {
        String title = event.getArgs().toLowerCase().startsWith("playing") ? event.getArgs().substring(7).trim() : event.getArgs();
        try {
            event.getJDA().getPresence().setActivity(title.isEmpty() ? null : Activity.playing(title));
            event.reply(event.getClient().getSuccess() + " **" + event.getSelfUser().getName()
                    + "** は " + (title.isEmpty() ? "何もなくなりました。" : "現在、`" + title + "`を再生中です。"));
        } catch (Exception e) {
            event.reply(event.getClient().getError() + " ステータスを設定できませんでした。");
        }
    }

    private class NoneCmd extends OwnerCommand {
        private NoneCmd() {
            this.name = "none";
            this.aliases = new String[]{"none"};
            this.help = "ステータスをリセットします。";
            //this.guildOnly = false;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.getJDA().getPresence().setActivity(null);
            event.reply("ステータスをリセットしました。").queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            event.getJDA().getPresence().setActivity(null);
            event.reply("ステータスをリセットしました。");
        }
    }

    private class PlayingCmd extends OwnerCommand {
        private PlayingCmd() {
            this.name = "playing";
            this.aliases = new String[]{"twitch", "streaming"};
            this.help = "ボットがプレイしているゲームを設定します。";
            this.arguments = "<title>";
            //this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "ゲームのタイトル", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.playing(title));
                event.reply(event.getClient().getSuccess() + " **" + event.getJDA().getSelfUser().getName()
                        + "** は " + "現在、`" + title + "`をプレイ中です。");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ステータスを設定できませんでした。").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
        }
    }

    private class SetstreamCmd extends OwnerCommand {
        private SetstreamCmd() {
            this.name = "stream";
            this.aliases = new String[]{"twitch", "streaming"};
            this.help = "ボットがプレイしているゲームをストリームに設定します。";
            this.arguments = "<username> <game>";
            //this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "user", "ユーザー名", true));
            options.add(new OptionData(OptionType.STRING, "game", "ゲームのタイトル", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            try {
                event.getJDA().getPresence().setActivity(Activity.streaming(event.getOption("game").getAsString(), "https://twitch.tv/" + event.getOption("user").getAsString()));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName()
                        + "** は、現在`" + event.getOption("game").getAsString() + "`をストリーム中です。").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            String[] parts = event.getArgs().split("\\s+", 2);
            if (parts.length < 2) {
                event.replyError("ユーザー名と'ストリーミングするゲーム'の名前を入力してください");
                return;
            }
            try {
                event.getJDA().getPresence().setActivity(Activity.streaming(parts[1], "https://twitch.tv/" + parts[0]));
                event.replySuccess("**" + event.getSelfUser().getName()
                        + "** は、現在`" + parts[1] + "`をストリーム中です。");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。");
            }
        }
    }

    private class SetlistenCmd extends OwnerCommand {
        private SetlistenCmd() {
            this.name = "listen";
            this.aliases = new String[]{"listening"};
            this.help = "ボットが聞いているゲームを設定します";
            this.arguments = "<title>";
            //this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "タイトル", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** は、現在`" + title + "`を聴いています。").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("聴いているタイトルを含めてください！");
                return;
            }
            String title = event.getArgs().toLowerCase().startsWith("to") ? event.getArgs().substring(2).trim() : event.getArgs();
            try {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** は、現在`" + title + "`を聴いています。");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。");
            }
        }
    }

    private class SetwatchCmd extends OwnerCommand {
        private SetwatchCmd() {
            this.name = "watch";
            this.aliases = new String[]{"watching"};
            this.help = "ボットが見ているゲームを設定します";
            this.arguments = "<title>";
            //this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "タイトル", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** は、現在`" + title + "`を見ています。").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("見ているタイトルを入力してください。");
                return;
            }
            String title = event.getArgs();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** は、現在`" + title + "`を見ています。");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。");
            }
        }
    }

    private class SetCompetingCmd extends OwnerCommand {
        private SetCompetingCmd() {
            this.name = "competing";
            this.help = "ボットが参戦しているゲームを設定します";
            this.arguments = "<title>";
            //this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "ゲームタイトル", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.competing(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** は、現在`" + title + "`を競い合っています。").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("参戦しているタイトルを入力してください。");
                return;
            }
            String title = event.getArgs();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** は、現在`" + title + "`に参戦してています。");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " ゲームを設定できませんでした。");
            }
        }
    }
}
