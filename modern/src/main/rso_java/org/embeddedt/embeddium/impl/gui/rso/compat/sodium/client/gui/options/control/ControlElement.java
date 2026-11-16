package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control;

import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumWidgetDimensions;
import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import me.flashyreese.mods.reeses_sodium_options.client.gui.widget.BaseWidget;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

public abstract class ControlElement extends BaseWidget implements SodiumWidgetDimensions {
    protected ControlElement(Dim2i dim) {
        super(new LayoutBounds(dim.x(), dim.y(), dim.width(), dim.height()));
    }

    public boolean isVisible() {
        return true;
    }

    public void rso$setDimensions(Dim2i dim) {
        setDim(new LayoutBounds(dim.x(), dim.y(), dim.width(), dim.height()));
    }
}
