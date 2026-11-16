package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

public final class ExternalButtonControl implements Control {
    private final ExternalButtonOption option;

    public ExternalButtonControl(ExternalButtonOption option) {
        this.option = option;
    }

    public ExternalButtonOption getOption() {
        return option;
    }

    public ControlElement createElement(
            net.minecraft.client.gui.screens.Screen screen,
            AbstractOptionList list,
            Dim2i dim,
            ColorTheme theme) {
        return new me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option
                .NativeRowControlElement(screen, dim, theme, option);
    }
}
