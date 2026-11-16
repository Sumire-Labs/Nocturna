package org.embeddedt.embeddium.validation;

import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.cache.ShaderCacheWorkers;
import net.irisshaders.iris.cache.ShaderDiskCache;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Driver-backed cold/hit/rejection validation, isolated from the product and the user's normal shader cache. */
final class ShaderCacheValidation {
    private static final String VERTEX = "#version 150\nin vec2 Position; void main(){gl_Position=vec4(Position,0,1);}";
    private static final String FRAGMENT = "#version 150\nuniform vec4 color; out vec4 result; void main(){result=color;}";

    static void verify() throws Exception {
        if (!ProgramBinaryCache.isSupported()) {
            System.out.println("SKIP program binary driver test: unsupported by this GL context");
            return;
        }
        Path directory = Path.of("embeddium", "shadercache-validation").toAbsolutePath();
        ShaderDiskCache.configure(directory, "validation-" + UUID.randomUUID());
        ProgramBinaryCache.setEnabled(true); ProgramBinaryCache.usePack("validation");
        var sources = new EnumMap<ShaderType, String>(ShaderType.class);
        sources.put(ShaderType.VERTEX, VERTEX); sources.put(ShaderType.FRAGMENT, FRAGMENT);
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        try {
            var cold = ProgramBuilder.submit("cache-validation", sources, Map.of("Position", 0));
            require(!hit(cold), "First build unexpectedly hit disk");
            int first = cold.finish();
            byte[] expected = draw(first, 0);
            GL20C.glDeleteProgram(first);
            awaitWrites();

            var warm = ProgramBuilder.submit("cache-validation", sources, Map.of("Position", 0));
            require(hit(warm), "Warm build compiled shader stages");
            int loaded = warm.finish();
            require(Arrays.equals(expected, draw(loaded, 0)), "Cached program rendered different pixels");
            GL20C.glDeleteProgram(loaded);

            var moved = ProgramBuilder.submit("cache-validation", sources, Map.of("Position", 3));
            require(!hit(moved), "Attribute binding change did not invalidate the program");
            int rebound = moved.finish();
            require(Arrays.equals(expected, draw(rebound, 3)), "Changed attribute location rendered incorrectly");
            GL20C.glDeleteProgram(rebound);

            var key = ProgramCreator.cacheKey(sources, Map.of("Position", 0));
            var field = ProgramBinaryCache.Key.class.getDeclaredField("diskKey"); field.setAccessible(true);
            var diskKey = (ShaderDiskCache.Key) field.get(key);
            ShaderDiskCache.putBlob(diskKey, "-1", new byte[]{1, 2, 3, 4});
            var rejected = ProgramBuilder.submit("cache-validation", sources, Map.of("Position", 0));
            require(!hit(rejected), "Driver-rejected program did not fall back to source");
            // GL_INVALID_ENUM is expected from the deliberately invalid binary format.
            while (GL11C.glGetError() != GL11C.GL_NO_ERROR) { }
            int recovered = rejected.finish();
            require(Arrays.equals(expected, draw(recovered, 0)), "Fallback program rendered different pixels");
            GL20C.glDeleteProgram(recovered);

            sources.put(ShaderType.FRAGMENT, FRAGMENT.replace("result=color", "result=color.bgra"));
            var changed = ProgramBuilder.submit("cache-validation", sources, Map.of("Position", 0));
            require(!hit(changed), "Changed shader source hit an old binary");
            int changedProgram = changed.finish();
            require(!Arrays.equals(expected, draw(changedProgram, 0)), "Changed shader source was not rendered");
            GL20C.glDeleteProgram(changedProgram);

            if (net.irisshaders.iris.gl.IrisRenderSystem.supportsCompute()) {
            var computeSources = Map.of(ShaderType.COMPUTE,
                    "#version 430\nlayout(local_size_x=1) in; void main(){}\n");
            var compute = ProgramBuilder.submitCompute("cache-compute", computeSources.get(ShaderType.COMPUTE));
            int computeProgram = compute.finish(); GL20C.glDeleteProgram(computeProgram); awaitWrites();
            var warmCompute = ProgramBuilder.submitCompute("cache-compute", computeSources.get(ShaderType.COMPUTE));
            require(hit(warmCompute), "Compute cache did not hit");
            GL20C.glDeleteProgram(warmCompute.finish());
            }
            ProgramBinaryCache.setEnabled(false);
            require(ProgramCreator.cacheKey(sources, Map.of("Position", 0)) == null, "Disabled cache still active");
            require(GL11C.glGetError() == GL11C.GL_NO_ERROR, "Unexpected OpenGL error after cache tests");
            System.out.println("PASS shader cache: cold/hit pixel identity, attribute/source invalidation, driver rejection fallback, compute hit, disabled path");
        } finally {
            GL20C.glUseProgram(previous);
            awaitWrites(); ProgramBinaryCache.setEnabled(false); ProgramBinaryCache.usePack(null);
        }
    }

    private static void awaitWrites() throws Exception {
        var field = ShaderCacheWorkers.class.getDeclaredField("inFlight"); field.setAccessible(true);
        var count = (java.util.concurrent.atomic.AtomicInteger) field.get(null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (count.get() != 0) {
            if (System.nanoTime() > deadline) throw new AssertionError("Cache writes did not finish");
            Thread.sleep(5);
        }
    }

    private static boolean hit(ProgramCreator.Submitted submitted) throws Exception {
        var field = ProgramCreator.Submitted.class.getDeclaredField("shaders"); field.setAccessible(true);
        return ((List<?>) field.get(submitted)).isEmpty();
    }

    private static byte[] draw(int program, int attribute) {
        int oldFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        int oldRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
        int[] viewport = new int[4]; GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        int fbo = GL30C.glGenFramebuffers(), target = GL30C.glGenRenderbuffers();
        int vao = GL30C.glGenVertexArrays(), buffer = GL15C.glGenBuffers();
        try (var stack = MemoryStack.stackPush()) {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, target);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL11C.GL_RGBA8, 8, 8);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_RENDERBUFFER, target);
            require(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE, "Incomplete validation framebuffer");
            GL11C.glViewport(0, 0, 8, 8);
            GL30C.glBindVertexArray(vao); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, buffer);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, stack.floats(-1, -1, 3, -1, -1, 3), GL15C.GL_STATIC_DRAW);
            GL20C.glEnableVertexAttribArray(attribute); GL20C.glVertexAttribPointer(attribute, 2, GL11C.GL_FLOAT, false, 8, 0L);
            GL20C.glUseProgram(program);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(program, "color"), .25f, .5f, .75f, 1);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            var pixels = stack.malloc(8 * 8 * 4);
            GL11C.glReadPixels(0, 0, 8, 8, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            byte[] bytes = new byte[pixels.remaining()]; pixels.get(bytes);
            return bytes;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, oldFbo);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, oldRenderbuffer);
            GL30C.glBindVertexArray(oldVao); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, oldBuffer);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL15C.glDeleteBuffers(buffer); GL30C.glDeleteVertexArrays(vao);
            GL30C.glDeleteRenderbuffers(target); GL30C.glDeleteFramebuffers(fbo);
        }
    }

    private static void require(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
}
