package me.jellysquid.mods.sodium.client.compatibility.workarounds;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.OSInfo;
import me.jellysquid.mods.sodium.client.gl.arena.staging.FallbackStagingBuffer;
import me.jellysquid.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -PtestOpenGL on a host with an available desktop OpenGL driver. The window stays hidden. */
@Tag("opengl")
class AmdOpenGLTest {
    @Test
    void bufferUploadsAndStagingProduceTheSameDataOnARealContext() {
        var errors = GLFWErrorCallback.createPrint(System.err);
        var previousCallback = GLFW.glfwSetErrorCallback(errors);
        long window = MemoryUtil.NULL;
        try {
            assertTrue(GLFW.glfwInit(), "GLFW initialization");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            window = GLFW.glfwCreateWindow(64, 64, "Embeddium buffer test", MemoryUtil.NULL, MemoryUtil.NULL);
            assertNotEquals(MemoryUtil.NULL, window);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            var context = GLContextInfo.create();
            assertNotNull(context);
            System.out.println("Buffer test OpenGL: " + context);

            Workarounds.onContextInitialized(context);
            assertEquals(Workarounds.isAmdWindowsContext(OSInfo.getOS(), context),
                    Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_BUFFER_MAPPING));

            var device = RenderDevice.INSTANCE;
            device.makeActive();
            try (var commands = device.createCommandList()) {
                // Exercise both production paths on the same real driver; compare every requested index on readback.
                for (boolean workaround : new boolean[] {false, true}) {
                    Workarounds.onContextInitialized(workaround ? OSInfo.OS.WINDOWS : OSInfo.OS.LINUX,
                            new GLContextInfo("ATI Technologies Inc.", context.renderer(), context.version()));
                    for (var type : SharedQuadIndexBuffer.IndexType.VALUES) {
                        verifyIndices(commands, type);
                    }
                    verifyStaging(commands, workaround);
                    assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
                }
            } finally {
                device.makeInactive();
                Workarounds.onContextInitialized(context);
            }
        } finally {
            Workarounds.onContextInitialized(OSInfo.OS.UNKNOWN, null);
            GL.setCapabilities(null);
            if (window != MemoryUtil.NULL) {
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            GLFW.glfwSetErrorCallback(previousCallback);
            errors.free();
        }
    }

    private static void verifyIndices(CommandList commands, SharedQuadIndexBuffer.IndexType type) {
        var buffer = new SharedQuadIndexBuffer(commands, type);
        int elements = 6 * 100;
        var result = MemoryUtil.memAlloc(elements * type.getBytesPerElement());
        try {
            buffer.ensureCapacity(commands, elements);
            commands.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer.getBufferObject());
            GL15C.glGetBufferSubData(GL15C.GL_ARRAY_BUFFER, 0, result);
            int[] pattern = {0, 1, 2, 2, 3, 0};
            for (int i = 0; i < elements; i++) {
                int actual = type.getBytesPerElement() == 2
                        ? Short.toUnsignedInt(result.getShort(i * 2)) : result.getInt(i * 4);
                assertEquals(i / 6 * 4 + pattern[i % 6], actual);
            }
        } finally {
            MemoryUtil.memFree(result);
            buffer.delete(commands);
        }
    }

    private static void verifyStaging(CommandList commands, boolean workaround) {
        boolean oldSetting = SodiumClientMod.options().advanced.useAdvancedStagingBuffers;
        SodiumClientMod.options().advanced.useAdvancedStagingBuffers = true;
        var regions = new RenderRegionManager(commands);
        var destination = commands.createMutableBuffer();
        var source = MemoryUtil.memAlloc(128);
        var result = MemoryUtil.memAlloc(128);
        try {
            if (workaround) {
                assertInstanceOf(FallbackStagingBuffer.class, regions.getStagingBuffer());
            } else if (MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
                assertInstanceOf(MappedStagingBuffer.class, regions.getStagingBuffer());
            }
            commands.allocateStorage(destination, 128, GlBufferUsage.STATIC_DRAW);
            for (int i = 0; i < 128; i++) {
                source.put(i, (byte) (i * 7));
            }
            regions.getStagingBuffer().enqueueCopy(commands, source, destination, 0);
            regions.getStagingBuffer().flush(commands);
            commands.bindBuffer(GlBufferTarget.ARRAY_BUFFER, destination);
            GL15C.glGetBufferSubData(GL15C.GL_ARRAY_BUFFER, 0, result);
            for (int i = 0; i < 128; i++) {
                assertEquals(source.get(i), result.get(i));
            }
        } finally {
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(result);
            commands.deleteBuffer(destination);
            regions.delete(commands);
            SodiumClientMod.options().advanced.useAdvancedStagingBuffers = oldSetting;
        }
    }
}
