package org.embeddedt.embeddium.impl.extra.compat.config.option;

import net.minecraft.network.chat.Component;

@FunctionalInterface
public interface ControlValueFormatter {
    Component format(int value);
}
