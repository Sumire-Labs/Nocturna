package net.irisshaders.iris.cache;

import net.irisshaders.iris.config.IrisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

class ShaderCacheRuntimeTest {
    @TempDir Path dir;

    @AfterEach void reset() throws Exception {
        ProgramCacheLifecycleTest.set(ShaderDiskCache.class, "root", null);
        ProgramBinaryCache.setEnabled(false);
    }

    @Test void jarIdentityInvalidatesAndDevelopmentDirectoriesDoNotPersist() throws Exception {
        ShaderCacheRuntime.configureArtifact(dir, "1", dir.resolve("game"));
        assertFalse(ShaderDiskCache.isEnabled());
        var jar = dir.resolve("nocturna.jar"); Files.write(jar, new byte[]{1});
        ShaderCacheRuntime.configureArtifact(jar, "1", dir.resolve("game"));
        ShaderDiskCache.put(ShaderDiskCache.key("test").str("a"), new byte[]{7});
        ShaderCacheRuntime.configureArtifact(jar, "1", dir.resolve("game"));
        assertNotNull(ShaderDiskCache.get(ShaderDiskCache.key("test").str("a")));
        ShaderCacheRuntime.configureArtifact(jar, "2", dir.resolve("game"));
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("test").str("a")));
        ShaderDiskCache.put(ShaderDiskCache.key("test").str("a"), new byte[]{7});
        var unchangedTime = Files.getLastModifiedTime(jar);
        Files.write(jar, new byte[]{1, 2}); Files.setLastModifiedTime(jar, unchangedTime);
        ShaderCacheRuntime.configureArtifact(jar, "2", dir.resolve("game"));
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("test").str("a")));
        ShaderDiskCache.put(ShaderDiskCache.key("test").str("a"), new byte[]{7});
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 2000));
        ShaderCacheRuntime.configureArtifact(jar, "2", dir.resolve("game"));
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("test").str("a")));
    }

    @Test void compiledProgramSavingDefaultsOffAndRoundTripsThroughConfig() throws Exception {
        var file = dir.resolve("options.properties");
        var config = new IrisConfig(file); config.initialize();
        assertFalse(config.shouldSaveCompiledShaders());
        config.setSaveCompiledShaders(true);
        var reloaded = new IrisConfig(file); reloaded.load();
        assertTrue(reloaded.shouldSaveCompiledShaders());
        reloaded.setSaveCompiledShaders(false);
        config.load(); assertFalse(config.shouldSaveCompiledShaders());
    }
}
