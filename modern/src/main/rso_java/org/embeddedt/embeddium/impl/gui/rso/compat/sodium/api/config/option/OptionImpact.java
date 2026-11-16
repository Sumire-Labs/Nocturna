package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option;

import net.minecraft.network.chat.Component;

public enum OptionImpact {
    LOW,
    MEDIUM,
    HIGH,
    VARIES;

    public Component getName() {
        return Component.translatable(
                "sodium.option_impact." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
