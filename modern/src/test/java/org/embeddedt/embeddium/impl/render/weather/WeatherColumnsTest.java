package org.embeddedt.embeddium.impl.render.weather;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeatherColumnsTest {
    static final WeatherWorld.Biome WARM = biome(.6f), COLD = biome(-.1f);
    static WeatherWorld.Biome biome(float temperature) {
        return new WeatherWorld.Biome() {
            public boolean canSpawnLightningBolt() { return true; }
            public boolean getEnableSnow() { return false; }
            public float getFloatTemperature(int x, int y, int z) { return temperature - Math.max(0, y - 65) * (.05f / 30); }
        };
    }
    static class World implements WeatherWorld {
        int biomeCalls, heightCalls, lightCalls, epoch;
        public Biome getBiomeGenForCoords(int x, int z) { biomeCalls++;return ((x + z + epoch) & 1) == 0 ? WARM : COLD; }
        public int getPrecipitationHeight(int x, int z) { heightCalls++;return 50 + Math.floorMod(x * 3 + z + epoch, 20); }
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) { lightCalls++;return (x * 16 ^ z * 16 ^ y * 65536) & 0xf000f0; }
        void resetCounters() { biomeCalls = heightCalls = lightCalls = 0; }
        String counters() { return biomeCalls + ":" + heightCalls + ":" + lightCalls; }
    }

    @Test void cacheScheduleAndAllColumnDataMatchOriginalAcrossMovementAndWorldChanges() {
        var current = new WeatherColumns(); var reference = new AngelicaWeatherColumnsReference(); var world = new World();
        for (int frame = 0; frame < 240; frame++) {
            if (frame == 120) world = new World();
            world.epoch = frame / 31;
            int radius = frame < 60 || frame > 190 ? 5 : 10;
            int x = -140 + frame / 19, z = frame < 100 ? -31 : 8;
            int tick = frame / 3;
            world.resetCounters(); reference.update(world, x, z, radius, tick); String expectedCalls = world.counters();
            world.resetCounters(); current.update(world, x, z, radius, tick); assertEquals(expectedCalls, world.counters());
            assertEquals(reference.revision(), current.revision());
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int cx = x + dx, cz = z + dz;
                int a = reference.slotAt(cx, cz), b = current.slotAt(cx, cz);
                assertEquals(a, b); assertTrue(current.covers(cx, cz));
                assertEquals(reference.precipitatesAtSlot(a), current.precipitatesAtSlot(b));
                assertEquals(reference.heightAtSlot(a), current.heightAtSlot(b));
                assertEquals(reference.hashAtSlot(a), current.hashAtSlot(b));
                for (int j = 0; j < 4; j++) assertEquals(reference.jitterAtSlot(a, j), current.jitterAtSlot(b, j));
                for (int y : new int[]{-64, 64, 65, 110}) {
                    assertEquals(reference.temperatureAtSlot(a, y), current.temperatureAtSlot(b, y));
                    assertEquals(reference.lightAtSlot(world, a, cx, cz, y), current.lightAtSlot(world, b, cx, cz, y));
                }
            }
        }
    }

    @Test void stableFrameReusesQueriesAndNativePrecipitationIsNotApproximated() {
        var world = new World(); var columns = new WeatherColumns(); columns.update(world, 0, 0, 5, 1);
        world.resetCounters(); columns.update(world, 0, 0, 5, 1);
        assertEquals("0:0:0", world.counters());
        columns.update(new World() {
            public Biome getBiomeGenForCoords(int x, int z) {
                return new Biome() {
                    public boolean canSpawnLightningBolt() { return true; }
                    public boolean getEnableSnow() { return false; }
                    public float getFloatTemperature(int x, int y, int z) { return 99; }
                    public boolean usesNativeTemperature() { return true; }
                    public int precipitationAt(int x, int y, int z) { return y < 80 ? 1 : y < 100 ? 2 : 0; }
                };
            }
        }, 0, 0, 5, 2);
        int slot = columns.slotAt(0, 0);
        assertEquals(1, columns.precipitationAtSlot(slot, 60));
        assertEquals(2, columns.precipitationAtSlot(slot, 90));
        assertEquals(0, columns.precipitationAtSlot(slot, 120));
    }
}
