package org.embeddedt.embeddium.extravalidation;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Real resource-pack graph/vertex/UBO overrides, plus a reformatted depth shader and hand merge.
 */
final class ResourceGraphParity {
    private static List<String> previous;
    private static CompletableFuture<Void> reload;

    private static String original(String name) throws Exception {
        try (var stream =
                me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod.class
                        .getResourceAsStream("/assets/sodium-extra/" + name)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static void begin(Minecraft client) throws Exception {
        Path pack = client.gameDirectory.toPath().resolve("resourcepacks/nocturna-extra-parity");
        Files.createDirectories(pack);
        write(
                pack,
                "pack.mcmeta",
                "{\"pack\":{\"pack_format\":15,\"description\":\"Extra resource parity"
                        + " fixture\"}}");
        var json = JsonParser.parseString(original("post_effect/panini.json")).getAsJsonObject();
        var first = json.getAsJsonArray("passes").get(0).getAsJsonObject();
        first.addProperty("vertex_shader", "nocturna_parity:custom_quad");
        json.getAsJsonArray("passes")
                .get(1)
                .getAsJsonObject()
                .getAsJsonObject("uniforms")
                .getAsJsonArray("BlitConfig")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("value")
                .set(0, new JsonPrimitive(0.5));
        write(pack, "assets/sodium-extra/post_effect/panini.json", json.toString());
        write(
                pack,
                "assets/nocturna_parity/shaders/custom_quad.vsh",
                "#version 330\n"
                    + "#include \"nested/coords.glsl\"\n"
                    + "#include \"nested/coords.glsl\"\n"
                    + "out vec2 texCoord;void main(){vec2"
                    + " uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=flipUv(uv);gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}");
        write(
                pack,
                "assets/nocturna_parity/shaders/nested/coords.glsl",
                "#version 330\nvec2 flipUv(vec2 uv){return vec2(1.0-uv.x,uv.y);}");
        String globalCheck =
                "\n"
                        + "if (any(greaterThan(CameraOffset,vec3(0))) ||"
                        + " any(lessThan(CameraOffset,vec3(-1))) || ScreenSize != vec2("
                        + client.getWindow().getWidth()
                        + ","
                        + client.getWindow().getHeight()
                        + ")) fragColor.g = 0.0;";
        String color =
                original("shaders/post/panini.fsh")
                        .replace("#version 330", "#version 330\n#include <minecraft:globals.glsl>")
                        .replace(
                                "fragColor = restoreOutlineMaskColor(fragColor);",
                                "fragColor = restoreOutlineMaskColor(fragColor);\n"
                                        + "fragColor.g = InSize.x / 128.0;\n"
                                        + "fragColor.b = OutSize.y / 96.0;"
                                        + globalCheck);
        write(pack, "assets/sodium-extra/shaders/post/panini.fsh", color);
        write(
                pack,
                "assets/sodium-extra/shaders/post/panini_depth.fsh",
                original("shaders/post/panini_depth.fsh")
                        .replace(
                                "gl_FragDepth = texelFetch(DepthSampler, sourceTexel, 0).r;",
                                "gl_FragDepth=texelFetch( DepthSampler,sourceTexel,0 ).r;"));
        previous = new ArrayList<>(client.getResourcePackRepository().getSelectedIds());
        client.getResourcePackRepository().reload();
        var selected = new ArrayList<>(previous);
        selected.add("file/nocturna-extra-parity");
        client.getResourcePackRepository().setSelected(selected);
        reload = client.reloadResourcePacks();
    }

    static boolean ready() {
        return reload != null && reload.isDone();
    }

    private static void write(Path root, String path, String data) throws Exception {
        var file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, data);
    }

    static void verify() throws Exception {
        try (var transfer = new PixelTransferState()) {
            verifyPixels();
        }
    }

    private static void verifyPixels() throws Exception {
        var source = new TextureTarget(64, 48, true, false);
        var output = new TextureTarget(32, 24, true, false);
        var hand = new TextureTarget(32, 24, true, false);
        var combined = new TextureTarget(32, 24, true, false);
        var bytes = MemoryUtil.memAlloc(64 * 48 * 4);
        var depths = MemoryUtil.memAllocFloat(64 * 48);
        Object program = null;
        try {
            for (int y = 0; y < 48; y++)
                for (int x = 0; x < 64; x++) {
                    bytes.put((byte) (x * 4)).put((byte) 0).put((byte) 0).put((byte) 255);
                    depths.put(0.7F);
                }
            bytes.flip();
            depths.flip();
            source.setFilterMode(GL33C.GL_LINEAR);
            GlStateManager._bindTexture(source.getColorTextureId());
            GL33C.glTexSubImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    64,
                    48,
                    GL33C.GL_RGBA,
                    GL33C.GL_UNSIGNED_BYTE,
                    bytes);
            GlStateManager._bindTexture(source.getDepthTextureId());
            GL33C.glTexSubImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    64,
                    48,
                    GL33C.GL_DEPTH_COMPONENT,
                    GL33C.GL_FLOAT,
                    depths);
            GlStateManager._bindTexture(0);
            Class<?> type =
                    Class.forName("me.flashyreese.mods.sodiumextra.client.render.PaniniProgram");
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            program = constructor.newInstance();
            var draw =
                    type.getDeclaredMethod(
                            "draw",
                            RenderTarget.class,
                            RenderTarget.class,
                            float.class,
                            float.class,
                            float.class,
                            boolean.class,
                            boolean.class,
                            int.class);
            draw.setAccessible(true);
            draw.invoke(program, source, output, 0F, 1.2F, 0.9F, false, false, -1);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, output.frameBufferId);
            var pixel = MemoryUtil.memAlloc(4);
            try {
                GL33C.glReadPixels(8, 12, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                ExtraValidation.require(
                        Math.abs(Byte.toUnsignedInt(pixel.get(0)) - 93) <= 3,
                        "JSON BlitConfig or replacement vertex shader ignored: "
                                + Byte.toUnsignedInt(pixel.get(0)));
                ExtraValidation.require(
                        Math.abs(Byte.toUnsignedInt(pixel.get(1)) - 128) <= 1
                                && Math.abs(Byte.toUnsignedInt(pixel.get(2)) - 64) <= 1,
                        "SamplerInfo InSize/OutSize not uploaded");
            } finally {
                MemoryUtil.memFree(pixel);
            }
            draw.invoke(program, source, output, 0F, 1.2F, 0.9F, false, true, -1);
            hand.bindWrite(true);
            GL33C.glClearBufferfv(GL33C.GL_DEPTH, 0, new float[] {0.2F});
            draw.invoke(
                    program,
                    output,
                    combined,
                    0F,
                    1.2F,
                    0.9F,
                    false,
                    true,
                    hand.getDepthTextureId());
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, combined.frameBufferId);
            var depth = MemoryUtil.memAllocFloat(1);
            try {
                GL33C.glReadPixels(8, 12, 1, 1, GL33C.GL_DEPTH_COMPONENT, GL33C.GL_FLOAT, depth);
                ExtraValidation.require(
                        Math.abs(depth.get(0) - 0.2F) < 0.00001,
                        "reformatted depth shader lost independent hand-depth integration");
            } finally {
                MemoryUtil.memFree(depth);
            }
            ExtraValidation.require(
                    GL33C.glGetError() == GL33C.GL_NO_ERROR, "resource graph generated a GL error");
            missingConfiguration(constructor);
            System.out.println(
                    "PASS resource JSON passes/uniforms, replacement vertex, SamplerInfo and"
                            + " whitespace-independent hand depth");
        } finally {
            if (program instanceof AutoCloseable c) c.close();
            source.destroyBuffers();
            output.destroyBuffers();
            hand.destroyBuffers();
            combined.destroyBuffers();
            MemoryUtil.memFree(bytes);
            MemoryUtil.memFree(depths);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private static void missingConfiguration(java.lang.reflect.Constructor<?> constructor)
            throws Exception {
        var path =
                Minecraft.getInstance()
                        .gameDirectory
                        .toPath()
                        .resolve(
                                "resourcepacks/nocturna-extra-parity/assets/sodium-extra/post_effect/panini.json");
        String previous = Files.readString(path);
        var graph = JsonParser.parseString(previous).getAsJsonObject();
        graph.getAsJsonArray("passes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("uniforms")
                .remove("PaniniConfig");
        try {
            Files.writeString(path, graph.toString());
            try {
                var unexpected = (AutoCloseable) constructor.newInstance();
                unexpected.close();
                throw new AssertionError(
                        "Panini accepted a GLSL block without its JSON custom-uniform declaration");
            } catch (java.lang.reflect.InvocationTargetException failure) {
                ExtraValidation.require(
                        failure.getCause() instanceof java.io.IOException
                                && failure.getCause().getMessage().contains("PaniniConfig"),
                        "missing JSON configuration failed for an unrelated reason");
            }
        } finally {
            Files.writeString(path, previous);
        }
    }

    static void restore(Minecraft client) {
        if (previous != null) {
            client.getResourcePackRepository().setSelected(previous);
            previous = null;
            reload = client.reloadResourcePacks();
        }
    }
}
