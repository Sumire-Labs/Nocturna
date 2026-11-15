package net.caffeinemc.mods.sodium.api.vertex.serializer;

import net.caffeinemc.mods.sodium.api.internal.DependencyInjection;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;

public interface VertexSerializerRegistry {
    VertexSerializerRegistry INSTANCE = org.embeddedt.embeddium.impl.compat.legacy.LegacyVertexApi.SERIALIZERS;

    static VertexSerializerRegistry instance() {
        return INSTANCE;
    }

    VertexSerializer get(VertexFormatDescription srcFormat, VertexFormatDescription dstFormat);
}
