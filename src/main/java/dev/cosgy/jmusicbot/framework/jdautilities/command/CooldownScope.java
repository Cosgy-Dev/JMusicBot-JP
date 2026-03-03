package dev.cosgy.jmusicbot.framework.jdautilities.command;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum CooldownScope {
    USER("per user"),
    USER_GUILD("per user per guild"),
    USER_CHANNEL("per user per channel"),
    GUILD("per guild"),
    CHANNEL("per channel"),
    SHARD("per shard"),
    USER_SHARD("per user per shard"),
    GLOBAL("globally");

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
