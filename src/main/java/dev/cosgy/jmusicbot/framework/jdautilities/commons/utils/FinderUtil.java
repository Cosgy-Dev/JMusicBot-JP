package dev.cosgy.jmusicbot.framework.jdautilities.commons.utils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.Guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FinderUtil {
    private static final Pattern ID = Pattern.compile("(\\d{15,21})");

    private FinderUtil() {}

    public static List<Member> findMembers(String query, Guild guild) {
        List<Member> out = new ArrayList<>();
        if (guild == null || query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        Matcher m = ID.matcher(q);
        if (m.find()) {
            Member byId = guild.getMemberById(m.group(1));
            if (byId != null) out.add(byId);
            return out;
        }
        for (Member member : guild.getMembers()) {
            String name = member.getUser().getName().toLowerCase(Locale.ROOT);
            String nick = member.getNickname() == null ? "" : member.getNickname().toLowerCase(Locale.ROOT);
            if (name.contains(q) || nick.contains(q)) out.add(member);
        }
        return out;
    }

    public static List<Role> findRoles(String query, Guild guild) {
        List<Role> out = new ArrayList<>();
        if (guild == null || query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        Matcher m = ID.matcher(q);
        if (m.find()) {
            Role byId = guild.getRoleById(m.group(1));
            if (byId != null) out.add(byId);
            return out;
        }
        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(role);
        }
        return out;
    }

    public static List<TextChannel> findTextChannels(String query, Guild guild) {
        List<TextChannel> out = new ArrayList<>();
        if (guild == null || query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        Matcher m = ID.matcher(q);
        if (m.find()) {
            TextChannel byId = guild.getTextChannelById(m.group(1));
            if (byId != null) out.add(byId);
            return out;
        }
        for (TextChannel ch : guild.getTextChannels()) {
            if (ch.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(ch);
        }
        return out;
    }

    public static List<VoiceChannel> findVoiceChannels(String query, Guild guild) {
        List<VoiceChannel> out = new ArrayList<>();
        if (guild == null || query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        Matcher m = ID.matcher(q);
        if (m.find()) {
            VoiceChannel byId = guild.getVoiceChannelById(m.group(1));
            if (byId != null) out.add(byId);
            return out;
        }
        for (VoiceChannel ch : guild.getVoiceChannels()) {
            if (ch.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(ch);
        }
        return out;
    }
}
