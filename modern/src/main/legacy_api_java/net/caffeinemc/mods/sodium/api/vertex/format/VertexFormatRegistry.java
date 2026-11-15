package net.caffeinemc.mods.sodium.api.vertex.format;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.internal.DependencyInjection;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = org.embeddedt.embeddium.impl.compat.legacy.LegacyVertexApi.FORMATS;

    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    VertexFormatDescription get(VertexFormat format);
}
