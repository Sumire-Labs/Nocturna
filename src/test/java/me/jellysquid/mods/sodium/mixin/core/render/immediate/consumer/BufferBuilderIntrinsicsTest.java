package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class BufferBuilderIntrinsicsTest {
    private static final VertexFormatDescription SIMPLE = new Format(true);
    private static final VertexFormatDescription COMPLEX = new Format(false);

    @Test
    void matchesUpstreamEligibilityForMissingFormatsAndBuffers() throws Exception {
        var builder = new TestBuilder();
        for (var buffer : new ByteBuffer[] {null, ByteBuffer.allocate(16), ByteBuffer.allocateDirect(16)}) {
            configure(builder, null, buffer);
            assertFalse(builder.canUseIntrinsics(), "No format is never eligible");
            configure(builder, COMPLEX, buffer);
            assertFalse(builder.canUseIntrinsics(), "Complex formats are never eligible");
        }
        configure(builder, SIMPLE, null);
        assertFalse(builder.canUseIntrinsics(), "A fake builder without backing storage is not a writer");
    }

    @Test
    void preservesUpstreamNonNullCheckWithoutAddingCapacityOrDirectnessRequirements() throws Exception {
        var builder = new TestBuilder();
        for (var buffer : new ByteBuffer[] {ByteBuffer.allocate(0), ByteBuffer.allocateDirect(0),
                ByteBuffer.allocate(16), ByteBuffer.allocateDirect(16)}) {
            configure(builder, SIMPLE, buffer);
            assertTrue(builder.canUseIntrinsics());
            assertSame(builder, VertexBufferWriter.tryOf(builder));
            assertSame(builder, VertexBufferWriter.of(builder));
        }
    }

    @Test
    void publicWriterSelectionTracksBufferRemovalAndRestoration() throws Exception {
        var builder = new TestBuilder();
        configure(builder, SIMPLE, ByteBuffer.allocateDirect(16));
        assertSame(builder, VertexBufferWriter.tryOf(builder));
        configure(builder, SIMPLE, null);
        assertNull(VertexBufferWriter.tryOf(builder));
        assertThrows(IllegalArgumentException.class, () -> VertexBufferWriter.of(builder));
        configure(builder, SIMPLE, ByteBuffer.allocateDirect(16));
        assertSame(builder, VertexBufferWriter.of(builder));
        configure(builder, COMPLEX, builder.sodium$getBuffer());
        assertNull(VertexBufferWriter.tryOf(builder));
        configure(builder, null, builder.sodium$getBuffer());
        assertNull(VertexBufferWriter.tryOf(builder));
    }

    @Test
    void existingSodiumDelegateUsesTheSameLiveEligibilityDecision() throws Exception {
        var builder = new TestBuilder();
        configure(builder, SIMPLE, null);
        var delegate = new SodiumBufferBuilder(builder);
        assertFalse(delegate.canUseIntrinsics());
        assertNull(VertexBufferWriter.tryOf(delegate));
        assertThrows(IllegalArgumentException.class, () -> VertexBufferWriter.of(delegate));
        configure(builder, SIMPLE, ByteBuffer.allocateDirect(16));
        assertTrue(delegate.canUseIntrinsics());
        assertSame(delegate, VertexBufferWriter.tryOf(delegate));
        configure(builder, SIMPLE, null);
        assertFalse(delegate.canUseIntrinsics());
    }

    private static void configure(TestBuilder builder, VertexFormatDescription format, ByteBuffer buffer)
            throws Exception {
        var formatField = BufferBuilderMixin.class.getDeclaredField("format");
        formatField.setAccessible(true);
        formatField.set(builder, format);
        var bufferField = BufferBuilderMixin.class.getDeclaredField("buffer");
        bufferField.setAccessible(true);
        bufferField.set(builder, buffer);
    }

    private record Format(boolean isSimpleFormat) implements VertexFormatDescription {
        @Override public int id() { return 0; }
        @Override public int stride() { return 4; }
        @Override public boolean containsElement(CommonVertexAttribute element) { return false; }
        @Override public int getElementOffset(CommonVertexAttribute element) {
            throw new UnsupportedOperationException("The test format has no attributes");
        }
    }

    /** Runs the production mixin predicate/getters; all unrelated vertex operations must remain unused. */
    private static final class TestBuilder extends BufferBuilderMixin {
        @Override protected void ensureCapacity(int size) { throw unexpectedWrite(); }
        @Override public VertexConsumer vertex(double x, double y, double z) { throw unexpectedWrite(); }
        @Override public VertexConsumer color(int r, int g, int b, int a) { throw unexpectedWrite(); }
        @Override public VertexConsumer uv(float u, float v) { throw unexpectedWrite(); }
        @Override public VertexConsumer overlayCoords(int u, int v) { throw unexpectedWrite(); }
        @Override public VertexConsumer uv2(int u, int v) { throw unexpectedWrite(); }
        @Override public VertexConsumer normal(float x, float y, float z) { throw unexpectedWrite(); }
        @Override public void endVertex() { throw unexpectedWrite(); }

        private static AssertionError unexpectedWrite() {
            return new AssertionError("Eligibility checks must not allocate or write vertices");
        }
    }
}
