package org.embeddedt.embeddium.impl.extra.compat;

public final class PlatformRuntimeInformation {
    private static final PlatformRuntimeInformation INSTANCE = new PlatformRuntimeInformation();

    public static PlatformRuntimeInformation getInstance() {
        return INSTANCE;
    }

    public java.nio.file.Path getConfigDirectory() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
    }
}
