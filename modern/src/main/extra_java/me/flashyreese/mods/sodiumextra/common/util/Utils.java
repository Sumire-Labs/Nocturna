// Sodium Extra 0.9.6-beta.4, LGPL-3.0, upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.common.util;

public class Utils {
    public static int packLight(int lightU, int lightV) {
        return (lightV << 16) | (lightU & 0xFFFF);
    }

    public static long packPosition(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int[] unpackIntegers(long packedValue) {
        int int1 = (int) (packedValue >> 32);
        int int2 = (int) (packedValue & 0xFFFFFFFFL);

        return new int[] {int1, int2};
    }
}
