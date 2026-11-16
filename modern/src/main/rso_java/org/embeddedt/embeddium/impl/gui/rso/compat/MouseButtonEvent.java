package org.embeddedt.embeddium.impl.gui.rso.compat;

public record MouseButtonEvent(double x, double y, int button, int modifiers) {
    public boolean hasShiftDown() {
        return (modifiers & 1) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & 2) != 0;
    }
}
