/*
 * Copyright 2016 John Grosh (jagrosh).
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

import com.github.lalyos.jfiglet.FigletFont;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import dev.cosgy.agent.GensokyoInfoAgent;
import dev.cosgy.jmusicbot.slashcommands.admin.*;
import dev.cosgy.jmusicbot.slashcommands.dj.*;
import dev.cosgy.jmusicbot.slashcommands.general.*;
import dev.cosgy.jmusicbot.slashcommands.listeners.CommandAudit;
import dev.cosgy.jmusicbot.slashcommands.listeners.buttons.QueueButtonListener;
import dev.cosgy.jmusicbot.slashcommands.music.*;
import dev.cosgy.jmusicbot.slashcommands.owner.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author John Grosh (jagrosh)
 */
public class JMusicBot {
    public final static String PLAY_EMOJI = "▶"; // ▶
    public final static String PAUSE_EMOJI = "⏸"; // ⏸
    public final static String STOP_EMOJI = "⏹"; // ⏹
    public final static Permission[] RECOMMENDED_PERMS = {Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EXT_EMOJI,
            Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.NICKNAME_CHANGE, Permission.VOICE_SET_STATUS};
    public final static GatewayIntent[] INTENTS = {GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT}; // , GatewayIntent.MESSAGE_CONTENT
    public static boolean CHECK_UPDATE = true;
    public static boolean COMMAND_AUDIT_ENABLED = false;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // startup log
        Logger log = getLogger("Startup");

        try {
            System.out.println(FigletFont.convertOneLine("JMusicBot v" + OtherUtil.getCurrentVersion()) + "\n" + "by Cosgy Dev");
        } catch (IOException e) {
            System.out.println("JMusicBot v" + OtherUtil.getCurrentVersion() + "\nby Cosgy Dev");
        }


        // create prompt to handle startup
        Prompt prompt = new Prompt("JMusicBot", "noguiモードに切り替えます。  -Dnogui=trueフラグを含めると、手動でnoguiモードで起動できます。");

        // check deprecated nogui mode (new way of setting it is -Dnogui=true)
        for (String arg : args)
            if ("-nogui".equalsIgnoreCase(arg)) {
                prompt.alert(Prompt.Level.WARNING, "GUI", "-noguiフラグは廃止予定です。 "
                        + "jarの名前の前に-Dnogui = trueフラグを使用してください。 例：java -jar -Dnogui=true JMusicBot.jar");
            } else if ("-nocheckupdates".equalsIgnoreCase(arg)) {
                CHECK_UPDATE = false;
                log.info("アップデートチェックを無効にしました");
            } else if ("-auditcommands".equalsIgnoreCase(arg)) {
                COMMAND_AUDIT_ENABLED = true;
                log.info("実行されたコマンドの記録を有効にしました。");
            }

        // get and check latest version
        String version = OtherUtil.checkVersion(prompt);

        if (!System.getProperty("java.vm.name").contains("64"))
            prompt.alert(Prompt.Level.WARNING, "Java Version", "サポートされていないJavaバージョンを使用しています。64ビット版のJavaを使用してください。");

        try {
            Process checkPython3 = Runtime.getRuntime().exec("python3 --version");
            int python3ExitCode = checkPython3.waitFor();

            if (python3ExitCode != 0) {
                log.info("Python3 is not installed. Checking for python.");
                Process checkPython = Runtime.getRuntime().exec("python --version");
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkPython.getInputStream()));
                String pythonVersion = reader.readLine();
                int pythonExitCode = checkPython.waitFor();

