package me.jellysquid.mods.sodium.mixin.features.world.biome;

import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiomeMixinTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void readsCurrentBiomeEffectsThroughTheGetter() {
        var biome = new TestBiome();
        biome.effects = effects(0x123456, 0x654321);
        assertEquals(0x123456, biome.getGrassColor(17, 31));
        assertEquals(0x654321, biome.getFoliageColor());

        // Model a resource reload or biome modifier replacing Forge's effective biome effects.
        biome.effects = effects(0xABCDEF, 0xFEDCBA);
        assertEquals(0xABCDEF, biome.getGrassColor(17, 31));
        assertEquals(0xFEDCBA, biome.getFoliageColor());
    }

    @Test
    void appliesTheCurrentGrassColorModifier() {
        var biome = new TestBiome();
        biome.effects = new BiomeSpecialEffects.Builder()
                .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0)
                .grassColorOverride(0x123456)
                .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST)
                .build();
        assertEquals(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST.modifyColor(17, 31, 0x123456),
                biome.getGrassColor(17, 31));
    }

    private static BiomeSpecialEffects effects(int grass, int foliage) {
        return new BiomeSpecialEffects.Builder()
                .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0)
                .grassColorOverride(grass).foliageColorOverride(foliage).build();
    }

    private static class TestBiome extends BiomeMixin {
        BiomeSpecialEffects effects;

        @Override
        public BiomeSpecialEffects getSpecialEffects() {
            return this.effects;
        }
    }
}
