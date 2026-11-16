package org.embeddedt.embeddium.impl.gl.shader.uniform;

import org.embeddedt.embeddium.testutil.GlServiceHarness;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.lwjgl.LWJGLService;

import java.lang.reflect.Proxy;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MatrixUniformBufferTest {
    record Upload(int location, boolean transpose, FloatBuffer buffer, int[] bits) { }

    @AfterEach
    void clearService() {
        GlServiceHarness.clear();
    }

    @Test
    void matrix3MatchesOriginalColumnOrderAndKeepsOneBufferPerUniform() throws Exception {
        verify(GlUniformMatrix3f.class, Matrix3f.class, 9, "glUniformMatrix3fv");
    }

    @Test
    void matrix4MatchesOriginalColumnOrderAndKeepsOneBufferPerUniform() throws Exception {
        verify(GlUniformMatrix4f.class, Matrix4fc.class, 16, "glUniformMatrix4fv");
    }

    private static void verify(Class<?> original, Class<?> matrixType, int length, String glMethod) throws Exception {
        var uploads = new ArrayList<Upload>();
        GlServiceHarness.bind((LWJGLService) Proxy.newProxyInstance(LWJGLService.class.getClassLoader(),
                new Class[]{LWJGLService.class}, (proxy, method, args) -> {
                    assertEquals(glMethod, method.getName(), "No stack allocation or other GL calls are needed");
                    var buffer = (FloatBuffer) args[2];
                    assertTrue(buffer.isDirect());
                    assertEquals(ByteOrder.nativeOrder(), buffer.order());
                    assertEquals(0, buffer.position());
                    assertEquals(length, buffer.remaining());
                    int[] bits = new int[length];
                    for (int i = 0; i < length; i++) bits[i] = Float.floatToRawIntBits(buffer.get(i));
                    uploads.add(new Upload((int) args[0], (boolean) args[1], buffer, bits));
                    return null;
                }));
        var harness = GlServiceHarness.load(original);
        var first = harness.getConstructor(int.class).newInstance(7);
        var second = harness.getConstructor(int.class).newInstance(19);
        var set = harness.getMethod("set", matrixType);
        for (int iteration = 0; iteration < 64; iteration++) {
            float[] values = new float[length];
            for (int i = 0; i < length; i++) values[i] = iteration * 100 + i + .25f;
            if (iteration == 0) {
                values[0] = -0.0f;
                values[1] = Float.intBitsToFloat(0x7fc01234);
                values[2] = Float.POSITIVE_INFINITY;
            }
            Object matrix = length == 9 ? new Matrix3f().set(values) : new Matrix4f().set(values);
            set.invoke(first, matrix);
            set.invoke(second, matrix);
            int[] expected = new int[length];
            for (int i = 0; i < length; i++) expected[i] = Float.floatToRawIntBits(values[i]);
            for (int j = 0; j < 2; j++) {
                Upload upload = uploads.get(iteration * 2 + j);
                assertEquals(j == 0 ? 7 : 19, upload.location());
                assertFalse(upload.transpose());
                assertArrayEquals(expected, upload.bits(), "No stale values from earlier uploads");
                assertSame(uploads.get(j).buffer(), upload.buffer());
            }
        }
        assertNotSame(uploads.get(0).buffer(), uploads.get(1).buffer());
    }
}
