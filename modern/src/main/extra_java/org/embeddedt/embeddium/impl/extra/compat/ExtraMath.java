package org.embeddedt.embeddium.impl.extra.compat;

/** Java 17 equivalents of the bounded Math.clamp operations used by the pinned Extra sources. */
public final class ExtraMath {
    private ExtraMath() {}

    public static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("min > max");
        return Math.min(max, Math.max(min, value));
    }

    public static float clamp(float value, float min, float max) {
        if (!(min <= max) || Float.compare(min, max) > 0)
            throw new IllegalArgumentException("Invalid range");
        return Math.min(max, Math.max(min, value));
    }

    public static double clamp(double value, double min, double max) {
        if (!(min <= max) || Double.compare(min, max) > 0)
            throw new IllegalArgumentException("Invalid range");
        return Math.min(max, Math.max(min, value));
    }
}
