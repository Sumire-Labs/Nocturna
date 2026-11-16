package org.embeddedt.embeddium.impl.render.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.ref.WeakReference;

/** Native 3D biome and altitude rules replace the 1.7.10 temperature approximation. No world is retained strongly. */
final class NativeWeatherWorld implements WeatherWorld {
    private final WeakReference<ClientLevel> world;
    private final int biomeY;

    NativeWeatherWorld(ClientLevel world, int biomeY) {
        this.world = new WeakReference<>(world);
        this.biomeY = biomeY;
    }

    boolean matches(ClientLevel level, int y) { return this.world.get() == level && this.biomeY == y; }

    private ClientLevel level() { return java.util.Objects.requireNonNull(this.world.get(), "World was unloaded"); }

    public Biome getBiomeGenForCoords(int x, int z) {
        var biome = this.level().getBiome(new BlockPos(x, this.biomeY, z)).value();
        return new Biome() {
            public boolean canSpawnLightningBolt() { return biome.hasPrecipitation(); }
            public boolean getEnableSnow() { return false; }
            public float getFloatTemperature(int x, int y, int z) {
                return ((org.embeddedt.embeddium.impl.mixin.features.render.weather.BiomeTemperatureAccessor) (Object) biome)
                        .nocturna$getTemperature(new BlockPos(x, y, z));
            }
            public boolean usesNativeTemperature() { return true; }
            public int precipitationAt(int x, int y, int z) {
                return switch (biome.getPrecipitationAt(new BlockPos(x, y, z))) {
                    case RAIN -> 1;
                    case SNOW -> 2;
                    case NONE -> 0;
                };
            }
        };
    }

    public int getPrecipitationHeight(int x, int z) { return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z); }
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
        return LevelRenderer.getLightColor(this.level(), new BlockPos(x, y, z));
    }
}
