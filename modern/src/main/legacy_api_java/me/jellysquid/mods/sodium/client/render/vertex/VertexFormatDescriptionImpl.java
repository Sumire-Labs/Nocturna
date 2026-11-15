package me.jellysquid.mods.sodium.client.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import org.embeddedt.embeddium.impl.compat.legacy.LegacyVertexApi;

/** Retains the concrete descriptor used by the older translation shim. */
public final class VertexFormatDescriptionImpl implements VertexFormatDescription {
    private final org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription delegate;
    private final VertexFormat format;

    public VertexFormatDescriptionImpl(VertexFormat format, int id) {
        this(new org.embeddedt.embeddium.impl.render.vertex.VertexFormatDescriptionImpl(format, id), format);
    }

    public VertexFormatDescriptionImpl(org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription delegate,
                                       VertexFormat format) {
        this.delegate = delegate;
        this.format = format;
    }

    public boolean containsElement(CommonVertexAttribute attribute) {
        return this.delegate.containsElement(LegacyVertexApi.element(attribute));
    }
    public int getElementOffset(CommonVertexAttribute attribute) {
        return this.delegate.getElementOffset(LegacyVertexApi.element(attribute));
    }
    public int id() { return this.delegate.id(); }
    public int stride() { return this.delegate.stride(); }
    public boolean isSimpleFormat() { return this.delegate.isSimpleFormat(); }
    public VertexFormat format() { return this.format; }
    public org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription nativeDescription() { return this.delegate; }
}
