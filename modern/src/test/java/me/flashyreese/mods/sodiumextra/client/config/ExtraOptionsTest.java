package me.flashyreese.mods.sodiumextra.client.config;

import static org.junit.jupiter.api.Assertions.*;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ExtraOptionsTest {
    @TempDir Path dir;

    @Test
    void preservesPublicDefaultsAndRoundTripsUnknownParticleIds() throws Exception {
        Path config = dir.resolve(SodiumExtraConfigKeys.FILE_NAME);
        var options = SodiumExtraGameOptions.load(config);
        assertTrue(options.animationSettings.animation);
        assertTrue(options.animationSettings.fire);
        assertTrue(options.detailSettings.sun);
        assertTrue(options.detailSettings.moon);
        assertTrue(options.extraSettings.showFPSExtended);
        assertFalse(options.extraSettings.showFps);
        assertFalse(options.extraSettings.paniniProjection);
        assertEquals(25, options.extraSettings.paniniProjectionStrength);
        assertEquals(150, options.extraSettings.paniniProjectionQuality);
        assertTrue(options.extraSettings.steadyDebugHud);
        assertEquals(1, options.extraSettings.steadyDebugHudRefreshInterval);
        assertFalse(options.renderSettings.limitBeaconBeamHeight);
        assertFalse(options.renderSettings.fogSettings.advanced);
        assertEquals(0, options.renderSettings.fogSettings.atmospheric.distanceChunks);
        assertEquals(100, options.renderSettings.fogSettings.atmospheric.startPercent);
        assertEquals(100, options.renderSettings.fogSettings.atmospheric.cloudFogPercent);
        assertFalse(options.renderSettings.fogSettings.protectedGameplay.enabledWhenAllowed);
        var particle = new ResourceLocation("addon", "custom_particle");
        assertTrue(options.particleSettings.isParticleEnabled(particle));
        assertTrue(options.particleSettings.otherMap.isEmpty());
        options.particleSettings.otherMap.put(particle, false);
        options.writeChanges();
        assertFalse(
                SodiumExtraGameOptions.load(config).particleSettings.isParticleEnabled(particle));
        options.particleSettings.particles = false;
        assertFalse(options.particleSettings.isParticleEnabled(null));
    }

    @Test
    void dimensionsOnlyInheritOnFirstEditAndKeepIndependentSnapshots() {
        var fog = new SodiumExtraGameOptions.FogSettings();
        var id = new ResourceLocation("addon", "dimension_type");
        fog.atmospheric.distanceChunks = 25;
        fog.atmospheric.startPercent = 37;
        fog.atmospheric.cloudFogPercent = 49;
        assertEquals(0, fog.getDimensionFogDistance(id));
        assertEquals(37, fog.getDimensionFogStart(id));
        assertTrue(fog.dimensionOverrides.isEmpty());
        var snapshot = fog.getOrCreateDimensionOverride(id);
        assertEquals(0, snapshot.distanceChunks);
        assertEquals(37, snapshot.startPercent);
        fog.atmospheric.startPercent = 80;
        assertEquals(37, snapshot.startPercent);
        assertEquals(49, snapshot.cloudFogPercent);
    }

    @Test
    void repairsNullSectionsAndBoundsWithoutLosingValidValues() throws Exception {
        Path config = dir.resolve("options.json");
        Files.writeString(
                config,
                """
                {"animation_settings":null,"particle_settings":{"other":null},"detail_settings":null,
                "render_settings":{"fog_settings":{"atmospheric":{"start_percent":-2,"cloud_fog_percent":200,"shape_mode":null},"dimension_overrides":null,"protected_gameplay":null}},
                "extra_settings":{"overlay_corner":null,"text_contrast":null,"panini_projection_strength":200,"panini_projection_quality":1,"steady_debug_hud_refresh_interval":0}}
                """);
        var options = SodiumExtraGameOptions.load(config);
        assertNotNull(options.animationSettings);
        assertNotNull(options.particleSettings.otherMap);
        assertNotNull(options.detailSettings);
        assertNotNull(options.renderSettings.fogSettings.protectedGameplay);
        assertEquals(0, options.renderSettings.fogSettings.atmospheric.startPercent);
        assertEquals(100, options.renderSettings.fogSettings.atmospheric.cloudFogPercent);
        assertEquals(
                SodiumExtraGameOptions.FogShapeMode.VANILLA,
                options.renderSettings.fogSettings.atmospheric.shapeMode);
        assertEquals(100, options.extraSettings.paniniProjectionStrength);
        assertEquals(100, options.extraSettings.paniniProjectionQuality);
        assertEquals(1, options.extraSettings.steadyDebugHudRefreshInterval);
    }

    @Test
    void backsUpCorruptFilesWithoutOverwritingPreviousBackups() throws Exception {
        Path config = dir.resolve("options.json");
        Files.writeString(config, "broken-1");
        SodiumExtraGameOptions.load(config);
        assertEquals("broken-1", Files.readString(dir.resolve("options.json.corrupt")));
        Files.writeString(config, "broken-2");
        SodiumExtraGameOptions.load(config);
        assertEquals("broken-2", Files.readString(dir.resolve("options.json.corrupt.1")));
        assertEquals("broken-1", Files.readString(dir.resolve("options.json.corrupt")));
    }

    @Test
    void legacyProtectedFogAliasesRemainReadable() throws Exception {
        Path config = dir.resolve("options.json");
        Files.writeString(
                config,
                "{\"render_settings\":{\"fog_settings\":{\"protected_gameplay\":{\"enabled_in_private_singleplayer\":true,\"blindness_distance_chunks\":18}}}}");
        var protectedFog =
                SodiumExtraGameOptions.load(config).renderSettings.fogSettings.protectedGameplay;
        assertTrue(protectedFog.enabledWhenAllowed);
        assertEquals(18, protectedFog.blindnessDistanceBlocks);
    }

    @Test
    void shapeSentinelsRoundTripAndCylindricalCullUsesTallAxisWithoutWideningHorizontalLimit() {
        for (var shape : SodiumExtraGameOptions.FogShapeMode.values()) {
            var fog = new org.embeddedt.embeddium.impl.extra.compat.FogData();
            fog.renderDistanceStart = 80;
            fog.renderDistanceEnd = 112;
            var settings = new SodiumExtraGameOptions.AtmosphericFogSettings();
            settings.shapeMode = shape;
            FogDistanceHelper.applyRenderDistanceShape(fog, settings);
            assertEquals(
                    80,
                    FogDistanceHelper.decodeRenderDistanceStart(
                            fog.renderDistanceStart, fog.renderDistanceEnd));
            assertEquals(
                    112,
                    FogDistanceHelper.decodeRenderDistanceEnd(
                            fog.renderDistanceStart, fog.renderDistanceEnd));
            float limit =
                    FogDistanceHelper.expandCylindricalCullDistance(
                            112, fog.renderDistanceStart, fog.renderDistanceEnd, 256);
            if (shape == SodiumExtraGameOptions.FogShapeMode.CYLINDRICAL) {
                assertEquals(4096.75F, limit);
                assertTrue(
                        FogDistanceHelper.testExpandedCylindricalCullDistance(
                                255 * 255, 4095, limit));
                assertFalse(
                        FogDistanceHelper.testExpandedCylindricalCullDistance(256 * 256, 0, limit));
                assertFalse(FogDistanceHelper.testExpandedCylindricalCullDistance(0, 4096, limit));
                assertFalse(
                        FogDistanceHelper.isExpandedCylindricalCullDistance(Math.nextUp(limit)));
            } else assertEquals(112, limit);
        }
        var fog = new org.embeddedt.embeddium.impl.extra.compat.FogData();
        FogDistanceHelper.applyProtectedGameplayFog(fog, -1, 0.25F, 0.8F);
        assertEquals(Float.MAX_VALUE, fog.environmentalEnd);
        assertEquals(Float.MAX_VALUE, fog.cloudEnd);
        FogDistanceHelper.applyProtectedGameplayFog(fog, 20, 0.25F, 0.8F);
        assertEquals(5, fog.environmentalStart);
        assertEquals(16, fog.skyEnd);
    }
}
