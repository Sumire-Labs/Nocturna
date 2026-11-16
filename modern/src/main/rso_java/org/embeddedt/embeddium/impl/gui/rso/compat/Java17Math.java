package org.embeddedt.embeddium.impl.gui.rso.compat;

/** Java 21 Math operations used by upstream RSO, implemented for Java 17. */
public final class Java17Math {
    private Java17Math() {}

    public static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);
        return Math.min(max, Math.max(min, value));
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || Float.compare(min, max) > 0)
            throw new IllegalArgumentException("Invalid clamp bounds");
        return Math.min(max, Math.max(min, value));
    }

    public static int ceilDiv(int x, int y) {
        int quotient = x / y;
        return ((x ^ y) >= 0 && quotient * y != x) ? quotient + 1 : quotient;
    }
}
