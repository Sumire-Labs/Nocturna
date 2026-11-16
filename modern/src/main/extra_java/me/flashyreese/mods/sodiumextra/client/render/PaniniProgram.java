package me.flashyreese.mods.sodiumextra.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.IntBuffer;

/** OpenGL backend for the upstream color filter and nearest-surface depth projection shaders. */
final class PaniniProgram implements AutoCloseable {
    private final int color, depth, mergeDepth, vao;

    PaniniProgram() throws IOException {
        int c = 0, d = 0, merge = 0, vertexArray = 0;
        try {
            c = link(fragment("panini"));
            String depthSource = fragment("panini_depth");
            d = link(depthSource);
            merge =
                    link(
                            depthSource
                                    .replace(
                                            "void main() {",
                                            "uniform sampler2D HandDepthSampler;\nvoid main() {")
                                    .replace(
                                            "gl_FragDepth = texelFetch(DepthSampler, sourceTexel,"
                                                    + " 0).r;",
                                            "gl_FragDepth = min(texelFetch(DepthSampler,"
                                                + " sourceTexel, 0).r, texelFetch(HandDepthSampler,"
                                                + " ivec2(gl_FragCoord.xy), 0).r);"));
            vertexArray = GL33C.glGenVertexArrays();
        } catch (IOException | RuntimeException ex) {
            GL33C.glDeleteProgram(c);
            GL33C.glDeleteProgram(d);
            GL33C.glDeleteProgram(merge);
            if (vertexArray != 0) GL33C.glDeleteVertexArrays(vertexArray);
            throw ex;
        }
        color = c;
        depth = d;
        mergeDepth = merge;
        vao = vertexArray;
    }

    private static String resource(String path) throws IOException {
        try (var reader =
                Minecraft.getInstance()
                        .getResourceManager()
                        .openAsReader(new ResourceLocation("sodium-extra", "shaders/" + path))) {
            StringBuilder result = new StringBuilder();
            char[] chars = new char[4096];
            for (int n; (n = reader.read(chars)) != -1; ) result.append(chars, 0, n);
            return result.toString();
        }
    }

    private static String fragment(String name) throws IOException {
        return resource("post/" + name + ".fsh")
                .replace("#extension GL_ARB_separate_shader_objects : require", "")
                .replace("#include <sodium-extra:panini.glsl>", resource("include/panini.glsl"))
                .replaceAll("layout\\(location = 0\\) (in|out)", "$1")
                .replaceAll("(?s)layout\\(std140\\) uniform SamplerInfo \\{.*?};", "")
                .replaceAll(
                        "(?s)layout\\(std140\\) uniform PaniniConfig \\{.*?};",
                        "uniform vec4 Params;");
    }

