package net.irisshaders.iris.cache;

import net.minecraftforge.fml.loading.FMLLoader;
import org.embeddedt.embeddium.impl.util.PlatformUtil;
import org.taumc.celeritas.shaders.CeleritasShaders;

import java.nio.file.Files;

/** Matches AngelicaJar's version/size/mtime invalidation, using Forge's original mod-file path. */
public final class ShaderCacheRuntime {
    private ShaderCacheRuntime() { }

    public static void initialize() {
        try {
            var file = FMLLoader.getLoadingModList().getModFileById("embeddium");
            var jar = file.getFile().getFilePath();
            configureArtifact(jar, file.getMods().get(0).getVersion().toString(), PlatformUtil.getGameDir());
        } catch (Exception e) {
            CeleritasShaders.logger().warn("Could not initialize the shader disk cache", e);
        }
    }

    static void configureArtifact(java.nio.file.Path jar, String version, java.nio.file.Path gameDir)
            throws java.io.IOException {
        // As in Angelica, development class directories are not persisted automatically.
        if (!Files.isRegularFile(jar)) return;
        String salt = version + "|" + Files.size(jar) + "|" + Files.getLastModifiedTime(jar).toMillis();
        ShaderDiskCache.configure(gameDir.resolve("embeddium").resolve("shadercache"), salt);
    }
}
