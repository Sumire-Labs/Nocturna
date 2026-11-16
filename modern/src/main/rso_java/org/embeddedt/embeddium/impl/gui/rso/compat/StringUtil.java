package org.embeddedt.embeddium.impl.gui.rso.compat;

public final class StringUtil {
    public static int offsetByCodepoints(String text, int index, int offset) {
        return text.offsetByCodePoints(index, offset);
    }

    public static String filterText(String text) {
        var out = new StringBuilder();
        text.codePoints()
                .filter(c -> c >= 32 && c != 127 && c != 167)
                .forEach(out::appendCodePoint);
        return out.toString();
    }
}
