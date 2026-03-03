package dev.cosgy.jmusicbot.framework.jdautilities.doc.standard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface CommandInfo {
    String[] name() default {};

    String description() default "";
}