    private static int compile(int type, String source) {
        int shader = GL33C.glCreateShader(type);
        GL33C.glShaderSource(shader, source);
        GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == 0) {
            String message = GL33C.glGetShaderInfoLog(shader);
            GL33C.glDeleteShader(shader);
            throw new IllegalStateException("Panini shader compilation failed: " + message);
        }
        return shader;
    }

    private static int link(String fragment) {
        int vertex = 0, pixel = 0, program = 0;
        try {
            vertex =
                    compile(
                            GL33C.GL_VERTEX_SHADER,
                            """
                            #version 330
                            out vec2 texCoord;
                            void main() {
                                vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                                texCoord = uv;
                                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                            }
                            """);
            pixel = compile(GL33C.GL_FRAGMENT_SHADER, fragment);
            program = GL33C.glCreateProgram();
            GL33C.glAttachShader(program, vertex);
            GL33C.glAttachShader(program, pixel);
            GL33C.glLinkProgram(program);
            if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(
                        "Panini shader link failed: " + GL33C.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException ex) {
            if (program != 0) GL33C.glDeleteProgram(program);
            throw ex;
        } finally {
            if (vertex != 0) GL33C.glDeleteShader(vertex);
            if (pixel != 0) GL33C.glDeleteShader(pixel);
        }
    }

    void draw(
            com.mojang.blaze3d.pipeline.RenderTarget source,
            com.mojang.blaze3d.pipeline.RenderTarget output,
            float strength,
            float horizontal,
            float vertical,
            boolean mask,
            boolean writeDepth,
            int handDepth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int framebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousProgram = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
            int previousVao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
            int activeTexture = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
            IntBuffer viewport = stack.mallocInt(4);
            GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
            var colorMask = stack.malloc(4);
            GL33C.glGetBooleanv(GL33C.GL_COLOR_WRITEMASK, colorMask);
            boolean depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
            boolean blend = GL33C.glIsEnabled(GL33C.GL_BLEND),
                    cull = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
            boolean depthTest = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST),
                    scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
            int depthFunc = GL33C.glGetInteger(GL33C.GL_DEPTH_FUNC);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            int texture0 = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE1);
            int texture1 = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            int sampler0 = GL33C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, 0);
            int sampler1 = GL33C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, 1);
            try {
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, output.frameBufferId);
                GL33C.glViewport(0, 0, output.width, output.height);
                GL33C.glDisable(GL33C.GL_BLEND);
                GL33C.glDisable(GL33C.GL_CULL_FACE);
                GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
                set(GL33C.GL_DEPTH_TEST, writeDepth);
                GL33C.glDepthFunc(GL33C.GL_ALWAYS);
                GL33C.glDepthMask(writeDepth);
                GL33C.glColorMask(!writeDepth, !writeDepth, !writeDepth, !writeDepth);
                int program = writeDepth ? handDepth >= 0 ? mergeDepth : depth : color;
                GL33C.glUseProgram(program);
                GL33C.glBindVertexArray(vao);
                GL33C.glUniform4f(
                        GL33C.glGetUniformLocation(program, "Params"),
                        strength,
                        horizontal,
                        vertical,
                        mask ? 1 : 0);
                GL33C.glUniform1i(
                        GL33C.glGetUniformLocation(
                                program, writeDepth ? "DepthSampler" : "InSampler"),
                        0);
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
                GL33C.glBindSampler(0, 0);
                GL33C.glBindTexture(
                        GL33C.GL_TEXTURE_2D,
                        writeDepth ? source.getDepthTextureId() : source.getColorTextureId());
                if (handDepth >= 0) {
                    GL33C.glUniform1i(GL33C.glGetUniformLocation(program, "HandDepthSampler"), 1);
                    GL33C.glActiveTexture(GL33C.GL_TEXTURE1);
                    GL33C.glBindSampler(1, 0);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, handDepth);
                }
                GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
            } finally {
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture0);
                GL33C.glActiveTexture(GL33C.GL_TEXTURE1);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
                GL33C.glBindSampler(0, sampler0);
                GL33C.glBindSampler(1, sampler1);
                GL33C.glActiveTexture(activeTexture);
                GL33C.glBindVertexArray(previousVao);
                GL33C.glUseProgram(previousProgram);
                GL33C.glDepthMask(depthMask);
                GL33C.glDepthFunc(depthFunc);
                GL33C.glColorMask(
                        colorMask.get(0) != 0,
                        colorMask.get(1) != 0,
                        colorMask.get(2) != 0,
                        colorMask.get(3) != 0);
                set(GL33C.GL_BLEND, blend);
                set(GL33C.GL_CULL_FACE, cull);
                set(GL33C.GL_DEPTH_TEST, depthTest);
                set(GL33C.GL_SCISSOR_TEST, scissor);
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL33C.glViewport(
                        viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            }
        }
    }

    private static void set(int capability, boolean enabled) {
        if (enabled) GL33C.glEnable(capability);
        else GL33C.glDisable(capability);
    }

    @Override
    public void close() {
        GL33C.glDeleteProgram(color);
        GL33C.glDeleteProgram(depth);
        GL33C.glDeleteProgram(mergeDepth);
        GL33C.glDeleteVertexArrays(vao);
    }
}
