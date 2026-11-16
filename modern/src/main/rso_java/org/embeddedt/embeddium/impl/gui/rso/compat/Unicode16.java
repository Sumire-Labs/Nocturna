package org.embeddedt.embeddium.impl.gui.rso.compat;

import org.embeddedt.embeddium.impl.shadow.icu.lang.UCharacter;
import org.embeddedt.embeddium.impl.shadow.icu.text.Normalizer2;

import java.util.Locale;

/** Unicode 16 normalization and casing, independent of the client's Java version. */
public final class Unicode16 {
    private Unicode16() {}

    public static String nfkcLowercase(String text) {
        return UCharacter.toLowerCase(Locale.ROOT, Normalizer2.getNFKCInstance().normalize(text));
    }

    public static String foldDiacritics(String text) {
        String decomposed = Normalizer2.getNFDInstance().normalize(text);
        var result = new StringBuilder(decomposed.length());
        decomposed
                .codePoints()
                .filter(
                        cp -> {
                            int type = UCharacter.getType(cp);
                            return type != Character.NON_SPACING_MARK
                                    && type != Character.COMBINING_SPACING_MARK
                                    && type != Character.ENCLOSING_MARK;
                        })
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    public static int getType(int codePoint) {
        int type = UCharacter.getType(codePoint);
        // java.lang.Character reserves category 17; ICU follows the contiguous UCD enumeration.
        return type >= 17 ? type + 1 : type;
    }

    public static boolean isWhitespace(int codePoint) {
        return UCharacter.isWhitespace(codePoint);
    }
    public static boolean isLetterOrDigit(int codePoint) { return UCharacter.isLetterOrDigit(codePoint); }
}
