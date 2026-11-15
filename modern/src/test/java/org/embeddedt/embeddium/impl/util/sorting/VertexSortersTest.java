package org.embeddedt.embeddium.impl.util.sorting;

import org.embeddedt.embeddium.impl.modern.sorting.VertexSorters;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class VertexSortersTest {
    @Test
    void matchesStableNumericOrderAcrossScratchAndDispatchBoundaries() {
        Random random = new Random(580);
        for (int size : new int[] {0, 1, 15, 16, 63, 64, 65, 255, 1024, 65535, 65536, 65537}) {
            for (int distribution = 0; distribution < 5; distribution++) {
                float[] keys = new float[size];
                for (int i = 0; i < size; i++) {
                    keys[i] = switch (distribution) {
                        case 0 -> random.nextInt(7) - 3;
                        case 1 -> i % 2 == 0 ? -0.0f : 0.0f;
                        case 2 -> i % 2 == 0 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
                        case 3 -> i;
                        default -> finiteOrInfiniteFloat(random);
                    };
                }
                assertOrder(keys);
            }
        }
    }

    @Test
    void preservesMergeSortBehaviorForNaNKeys() {
        for (int nanIndex : new int[] {0, 32, 63, 64, 127}) {
            float[] keys = new float[128];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = i % 11;
            }
            keys[nanIndex] = Float.NaN;
            assertNull(RadixSort.sort(keys));
            assertArrayEquals(MergeSort.mergeSort(keys), sortKeys(keys));
        }
    }

    @Test
    void sortsDistanceKeysThroughPublicApi() {
        Vector3f origin = new Vector3f(17, -4, 2);
        Random random = new Random(20);
        for (int size : new int[] {0, 1, 63, 64, 1024}) {
            Vector3f[] positions = new Vector3f[size];
            float[] keys = new float[size];
            for (int i = 0; i < size; i++) {
                positions[i] = new Vector3f(random.nextInt(8), random.nextInt(8), random.nextInt(8));
                keys[i] = origin.distanceSquared(positions[i]);
            }
            assertArrayEquals(MergeSort.mergeSort(keys), VertexSorters.sortByDistance(origin).sort(positions));
            if (size > 0) {
                positions[size / 2].x = Float.NaN;
                keys[size / 2] = Float.NaN;
                assertArrayEquals(MergeSort.mergeSort(keys), VertexSorters.sortByDistance(origin).sort(positions));
            }
        }
    }

    @Test
    void keepsScratchIndependentAcrossConcurrentCalls() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<Callable<Void>>();
            for (int thread = 0; thread < 4; thread++) {
                final int seed = thread;
                tasks.add(() -> {
                    Random random = new Random(seed);
                    for (int iteration = 0; iteration < 200; iteration++) {
                        float[] keys = new float[64 + random.nextInt(2048)];
                        for (int i = 0; i < keys.length; i++) {
                            keys[i] = finiteOrInfiniteFloat(random);
                        }
                        assertOrder(keys);
                    }
                    return null;
                });
            }
            for (var future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertOrder(float[] keys) {
        Integer[] expected = new Integer[keys.length];
        Arrays.setAll(expected, i -> i);
        // Object-array sorting is stable. Normalize signed zeros to match numeric comparisons in the old sorter.
        Arrays.sort(expected, (a, b) -> Float.compare(keys[b] == 0 ? 0 : keys[b], keys[a] == 0 ? 0 : keys[a]));
        int[] order = Arrays.stream(expected).mapToInt(Integer::intValue).toArray();
        assertArrayEquals(order, sortKeys(keys));
        assertArrayEquals(MergeSort.mergeSort(keys), sortKeys(keys));
        if (keys.length < 64) {
            assertNull(RadixSort.sort(keys));
        } else {
            assertArrayEquals(order, RadixSort.sort(keys));
        }
    }

    private static int[] sortKeys(float[] keys) {
        int[] order = RadixSort.sort(keys);
        return order != null ? order : MergeSort.mergeSort(keys);
    }

    private static float finiteOrInfiniteFloat(Random random) {
        float value;
        do {
            value = Float.intBitsToFloat(random.nextInt());
        } while (Float.isNaN(value));
        return value;
    }
}
