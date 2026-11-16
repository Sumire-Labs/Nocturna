package org.embeddedt.embeddium.impl.extra.compat.config;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.extra.compat.config.option.ControlValueFormatter;

public final class ControlValueFormatterImpls {
    public static ControlValueFormatter percentage() {
        return v -> Component.literal(v + "%");
    }

    public static ControlValueFormatter number() {
        return v -> Component.literal(Integer.toString(v));
    }

    public static ControlValueFormatter multiplier() {
        return v -> Component.literal(v + "x");
    }
}
