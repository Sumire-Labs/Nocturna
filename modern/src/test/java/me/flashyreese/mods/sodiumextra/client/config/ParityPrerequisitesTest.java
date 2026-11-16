package me.flashyreese.mods.sodiumextra.client.config;

import static org.junit.jupiter.api.Assertions.*;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import org.embeddedt.embeddium.impl.extra.compat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class ParityPrerequisitesTest {
    @TempDir Path directory;

    @Test
    void protectedDistancesNeverReplaceRenderFog() {
        ExtraRendererOptions.setCloudRange(128);
        var atmospheric = new SodiumExtraGameOptions.AtmosphericFogSettings();
        for (var type : FogDistanceHelper.ProtectedFogType.values())
            for (int distance : new int[] {0, 20, 256, -1}) {
                var data =
                        ExtraFog.calculate(-8, 64, 64, false, false, type, atmospheric, distance);
                assertEquals(64, data.renderDistanceEnd);
                assertEquals(57.6F, data.renderDistanceStart, 0.0001);
                assertEquals(
                        distance == -1 ? Float.MAX_VALUE : distance == 0 ? 64 : distance,
                        data.environmentalEnd);
            }
    }

    @Test
    void cloudDistanceIsIndependentOfTerrainAndUsesLinearCameraFade() {
        ExtraRendererOptions.setCloudRange(128);
        var settings = new SodiumExtraGameOptions.AtmosphericFogSettings();
        settings.cloudFogPercent = 50;
        for (float terrain : new float[] {32, 64, 512})
            assertEquals(
                    1024,
                    ExtraFog.calculate(
                                    terrain - 4, terrain, terrain, true, false, null, settings, 0)
                            .cloudEnd);
        settings.distanceChunks = -1;
        var off = ExtraFog.calculate(60, 64, 64, true, false, null, settings, 0);
        assertEquals(Float.MAX_VALUE, off.renderDistanceEnd);
        assertEquals(1024, off.cloudEnd);
    }

    @Test
    void profileTransitionsRetainBothBuiltinProfilesAndCustomStatuses() throws Exception {
        Path path = directory.resolve("debug-profile.json");
        var state = new DebugOverlayState(path);
        assertEquals(DebugOverlayState.Profile.DEFAULT, state.profile().orElseThrow());
        assertEquals(DebugOverlayState.Visibility.OVERLAY, state.get("minecraft:player_position"));
        state.loadProfile(DebugOverlayState.Profile.PERFORMANCE);
        assertEquals(DebugOverlayState.Visibility.ALWAYS, state.get("minecraft:fps"));
        assertEquals(DebugOverlayState.Visibility.NEVER, state.get("minecraft:player_position"));
        state.set("example:custom", DebugOverlayState.Visibility.ALWAYS);
        assertTrue(state.profile().isEmpty());
        var loaded = new DebugOverlayState(path);
        assertEquals(DebugOverlayState.Visibility.ALWAYS, loaded.get("example:custom"));
        assertEquals(DebugOverlayState.Visibility.ALWAYS, loaded.get("minecraft:fps"));
        loaded.loadProfile(DebugOverlayState.Profile.DEFAULT);
        assertEquals(DebugOverlayState.Visibility.NEVER, loaded.get("example:custom"));
    }

    @Test
    void togglingUsesTheOverlayStateAndAlwaysCanBeDisabled() {
        var state = new DebugOverlayState(directory.resolve("profile.json"));
        assertTrue(state.toggle("minecraft:entity_hitboxes", false));
        assertEquals(DebugOverlayState.Visibility.ALWAYS, state.get("minecraft:entity_hitboxes"));
        assertFalse(state.toggle("minecraft:entity_hitboxes", false));
        assertTrue(state.toggle("minecraft:entity_hitboxes", true));
        assertEquals(DebugOverlayState.Visibility.OVERLAY, state.get("minecraft:entity_hitboxes"));
        assertFalse(state.toggle("minecraft:entity_hitboxes", true));
    }

    @Test
    void invalidProfileCannotCrashTheHud() throws Exception {
        Path path = directory.resolve("profile.json");
        Files.writeString(path, "{\"custom\":{\"minecraft:fps\":null}}");
        var state = assertDoesNotThrow(() -> new DebugOverlayState(path));
        assertEquals(DebugOverlayState.Profile.DEFAULT, state.profile().orElseThrow());
    }

    @Test
    void adaptiveProbeRestoresQueriedStateAndDoesNotGuessAnUnknownInterval() {
        var calls = new ArrayList<Integer>();
        var backend =
                new SwapIntervalProbe.Backend() {
                    public long context() {
                        return 1;
                    }

                    public OptionalInt current() {
                        return OptionalInt.of(2);
                    }

                    public boolean set(int value) {
                        calls.add(value);
                        return true;
                    }
                };
        var probe = new SwapIntervalProbe();
        assertTrue(probe.supported(backend));
        assertEquals(List.of(-1, 2), calls);
        assertTrue(probe.supported(backend));
        assertEquals(2, calls.size());
        var unknown =
                new SwapIntervalProbe.Backend() {
                    public long context() {
                        return 2;
                    }

                    public OptionalInt current() {
                        return OptionalInt.empty();
                    }

                    public boolean set(int value) {
                        throw new AssertionError("unqueried state modified");
                    }
                };
        assertFalse(probe.supported(unknown));
    }

    @Test
    void failedAdaptiveRequestAlsoRestoresTheOldInterval() {
        var calls = new ArrayList<Integer>();
        var backend =
                new SwapIntervalProbe.Backend() {
                    public long context() {
                        return 1;
                    }

                    public OptionalInt current() {
                        return OptionalInt.of(1);
                    }

                    public boolean set(int value) {
                        calls.add(value);
                        return value != -1;
                    }
                };
        assertFalse(new SwapIntervalProbe().supported(backend));
        assertEquals(List.of(-1, 1), calls);
    }
}
