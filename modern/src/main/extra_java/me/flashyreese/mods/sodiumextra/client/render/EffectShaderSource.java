package me.flashyreese.mods.sodiumextra.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Resource-pack shader resolution for the post-effect ABI used by Extra's pinned assets. */
final class EffectShaderSource {
    static String stage(ResourceLocation id, String extension) throws IOException {
        ResourceLocation stage =
                new ResourceLocation(id.getNamespace(), "shaders/" + id.getPath() + extension);
        String directory = net.minecraft.FileUtil.getFullResourcePath(stage.getPath());
        var imported = new HashSet<ResourceLocation>();
        imported.add(stage);
        var preprocessor =
                new com.mojang.blaze3d.preprocessor.GlslPreprocessor() {
                    @Override
                    public String applyImport(boolean relative, String name) {
                        var include = new ResourceLocation(name);
                        var path =
                                relative && !name.contains(":")
                                        ? new ResourceLocation(
                                                stage.getNamespace(), directory + name)
                                        : new ResourceLocation(
                                                include.getNamespace(),
                                                "shaders/include/" + include.getPath());
                        if (!imported.add(path)) return null;
                        try {
                            return read(path);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    }
                };
        try {
            String code = String.join("", preprocessor.process(read(stage)));
            if (!org.lwjgl.opengl.GL.getCapabilities().GL_ARB_separate_shader_objects
                    && !org.lwjgl.opengl.GL.getCapabilities().OpenGL41)
                code =
                        code.replace("#extension GL_ARB_separate_shader_objects : require", "")
                                .replaceAll(
                                        "layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*(in|out)",
                                        "$1");
            return code;
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static String read(ResourceLocation id) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        String fallback = builtin(id);
        String source;
        if (resource.isEmpty()
                || fallback != null && resource.get().sourcePackId().equals("vanilla")) {
            if (fallback == null) throw new FileNotFoundException(id.toString());
            source = fallback;
        } else
            try (var reader = resource.get().openAsReader()) {
                var out = new StringBuilder();
                char[] buffer = new char[4096];
                for (int n; (n = reader.read(buffer)) != -1; ) out.append(buffer, 0, n);
                source = out.toString();
            }
        return source.replaceAll("(?m)(^\\h*#\\h*)include\\b", "$1moj_import");
    }

    private static String builtin(ResourceLocation id) {
        if (!id.getNamespace().equals("minecraft")) return null;
        return switch (id.getPath()) {
            case "shaders/core/screenquad.vsh" ->
                    """
                    #version 330
                    out vec2 texCoord;
                    void main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=uv;gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}
                    """;
            case "shaders/post/blit.fsh" ->
                    """
                    #version 330
                    uniform sampler2D InSampler;
                    layout(std140) uniform BlitConfig { vec4 ColorModulate; };
                    in vec2 texCoord;out vec4 fragColor;
                    void main(){fragColor=texture(InSampler,texCoord)*ColorModulate;}
                    """;
            case "shaders/include/globals.glsl" ->
                    """
                    layout(std140) uniform Globals { ivec3 CameraBlockPos;float GlintAlpha;vec3 CameraOffset;float GameTime;vec2 ScreenSize;int MenuBlurRadius;int UseRgss; };
                    """;
            default -> null;
        };
    }
}
