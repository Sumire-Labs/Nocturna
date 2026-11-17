package me.flashyreese.mods.sodiumextra.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL33C;

import java.io.*;
import java.util.*;

/**
 * The pinned post-effect target/pass/uniform schema, with persistent targets owned by the chain.
 */
final class ExtraPostChain implements AutoCloseable {
    private record TargetSpec(Integer width, Integer height, boolean persistent, int clearColor) {}

    private record Input(
            String sampler,
            ResourceLocation target,
            ResourceLocation texture,
            boolean depth,
            boolean bilinear,
            int width,
            int height) {}

    private record Pass(EffectGpuProgram program, List<Input> inputs, ResourceLocation output) {}

    private final Map<ResourceLocation, TargetSpec> specs = new LinkedHashMap<>();
    private final Map<ResourceLocation, RenderTarget> targets = new LinkedHashMap<>();
    private final List<Pass> passes = new ArrayList<>();
    private EffectGpuProgram configuration;

    ExtraPostChain(ResourceLocation id) throws IOException {
        try {
            JsonObject json;
            try (var reader =
                    Minecraft.getInstance()
                            .getResourceManager()
                            .openAsReader(
                                    new ResourceLocation(
                                            id.getNamespace(),
                                            "post_effect/" + id.getPath() + ".json"))) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (var entry :
                    (json.has("targets") ? json.getAsJsonObject("targets") : new JsonObject())
                            .entrySet()) {
                var target = entry.getValue().getAsJsonObject();
                specs.put(
                        new ResourceLocation(entry.getKey()),
                        new TargetSpec(
                                integer(target, "width"),
                                integer(target, "height"),
                                bool(target, "persistent"),
                                target.has("clear_color")
                                        ? target.get("clear_color").getAsInt()
                                        : 0));
            }
            for (var element : json.getAsJsonArray("passes")) {
                var pass = element.getAsJsonObject();
                var program =
                        new EffectGpuProgram(
                                EffectShaderSource.stage(
                                        new ResourceLocation(
                                                pass.get("vertex_shader").getAsString()),
                                        ".vsh"),
                                EffectShaderSource.stage(
                                        new ResourceLocation(
                                                pass.get("fragment_shader").getAsString()),
                                        ".fsh"));
                try {
                    var inputs = new ArrayList<Input>();
                    var names = new HashSet<String>();
                    for (var value :
                            (pass.has("inputs")
                                    ? pass.getAsJsonArray("inputs")
                                    : new JsonArray())) {
                        var input = value.getAsJsonObject();
                        String sampler = input.get("sampler_name").getAsString();
                        if (!names.add(sampler))
                            throw new IOException("Duplicate post-effect sampler " + sampler);
                        inputs.add(
                                new Input(
                                        sampler,
                                        input.has("target")
                                                ? new ResourceLocation(
                                                        input.get("target").getAsString())
                                                : null,
                                        input.has("location")
                                                ? new ResourceLocation(
                                                        input.get("location").getAsString())
                                                : null,
                                        bool(input, "use_depth_buffer"),
                                        bool(input, "bilinear"),
                                        input.has("width") ? input.get("width").getAsInt() : 1,
                                        input.has("height") ? input.get("height").getAsInt() : 1));
                    }
                    program.defaults(
                            pass.has("uniforms")
                                    ? pass.getAsJsonObject("uniforms")
                                    : new JsonObject());
                    passes.add(
                            new Pass(
                                    program,
                                    List.copyOf(inputs),
                                    new ResourceLocation(pass.get("output").getAsString())));
                    if (configuration == null
                            && pass.has("uniforms")
                            && pass.getAsJsonObject("uniforms").has("PaniniConfig"))
                        configuration = program;
                } catch (IOException | RuntimeException failure) {
                    program.close();
                    throw failure;
                }
            }
            validateTargets();
        } catch (IOException | RuntimeException failure) {
            close();
            throw failure;
        }
    }

    private void validateTargets() throws IOException {
        var external =
                Set.of(
                        new ResourceLocation("sodium-extra", "panini_source"),
                        new ResourceLocation("minecraft", "main"));
        var known = new HashSet<>(external);
        for (var id : specs.keySet())
            if (!known.add(id)) throw new IOException("Reserved post-effect target " + id);
        for (var pass : passes) {
            if (!known.contains(pass.output))
                throw new IOException("Unknown post-effect output " + pass.output);
            for (var input : pass.inputs) {
                if (input.target == null) {
                    if (input.texture == null)
                        throw new IOException("Post-effect input needs target or texture");
                    continue;
                }
                if (!known.contains(input.target))
                    throw new IOException("Unknown post-effect input " + input.target);
                if (input.target.equals(pass.output))
                    throw new IOException("Post-effect read/write feedback: " + input.target);
                if (input.depth && specs.containsKey(input.target))
                    throw new IOException(
                            "Internal post-effect target has no depth: " + input.target);
            }
        }
    }