                if (pythonExitCode == 0 && pythonVersion != null && pythonVersion.startsWith("Python 3")) {
                    log.info("Python is version 3.x.");
                } else {
                    prompt.alert(Prompt.Level.WARNING, "Python", "Python (バージョン3.x)がインストールされていません。Python 3をインストールしてください。");
                }
            } else {
                log.info("Python3 is installed.");
            }
        } catch (Exception e) {
            prompt.alert(Prompt.Level.WARNING, "Python", "Pythonのバージョン確認中にエラーが発生しました。Python3がインストールされているか確認してください。");
        }



        // load config
        BotConfig config = new BotConfig(prompt);
        config.load();

        if (!config.isValid())
            return;


        if (config.getAuditCommands()) {
            COMMAND_AUDIT_ENABLED = true;
            log.info("実行されたコマンドの記録を有効にしました。");
        }

        // set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();
        Bot bot = new Bot(waiter, config, settings);
        Bot.INSTANCE = bot;

        AboutCommand aboutCommand = new AboutCommand(Color.BLUE.brighter(),
                "[JMusicBot JP(v" + version + ")](https://github.com/Cosgy-Dev/MusicBot-JP-java)",
                new String[]{"高品質の音楽再生", "FairQueue™テクノロジー", "自分で簡単にホスト"},
                RECOMMENDED_PERMS);
        aboutCommand.setIsAuthor(false);
        aboutCommand.setReplacementCharacter("\uD83C\uDFB6"); // 🎶

        // set up the command client
        CommandClientBuilder cb = new CommandClientBuilder()
                .setPrefix(config.getPrefix())
                .setAlternativePrefix(config.getAltPrefix())
                .setOwnerId(Long.toString(config.getOwnerId()))
                .setEmojis(config.getSuccess(), config.getWarning(), config.getError())
                .useHelpBuilder(false)
                .setLinkedCacheSize(200)
                .setGuildSettingsManager(settings)
                .setListener(new CommandAudit());

        if (config.isOfficialInvite()) {
            cb.setServerInvite("https://discord.gg/MjNfC6TK2y");
        }

        // スラッシュコマンドの実装
        List<SlashCommand> slashCommandList = new ArrayList<>() {{
            add(new HelpCmd(bot));
            add(aboutCommand);
            if (config.isUseInviteCommand()) {
                add(new InviteCommand());
            }
            add(new PingCommand());
            add(new SettingsCmd(bot));
            //if (config.getCosgyDevHost()) add(new InfoCommand(bot));
            // General
            add(new ServerInfo(bot));
            //add(new UserInfo());
            add(new CashCmd(bot));
            // Music
            add(new LyricsCmd(bot));
            add(new NowplayingCmd(bot));
            add(new PlayCmd(bot));
            add(new SpotifyCmd(bot));
            add(new PlaylistsCmd(bot));
            add(new MylistCmd(bot));
            //add(new QueueCmd(bot));
            add(new QueueCmd(bot));
            add(new RemoveCmd(bot));
            add(new SearchCmd(bot));
            add(new SCSearchCmd(bot));
            add(new SeekCmd(bot));
            add(new NicoSearchCmd(bot));
            add(new ShuffleCmd(bot));
            add(new SkipCmd(bot));
            add(new VolumeCmd(bot));
            // DJ
            add(new ForceRemoveCmd(bot));
            add(new ForceskipCmd(bot));
            add(new NextCmd(bot));
            add(new MoveTrackCmd(bot));
            add(new PauseCmd(bot));
            add(new PlaynextCmd(bot));
            //add(new RepeatCmd(bot));
            add(new RepeatCmd(bot));
            add(new SkipToCmd(bot));
            add(new ForceToEnd(bot));
            add(new StopCmd(bot));
            //add(new VolumeCmd(bot));
            // Admin
            //add(new ActivateCmd(bot));
            add(new PrefixCmd(bot));
            add(new SetdjCmd(bot));
            add(new SkipratioCmd(bot));
            add(new SettcCmd(bot));
            add(new SetvcCmd(bot));
            add(new SetvcStatusCmd(bot));
            add(new AutoplaylistCmd(bot));
            add(new ServerListCmd(bot));
            // Owner
            add(new DebugCmd(bot));
            add(new SetavatarCmd(bot));
            add(new SetgameCmd(bot));
            add(new SetnameCmd(bot));
            add(new SetstatusCmd(bot));
            add(new PublistCmd(bot));
            add(new ShutdownCmd(bot));
            //add(new LeaveCmd(bot));
        }};

        cb.addCommands(slashCommandList.toArray(new Command[0]));
        cb.addSlashCommands(slashCommandList.toArray(new SlashCommand[0]));

        if (config.useEval())
            cb.addCommand(new EvalCmd(bot));
        boolean nogame = false;
        if (config.getStatus() != OnlineStatus.UNKNOWN)
            cb.setStatus(config.getStatus());
        if (config.getGame() == null)
            cb.setActivity(Activity.playing(config.getPrefix() + config.getHelp() + "でヘルプを確認"));
        else if (config.getGame().getName().toLowerCase().matches("(none|なし)")) {
            cb.setActivity(null);
            nogame = true;
        } else
            cb.setActivity(config.getGame());
        if (!prompt.isNoGUI()) {
            try {
                GUI gui = new GUI(bot);
                bot.setGUI(gui);
                gui.init();
            } catch (Exception e) {
                log.error("GUIを開くことができませんでした。次の要因が考えられます:\n"
                        + "サーバー上で実行している\n"
                        + "画面がない環境下で実行している\n"
                        + "このエラーを非表示にするには、 -Dnogui=true フラグを使用してGUIなしモードで実行してください。");
            }
        }

        log.info("{} から設定を読み込みました", config.getConfigLocation());

        // attempt to log in and start
        try {
            JDA jda = JDABuilder.create(config.getToken(), Arrays.asList(INTENTS))
                    .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ONLINE_STATUS)
                    .setActivity(nogame ? null : Activity.playing("ロード中..."))
                    .setStatus(config.getStatus() == OnlineStatus.INVISIBLE || config.getStatus() == OnlineStatus.OFFLINE
                            ? OnlineStatus.INVISIBLE : OnlineStatus.DO_NOT_DISTURB)
                    .addEventListeners(cb.build(), waiter, new Listener(bot),new QueueButtonListener(bot))
                    .setBulkDeleteSplittingEnabled(true)
                    .build();
            bot.setJDA(jda);

            String unsupportedReason = OtherUtil.getUnsupportedBotReason(jda);
            if (unsupportedReason != null)
            {
                prompt.alert(Prompt.Level.ERROR, "JMusicBot", "このDiscordボットユーザーではJMusicBotを実行できません: " + unsupportedReason);
                try{ Thread.sleep(5000);}catch(InterruptedException ignored){} // this is awful but until we have a better way...
                jda.shutdown();
                System.exit(1);
            }

            // other check that will just be a warning now but may be required in the future
            // check if the user has changed the prefix and provide info about the
            // message content intent
            /*if(!"@mention".equals(config.getPrefix()))
            {
                prompt.alert(Prompt.Level.INFO, "JMusicBot", "現在、カスタム接頭辞が設定されています。 "
                        + "カスタム接頭辞が機能しない場合は、「MESSAGE CONTENT INTENT」が有効になっていることを確認してください。"
                        + "https://discord.com/developers/applications/" + jda.getSelfUser().getId() + "/bot");
            }*/

        }
        catch (InvalidTokenException ex) {
            //ex.getCause().getMessage();
            prompt.alert(Prompt.Level.ERROR, "JMusicBot", ex + "\n" +
                    "正しい設定ファイルを編集していることを確認してください。Botトークンでのログインに失敗しました。" +
                    "正しいBotトークンを入力してください。(CLIENT SECRET ではありません!)\n" +
                    "設定ファイルの場所: " + config.getConfigLocation());
            System.exit(1);

        } catch (IllegalArgumentException ex) {

            prompt.alert(Prompt.Level.ERROR, "JMusicBot", "設定の一部が無効です:" + ex + "\n" +
                    "設定ファイルの場所: " + config.getConfigLocation());
            System.exit(1);
        }

        new GensokyoInfoAgent().start();
    }
}
