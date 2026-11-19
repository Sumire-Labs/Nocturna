// SPDX-License-Identifier: LGPL-3.0-or-later
package net.irisshaders.iris.pathways.scaling.temporal;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Real SDK dispatch, with explicit GL/D3D image orientation and linear/sRGB conversions. */
public final class TemporalFsrUpscaler implements AutoCloseable {
    private static final String INPUT = """
            #version 450 core
            layout(local_size_x=8,local_size_y=8) in;
            layout(binding=0) uniform sampler2D colorInput;
            layout(binding=1) uniform sampler2D depthInput;
            layout(binding=2) uniform sampler2D motionInput;
            layout(rgba16f,binding=0) writeonly uniform image2D colorOutput;
            layout(r32f,binding=1) writeonly uniform image2D depthOutput;
            layout(rg16f,binding=2) writeonly uniform image2D motionOutput;
            vec3 linearColor(vec3 c) {
                return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),greaterThan(c,vec3(0.04045)));
            }
            void main() {
                ivec2 p=ivec2(gl_GlobalInvocationID.xy), size=imageSize(colorOutput);
                if(any(greaterThanEqual(p,size))) return;
                ivec2 source=ivec2(p.x,size.y-1-p.y);
                vec4 color=texelFetch(colorInput,source,0);
                vec2 motion=texelFetch(motionInput,source,0).xy;
                imageStore(colorOutput,p,vec4(linearColor(clamp(color.rgb,0.0,1.0)),color.a));
                imageStore(depthOutput,p,vec4(texelFetch(depthInput,source,0).r));
                imageStore(motionOutput,p,vec4(motion.x,-motion.y,0,0));
            }
            """;
    private static final String OUTPUT = """
            #version 450 core
            layout(local_size_x=8,local_size_y=8) in;
            layout(binding=0) uniform sampler2D colorInput;
            layout(rgba8,binding=0) writeonly uniform image2D colorOutput;
            vec3 srgbColor(vec3 c) {
                return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-0.055,greaterThan(c,vec3(0.0031308)));
            }
            void main() {
                ivec2 p=ivec2(gl_GlobalInvocationID.xy), size=imageSize(colorOutput);
                if(any(greaterThanEqual(p,size))) return;
                vec4 c=texelFetch(colorInput,ivec2(p.x,size.y-1-p.y),0);
                imageStore(colorOutput,p,vec4(srgbColor(max(c.rgb,vec3(0))),1));
            }
            """;
    private long context;
    private int inputProgram, outputProgram, resolved, framebuffer;
    private final int width, height, outputWidth, outputHeight;
    private final String provider;

