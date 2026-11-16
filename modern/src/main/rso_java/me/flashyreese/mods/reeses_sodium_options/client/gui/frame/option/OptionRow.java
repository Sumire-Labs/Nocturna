package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option;

import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Option;
import org.embeddedt.embeddium.impl.gui.rso.compat.Renderable;
import org.embeddedt.embeddium.impl.gui.rso.compat.GuiEventListener;
import org.embeddedt.embeddium.impl.gui.rso.compat.NarratableEntry;

public interface OptionRow extends Renderable, GuiEventListener, NarratableEntry {
    Option getOption();

    LayoutBounds getDimensions();

    void releaseActionButtonLayoutHold();

    boolean handleBackNavigation();

    boolean undoFocusedActionButton();

    void clearActionButtonFocus();
}
