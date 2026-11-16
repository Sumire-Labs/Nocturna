package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizonRendererTest {
    @Test
    void verticesMatchThePinnedIrisGeometryBitForBit() {
        var radii = new ArrayList<>(List.of(Integer.MIN_VALUE, -256, -1, 0, 1, 16, 32, 128, 255, 256,
            257, 512, 1024, Integer.MAX_VALUE));
        var random = new Random(2941);
        for (int i = 0; i < 256; i++) radii.add(random.nextInt());
        for (int radius : radii) {
            var actual = new Positions();
            var expected = new Positions();
            HorizonRenderer.buildHorizon(radius, actual);
            IrisHorizonReference.buildHorizon(radius, expected);
            assertEquals(expected.bits, actual.bits, "radius " + radius);
            assertEquals(10, actual.bits.size() / 3);
        }
    }

    @Test
    void nativeFanBufferHasTenPositionsAndEightTriangles() {
        var builder = new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        HorizonRenderer.buildHorizon(512, builder);
        var rendered = builder.end();
        try {
            var state = rendered.drawState();
            assertEquals(VertexFormat.Mode.TRIANGLE_FAN, state.mode());
            assertEquals(10, state.vertexCount());
            assertEquals(10, state.indexCount());
            assertEquals(8, state.indexCount() - 2);
            var bytes = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
            assertEquals(120, bytes.remaining());
            var reference = new Positions();
            IrisHorizonReference.buildHorizon(512, reference);
            for (int i = 0; i < reference.bits.size(); i++) {
                assertEquals(reference.bits.get(i).intValue(), Float.floatToRawIntBits(bytes.getFloat(i * 4)));
            }
        } finally {
            rendered.release();
        }
    }

    @Test
    void originalFloatAngleRoundingAndRadiusClampArePreserved() {
        var atLimit = new Positions();
        var beyondLimit = new Positions();
        HorizonRenderer.buildHorizon(256, atLimit);
        HorizonRenderer.buildHorizon(Integer.MAX_VALUE, beyondLimit);
        assertEquals(atLimit.bits, beyondLimit.bits);
        assertEquals(Float.floatToRawIntBits(-16.0F), atLimit.bits.get(1).intValue());
        assertEquals(Float.floatToRawIntBits(16.0F), atLimit.bits.get(4).intValue());
        // Iris rounds the angle to float before sin/cos; its closing point is not bit-identical to the first.
        assertNotEquals(atLimit.bits.get(5), atLimit.bits.get(29));
        assertTrue(Math.abs(Float.intBitsToFloat(atLimit.bits.get(29))) < 0.001F);
    }

    private static final class Positions implements VertexConsumer {
        final List<Integer> bits = new ArrayList<>();

        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.bits.add(Float.floatToRawIntBits((float) x));
            this.bits.add(Float.floatToRawIntBits((float) y));
            this.bits.add(Float.floatToRawIntBits((float) z));
            return this;
        }
        @Override public void endVertex() {}
        @Override public VertexConsumer color(int r, int g, int b, int a) { throw new AssertionError("color"); }
        @Override public VertexConsumer uv(float u, float v) { throw new AssertionError("uv"); }
        @Override public VertexConsumer overlayCoords(int u, int v) { throw new AssertionError("overlay"); }
        @Override public VertexConsumer uv2(int u, int v) { throw new AssertionError("light"); }
        @Override public VertexConsumer normal(float x, float y, float z) { throw new AssertionError("normal"); }
        @Override public void defaultColor(int r, int g, int b, int a) { throw new AssertionError("default color"); }
        @Override public void unsetDefaultColor() { throw new AssertionError("unset color"); }
    }
}
