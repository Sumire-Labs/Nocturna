package org.embeddedt.embeddium.impl.compat.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/** Bridges the public 0.5.x vertex contracts, keeping opaque format IDs and native layouts separate. */
public final class LegacyVertexApi {
    private static final Map<org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription,
            VertexFormatDescription> OLD_DESCRIPTIONS = new IdentityHashMap<>();
    private static final Map<VertexFormatDescription,
            org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription> CUSTOM_DESCRIPTIONS = new IdentityHashMap<>();
    // Native registry IDs are non-negative map sizes. Compatibility-only formats use a disjoint negative range.
    private static final AtomicInteger NEXT_CUSTOM_ID = new AtomicInteger(-1);

    public static final VertexFormatRegistry FORMATS = format -> wrap(
            org.embeddedt.embeddium.api.vertex.format.VertexFormatRegistry.instance().get(format), format);
    public static final VertexSerializerRegistry SERIALIZERS = (source, destination) -> {
        var serializer = org.embeddedt.embeddium.api.vertex.serializer.VertexSerializerRegistry.instance()
                .get(unwrap(source), unwrap(destination));
        return serializer::serialize;
    };

    private LegacyVertexApi() { }

    public static synchronized VertexFormatDescription wrap(
            org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription description, VertexFormat format) {
        return OLD_DESCRIPTIONS.computeIfAbsent(description,
                value -> new me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescriptionImpl(value, format));
    }

    public static synchronized org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription unwrap(
            VertexFormatDescription description) {
        if (description instanceof me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescriptionImpl wrapped) {
            return wrapped.nativeDescription();
        }
        return CUSTOM_DESCRIPTIONS.computeIfAbsent(description, value -> {
            int id = NEXT_CUSTOM_ID.getAndUpdate(previous -> previous == Integer.MIN_VALUE ? previous : previous - 1);
            if (id == Integer.MIN_VALUE) throw new IllegalStateException("Legacy vertex format ID space exhausted");
            var elements = new ArrayList<VertexFormatElement>();
            for (var attribute : CommonVertexAttribute.values()) {
                if (value.containsElement(attribute)) elements.add(element(attribute));
            }
            var immutable = List.copyOf(elements);
            return new org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription() {
                public boolean containsElement(VertexFormatElement element) {
                    var attribute = CommonVertexAttribute.getCommonType(element);
                    return attribute != null && value.containsElement(attribute);
                }
                public int getElementOffset(VertexFormatElement element) {
                    var attribute = CommonVertexAttribute.getCommonType(element);
                    if (attribute == null) throw new NoSuchElementException("Unknown legacy attribute: " + element);
                    return value.getElementOffset(attribute);
                }
                public Collection<VertexFormatElement> getElements() { return immutable; }
                public int id() { return id; }
                public int stride() { return value.stride(); }
                public boolean isSimpleFormat() { return value.isSimpleFormat(); }
            };
        });
    }

    public static VertexFormatElement element(CommonVertexAttribute attribute) {
        return switch (attribute) {
            case POSITION -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.POSITION;
            case COLOR -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.COLOR;
            case TEXTURE -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.TEXTURE;
            case OVERLAY -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.OVERLAY;
            case LIGHT -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.LIGHT;
            case NORMAL -> org.embeddedt.embeddium.api.vertex.attributes.CommonVertexAttribute.NORMAL;
        };
    }

    public static VertexBufferWriter writer(VertexConsumer consumer) {
        var writer = org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter.tryOf(consumer);
        return writer == null ? null : new Writer(writer);
    }

    public static me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter legacyWriter(VertexBufferWriter writer) {
        if (writer instanceof me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter legacy) return legacy;
        return new me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter() {
            public void push(MemoryStack stack, long ptr, int count,
                             me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescription format) {
                writer.push(stack, ptr, count, FORMATS.get(format.format()));
            }
            public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
                writer.push(stack, ptr, count, format);
            }
            public boolean canUseIntrinsics() { return writer.canUseIntrinsics(); }
        };
    }

    public static org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter modernWriter(VertexConsumer consumer) {
        if (!(consumer instanceof VertexBufferWriter old) || !old.canUseIntrinsics()) return null;
        return new org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter() {
            public void push(MemoryStack stack, long ptr, int count,
                             org.embeddedt.embeddium.api.vertex.format.VertexFormatDescription format) {
                var vanilla = format instanceof org.embeddedt.embeddium.impl.render.vertex.VertexFormatDescriptionImpl impl ? impl.format() : null;
                old.push(stack, ptr, count, wrap(format, vanilla));
            }
            public boolean canUseIntrinsics() { return old.canUseIntrinsics(); }
        };
    }

    public static IntList offsets(VertexFormat format) {
        var result = new IntArrayList(format.getElements().size());
        int offset = 0;
        for (var element : format.getElements()) {
            result.add(offset);
            offset += element.getByteSize();
        }
        return result;
    }

    private record Writer(org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter delegate)
            implements me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter {
        public void push(MemoryStack stack, long pointer, int count, VertexFormatDescription format) {
            this.delegate.push(stack, pointer, count, unwrap(format));
        }
        public void push(MemoryStack stack, long pointer, int count,
                         me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescription format) {
            this.delegate.push(stack, pointer, count,
                    org.embeddedt.embeddium.api.vertex.format.VertexFormatRegistry.instance().get(format.format()));
        }
        public boolean canUseIntrinsics() { return this.delegate.canUseIntrinsics(); }
    }
}
