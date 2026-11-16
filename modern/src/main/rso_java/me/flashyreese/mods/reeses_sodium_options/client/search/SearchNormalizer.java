package me.flashyreese.mods.reeses_sodium_options.client.search;

import java.text.Normalizer;
import java.util.Locale;
import org.embeddedt.embeddium.impl.gui.rso.compat.Unicode16;

public final class SearchNormalizer {
    private final boolean foldDiacritics;

    public SearchNormalizer(boolean foldDiacritics) {
        this.foldDiacritics = foldDiacritics;
    }

    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = Unicode16.nfkcLowercase(text);
        if (this.foldDiacritics) {
            normalized = Unicode16.foldDiacritics(normalized);
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean lastWasSpace = false;
        int length = normalized.length();
        for (int offset = 0; offset < length; ) {
            int codePoint = normalized.codePointAt(offset);
            int type = Unicode16.getType(codePoint);
            boolean isSpace = Unicode16.isWhitespace(codePoint)
                    || type == Character.SPACE_SEPARATOR
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR;
            boolean isPunctuation = type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || type == Character.FINAL_QUOTE_PUNCTUATION;
            if ((isSpace || isPunctuation)) {
                if (!lastWasSpace) {
                    builder.append(' ');
                    lastWasSpace = true;
                }
            } else {
                builder.appendCodePoint(codePoint);
                lastWasSpace = false;
            }
            offset += Character.charCount(codePoint);
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? "" : result;
    }
}
