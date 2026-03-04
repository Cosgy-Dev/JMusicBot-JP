package dev.cosgy.jmusicbot.framework.jdautilities.command;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum CooldownScope {
    USER("ユーザー単位"),
    USER_GUILD("ユーザー・サーバー単位"),
    USER_CHANNEL("ユーザー・チャンネル単位"),
    GUILD("サーバー単位"),
    CHANNEL("チャンネル単位"),
    SHARD("シャード単位"),
    USER_SHARD("ユーザー・シャード単位"),
    GLOBAL("全体");

    public final String errorSpecification;

    CooldownScope(String errorSpecification) {
        this.errorSpecification = errorSpecification;
    }

    public String genKey(String name, long... ids) {
        String suffix = Arrays.stream(ids)
                .mapToObj(Long::toString)
                .collect(Collectors.joining("_"));
        return name + ':' + suffix;
    }
}
