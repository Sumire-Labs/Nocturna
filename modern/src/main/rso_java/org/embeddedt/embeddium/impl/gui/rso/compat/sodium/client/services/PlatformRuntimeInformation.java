package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.services;

public final class PlatformRuntimeInformation {
    private static final PlatformRuntimeInformation INSTANCE = new PlatformRuntimeInformation();

    public static PlatformRuntimeInformation getInstance() {
        return INSTANCE;
    }

    public java.nio.file.Path getConfigDirectory() {
        return org.embeddedt.embeddium.impl.util.PlatformUtil.getConfigDir();
    }

    public boolean isDevelopmentEnvironment() {
        return org.embeddedt.embeddium.impl.util.PlatformUtil.isDevelopmentEnvironment();
    }
}
