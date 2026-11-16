package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class ExternalPage extends Page {
    private final java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> action;

    public ExternalPage(
            Component name,
            java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> action) {
        super(name, List.of());
        this.action = action;
    }

    public java.util.function.Consumer<net.minecraft.client.gui.screens.Screen>
            currentScreenConsumer() {
        return action;
    }
}
