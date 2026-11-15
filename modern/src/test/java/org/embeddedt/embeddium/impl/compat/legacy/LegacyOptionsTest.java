package org.embeddedt.embeddium.impl.compat.legacy;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyOptionsTest {
    @Test void legacySavePreservesNewSettingsAndHonorsNativeReadOnlyState(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var nativeOptions=SodiumGameOptions.defaults();
        var path=SodiumGameOptions.class.getDeclaredField("configPath");path.setAccessible(true);
        var destination=directory.resolve("options.json");path.set(nativeOptions,destination);
        nativeOptions.quality.chunkFadeInDuration=123;
        var old=LegacyOptions.view(nativeOptions);old.advanced.cpuRenderAheadLimit=8;old.writeChanges();
        var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(destination)).getAsJsonObject();
        assertEquals(123,saved.getAsJsonObject("quality").get("chunk_fade_in_duration").getAsInt());
        assertEquals(8,saved.getAsJsonObject("advanced").get("cpu_render_ahead_limit").getAsInt());
        assertFalse(saved.getAsJsonObject("performance").get("use_compact_vertex_format").getAsBoolean());
        old.setReadOnly();assertTrue(nativeOptions.isReadOnly());assertTrue(old.isReadOnly());
        assertThrows(IllegalStateException.class,old::writeChanges);
        assertFalse(LegacyOptions.view(SodiumGameOptions.defaults()).isReadOnly());
    }
    @Test void oldAndNativeWritesCoexistWithoutResettingCeleritasOnlyFields() {
        var nativeOptions=SodiumGameOptions.defaults();
        var old=LegacyOptions.view(nativeOptions);
        assertFalse(old.performance.useCompactVertexFormat);
        assertTrue(old.quality.useQuadNormalsForShading);
        old.advanced.cpuRenderAheadLimit=7;
        old.quality.leavesQuality=me.jellysquid.mods.sodium.client.gui.SodiumGameOptions.GraphicsQuality.FAST;
        nativeOptions.performance.chunkBuilderThreads=4;
        nativeOptions.quality.chunkFadeInDuration=321;
        LegacyOptions.synchronize(nativeOptions);
        assertEquals(7,nativeOptions.advanced.cpuRenderAheadLimit);
        assertEquals(SodiumGameOptions.GraphicsQuality.FAST,nativeOptions.quality.leavesQuality);
        assertEquals(4,old.performance.chunkBuilderThreads);
        assertEquals(321,nativeOptions.quality.chunkFadeInDuration);
        var replacement=SodiumGameOptions.defaults();
        assertSame(old,LegacyOptions.view(replacement));
        assertEquals(3,old.advanced.cpuRenderAheadLimit);
        assertEquals(SodiumGameOptions.GraphicsQuality.DEFAULT,replacement.quality.leavesQuality);
    }
}
