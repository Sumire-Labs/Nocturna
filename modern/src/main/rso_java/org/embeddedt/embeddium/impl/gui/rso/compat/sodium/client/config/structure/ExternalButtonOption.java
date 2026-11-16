package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.resources.ResourceLocation;

public final class ExternalButtonOption extends Option {
    public java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> action;

    public ExternalButtonOption(ResourceLocation id, Config state) {
        super(id, state);
        control =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control
                        .ExternalButtonControl(this);
    }

    public java.util.function.Consumer<net.minecraft.client.gui.screens.Screen>
            getCurrentScreenConsumer() {
        return action;
    }
}
