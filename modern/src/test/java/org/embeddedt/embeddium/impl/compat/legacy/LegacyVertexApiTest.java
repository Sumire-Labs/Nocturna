package org.embeddedt.embeddium.impl.compat.legacy;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.IntList;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import org.embeddedt.embeddium.impl.mixin.core.render.VertexFormatAccessor;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LegacyVertexApiTest {
    static final class HeadlessFormat extends VertexFormat implements VertexFormatAccessor {
        HeadlessFormat(ImmutableMap<String,VertexFormatElement> elements) { super(elements); }
        public IntList getOffsets() { return LegacyVertexApi.offsets(this); }
    }
    @Test void oldAndNativeFormatsShareIdentityOffsetsAndSerializerMemoryLayout() {
        var source=new HeadlessFormat(ImmutableMap.of("Position",DefaultVertexFormat.ELEMENT_POSITION,"Color",DefaultVertexFormat.ELEMENT_COLOR));
        var destination=new HeadlessFormat(ImmutableMap.of("Color",DefaultVertexFormat.ELEMENT_COLOR,"Position",DefaultVertexFormat.ELEMENT_POSITION));
        var oldSource=VertexFormatRegistry.instance().get(source);
        var oldDestination=VertexFormatRegistry.instance().get(destination);
        assertSame(oldSource,VertexFormatRegistry.instance().get(source));
        assertSame(org.embeddedt.embeddium.api.vertex.format.VertexFormatRegistry.instance().get(source),LegacyVertexApi.unwrap(oldSource));
        assertEquals(12,oldSource.getElementOffset(CommonVertexAttribute.COLOR));
        assertEquals(0,oldDestination.getElementOffset(CommonVertexAttribute.COLOR));
        var serializer=VertexSerializerRegistry.instance().get(oldSource,oldDestination);
        try(var stack=MemoryStack.stackPush()) {
            long from=stack.nmalloc(32),to=stack.nmalloc(32);
            for(int i=0;i<8;i++)MemoryUtil.memPutInt(from+i*4,100+i);
            serializer.serialize(from,to,2);
            assertEquals(103,MemoryUtil.memGetInt(to));assertEquals(100,MemoryUtil.memGetInt(to+4));
            assertEquals(107,MemoryUtil.memGetInt(to+16));assertEquals(104,MemoryUtil.memGetInt(to+20));
        }
        var legacy=me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescription.translateModern(oldSource);
        assertEquals(IntList.of(0,12),legacy.getOffsets());
        assertEquals(oldSource.stride(),legacy.stride);
    }
    @Test void oldWriterHelpersAdaptNativeConsumersAndTrackLiveEligibility() {
        boolean[] enabled={true};var writes=new AtomicInteger();
        var consumer=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{VertexConsumer.class,org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter.class},(p,m,a)->switch(m.getName()) {
                    case "canUseIntrinsics" -> enabled[0];
                    case "push" -> {writes.incrementAndGet();assertEquals(3,a[2]);yield null;}
                    default -> throw new AssertionError(m.getName());
                });
        var writer=net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.of(consumer);
        assertNotNull(net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(consumer));
        assertNotNull(me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter.of(consumer));
        var format=new HeadlessFormat(ImmutableMap.of("Position",DefaultVertexFormat.ELEMENT_POSITION));
        writer.push(null,123L,3,VertexFormatRegistry.instance().get(format));assertEquals(1,writes.get());
        enabled[0]=false;assertFalse(writer.canUseIntrinsics());
        assertNull(net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(consumer));
        assertThrows(IllegalArgumentException.class,()->net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.of(consumer));
    }
    @Test void nativeRenderingCanUseOldOnlyAddonWritersAndLegacyHelperDoesNotCastThemBlindly() {
        boolean[] enabled={true};var writes=new AtomicInteger();
        var consumer=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{VertexConsumer.class,net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.class},(p,m,a)->switch(m.getName()) {
                    case "canUseIntrinsics" -> enabled[0];
                    case "push" -> {writes.incrementAndGet();yield null;}
                    default -> throw new AssertionError(m.getName());
                });
        var writer=org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter.of(consumer);
        assertNotNull(me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter.of(consumer));
        var format=new HeadlessFormat(ImmutableMap.of("Position",DefaultVertexFormat.ELEMENT_POSITION));
        writer.push(null,123L,1,org.embeddedt.embeddium.api.vertex.format.VertexFormatRegistry.instance().get(format));
        assertEquals(1,writes.get());enabled[0]=false;assertFalse(writer.canUseIntrinsics());
        assertNull(org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter.tryOf(consumer));
    }
}
