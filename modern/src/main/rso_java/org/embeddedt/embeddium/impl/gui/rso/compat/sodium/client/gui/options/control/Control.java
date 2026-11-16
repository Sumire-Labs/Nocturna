package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Option;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

public interface Control {
    Option getOption();

    ControlElement createElement(
            net.minecraft.client.gui.screens.Screen screen,
            AbstractOptionList list,
            Dim2i dim,
            ColorTheme theme);
}
