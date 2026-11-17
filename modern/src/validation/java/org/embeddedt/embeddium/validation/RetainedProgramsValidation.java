package org.embeddedt.embeddium.validation;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.GlStateManager;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.cache.ShaderDiskCache;
import net.irisshaders.iris.cache.ShaderCacheWorkers;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.program.*;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.joml.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Driver-backed lifetime and shader-output checks, isolated from the shipped mod. */
public final class RetainedProgramsValidation {
    private static final String VERTEX = "#version 150\nin vec2 Position; void main(){gl_Position=vec4(Position,0,1);}";
    private static final String FRAGMENT = "#version 150\nuniform float value; out vec4 result; void main(){result=vec4(value,.5,.75,1);}";
    private static int links, binaries, hits, terrainHits, computeHits;

    public static void link() { links++; }
    public static void binary() { binaries++; }
    public static void hit(String name, RetainedPrograms.Ownership owner) {
        hits++;
        if (name.startsWith("sodium:")) terrainHits++;
        if (owner.sources().compute() != null) computeHits++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Map<ShaderType, String> stages(String vertex, String fragment) {
        return Map.of(ShaderType.VERTEX, vertex, ShaderType.FRAGMENT, fragment);
    }

    private static Program graphics(Map<ShaderType, String> stages, int attribute, float value) {
        var builder = ProgramBuilder.finish(ProgramBuilder.submit("retained-validation", stages,
            Map.of("Position", attribute)), ImmutableSet.of());
        builder.uniform1f(UniformUpdateFrequency.ONCE, "value", (FloatSupplier) () -> value);
        return builder.build();
    }

    private static byte[] draw(int program, int attribute) throws Exception {
        var method = ShaderCacheValidation.class.getDeclaredMethod("draw", int.class, int.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(null, program, attribute);
    }

    public static void verify() throws Exception {
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        boolean cacheEnabled = ProgramBinaryCache.isEnabled();
        ProgramBinaryCache.setEnabled(false);
        RetainedPrograms.useConfiguration("retained-validation");
        try {
            graphicsAndEviction();
            uniformsAndNotifications();
            compute();
            samplersAndImages();
            nativeTerrainWrapper();
            shaderStages();
            cancellationAndFailures();
            fullCompileMarker();
            require(GL11C.glGetError() == GL11C.GL_NO_ERROR, "retained program validation GL error");
            System.out.println("PASS retained programs: handle identity, consuming ownership, two generations, " +
                "attribute/stage identity, pixels, zero/identity uniforms, notifications, compute, terrain and failures");
        } finally {
            Program.unbind();
            RetainedPrograms.useConfiguration(null);
            ProgramBinaryCache.setEnabled(cacheEnabled);
            GlStateManager._glUseProgram(previous);
        }
    }

    private static void graphicsAndEviction() throws Exception {
        var inputs = stages(VERTEX, FRAGMENT);
        var first = graphics(inputs, 0, 0.8F);
        var second = graphics(inputs, 0, 0.4F);
        int one = first.getProgramId(), two = second.getProgramId();
        require(one != two, "two live wrappers share a program");
        first.bind(); byte[] nonzero = draw(one, 0);
        require((nonzero[0] & 255) > 190, "reference shader did not draw");
        Program.unbind(); first.delete(); second.delete();
        require(GL20C.glIsProgram(one) && GL20C.glIsProgram(two), "destroy deleted a retained program");
        boolean invalid = false;
        try { first.handle(); } catch (IllegalStateException expected) { invalid = true; }
        require(invalid, "destroy left the old wrapper usable");
        int priorLinks = links, priorBinaries = binaries;
        var reused = graphics(inputs, 0, 0);
        var another = graphics(inputs, 0, 0.25F);
        require(reused.getProgramId() == one && another.getProgramId() == two, "FIFO/consuming reuse changed");
        require(links == priorLinks && binaries == priorBinaries, "reuse performed link or binary restore");
        reused.bind(); byte[] zero = draw(one, 0);
        require((zero[0] & 255) == 0, "zero-valued uniform kept the old program value");
        var cold = graphics(stages(VERTEX, FRAGMENT + "\n// cold reference"), 0, 0);
        cold.bind(); require(Arrays.equals(zero, draw(cold.getProgramId(), 0)), "reused/cold pixels differ");
        Program.unbind(); reused.delete(); another.delete(); cold.delete();
        var moved = graphics(inputs, 3, 0);
        require(moved.getProgramId() != one && moved.getProgramId() != two, "attribute change reused wrong bindings");
        moved.bind(); require(Arrays.equals(zero, draw(moved.getProgramId(), 3)), "rebound attributes changed output");
        Program.unbind(); moved.delete();
        RetainedPrograms.afterPipelineBuilt();
        require(GL20C.glIsProgram(one), "first generation aged out too early");
        RetainedPrograms.afterPipelineBuilt();
        require(!GL20C.glIsProgram(one) && !GL20C.glIsProgram(two), "old generation was not deleted");

        var stale = graphics(inputs, 0, 1);
        int staleId = stale.getProgramId();
        RetainedPrograms.useConfiguration("changed");
        RetainedPrograms.useConfiguration("retained-validation");
        stale.delete();
        require(!GL20C.glIsProgram(staleId), "a late old-configuration owner re-entered the pool");
    }

    private static final class Notifier implements ValueUpdateNotifier {
        Runnable listener;
        @Override public void setListener(Runnable listener) { this.listener = listener; }
        void fire() { require(listener != null, "missing uniform listener"); listener.run(); }
    }

    private static void uniformsAndNotifications() throws Exception {
        String vertex = """
            #version 150
            in vec2 Position; uniform mat4 matrix; uniform mat4 arrayMatrix;
            void main(){gl_Position=matrix*arrayMatrix*vec4(Position,0,1);}
            """;
        String fragment = """
            #version 430
            uniform float scalar; uniform int integer; uniform bool booleanValue;
            uniform vec2 v2; uniform vec3 v3; uniform vec4 v4; uniform vec4 arrayValue;
            uniform ivec2 i2; uniform ivec3 i3; uniform ivec4 i4; uniform uvec4 u4;
            uniform float iris_currentAlphaTest; uniform float alphaTestRef; uniform int iris_currentAlphaFunc;
            uniform vec4 iris_ColorModulator;
            out vec4 result;
            void main(){float n=scalar+float(integer)+float(booleanValue)+v2.x+v3.x+v4.x+arrayValue.x+
                float(i2.x+i3.x+i4.x)+float(u4.x)+iris_currentAlphaTest+alphaTestRef+
                float(iris_currentAlphaFunc); result=vec4(n)+iris_ColorModulator;}
            """;
        var inputs = stages(vertex, fragment);
        int previousId = 0;
        for (int value : new int[]{2, 0}) {
            var builder = ProgramBuilder.finish(ProgramBuilder.submit("retained-uniforms", inputs, Map.of("Position", 0)), ImmutableSet.of());
            builder.uniform1f(UniformUpdateFrequency.ONCE, "scalar", (FloatSupplier) () -> value);
            builder.uniform1i(UniformUpdateFrequency.ONCE, "integer", () -> value);
            builder.uniform1b(UniformUpdateFrequency.ONCE, "booleanValue", () -> value != 0);
            builder.uniform2f(UniformUpdateFrequency.ONCE, "v2", () -> new Vector2f(value));
            builder.uniform3f(UniformUpdateFrequency.ONCE, "v3", () -> new Vector3f(value));
            builder.uniform4f(UniformUpdateFrequency.ONCE, "v4", () -> new Vector4f(value));
            builder.uniform4fArray(UniformUpdateFrequency.ONCE, "arrayValue", () -> new float[]{value, value, value, value});
            builder.uniform2i(UniformUpdateFrequency.ONCE, "i2", () -> new Vector2i(value));
            builder.uniform3i(UniformUpdateFrequency.ONCE, "i3", () -> new Vector3i(value));
            builder.uniform4i("i4", () -> new Vector4i(value), new Notifier());
            builder.uniformMatrix(UniformUpdateFrequency.ONCE, "matrix", () -> new Matrix4f().scaling(value == 0 ? 1 : 2));
            builder.uniformMatrixFromArray(UniformUpdateFrequency.ONCE, "arrayMatrix", () -> new Matrix4f().scaling(value == 0 ? 1 : 2).get(new float[16]));
            var program = builder.build(); int id = program.getProgramId();
            if (previousId != 0) require(id == previousId, "uniform fixture did not reuse the handle");
            program.bind();
            for (String name : new String[]{"scalar", "v2", "v3", "v4", "arrayValue"}) checkFloat(id, name, value);
            for (String name : new String[]{"integer", "i2", "i3", "i4"}) {
                try (var stack = MemoryStack.stackPush()) {
                    var buffer = stack.mallocInt(4);
                    GL20C.glGetUniformiv(id, GL20C.glGetUniformLocation(id, name), buffer);
                    require(buffer.get(0) == value, "stale integer uniform " + name);
                }
            }
            checkFloat(id, "matrix", value == 0 ? 1 : 2);
            checkFloat(id, "arrayMatrix", value == 0 ? 1 : 2);
            var unsigned = net.irisshaders.iris.gl.uniform.Vector4UnsignedIntegerUniform.class
                .getDeclaredConstructor(int.class, java.util.function.Supplier.class);
            unsigned.setAccessible(true);
            try {
                var uniform = (net.irisshaders.iris.gl.uniform.Uniform) unsigned.newInstance(
                    GL20C.glGetUniformLocation(id, "u4"), (java.util.function.Supplier<Vector4i>) () -> new Vector4i(value));
                uniform.update();
                try (var stack = MemoryStack.stackPush()) {
                    var read = stack.mallocInt(4); GL30C.glGetUniformuiv(id, GL20C.glGetUniformLocation(id, "u4"), read);
                    require(read.get(0) == value, "stale unsigned uniform");
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            checkFloat(id, "iris_currentAlphaTest", -1);
            checkFloat(id, "alphaTestRef", -1);
            checkFloat(id, "iris_ColorModulator", 1);
            GL20C.glUniform1f(GL20C.glGetUniformLocation(id, "iris_currentAlphaTest"), .75F);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "iris_ColorModulator"), 0, 0, 0, 0);
            Program.unbind(); program.delete(); previousId = id;
        }

        float[] current = {0.5F}; var notifier = new Notifier();
        var a = ProgramBuilder.begin("listener-a", VERTEX, null, FRAGMENT, ImmutableSet.of());
        a.uniform1f("value", (FloatSupplier) () -> current[0], notifier);
        var first = a.build(); var second = graphics(stages(VERTEX, FRAGMENT + "\n// listener b"), 0, 0.25F);
        first.bind(); current[0] = 0;
        GlStateManager._glUseProgram(second.getProgramId()); notifier.fire();
        require(ProgramUniforms.activeHasDeferredUploads(), "unbound update was not deferred");
        checkFloat(second.getProgramId(), "value", 0);
        first.bind(); checkFloat(first.getProgramId(), "value", 0);
        second.bind(); checkFloat(second.getProgramId(), "value", 0.25F);
        Program.unbind(); first.delete(); second.delete();
    }

    private static void checkFloat(int program, String name, float expected) {
        try (var stack = MemoryStack.stackPush()) {
            var read = stack.mallocFloat(16);
            int location = GL20C.glGetUniformLocation(program, name);
            require(location >= 0, "inactive fixture uniform " + name);
            GL20C.glGetUniformfv(program, location, read);
            require(read.get(0) == expected, "uniform " + name + ": " + read.get(0) + " != " + expected);
        }
    }

    private static void compute() {
        String source = "#version 430\nlayout(local_size_x=1)in;layout(std430,binding=0)buffer Result{int result;};uniform int value;void main(){result=value;}";
        int buffer = GL15C.glGenBuffers(); int previousId = 0;
        try (var stack = MemoryStack.stackPush()) {
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, buffer);
            GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 4, GL15C.GL_DYNAMIC_READ);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, buffer);
            for (int value : new int[]{17, 0}) {
                var builder = ProgramBuilder.beginCompute("retained-compute", source, ImmutableSet.of());
                builder.uniform1i(UniformUpdateFrequency.ONCE, "value", () -> value);
                var program = builder.buildCompute(); int id = program.getProgramId();
                if (previousId != 0) require(id == previousId, "compute handle was not reused");
                program.use(); program.dispatch(1, 1);
                GL42C.glMemoryBarrier(GL43C.GL_BUFFER_UPDATE_BARRIER_BIT);
                var read = stack.mallocInt(1);
                GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, read);
                require(read.get(0) == value, "compute reuse kept the old value");
                ComputeProgram.unbind(); program.delete(); previousId = id;
            }
        } finally { GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, 0); GL15C.glDeleteBuffers(buffer); }
    }

    private static void nativeTerrainWrapper() {
        var owner = RetainedPrograms.ownership(stages(VERTEX, FRAGMENT), Map.of("Position", 0));
        var builder = GlProgram.builder("retained-native");
        builder.attachShader(GlShader.compileDeferred(ShaderType.VERTEX, "native.vsh", VERTEX));
        builder.attachShader(GlShader.compileDeferred(ShaderType.FRAGMENT, "native.fsh", FRAGMENT));
        builder.bindAttribute("Position", 0);
        int id = builder.getProgramId();
        var program = builder.linkDeferred().finish(context -> new Object(), handle -> RetainedPrograms.release(owner, handle));
        require(GL20C.glGetProgrami(id, GL20C.GL_ATTACHED_SHADERS) == 0, "retained native program kept its shader objects");
        program.delete();
        var taken = ProgramCreator.reuse("sodium:retained-native", owner, null);
        require(taken != null && taken.finish() == id, "native terrain ownership was not returned");
        GlProgram.fromLinkedProgram(id, context -> new Object(), handle -> RetainedPrograms.release(owner, handle)).delete();
    }

    private static void shaderStages() {
        String geometry = "#version 150\nlayout(triangles)in;layout(triangle_strip,max_vertices=3)out;void main(){for(int i=0;i<3;i++){gl_Position=gl_in[i].gl_Position;EmitVertex();}EndPrimitive();}";
        var stages = new EnumMap<ShaderType, String>(ShaderType.class); stages.putAll(stages(VERTEX, FRAGMENT));
        stages.put(ShaderType.GEOM, geometry);
        var first = graphics(stages, 0, 1); int id = first.getProgramId(); first.delete();
        var reused = graphics(stages, 0, 0); require(reused.getProgramId() == id, "geometry-stage program was not reused"); reused.delete();
        stages.put(ShaderType.GEOM, geometry + "\n// changed");
        var changed = graphics(stages, 0, 0); require(changed.getProgramId() != id, "geometry source change hit the old program"); changed.delete();
        stages.remove(ShaderType.GEOM);
        stages.put(ShaderType.VERTEX, VERTEX.replace("#version 150", "#version 400"));
        stages.put(ShaderType.FRAGMENT, FRAGMENT.replace("#version 150", "#version 400"));
        stages.put(ShaderType.TESS_CTRL, "#version 400\nlayout(vertices=3)out;void main(){gl_out[gl_InvocationID].gl_Position=gl_in[gl_InvocationID].gl_Position;if(gl_InvocationID==0){gl_TessLevelOuter[0]=1;gl_TessLevelOuter[1]=1;gl_TessLevelOuter[2]=1;gl_TessLevelInner[0]=1;}}");
        stages.put(ShaderType.TESS_EVALUATE, "#version 400\nlayout(triangles,equal_spacing,ccw)in;void main(){gl_Position=gl_in[0].gl_Position*gl_TessCoord.x+gl_in[1].gl_Position*gl_TessCoord.y+gl_in[2].gl_Position*gl_TessCoord.z;}");
        first = graphics(stages, 0, 1); id = first.getProgramId(); first.delete();
        reused = graphics(stages, 0, 0);
        require(reused.getProgramId() == id, "tessellation-stage program was not reused"); reused.delete();
    }

    private static int texture(int r, int g, int b) {
        int id = GL11C.glGenTextures();
        GlStateManager._bindTexture(id);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        int[] parameters = {GL11C.GL_UNPACK_ROW_LENGTH, GL11C.GL_UNPACK_SKIP_ROWS, GL11C.GL_UNPACK_SKIP_PIXELS,
            GL11C.GL_UNPACK_ALIGNMENT, GL11C.GL_PACK_ROW_LENGTH, GL11C.GL_PACK_SKIP_ROWS,
            GL11C.GL_PACK_SKIP_PIXELS, GL11C.GL_PACK_ALIGNMENT};
        int[] old = new int[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            old[i] = GL11C.glGetInteger(parameters[i]);
            GL11C.glPixelStorei(parameters[i], i == 3 || i == 7 ? 1 : 0);
        }
        int unpackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int packBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4).put((byte) r).put((byte) g).put((byte) b).put((byte) 255).flip();
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, 1, 1, 0,
                GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
            var read = stack.malloc(4);
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, read);
            require((read.get(0) & 255) == r && (read.get(1) & 255) == g && (read.get(2) & 255) == b,
                "texture fixture upload differs");
        } finally {
            for (int i = 0; i < parameters.length; i++) GL11C.glPixelStorei(parameters[i], old[i]);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBuffer);
        }
        return id;
    }

    private static void samplersAndImages() throws Exception {
        String fragment = "#version 430\nuniform sampler2D colorTexture;layout(rgba8)uniform image2D colorImage;out vec4 result;void main(){result=texelFetch(colorTexture,ivec2(0),0)*imageLoad(colorImage,ivec2(0));}";
        var stages = stages(VERTEX.replace("#version 150", "#version 430"), fragment);
        int oldActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND), scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean depth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST), cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        GlStateManager._disableBlend(); GlStateManager._disableScissorTest();
        GlStateManager._disableDepthTest(); GlStateManager._disableCull();
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int previousId = 0;
        try {
            for (int channel = 0; channel < 3; channel++) {
                int sampled = channel == 0 ? texture(255, 0, 0) : texture(255, 255, 255);
                int image = channel == 1 ? texture(0, 255, 0)
                    : channel == 2 ? texture(0, 0, 255) : texture(255, 255, 255);
                var builder = ProgramBuilder.finish(ProgramBuilder.submit("retained-bindings", stages,
                    Map.of("Position", 0)), ImmutableSet.of());
                require(builder.addDynamicSampler(() -> sampled, "colorTexture"), "inactive sampler fixture");
                builder.addTextureImage(() -> image, InternalTextureFormat.RGBA8, "colorImage");
                var program = builder.build(); int id = program.getProgramId();
                if (previousId != 0) require(id == previousId, "binding fixture did not reuse the program");
                try {
                    program.bind(); byte[] pixels = draw(id, 0);
                    for (int i = 0; i < 3; i++) require((pixels[i] & 255) == (i == channel ? 255 : 0),
                        "sampler/image channel=" + channel + " id=" + id + " previous=" + previousId +
                            " pixels=" + (pixels[0] & 255) + "," + (pixels[1] & 255) + "," + (pixels[2] & 255) +
                            " error=" + GL11C.glGetError());
                } finally {
                    Program.unbind(); program.delete();
                    GL42C.glBindImageTexture(0, 0, 0, false, 0, GL15C.GL_READ_WRITE, GL11C.GL_RGBA8);
                    GlStateManager._deleteTexture(sampled); GlStateManager._deleteTexture(image);
                }
                previousId = id;
            }
        } finally {
            GlStateManager._bindTexture(oldTexture); GlStateManager._activeTexture(oldActive);
            if (blend) GlStateManager._enableBlend(); if (scissor) GlStateManager._enableScissorTest();
            if (depth) GlStateManager._enableDepthTest(); if (cull) GlStateManager._enableCull();
        }
    }

    private static void awaitWrites() throws Exception {
        var field = ShaderCacheWorkers.class.getDeclaredField("inFlight"); field.setAccessible(true);
        var pending = (java.util.concurrent.atomic.AtomicInteger) field.get(null);
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (pending.get() != 0) {
            require(System.nanoTime() < deadline, "binary writes timed out"); Thread.sleep(5);
        }
    }

    private static void fullCompileMarker() throws Exception {
        if (!ProgramBinaryCache.isSupported()) return;
        ShaderDiskCache.configure(Path.of("embeddium", "retained-binary-validation"), "validation-" + UUID.randomUUID());
        ProgramBinaryCache.setEnabled(true); ProgramBinaryCache.usePack("retained-marker");
        var inputs = stages(VERTEX, FRAGMENT + "\n// full compile");
        var first = graphics(inputs, 0, 1); int id = first.getProgramId(); first.delete(); awaitWrites();
        int beforeLinks = links, beforeBinaries = binaries;
        ProgramBinaryCache.beginFullCompile();
        var reused = graphics(inputs, 0, 0);
        require(reused.getProgramId() == id && links == beforeLinks && binaries == beforeBinaries,
            "full compile did not save/keep the live program");
        reused.delete(); ProgramBinaryCache.finishFullCompile("retained-settings"); awaitWrites();
        require(ProgramBinaryCache.wasCompiledFor("retained-settings"), "retained program save lost the completed marker");
        String compute = "#version 430\nlayout(local_size_x=1)in;void main(){} // binary ownership";
        var cold = ProgramBuilder.beginCompute("binary-compute", compute, ImmutableSet.of()).buildCompute();
        cold.delete(); awaitWrites();
        RetainedPrograms.useConfiguration("binary-compute-reload");
        int beforeLoad = binaries;
        var loaded = ProgramBuilder.beginCompute("binary-compute", compute, ImmutableSet.of()).buildCompute();
        require(binaries > beforeLoad, "compute binary restoration was not exercised");
        int loadedId = loaded.getProgramId(); loaded.delete();
        beforeLoad = binaries;
        var live = ProgramBuilder.beginCompute("binary-compute", compute, ImmutableSet.of()).buildCompute();
        require(live.getProgramId() == loadedId && binaries == beforeLoad,
            "restored compute program did not return to the live pool");
        live.delete();
        ProgramBinaryCache.setEnabled(false);
    }

    private static void cancellationAndFailures() {
        var inputs = stages(VERTEX, FRAGMENT + "\n// cancellation");
        var program = graphics(inputs, 0, 1); int id = program.getProgramId(); program.delete();
        var pending = ProgramBuilder.submit("cancelled", inputs, Map.of("Position", 0));
        pending.delete(); require(!GL20C.glIsProgram(id), "cancelled retained submission leaked");
        boolean rejected = false;
        try { pending.finish(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "cancelled submission could be finished");
        var builder = ProgramBuilder.begin("once", VERTEX, null, FRAGMENT, ImmutableSet.of());
        var single = builder.build();
        rejected = false;
        try { builder.build(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "one builder could create duplicate owners"); single.delete();
        var nativeBuilder = GlProgram.builder("factory-failure");
        nativeBuilder.attachShader(GlShader.compileDeferred(ShaderType.VERTEX, "failure.vsh", VERTEX));
        nativeBuilder.attachShader(GlShader.compileDeferred(ShaderType.FRAGMENT, "failure.fsh", FRAGMENT));
        int failed = nativeBuilder.getProgramId();
        try { nativeBuilder.linkDeferred().finish(context -> { throw new IllegalArgumentException("fixture failure"); }); }
        catch (IllegalArgumentException expected) { /* linked handle must still be deleted */ }
        require(!GL20C.glIsProgram(failed), "factory failure leaked a linked program");
    }

    public static void beginWarmPack() { links = binaries = hits = terrainHits = computeHits = 0; }
    public static void verifyWarmPack(String phase) {
        require(links == 0 && binaries == 0, phase + " performed links=" + links + " binary loads=" + binaries);
        require(hits > 0 && terrainHits > 0 && computeHits > 0, phase + " did not reuse all native program paths");
        System.out.println("PASS retained pack " + phase + ": links=0 binary-loads=0 hits=" + hits + " terrain=" + terrainHits + " compute=" + computeHits);
    }

    public static void offOn() throws Exception {
        beginWarmPack();
        Iris.toggleShaders(net.minecraft.client.Minecraft.getInstance(), false);
        Iris.toggleShaders(net.minecraft.client.Minecraft.getInstance(), true);
    }

    public static void finish() throws Exception {
        Files.writeString(Path.of("retained-programs-validation.txt"),
            "PASS Java17; consuming two-generation handles; GL lifetime, attributes/stages, pixels, uniforms/listeners, " +
            "compute SSBO, terrain, failures; real reload and OFF/ON links=0 binary-loads=0\n");
    }
}
