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
package dev.cosgy.jmusicbot.slashcommands;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public abstract class MusicCommand extends SlashCommand {
    protected final Bot bot;
    protected boolean bePlaying;
    protected boolean beListening;
    Logger log = LoggerFactory.getLogger("MusicCommand");

    public MusicCommand(Bot bot) {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Music");
    }

    @Override
    protected void execute(SlashCommandEvent event) {

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        TextChannel channel = settings.getTextChannel(event.getGuild());

        bot.getPlayerManager().setUpHandler(event.getGuild());
        if (bePlaying && !((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).isMusicPlaying(event.getJDA())) {
            event.reply(event.getClient().getError() + "コマンドを使用するには、再生中である必要があります。").queue();
            return;
        }
        if (beListening) {
            AudioChannelUnion current = event.getGuild().getSelfMember().getVoiceState().getChannel();

            if (current == null)
                current = (AudioChannelUnion) settings.getVoiceChannel(event.getGuild());
            GuildVoiceState userState = event.getMember().getVoiceState();

            if (!userState.inAudioChannel() || userState.isDeafened() || (current != null && !userState.getChannel().equals(current))) {
                event.reply(event.getClient().getError() + String.format("このコマンドを使用するには、%sに参加している必要があります！", (current == null ? "音声チャンネル" : "**" + current.getAsMention() + "**"))).queue();
                return;
            }
            if (!event.getGuild().getSelfMember().getVoiceState().inAudioChannel()) {
                try {
                    event.getGuild().getAudioManager().openAudioConnection(userState.getChannel());
                    //event.getGuild().getAudioManager().setSelfDeafened(true);
                    event.getGuild().getAudioManager().setSelfMuted(false);
                } catch (PermissionException ex) {
                    event.reply(event.getClient().getError() + String.format("**%s**に接続できません!", userState.getChannel().getAsMention())).queue();
                    return;
                }
                if (userState.getChannel().getType() == ChannelType.STAGE) {
                    event.getTextChannel().sendMessage(event.getClient().getWarning() + String.format("ステージチャンネルに参加しました。ステージチャンネルで%sを使用するには手動でスピーカーに招待する必要があります。", event.getGuild().getSelfMember().getNickname())).queue();
                }
            }
        }

        doCommand(event);
    }

    @Override
    protected void execute(CommandEvent event) {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        TextChannel channel = settings.getTextChannel(event.getGuild());

        if (channel != null && !event.getTextChannel().equals(channel)) {
            try {
                event.getMessage().delete().queue();
            } catch (PermissionException ignore) {
            }
            event.replyInDm(event.getClient().getError() + String.format("コマンドは%sでのみ実行できます", channel.getAsMention()));
            return;
        }
        bot.getPlayerManager().setUpHandler(event.getGuild()); // no point constantly checking for this later

        if (bePlaying && !((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).isMusicPlaying(event.getJDA())) {
            event.reply(event.getClient().getError() + "コマンドを使用するには、再生中である必要があります。");
            return;
        }
        if (beListening) {
            AudioChannelUnion current = event.getGuild().getSelfMember().getVoiceState().getChannel();

            if (current == null)
                current = (AudioChannelUnion) settings.getVoiceChannel(event.getGuild());
            GuildVoiceState userState = event.getMember().getVoiceState();
            if (!userState.inAudioChannel() || userState.isDeafened() || (current != null && !userState.getChannel().equals(current))) {
                event.replyError(String.format("このコマンドを使用するには、%sに参加している必要があります！", (current == null ? "音声チャンネル" : "**" + current.getName() + "**")));
                return;
            }
            if (!event.getGuild().getSelfMember().getVoiceState().inAudioChannel()) {
                try {
                    event.getGuild().getAudioManager().openAudioConnection(userState.getChannel());
                } catch (PermissionException ex) {
                    event.reply(event.getClient().getError() + String.format("**%s**に接続できません!", userState.getChannel().getName()));
                    return;
                }
                if (userState.getChannel().getType() == ChannelType.STAGE) {
                    event.getTextChannel().sendMessage(event.getClient().getWarning() + String.format("ステージチャンネルに参加しました。ステージチャンネルで%sを使用するには手動でスピーカーに招待する必要があります。", event.getGuild().getSelfMember().getNickname())).queue();
                }
            }
        }

        doCommand(event);
    }

    public abstract void doCommand(CommandEvent event);

    public abstract void doCommand(SlashCommandEvent event);
}
