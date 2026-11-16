package me.flashyreese.mods.sodiumextra.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.*;
import java.util.*;

/**
 * A complete pass owns its fixed-function state and reflects, retains and uploads std140 blocks.
 */
final class EffectGpuProgram implements AutoCloseable {
    record Image(int texture, int width, int height, boolean bilinear) {}

    private record Member(int offset, int type, int count, int stride, int matrixStride) {}

    private static final class Block implements AutoCloseable {
        final int buffer;
        final ByteBuffer data;
        final Map<String, Member> members = new LinkedHashMap<>();

        Block(int size) {
            buffer = GL33C.glGenBuffers();
            data = MemoryUtil.memCalloc(size);
        }

        public void close() {
            GL33C.glDeleteBuffers(buffer);
            MemoryUtil.memFree(data);
        }
    }

    private final int program, vao, nearest, linear;
    private final Map<String, Block> blocks = new LinkedHashMap<>();

    EffectGpuProgram(String vertex, String fragment) {
        int v = 0, f = 0, p = 0, array = 0, n = 0, l = 0;
        try {
            v = compile(GL33C.GL_VERTEX_SHADER, vertex);
            f = compile(GL33C.GL_FRAGMENT_SHADER, fragment);
            p = GL33C.glCreateProgram();
            GL33C.glAttachShader(p, v);
            GL33C.glAttachShader(p, f);
            GL33C.glLinkProgram(p);
            if (GL33C.glGetProgrami(p, GL33C.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL33C.glGetProgramInfoLog(p));
            array = GL33C.glGenVertexArrays();
            n = sampler(GL33C.GL_NEAREST);
            l = sampler(GL33C.GL_LINEAR);
            int count = GL33C.glGetProgrami(p, GL33C.GL_ACTIVE_UNIFORM_BLOCKS);
            for (int i = 0; i < count; i++) {
                String name = GL33C.glGetActiveUniformBlockName(p, i);
                var block =
                        new Block(
                                GL33C.glGetActiveUniformBlocki(
                                        p, i, GL33C.GL_UNIFORM_BLOCK_DATA_SIZE));
                blocks.put(name, block);
                GL33C.glUniformBlockBinding(p, i, i);
                try (var stack = MemoryStack.stackPush()) {
                    int size =
                            GL33C.glGetActiveUniformBlocki(
                                    p, i, GL33C.GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS);
                    var indices = stack.mallocInt(size);
                    GL33C.glGetActiveUniformBlockiv(
                            p, i, GL33C.GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, indices);
                    for (int j = 0; j < size; j++) {
                        int index = indices.get(j);
                        String member = GL33C.glGetActiveUniformName(p, index);
                        if (member.contains("."))
                            member = member.substring(member.lastIndexOf('.') + 1);
                        member = member.replace("[0]", "");
                        block.members.put(
                                member,
                                new Member(
                                        uniform(p, index, GL33C.GL_UNIFORM_OFFSET),
                                        uniform(p, index, GL33C.GL_UNIFORM_TYPE),
                                        uniform(p, index, GL33C.GL_UNIFORM_SIZE),
                                        uniform(p, index, GL33C.GL_UNIFORM_ARRAY_STRIDE),
                                        uniform(p, index, GL33C.GL_UNIFORM_MATRIX_STRIDE)));
                    }
                }
            }
        } catch (RuntimeException failure) {
            blocks.values().forEach(Block::close);
            if (p != 0) GL33C.glDeleteProgram(p);
            if (array != 0) GL33C.glDeleteVertexArrays(array);
            if (n != 0) GL33C.glDeleteSamplers(n);
            if (l != 0) GL33C.glDeleteSamplers(l);
            throw failure;
        } finally {
            if (v != 0) GL33C.glDeleteShader(v);
            if (f != 0) GL33C.glDeleteShader(f);
        }
        program = p;
        vao = array;
        nearest = n;
        linear = l;
    }

    private static int uniform(int p, int index, int property) {
        return GL33C.glGetActiveUniformsi(p, index, property);
    }

    private static int sampler(int mode) {
        int id = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(id, GL33C.GL_TEXTURE_MIN_FILTER, mode);
        GL33C.glSamplerParameteri(id, GL33C.GL_TEXTURE_MAG_FILTER, mode);
        GL33C.glSamplerParameteri(id, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(id, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        return id;
    }

    private static int compile(int type, String source) {
        int shader = GL33C.glCreateShader(type);
        GL33C.glShaderSource(shader, source);
        GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == 0) {
            String message = GL33C.glGetShaderInfoLog(shader);
            GL33C.glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    boolean hasBlock(String block) {
        return blocks.containsKey(block);
    }

    void defaults(JsonObject values) {
        for (var entry : values.entrySet())
            for (var value : entry.getValue().getAsJsonArray()) {
                var declaration = value.getAsJsonObject();
                var array = declaration.get("value");
                var numbers = new ArrayList<Number>();
                flatten(array, numbers);
                write(entry.getKey(), declaration.get("name").getAsString(), numbers);
            }
    }

    private static void flatten(JsonElement e, List<Number> result) {
        if (e.isJsonArray()) for (var value : e.getAsJsonArray()) flatten(value, result);
        else if (e.getAsJsonPrimitive().isBoolean()) result.add(e.getAsBoolean() ? 1 : 0);
        else result.add(e.getAsNumber());
    }

    void vector(String block, String name, float... values) {
        var list = new ArrayList<Number>();
        for (float v : values) list.add(v);
        write(block, name, list);
    }

    private void write(String blockName, String name, List<Number> values) {
        var block = blocks.get(blockName);
        if (block == null) return;
        var member = block.members.get(name);
        if (member == null) return;
        int components =
                switch (member.type) {
                    case GL33C.GL_FLOAT_VEC2,
                            GL33C.GL_INT_VEC2,
                            GL33C.GL_BOOL_VEC2,
                            GL33C.GL_UNSIGNED_INT_VEC2 ->
                            2;
                    case GL33C.GL_FLOAT_VEC3,
                            GL33C.GL_INT_VEC3,
                            GL33C.GL_BOOL_VEC3,
                            GL33C.GL_UNSIGNED_INT_VEC3 ->
                            3;
                    case GL33C.GL_FLOAT_VEC4,
                            GL33C.GL_INT_VEC4,
                            GL33C.GL_BOOL_VEC4,
                            GL33C.GL_UNSIGNED_INT_VEC4 ->
                            4;
                    case GL33C.GL_FLOAT_MAT2 -> 4;
                    case GL33C.GL_FLOAT_MAT3 -> 9;
                    case GL33C.GL_FLOAT_MAT4 -> 16;
                    default -> 1;
                };
        boolean matrix = member.matrixStride > 0;
        int dimension = matrix ? (int) Math.sqrt(components) : 0;
        boolean integer =
                member.type == GL33C.GL_INT
                        || member.type == GL33C.GL_BOOL
                        || member.type == GL33C.GL_UNSIGNED_INT
                        || member.type >= GL33C.GL_INT_VEC2 && member.type <= GL33C.GL_BOOL_VEC4
                        || member.type >= GL33C.GL_UNSIGNED_INT_VEC2
                                && member.type <= GL33C.GL_UNSIGNED_INT_VEC4;
        if (values.size() != components * member.count)
            throw new IllegalArgumentException(
                    "Uniform value size differs: " + blockName + "." + name);
        for (int i = 0; i < values.size(); i++) {
            int element = i / components, component = i % components;
            int offset =
                    member.offset
                            + (member.stride > 0 ? element * member.stride : 0)
                            + (matrix
                                    ? (component / dimension) * member.matrixStride
                                            + (component % dimension) * 4
                                    : component * 4);
            if (integer) block.data.putInt(offset, values.get(i).intValue());
            else block.data.putFloat(offset, values.get(i).floatValue());
        }
    }

    void draw(RenderTarget output, Map<String, Image> images, boolean depth) {
        try (var stack = MemoryStack.stackPush()) {
            int framebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING),
                    oldProgram = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM),
                    oldVao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING),
                    active = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE),
                    oldUbo = GL33C.glGetInteger(GL33C.GL_UNIFORM_BUFFER_BINDING),
                    depthFunc = GL33C.glGetInteger(GL33C.GL_DEPTH_FUNC);
            int[] caps = {
                GL33C.GL_BLEND,
                GL33C.GL_CULL_FACE,
                GL33C.GL_SCISSOR_TEST,
                GL33C.GL_STENCIL_TEST,
                GL33C.GL_DEPTH_TEST,
                GL33C.GL_COLOR_LOGIC_OP,
                GL33C.GL_SAMPLE_ALPHA_TO_COVERAGE,
                GL33C.GL_RASTERIZER_DISCARD
            };
            boolean[] enabled = new boolean[caps.length];
            for (int i = 0; i < caps.length; i++) enabled[i] = GL33C.glIsEnabled(caps[i]);
            var viewport = stack.mallocInt(4);
            GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
            var mask = stack.malloc(4);
            GL33C.glGetBooleanv(GL33C.GL_COLOR_WRITEMASK, mask);
            boolean oldDepthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
            var polygon = stack.mallocInt(2);
            GL33C.glGetIntegerv(GL33C.GL_POLYGON_MODE, polygon);
            int[] textures = new int[images.size()],
                    samplers = new int[images.size()],
                    buffers = new int[blocks.size()];
            long[] offsets = new long[buffers.length], sizes = new long[buffers.length];
            for (int i = 0; i < textures.length; i++) {
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + i);
                textures[i] = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
                samplers[i] = GL33C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, i);
            }
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = GL33C.glGetIntegeri(GL33C.GL_UNIFORM_BUFFER_BINDING, i);
                offsets[i] = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_START, i);
                sizes[i] = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_SIZE, i);
            }
            try {
                GL33C.glUseProgram(program);
                GL33C.glBindVertexArray(vao);
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, output.frameBufferId);
                GL33C.glViewport(0, 0, output.width, output.height);
                for (int cap : caps) GL33C.glDisable(cap);
                if (depth) GL33C.glEnable(GL33C.GL_DEPTH_TEST);
                GL33C.glDepthFunc(GL33C.GL_ALWAYS);
                GL33C.glDepthMask(depth);
                GL33C.glColorMask(!depth, !depth, !depth, !depth);
                GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, GL33C.GL_FILL);
                vector("SamplerInfo", "OutSize", output.width, output.height);
                var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                var window = Minecraft.getInstance().getWindow();
                vector("Globals", "ScreenSize", window.getWidth(), window.getHeight());
                write(
                        "Globals",
                        "CameraBlockPos",
                        List.of(
                                net.minecraft.util.Mth.floor(camera.x),
                                net.minecraft.util.Mth.floor(camera.y),
                                net.minecraft.util.Mth.floor(camera.z)));
                vector(
                        "Globals",
                        "CameraOffset",
                        (float) (Math.floor(camera.x) - camera.x),
                        (float) (Math.floor(camera.y) - camera.y),
                        (float) (Math.floor(camera.z) - camera.z));
                vector(
                        "Globals",
                        "GlintAlpha",
                        Minecraft.getInstance().options.glintStrength().get().floatValue());
                vector(
                        "Globals",
                        "GameTime",
                        com.mojang.blaze3d.systems.RenderSystem.getShaderGameTime());
                int unit = 0;
                for (var entry : images.entrySet()) {
                    var image = entry.getValue();
                    GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, image.texture);
                    GL33C.glBindSampler(unit, image.bilinear ? linear : nearest);
                    GL33C.glUniform1i(
                            GL33C.glGetUniformLocation(program, entry.getKey() + "Sampler"), unit);
                    vector("SamplerInfo", entry.getKey() + "Size", image.width, image.height);
                    unit++;
                }
                int binding = 0;
                for (var block : blocks.values()) {
                    GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, block.buffer);
                    GL33C.glBufferData(GL33C.GL_UNIFORM_BUFFER, block.data, GL33C.GL_DYNAMIC_DRAW);
                    GL33C.glBindBufferBase(GL33C.GL_UNIFORM_BUFFER, binding++, block.buffer);
                }
                GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
            } finally {
                for (int i = 0; i < textures.length; i++) {
                    GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + i);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, textures[i]);
                    GL33C.glBindSampler(i, samplers[i]);
                }
                for (int i = 0; i < buffers.length; i++) {
                    if (buffers[i] != 0 && sizes[i] > 0)
                        GL33C.glBindBufferRange(
                                GL33C.GL_UNIFORM_BUFFER, i, buffers[i], offsets[i], sizes[i]);
                    else GL33C.glBindBufferBase(GL33C.GL_UNIFORM_BUFFER, i, buffers[i]);
                }
                GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, oldUbo);
                GL33C.glActiveTexture(active);
                GL33C.glUseProgram(oldProgram);
                GL33C.glBindVertexArray(oldVao);
                for (int i = 0; i < caps.length; i++) {
                    if (enabled[i]) GL33C.glEnable(caps[i]);
                    else GL33C.glDisable(caps[i]);
                }
                GL33C.glDepthFunc(depthFunc);
                GL33C.glDepthMask(oldDepthMask);
                GL33C.glColorMask(
                        mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
                GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, polygon.get(0));
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL33C.glViewport(
                        viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            }
        }
    }

    @Override
    public void close() {
        blocks.values().forEach(Block::close);
        GL33C.glDeleteProgram(program);
        GL33C.glDeleteVertexArrays(vao);
        GL33C.glDeleteSamplers(nearest);
        GL33C.glDeleteSamplers(linear);
    }
}
