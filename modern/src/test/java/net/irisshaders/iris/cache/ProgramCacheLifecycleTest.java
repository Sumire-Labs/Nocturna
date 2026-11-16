package net.irisshaders.iris.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ProgramCacheLifecycleTest {
    @TempDir Path dir;

    static void set(Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }

    @BeforeEach void prepare() throws Exception {
        ShaderDiskCache.configure(dir, "tests");
        set(ProgramBinaryCache.class, "driverId", "test-driver");
        ProgramBinaryCache.setEnabled(false);
        ProgramBinaryCache.usePack("pack");
    }

    @AfterEach void reset() throws Exception {
        set(ShaderDiskCache.class, "root", null);
        set(ProgramBinaryCache.class, "driverId", null);
        ProgramBinaryCache.usePack(null);
    }

    @Test void markerWaitsForAllWritesAndFailedWriteNeverPublishes() throws Exception {
        ProgramBinaryCache.beginFullCompile();
        var write = new CompletableFuture<Boolean>();
        set(ProgramBinaryCache.class, "fullCompileWrites", List.of(write));
        ProgramBinaryCache.finishFullCompile("settings");
        assertFalse(ProgramBinaryCache.wasCompiledFor("settings"));
        write.complete(true);
        assertTrue(ProgramBinaryCache.wasCompiledFor("settings"));
        ProgramBinaryCache.beginFullCompile();
        set(ProgramBinaryCache.class, "fullCompileWrites", List.of(CompletableFuture.completedFuture(false)));
        ProgramBinaryCache.finishFullCompile("different");
        assertFalse(ProgramBinaryCache.wasCompiledFor("different"));
    }

    @Test void deletedOrSupersededCompileCannotRecreateOldMarker() throws Exception {
        ProgramBinaryCache.beginFullCompile();
        var old = new CompletableFuture<Boolean>();
        set(ProgramBinaryCache.class, "fullCompileWrites", List.of(old));
        ProgramBinaryCache.finishFullCompile("old");
        ProgramBinaryCache.beginFullCompile();
        ProgramBinaryCache.finishFullCompile("new");
        old.complete(true);
        assertFalse(ProgramBinaryCache.wasCompiledFor("old"));
        assertTrue(ProgramBinaryCache.wasCompiledFor("new"));
        ProgramBinaryCache.beginFullCompile();
        var deleted = new CompletableFuture<Boolean>();
        set(ProgramBinaryCache.class, "fullCompileWrites", List.of(deleted));
        ProgramBinaryCache.finishFullCompile("deleted");
        ProgramBinaryCache.deleteAll(); deleted.complete(true);
        assertFalse(ProgramBinaryCache.wasCompiledFor("deleted"));
    }

    @Test void driverAndSettingsChangesMissMarkers() throws Exception {
        ProgramBinaryCache.beginFullCompile(); ProgramBinaryCache.finishFullCompile("settings");
        assertTrue(ProgramBinaryCache.wasCompiledFor("settings"));
        assertFalse(ProgramBinaryCache.wasCompiledFor("other"));
        set(ProgramBinaryCache.class, "driverId", "new-driver");
        assertFalse(ProgramBinaryCache.wasCompiledFor("settings"));
    }
}
