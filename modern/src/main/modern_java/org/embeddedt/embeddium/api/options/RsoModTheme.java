package org.embeddedt.embeddium.api.options;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Per-mod ARGB colors consumed by the original RSO theme renderer. */
public record RsoModTheme(int primary, int lighter, int darker) {
    private static final ConcurrentHashMap<String, RsoModTheme> THEMES = new ConcurrentHashMap<>();

    public void register(String modId) {
        THEMES.put(java.util.Objects.requireNonNull(modId), this);
    }

    public static Optional<RsoModTheme> find(String modId) {
        return Optional.ofNullable(THEMES.get(modId));
    }

    public static void unregister(String modId) {
        THEMES.remove(modId);
    }
}
