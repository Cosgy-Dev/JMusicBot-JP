/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * スラッシュコマンド実行の共通基底クラスです。
 * 権限チェック、クールダウン、コマンドデータ生成を担当します。
 */
public abstract class SlashCommand extends Command
{
    
    protected Map<DiscordLocale, String> nameLocalization = new HashMap<>();

    
    protected Map<DiscordLocale, String> descriptionLocalization = new HashMap<>();

    
    @Deprecated
    protected String requiredRole = null;

    
    protected SlashCommand[] children = new SlashCommand[0];

    
    protected SubcommandGroupData subcommandGroup = null;

    
    protected List<OptionData> options = new ArrayList<>();

    
    protected CommandClient client;

    
    protected abstract void execute(SlashCommandEvent event);

    
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

    
    @Override
    protected void execute(CommandEvent event) {}

    
    public final void run(SlashCommandEvent event)
    {
        // クライアント参照を設定
        this.client = event.getClient();

        // オーナー権限判定
        if(ownerCommand && !(isOwner(event, client)))
        {
            terminate(event, "Only an owner may run this command. Sorry.", client);
            return;
        }

        // チャンネル利用可否判定
        try {
            if(!isAllowed(event.getTextChannel()))
            {
                terminate(event, "That command cannot be used in this channel!", client);
                return;
            }
        } catch (Exception e) {
            // テキストチャンネル以外は判定対象外
        }

        // 必要ロール判定
        if(requiredRole!=null)
            if(!(event.getChannelType() == ChannelType.TEXT) || event.getMember().getRoles().stream().noneMatch(r -> r.getName().equalsIgnoreCase(requiredRole)))
            {
                terminate(event, client.getError()+" You must have a role called `"+requiredRole+"` to use that!", client);
                return;
            }

        // 実行可能条件判定
        if(event.getChannelType() != ChannelType.PRIVATE)
        {
            // ユーザー権限判定
            for(Permission p: userPermissions)
            {
                // サーバー内実行時は通常 null にならない
                if(event.getMember() == null)
                    continue;

                if(p.isChannel())
                {
                    if(!event.getMember().hasPermission(event.getGuildChannel(), p))
                    {
                        terminate(event, String.format(userMissingPermMessage, client.getError(), p.getName(), "channel"), client);
                        return;
                    }
                }
                else
                {
                    if(!event.getMember().hasPermission(p))
                    {
                        terminate(event, String.format(userMissingPermMessage, client.getError(), p.getName(), "server"), client);
                        return;
                    }
                }
            }

            // Bot権限判定
            for (Permission p : botPermissions) {
                // 動作に必須でない権限は判定対象から除外
                if (p == Permission.VIEW_CHANNEL || p == Permission.MESSAGE_EMBED_LINKS) {
                    continue;
                }

                // ギルドとBotメンバー情報を取得
                Member selfMember = event.getGuild() != null ? event.getGuild().getSelfMember() : null;

                if (p.isChannel()) {
                    // チャンネル単位権限を判定
                    GuildVoiceState voiceState = event.getMember().getVoiceState();
                    AudioChannelUnion channel = voiceState != null ? voiceState.getChannel() : null;

                    if (channel == null || !channel.getType().isAudio()) {
                        terminate(event, client.getError() + " You must be in a voice channel to use that!", client);
                        return;
                    }

                    // ボイスチャンネル内のBot権限を判定
                    if (!selfMember.hasPermission(channel, p)) {
                        terminate(event, String.format(botMissingPermMessage, client.getError(), p.getName(), "voice channel"), client);
                        return;
                    }
                } else {
                    // ギルド全体権限を判定
                    if (!selfMember.hasPermission(p)) {
                        terminate(event, String.format(botMissingPermMessage, client.getError(), p.getName(), "server"), client);
                        return;
                    }
                }
            }

            // NSFW判定
            if (nsfwOnly && event.getChannelType() == ChannelType.TEXT && !event.getTextChannel().isNSFW())
            {
                terminate(event, "This command may only be used in NSFW text channels!", client);
                return;
            }
        }
        else if(guildOnly)
        {
            terminate(event, client.getError()+" This command cannot be used in direct messages", client);
            return;
        }

        // クールダウン判定（オーナーは除外）
        if(cooldown>0 && !(isOwner(event, client)))
        {
            String key = getCooldownKey(event);
            int remaining = client.getRemainingCooldown(key);
            if(remaining>0)
            {
                terminate(event, getCooldownError(event, remaining, client), client);
                return;
            }
            else client.applyCooldown(key, cooldown);
        }

        // 本処理実行
        try {
            execute(event);
        } catch(Throwable t) {
            if(client.getListener() != null)
            {
                client.getListener().onSlashCommandException(event, this, t);
                return;
            }
            // リスナー未設定時は例外を再送出
            throw t;
        }

        if(client.getListener() != null)
            client.getListener().onCompletedSlashCommand(event, this);
    }

    
    public boolean isOwner(SlashCommandEvent event, CommandClient client)
    {
        if(event.getUser().getId().equals(client.getOwnerId()))
            return true;
        if(client.getCoOwnerIds()==null)
            return false;
        for(String id : client.getCoOwnerIds())
            if(id.equals(event.getUser().getId()))
                return true;
        return false;
    }

    
    @Deprecated
    @ForRemoval(deadline = "2.0.0")
    public CommandClient getClient()
    {
        return client;
    }

    
    public SubcommandGroupData getSubcommandGroup()
    {
        return subcommandGroup;
    }

    
    public List<OptionData> getOptions()
    {
        return options;
    }

    
    public CommandData buildCommandData()
    {
        // コマンド定義を構築
        SlashCommandData data = Commands.slash(getName(), getHelp());
        if (!getOptions().isEmpty())
        {
            data.addOptions(getOptions());
        }

        // 名前ローカライズを反映
        if (!getNameLocalization().isEmpty())
        {
            // ローカライズ設定
            data.setNameLocalizations(getNameLocalization());
        }
        // 説明ローカライズを反映
        if (!getDescriptionLocalization().isEmpty())
        {
            // ローカライズ設定
            data.setDescriptionLocalizations(getDescriptionLocalization());
        }

        // サブコマンドを反映
        if (children.length != 0)
        {
            // サブコマンドグループ集約用マップ
            Map<String, SubcommandGroupData> groupData = new HashMap<>();
            for (SlashCommand child : children)
            {
                // サブコマンド定義を作成
                SubcommandData subcommandData = new SubcommandData(child.getName(), child.getHelp());
                // オプションを反映
                if (!child.getOptions().isEmpty())
                {
                    subcommandData.addOptions(child.getOptions());
                }

                // 子コマンド名のローカライズを反映
                if (!child.getNameLocalization().isEmpty())
                {
                    // ローカライズ設定
                    subcommandData.setNameLocalizations(child.getNameLocalization());
                }
                // 子コマンド説明のローカライズを反映
                if (!child.getDescriptionLocalization().isEmpty())
                {
                    // ローカライズ設定
                    subcommandData.setDescriptionLocalizations(child.getDescriptionLocalization());
                }

                // サブコマンドグループがある場合
                if (child.getSubcommandGroup() != null)
                {
                    SubcommandGroupData group = child.getSubcommandGroup();

                    SubcommandGroupData newData = groupData.getOrDefault(group.getName(), group)
                            .addSubcommands(subcommandData);

                    groupData.put(group.getName(), newData);
                }
                // グループ未指定は直接追加
                else
                {
                    data.addSubcommands(subcommandData);
                }
            }
            if (!groupData.isEmpty())
                data.addSubcommandGroups(groupData.values());
        }

        if (this.getUserPermissions() == null)
            data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
        else
            data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));

        //data.setGuildOnly(this.guildOnly);

        return data;
    }

    
    public SlashCommand[] getChildren()
    {
        return children;
    }

    private void terminate(SlashCommandEvent event, String message, CommandClient client)
    {
        if(message!=null)
            event.reply(message).setEphemeral(true).queue();
        if(client.getListener()!=null)
            client.getListener().onTerminatedSlashCommand(event, this);
    }

    
    public String getCooldownKey(SlashCommandEvent event)
    {
        switch (cooldownScope)
        {
            case USER:         return cooldownScope.genKey(name,event.getUser().getIdLong());
            case USER_GUILD:   return event.getGuild()!=null ? cooldownScope.genKey(name,event.getUser().getIdLong(),event.getGuild().getIdLong()) :
                    CooldownScope.USER_CHANNEL.genKey(name,event.getUser().getIdLong(), event.getChannel().getIdLong());
            case USER_CHANNEL: return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getChannel().getIdLong());
            case GUILD:        return event.getGuild()!=null ? cooldownScope.genKey(name,event.getGuild().getIdLong()) :
                    CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong());
            case CHANNEL:      return cooldownScope.genKey(name,event.getChannel().getIdLong());
            case SHARD:
                event.getJDA().getShardInfo();
                return cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
            case USER_SHARD:
                event.getJDA().getShardInfo();
                return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getJDA().getShardInfo().getShardId());
            case GLOBAL:       return cooldownScope.genKey(name, 0);
            default:           return "";
        }
    }

    
    public String getCooldownError(SlashCommandEvent event, int remaining, CommandClient client)
    {
        if(remaining<=0)
            return null;
        String front = client.getWarning()+" That command is on cooldown for "+remaining+" more seconds";
        if(cooldownScope.equals(CooldownScope.USER))
            return front+"!";
        else if(cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
            return front+" "+ CooldownScope.USER_CHANNEL.errorSpecification+"!";
        else if(cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
            return front+" "+ CooldownScope.CHANNEL.errorSpecification+"!";
        else
            return front+" "+cooldownScope.errorSpecification+"!";
    }

    
    public Map<DiscordLocale, String> getNameLocalization() {
        return nameLocalization;
    }

    
    public Map<DiscordLocale, String> getDescriptionLocalization() {
        return descriptionLocalization;
    }
}
