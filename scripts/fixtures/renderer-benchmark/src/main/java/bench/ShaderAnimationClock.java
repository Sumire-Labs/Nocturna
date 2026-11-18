package bench;

/** Test input only: animate at the same wall-clock rate on both renderer snapshots. */
public final class ShaderAnimationClock {
    private static final String INPUT = System.getProperty("bench.animationStart");
    private static final float OFFSET = INPUT == null ? 0 : Float.parseFloat(INPUT);
    private static long epoch;

    private ShaderAnimationClock() {}

    public static void begin() {
        if (INPUT != null) epoch = System.nanoTime();
    }

    public static boolean active() {
        return INPUT != null && epoch != 0;
    }

    public static float seconds() {
        return (OFFSET + (System.nanoTime() - epoch) / 1_000_000_000F) % 3600F;
    }
}