    private static Integer integer(JsonObject object, String key) {
        if (!object.has(key)) return null;
        int value = object.get(key).getAsInt();
        if (value < 1) throw new IllegalArgumentException("Invalid target size " + key);
        return value;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && object.get(key).getAsBoolean();
    }

    boolean hasConfiguration() {
        return configuration != null;
    }

    void parameters(float strength, float horizontal, float vertical, boolean mask) {
        if (configuration != null)
            configuration.vector(
                    "PaniniConfig", "Params", strength, horizontal, vertical, mask ? 1 : 0);
    }

    void execute(RenderTarget source, RenderTarget output) {
        var bindings = new LinkedHashMap<ResourceLocation, RenderTarget>();
        bindings.put(new ResourceLocation("sodium-extra", "panini_source"), source);
        bindings.put(new ResourceLocation("minecraft", "main"), output);
        for (var entry : specs.entrySet()) {
            var spec = entry.getValue();
            int width = spec.width != null ? spec.width : output.width,
                    height = spec.height != null ? spec.height : output.height;
            RenderTarget target = targets.get(entry.getKey());
            if (target == null || target.width != width || target.height != height) {
                if (target != null) target.destroyBuffers();
                target = new TargetFormat(GL33C.GL_RGBA8, 0, false).create(width, height);
                targets.put(entry.getKey(), target);
                clear(target, spec.clearColor);
            }
            bindings.put(entry.getKey(), target);
        }
        Set<ResourceLocation> written = new HashSet<>();
        for (var pass : passes) {
            var destination = require(bindings, pass.output);
            var images = new LinkedHashMap<String, EffectGpuProgram.Image>();
            for (var input : pass.inputs) {
                if (input.target != null) {
                    var target = require(bindings, input.target);
                    if (target == destination)
                        throw new IllegalStateException(
                                "Post-effect read/write feedback: " + input.target);
                    int texture =
                            input.depth ? target.getDepthTextureId() : target.getColorTextureId();
                    if (texture < 0)
                        throw new IllegalStateException("Missing depth input " + input.target);
                    images.put(
                            input.sampler,
                            new EffectGpuProgram.Image(
                                    texture, target.width, target.height, input.bilinear));
                } else if (input.texture != null) {
                    var texture =
                            Minecraft.getInstance()
                                    .getTextureManager()
                                    .getTexture(
                                            new ResourceLocation(
                                                    input.texture.getNamespace(),
                                                    "textures/"
                                                            + input.texture.getPath()
                                                            + ".png"));
                    images.put(
                            input.sampler,
                            new EffectGpuProgram.Image(
                                    texture.getId(), input.width, input.height, input.bilinear));
                } else throw new IllegalStateException("Post-effect input needs target or texture");
            }
            var internal = specs.get(pass.output);
            if (internal != null && !internal.persistent && !written.contains(pass.output))
                clear(destination, internal.clearColor);
            pass.program.draw(destination, images, false);
            written.add(pass.output);
        }
    }

    private static void clear(RenderTarget target, int color) {
        int framebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL33C.glGetBooleanv(GL33C.GL_COLOR_WRITEMASK, mask);
            try {
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
                GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
                GL33C.glColorMask(true, true, true, true);
                GL33C.glClearBufferfv(
                        GL33C.GL_COLOR,
                        0,
                        stack.floats(
                                ((color >>> 16) & 255) / 255F,
                                ((color >>> 8) & 255) / 255F,
                                (color & 255) / 255F,
                                ((color >>> 24) & 255) / 255F));
            } finally {
                GL33C.glColorMask(
                        mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
                if (scissor) GL33C.glEnable(GL33C.GL_SCISSOR_TEST);
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffer);
            }
        }
    }

    private static RenderTarget require(
            Map<ResourceLocation, RenderTarget> values, ResourceLocation id) {
        var target = values.get(id);
        if (target == null) throw new IllegalStateException("Unknown post-effect target " + id);
        return target;
    }

    @Override
    public void close() {
        passes.forEach(pass -> pass.program.close());
        passes.clear();
        targets.values().forEach(RenderTarget::destroyBuffers);
        targets.clear();
    }
}
