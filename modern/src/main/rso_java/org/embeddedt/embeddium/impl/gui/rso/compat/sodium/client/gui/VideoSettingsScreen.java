package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui;

import net.minecraft.client.gui.screens.Screen;

public final class VideoSettingsScreen {
    public static Screen createScreen(Screen previous) {
        return org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource.screen(previous);
    }
}
