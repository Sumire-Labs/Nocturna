package me.jellysquid.mods.sodium.client.compatibility.workarounds;

import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.OSInfo.OS;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferMapping;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class SharedQuadIndexBufferTest {
    @AfterEach
    void resetContext() {
        Workarounds.onContextInitialized(OS.UNKNOWN, null);
    }

    @Test
    void affectedContextsUploadIdenticalShortAndIntegerIndicesWithoutMapping() {
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("ATI Technologies Inc.", "Radeon", "4.6"));
        for (var type : SharedQuadIndexBuffer.IndexType.VALUES) {
            var calls = new RecordingCommands();
            var buffer = new SharedQuadIndexBuffer(calls.commands(), type);
            buffer.ensureCapacity(calls.commands(), 6);
            assertIndices(type, calls.data);
            assertEquals(1, calls.uploads);
            assertEquals(0, calls.allocations);
            assertEquals(0, calls.mappings);
            assertEquals(0, calls.unmaps);
            buffer.ensureCapacity(calls.commands(), 12);
            assertEquals(1, calls.uploads);
        }
    }

    @Test
    void otherContextsKeepTheMappedUploadPath() {
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("NVIDIA Corporation", "GeForce", "4.6"));
        for (var type : SharedQuadIndexBuffer.IndexType.VALUES) {
            var calls = new RecordingCommands();
            var buffer = new SharedQuadIndexBuffer(calls.commands(), type);
            buffer.ensureCapacity(calls.commands(), 6);
            assertIndices(type, calls.data);
            assertEquals(0, calls.uploads);
            assertEquals(1, calls.allocations);
            assertEquals(1, calls.mappings);
            assertEquals(1, calls.unmaps);
        }
    }

    @Test
    void failedUploadsCanRetryAndBuffersCanGrow() {
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("AMD", "Radeon", "4.6"));
        var calls = new RecordingCommands();
        var buffer = new SharedQuadIndexBuffer(calls.commands(), SharedQuadIndexBuffer.IndexType.INTEGER);
        calls.failUpload = true;
        assertThrows(IllegalStateException.class, () -> buffer.ensureCapacity(calls.commands(), 6));
        calls.failUpload = false;
        buffer.ensureCapacity(calls.commands(), 6);
        int initialSize = calls.data.remaining();
        buffer.ensureCapacity(calls.commands(), 6 * 20000);
        assertTrue(calls.data.remaining() > initialSize);
        assertEquals(3, calls.uploads);
        assertEquals(0, calls.mappings);
        assertIndices(SharedQuadIndexBuffer.IndexType.INTEGER, calls.data);
    }

    private static void assertIndices(SharedQuadIndexBuffer.IndexType type, ByteBuffer data) {
        int bytes = type.getBytesPerElement();
        int count = data.remaining() / bytes;
        int[] pattern = {0, 1, 2, 2, 3, 0};
        for (int i = 0; i < count; i++) {
            int expected = (i / 6) * 4 + pattern[i % 6];
            int actual = bytes == 2 ? Short.toUnsignedInt(data.getShort(i * bytes)) : data.getInt(i * bytes);
            assertEquals(expected, actual, "Index " + i);
        }
    }

    private static class RecordingCommands {
        int allocations;
        int mappings;
        int unmaps;
        int uploads;
        boolean failUpload;
        ByteBuffer data;

        CommandList commands() {
            return (CommandList) Proxy.newProxyInstance(CommandList.class.getClassLoader(),
                    new Class<?>[] {CommandList.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "createMutableBuffer":
                                return null; // Opaque handle: this recorder never creates or accesses an OpenGL object.
                            case "allocateStorage":
                                this.allocations++;
                                return null;
                            case "mapBuffer":
                                this.mappings++;
                                this.data = ByteBuffer.allocateDirect(Math.toIntExact((long) args[2]))
                                        .order(ByteOrder.nativeOrder());
                                return new GlBufferMapping(null, this.data);
                            case "unmap":
                                this.unmaps++;
                                return null;
                            case "uploadData":
                                this.uploads++;
                                if (this.failUpload) {
                                    throw new IllegalStateException("Simulated upload failure");
                                }
                                var source = (ByteBuffer) args[1];
                                this.data = ByteBuffer.allocate(source.remaining()).order(ByteOrder.nativeOrder());
                                this.data.put(source.duplicate()).flip();
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
