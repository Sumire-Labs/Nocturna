package org.embeddedt.embeddium.impl.render.weather;

/** Version adapter for the original column cache, also usable by deterministic reference tests. */
public interface WeatherWorld {
    Biome getBiomeGenForCoords(int x, int z);
    int getPrecipitationHeight(int x, int z);
    int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum);

    interface Biome {
        boolean canSpawnLightningBolt();
        boolean getEnableSnow();
        float getFloatTemperature(int x, int y, int z);
        default boolean usesNativeTemperature() { return false; }
        default int precipitationAt(int x, int y, int z) {
            return getFloatTemperature(x, y, z) >= 0.15F ? 1 : 2;
        }
    }
}
