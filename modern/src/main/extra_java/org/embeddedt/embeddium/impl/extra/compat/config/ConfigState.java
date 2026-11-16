package org.embeddedt.embeddium.impl.extra.compat.config;

import net.minecraft.resources.ResourceLocation;

public interface ConfigState {
    ResourceLocation UPDATE_ON_REBUILD = new ResourceLocation("nocturna", "update_on_rebuild");

    boolean readBooleanOption(ResourceLocation id);

    default int integerOptionMax(ResourceLocation id, int fallback) {
        return fallback;
    }
}
