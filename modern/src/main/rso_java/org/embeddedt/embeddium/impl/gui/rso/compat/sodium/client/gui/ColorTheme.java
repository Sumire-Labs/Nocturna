package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui;

public final class ColorTheme {
    public final int theme, themeLighter, themeDarker;

    public ColorTheme(int theme, int lighter, int darker) {
        this.theme = theme;
        this.themeLighter = lighter;
        this.themeDarker = darker;
    }

    public static final ColorTheme DEFAULT = new ColorTheme(0xFF94E4D3, 0xFFB5F0E4, 0xFF68AB9D);
}
