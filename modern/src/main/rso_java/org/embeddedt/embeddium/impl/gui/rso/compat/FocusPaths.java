package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.gui.ComponentPath;

public final class FocusPaths {
    private FocusPaths() {}

    public static GuiEventListener leaf(ComponentPath path) {
        while (path instanceof ComponentPath.Path parent) path = parent.childPath();
        return (GuiEventListener) path.component();
    }
}
