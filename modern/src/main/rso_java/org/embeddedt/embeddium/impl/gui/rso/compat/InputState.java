package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.gui.screens.Screen;

public final class InputState {
    public static int modifiers() {
        return (Screen.hasShiftDown() ? 1 : 0)
                | (Screen.hasControlDown() ? 2 : 0)
                | (Screen.hasAltDown() ? 4 : 0);
    }
}
