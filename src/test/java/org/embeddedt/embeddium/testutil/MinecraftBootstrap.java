package org.embeddedt.embeddium.testutil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftBootstrap {
    private MinecraftBootstrap() {
    }

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
}
