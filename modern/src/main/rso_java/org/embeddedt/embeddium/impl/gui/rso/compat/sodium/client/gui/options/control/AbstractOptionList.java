package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control;

import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

import java.util.*;

public abstract class AbstractOptionList extends ControlElement implements ContainerEventHandler {
    private final List<ControlElement> controls = new ArrayList<>();
    private GuiEventListener focused;
    private boolean dragging;

    protected AbstractOptionList(Dim2i dim) {
        super(dim);
    }

    public List<ControlElement> getControls() {
        return controls;
    }

    public abstract int getScrollAmount();

    public GuiEventListener getFocused() {
        return focused;
    }

    public void setFocused(GuiEventListener listener) {
        focused = listener;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        for (var control : controls) control.extractRenderState(graphics, x, y, delta);
    }
}
