package net.irisshaders.iris.uniforms.custom.cached;

import kroppeb.stareval.function.FunctionReturn;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniformFixedInputUniformsHolder;
import net.irisshaders.iris.uniforms.transforms.SmoothedFloat;
import net.irisshaders.iris.uniforms.transforms.SmoothedVec2f;
import org.joml.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.junit.jupiter.api.Assertions.*;

class UniformSupplierReuseTest {
    // A genuine interface-only supplier catches accidental casts back to mutable JOML implementations.
    private static <T> T view(Class<T> type, Object vector) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> method.invoke(vector, args)));
    }

    private static Object value(CachedUniform uniform) {
        var result = new FunctionReturn();
        uniform.writeTo(result);
        return result.objectReturn;
    }

    @Test
    void mutableIntSupplierRetainsItsOwnSnapshotAndAdapter() throws Exception {
        Vector2i input = new Vector2i(3, -9);
        Supplier<Vector2i> legacy = () -> input;
        var uniform = new Int2VectorCachedUniform("test", PER_FRAME, legacy);
        uniform.update();
        Vector2i output = (Vector2i) value(uniform);
        assertNotSame(input, output);
        int version = uniform.getVersion();
        uniform.update();
        assertEquals(version, uniform.getVersion());
        input.set(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(new Vector2i(3, -9), output);
        uniform.update();
        assertSame(output, value(uniform));
        assertEquals(input, output);
        assertEquals(version + 1, uniform.getVersion());

        var field = VectorCachedUniform.class.getDeclaredField("supplier");
        field.setAccessible(true);
        var adapter = (Supplier<?>) field.get(uniform);
        Object scratch = adapter.get();
        assertNotSame(input, scratch);
        for (int i = 0; i < 32; i++) {
            input.set(i, -i);
            assertSame(scratch, adapter.get());
            assertEquals(input, scratch);
        }
    }

    @Test
    void readOnlyIntSuppliersAndTwoCachesDoNotAlias() {
        Vector2i input = new Vector2i(5, 7);
        Vector2ic readOnly = view(Vector2ic.class, input);
        var first = new Int2VectorCachedUniform("first", PER_FRAME, () -> readOnly);
        var second = new Int2VectorCachedUniform("second", PER_FRAME, () -> readOnly);
        first.update();
        input.zero();
        second.update();
        assertEquals(new Vector2i(5, 7), value(first));
        assertEquals(new Vector2i(), value(second));
        assertNotSame(value(first), value(second));
        first.update();
        assertEquals(new Vector2i(), value(first));
    }

    @Test
    void floatCopiesPreserveAllComponentBitsAndReuseStorage() throws Exception {
        Vector4f input = new Vector4f(1, 2, 3, 4);
        Vector4fc readOnly = view(Vector4fc.class, input);
        var uniform = new Float4VectorCachedUniform("test", PER_FRAME, () -> readOnly);
        uniform.update();
        Vector4f output = (Vector4f) value(uniform);
        input.set(-0.0f, Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7fc00042), -13.5f);
        assertEquals(new Vector4f(1, 2, 3, 4), output);
        uniform.update();
        assertSame(output, value(uniform));
        for (int i = 0; i < 4; i++) {
            assertEquals(Float.floatToRawIntBits(input.get(i)), Float.floatToRawIntBits(output.get(i)));
        }
        int version = uniform.getVersion();
        uniform.update();
        assertEquals(version, uniform.getVersion());
        var field = VectorCachedUniform.class.getDeclaredField("supplier");
        field.setAccessible(true);
        var adapter = (Supplier<?>) field.get(uniform);
        assertSame(adapter.get(), adapter.get());
        assertNotSame(input, adapter.get());
    }

    @Test
    void fixedInputsAcceptTypedMutableAndReadOnlySuppliers() {
        Vector2i ints = new Vector2i(11, 12);
        Vector4f floats = new Vector4f(2, 3, 5, 7);
        Supplier<Vector2i> legacyInt = () -> ints;
        Supplier<Vector4f> legacyFloat = () -> floats;
        Supplier<Vector4fc> readOnlyFloat = () -> view(Vector4fc.class, floats);
        var builder = new CustomUniformFixedInputUniformsHolder.Builder();
        builder.uniform2i(PER_FRAME, "legacyInt", legacyInt);
        builder.uniform2i(PER_FRAME, "readOnlyInt", () -> view(Vector2ic.class, ints));
        builder.uniform4f(PER_FRAME, "legacyFloat", legacyFloat);
        builder.uniform4f(PER_FRAME, "readOnlyFloat", readOnlyFloat);
        builder.uniformTruncated3f(PER_FRAME, "truncated", readOnlyFloat);
        builder.uniform4fArray(PER_FRAME, "array", () -> new float[]{2, 3, 5, 7});
        var holder = builder.build();
        holder.updateAll();
        assertEquals(ints, value(holder.getUniform("legacyInt")));
        assertEquals(ints, value(holder.getUniform("readOnlyInt")));
        assertEquals(floats, value(holder.getUniform("legacyFloat")));
        assertEquals(floats, value(holder.getUniform("readOnlyFloat")));
        assertEquals(new Vector3f(2, 3, 5), value(holder.getUniform("truncated")));
        assertEquals(floats, value(holder.getUniform("array")));
        ints.zero(); floats.zero();
        assertEquals(new Vector4f(2, 3, 5, 7), value(holder.getUniform("readOnlyFloat")));
        holder.updateAll();
        assertEquals(new Vector4f(0), value(holder.getUniform("readOnlyFloat")));
        assertEquals(new Vector3f(), value(holder.getUniform("truncated")));
    }

    @Test
    void smoothingKeepsTheOriginalScalarValuesAndFreshResult() {
        var notifier = new FrameUpdateNotifier();
        Vector2i input = new Vector2i(12, 23);
        Supplier<Vector2i> legacy = () -> input;
        var mutable = new SmoothedVec2f(2, 3, legacy, notifier);
        var readOnly = new SmoothedVec2f(2, 3, () -> view(Vector2ic.class, input), notifier);
        var x = new SmoothedFloat(2, 3, () -> input.x, notifier);
        var y = new SmoothedFloat(2, 3, () -> input.y, notifier);
        assertEquals(new Vector2f(12, 23), readOnly.get());
        assertNotSame(readOnly.get(), readOnly.get());
        for (int i = 0; i < 4; i++) {
            input.set(i * 19, -i * 31);
            notifier.onNewFrame();
            var expected = new Vector2f(x.getAsFloat(), y.getAsFloat());
            assertEquals(expected, readOnly.get());
            assertEquals(expected, mutable.get());
        }
    }
}