    public TemporalFsrUpscaler(int width, int height, int outputWidth, int outputHeight, int family) throws Exception {
        this.width = width; this.height = height;
        this.outputWidth = outputWidth; this.outputHeight = outputHeight;
        FsrNative.load();
        try {
            context = FsrNative.create(FsrNative.runtime().toString(), width, height, outputWidth, outputHeight, family);
            provider = FsrNative.provider(context);
            inputProgram = program(INPUT);
            outputProgram = program(OUTPUT);
            resolved = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
            GL45C.glTextureStorage2D(resolved, 1, GL11C.GL_RGBA8, outputWidth, outputHeight);
            framebuffer = GL45C.glCreateFramebuffers();
            GL45C.glNamedFramebufferTexture(framebuffer, GL30C.GL_COLOR_ATTACHMENT0, resolved, 0);
            if (GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete temporal FSR resolve framebuffer");
            validateTransport();
        } catch (Throwable failure) { close(); throw failure; }
    }

    public String provider() { return provider; }
    public float[] jitter(long frame) { return FsrNative.jitter(context, frame); }

    /** One readback at context creation, never per frame. Rejects a provider that accepts a GPU/size but emits black. */
    private void validateTransport() {
        int[] inputs = new int[3];
        int destination = 0, target = 0;
        int packBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int unpackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] packNames = {GL11C.GL_PACK_ALIGNMENT, GL11C.GL_PACK_ROW_LENGTH, GL11C.GL_PACK_SKIP_ROWS,
                GL11C.GL_PACK_SKIP_PIXELS, GL12C.GL_PACK_IMAGE_HEIGHT, GL12C.GL_PACK_SKIP_IMAGES};
        int[] packValues = new int[packNames.length];
        for (int i = 0; i < packNames.length; i++) packValues[i] = GL11C.glGetInteger(packNames[i]);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            for (int name : packNames) GL11C.glPixelStorei(name, name == GL11C.GL_PACK_ALIGNMENT ? 1 : 0);
            int[] formats = {GL11C.GL_RGBA8, GL30C.GL_R32F, GL30C.GL_RG16F};
            int[] channels = {GL11C.GL_RGBA, GL11C.GL_RED, GL30C.GL_RG};
            float[][] values = {{0.25f, 0.5f, 0.75f, 1}, {0.5f}, {0, 0}};
            for (int i = 0; i < 3; i++) {
                inputs[i] = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
                GL45C.glTextureStorage2D(inputs[i], 1, formats[i], width, height);
                GL45C.glTextureParameteri(inputs[i], GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL44C.glClearTexImage(inputs[i], 0, channels[i], GL11C.GL_FLOAT, stack.floats(values[i]));
            }
            destination = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
            GL45C.glTextureStorage2D(destination, 1, GL11C.GL_RGBA8, outputWidth, outputHeight);
            target = GL45C.glCreateFramebuffers();
            GL45C.glNamedFramebufferTexture(target, GL30C.GL_COLOR_ATTACHMENT0, destination, 0);
            float[] offset = jitter(0);
            process(inputs[0], inputs[1], inputs[2], target, offset[0], offset[1], 16.666667f,
                    0.05f, 256, 1.2f, 0, true);
            var pixel = stack.malloc(4);
            GL45C.glGetTextureSubImage(destination, 0, outputWidth / 2, outputHeight / 2, 0,
                    1, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
            int[] expected = {64, 128, 191};
            for (int c = 0; c < 3; c++) {
                if (Math.abs(Byte.toUnsignedInt(pixel.get(c)) - expected[c]) > 8)
                    throw new IllegalStateException("FSR " + provider + " failed its GPU output check at " + width + "x" + height);
            }
            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) throw new IllegalStateException("FSR transport check GL error " + error);
        } finally {
            if (target != 0) GL30C.glDeleteFramebuffers(target);
            if (destination != 0) GL11C.glDeleteTextures(destination);
            GL11C.glDeleteTextures(inputs);
            for (int i = 0; i < packNames.length; i++) GL11C.glPixelStorei(packNames[i], packValues[i]);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static int program(String source) {
        int shader = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        int program = 0;
        try {
            GL20C.glShaderSource(shader, source);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(GL20C.glGetShaderInfoLog(shader));
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, shader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
            return program;
        } catch (Throwable failure) { if (program != 0) GL20C.glDeleteProgram(program); throw failure; }
        finally { GL20C.glDeleteShader(shader); }
    }

    public void process(int color, int depth, int motion, int destinationFramebuffer,
                        float jitterX, float jitterY, float delta, float near, float far, float fov,
                        float sharpness, boolean reset) {
        if (context == 0) throw new IllegalStateException("Closed temporal FSR context");
        try (State ignored = new State()) {
            int[] shared = FsrNative.acquire(context);
            GL20C.glUseProgram(inputProgram);
            int[] sources = {color, depth, motion};
            int[] formats = {GL30C.GL_RGBA16F, GL30C.GL_R32F, GL30C.GL_RG16F};
            for (int i = 0; i < 3; i++) {
                GL45C.glBindTextureUnit(i, sources[i]);
                GL33C.glBindSampler(i, 0);
                GL42C.glBindImageTexture(i, shared[i], 0, false, 0, GL15C.GL_WRITE_ONLY, formats[i]);
            }
            GL43C.glDispatchCompute((width + 7) / 8, (height + 7) / 8, 1);
            GL42C.glMemoryBarrier(GL42C.GL_ALL_BARRIER_BITS);
            FsrNative.execute(context, jitterX, jitterY, delta, near, far, fov, sharpness, reset);
            GL20C.glUseProgram(outputProgram);
            GL45C.glBindTextureUnit(0, shared[3]);
            GL42C.glBindImageTexture(0, resolved, 0, false, 0, GL15C.GL_WRITE_ONLY, GL11C.GL_RGBA8);
            GL43C.glDispatchCompute((outputWidth + 7) / 8, (outputHeight + 7) / 8, 1);
            GL42C.glMemoryBarrier(GL42C.GL_ALL_BARRIER_BITS);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL45C.glBlitNamedFramebuffer(framebuffer, destinationFramebuffer,
                    0, 0, outputWidth, outputHeight, 0, 0, outputWidth, outputHeight,
                    GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        }
    }

    /** Restores raw GL state exactly, leaving Minecraft/Iris's state caches valid. No pack program is rebound through Iris. */
    private static final class State implements AutoCloseable {
        private final int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        private final int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        private final boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        private final boolean srgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        private final int[] textures = new int[3], samplers = new int[3];
        private final int[][] images = new int[3][6];
        private static final int[] IMAGE_STATE = {GL42C.GL_IMAGE_BINDING_NAME, GL42C.GL_IMAGE_BINDING_LEVEL,
                GL42C.GL_IMAGE_BINDING_LAYERED, GL42C.GL_IMAGE_BINDING_LAYER, GL42C.GL_IMAGE_BINDING_ACCESS, GL42C.GL_IMAGE_BINDING_FORMAT};
        State() {
            for (int i = 0; i < 3; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                textures[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                samplers[i] = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, i);
                for (int p = 0; p < 6; p++) images[i][p] = GL30C.glGetIntegeri(IMAGE_STATE[p], i);
            }
            GL13C.glActiveTexture(active);
        }
        @Override public void close() {
            for (int i = 0; i < 3; i++) {
                // Binding texture zero with glBindTextureUnit would also clear other texture targets.
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[i]);
                GL33C.glBindSampler(i, samplers[i]);
                int[] s = images[i];
                GL42C.glBindImageTexture(i, s[0], s[1], s[2] != 0, s[3], s[4], s[5]);
            }
            GL13C.glActiveTexture(active);
            GL20C.glUseProgram(program);
            if (scissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            if (srgb) GL11C.glEnable(GL30C.GL_FRAMEBUFFER_SRGB);
        }
    }

    @Override public void close() {
        if (context != 0) { FsrNative.close(context); context = 0; }
        if (inputProgram != 0) { GL20C.glDeleteProgram(inputProgram); inputProgram = 0; }
        if (outputProgram != 0) { GL20C.glDeleteProgram(outputProgram); outputProgram = 0; }
        if (framebuffer != 0) { GL30C.glDeleteFramebuffers(framebuffer); framebuffer = 0; }
        if (resolved != 0) { GL11C.glDeleteTextures(resolved); resolved = 0; }
    }
}
