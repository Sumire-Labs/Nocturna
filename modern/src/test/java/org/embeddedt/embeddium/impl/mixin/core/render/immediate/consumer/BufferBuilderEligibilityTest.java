package org.embeddedt.embeddium.impl.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class BufferBuilderEligibilityTest {
    @Test void requiresFastFormatAndBackingBufferWithoutExtraCapacityOrDirectnessConditions() throws Exception {
        var builder=new TestBuilder();
        var fast=BufferBuilderMixin.class.getDeclaredField("embeddium$fastFormat");fast.setAccessible(true);
        var backing=BufferBuilderMixin.class.getDeclaredField("buffer");backing.setAccessible(true);
        for(boolean eligible:new boolean[]{false,true}) {
            fast.setBoolean(builder,eligible);
            for(var buffer:new ByteBuffer[]{null,ByteBuffer.allocate(0),ByteBuffer.allocateDirect(0),ByteBuffer.allocate(32),ByteBuffer.allocateDirect(32)}) {
                backing.set(builder,buffer);
                boolean expected=eligible&&buffer!=null;
                assertEquals(expected,builder.canUseIntrinsics());
                assertEquals(expected,org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter.tryOf(builder)!=null);
                assertEquals(expected,net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(builder)!=null);
            }
        }
    }
    private static final class TestBuilder extends BufferBuilderMixin {
        protected void ensureCapacity(int size){throw new AssertionError("Unexpected allocation");}
        public VertexConsumer vertex(double x,double y,double z){throw unexpected();}
        public VertexConsumer color(int r,int g,int b,int a){throw unexpected();}
        public VertexConsumer uv(float u,float v){throw unexpected();}
        public VertexConsumer overlayCoords(int u,int v){throw unexpected();}
        public VertexConsumer uv2(int u,int v){throw unexpected();}
        public VertexConsumer normal(float x,float y,float z){throw unexpected();}
        public void endVertex(){throw unexpected();}
        public com.mojang.blaze3d.vertex.VertexFormatElement currentElement(){throw unexpected();}
        public void nextElement(){throw unexpected();}
        public void putByte(int offset,byte value){throw unexpected();}
        public void putShort(int offset,short value){throw unexpected();}
        public void putFloat(int offset,float value){throw unexpected();}
        private static AssertionError unexpected(){return new AssertionError("Unexpected vertex write");}
    }
}
