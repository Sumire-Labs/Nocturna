package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util;

public record Dim2i(int x, int y, int width, int height) {
    public int getLimitX() {
        return x + width;
    }

    public int getLimitY() {
        return y + height;
    }

    public int getCenterX() {
        return x + width / 2;
    }

    public int getCenterY() {
        return y + height / 2;
    }

    public boolean containsCursor(double cx, double cy) {
        return cx >= x && cx < x + width && cy >= y && cy < y + height;
    }
}
