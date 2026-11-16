package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui;

public final class ColorTheme {
    public final int theme, themeLighter, themeDarker;

    public ColorTheme(int theme, int lighter, int darker) {
        this.theme = theme;
        this.themeLighter = lighter;
        this.themeDarker = darker;
    }

    // ARGB outputs recorded from the reference runtime; no Sodium implementation is included.
    public static final ColorTheme DEFAULT = new ColorTheme(0xFF94E4D3, 0xFFC0FFF1, 0xFF80AFA5);
    private static final ColorTheme[] PRESETS = {
        new ColorTheme(0xFFE494A5, 0xFFFFC0CD, 0xFFAF808A),
        new ColorTheme(0xFFAB94E4, 0xFFD2C0FF, 0xFF8D80AF),
        new ColorTheme(0xFFCDE494, 0xFFEDFFC0, 0xFFA2AF80),
        new ColorTheme(0xFFD394E4, 0xFFF1C0FF, 0xFFA580AF),
        new ColorTheme(0xFFE4D394, 0xFFFFF1C0, 0xFFAFA580)
    };

    public static ColorTheme forMod(String modId, int preset) {
        var custom = org.embeddedt.embeddium.api.options.RsoModTheme.find(modId);
        if (custom.isPresent()) {
            var c = custom.get();
            return new ColorTheme(c.primary(), c.lighter(), c.darker());
        }
        return modId.equals("embeddium") || modId.equals("celeritas")
                ? DEFAULT
                : PRESETS[Math.floorMod(preset, PRESETS.length)];
    }
}
