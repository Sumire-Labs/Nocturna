package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.irisshaders.iris.texture.pbr.PBRResourceIndex;
import net.irisshaders.iris.texture.pbr.PBRType;
import net.irisshaders.iris.texture.pbr.loader.AtlasPBRLoader;
import net.irisshaders.iris.texture.pbr.loader.PBRTextureLoader;
import net.irisshaders.iris.texture.pbr.loader.SimplePBRLoader;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Runs the real loaders with and without the index on the Java 17 client and compares their output. */
public final class PBRResourceIndexValidation {
    private static final ResourceLocation SIMPLE = id("textures/simple.png");
    private static final ResourceLocation ATLAS = id("block/animated");

    private static ResourceLocation id(String path) { return new ResourceLocation("nocturna_validation", path); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void verify() throws Exception {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var owner = PBRResourceIndex.class.getDeclaredField("owner");
        owner.setAccessible(true);
        require(owner.get(null) == ((ReloadableResourceManager)
            net.minecraft.client.Minecraft.getInstance().getResourceManager()).resources,
            "texture reload did not start the client PBR index");
        Path root = Files.createTempDirectory(Path.of("."), "pbr-index-validation-");
        Path textures = Files.createDirectories(root.resolve("assets/nocturna_validation/textures"));
        for (String suffix : List.of("_n", "_s")) {
            png(textures.resolve("simple" + suffix + ".png"), 8, 8);
            Files.writeString(textures.resolve("simple" + suffix + ".png.mcmeta"),
                "{\"texture\":{\"blur\":true,\"clamp\":true}}");
            Path block = Files.createDirectories(textures.resolve("block"));
            png(block.resolve("animated" + suffix + ".png"), suffix.equals("_n") ? 16 : 8,
                suffix.equals("_n") ? 32 : 16);
            Files.writeString(block.resolve("animated" + suffix + ".png.mcmeta"),
                "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        }
        png(textures.resolve("face_n.png"), 8, 8);
        png(textures.resolve("face_e.png"), 8, 8);
        var manager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        try {
            reload(manager, root);
            require(PBRResourceIndex.mayExist(manager, id("textures/simple_n.png")), "lazy index lost a map");
            require(owner.get(null) == manager.resources, "lazy index did not select the current stack");
            ResourceManager direct = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    try { return method.invoke(manager, args); }
                    catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
            compareSimple(manager, direct, SIMPLE, 2);
            compareSimple(manager, direct, id("textures/missing.png"), 0);
            compareSimple(manager, direct, id("textures/face.png"), 0);
            compareAtlas(manager, direct);
            require(!PBRResourceIndex.mayExist(manager, id("textures/added_n.png")), "addition already exists");
            png(textures.resolve("added_n.png"), 8, 8);
            Files.delete(textures.resolve("simple_n.png"));
            reload(manager, root);
            require(owner.get(null) == null, "old stack was not released during reload");
            require(PBRResourceIndex.mayExist(manager, id("textures/added_n.png")), "reload kept a stale negative");
            require(owner.get(null) == manager.resources, "reload did not replace the owner");
            require(!PBRResourceIndex.mayExist(manager, id("textures/simple_n.png")), "reload kept a stale positive");
            manager.close();
            require(owner.get(null) == null, "close retained the pack stack");
            Files.writeString(Path.of("pbr-resource-index-validation.txt"),
                "PASS Java17; real reload/close mixins; simple PBR pixels and sampler state; " +
                "normal/specular atlas frames, interpolation and mipmaps; directional exclusions; missing maps; reload additions/removals.\n");
        } finally {
            manager.close();
            GlStateManager._bindTexture(previousTexture);
        }
    }

    private static void reload(ReloadableResourceManager manager, Path root) {
        manager.createReload(Runnable::run, Runnable::run, CompletableFuture.completedFuture(Unit.INSTANCE),
            List.of(new PathPackResources("pbr-validation", root, false))).done().join();
    }

    private static void png(Path path, int width, int height) throws Exception {
        try (var image = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, 0xff000000 | ((y < height / 2 ? 220 : 40) << 16) | (x * 7 << 8) | y);
            }
            image.writeToFile(path);
        }
    }

    private static List<AbstractTexture> simple(ResourceManager manager, ResourceLocation location) {
        var result = new ArrayList<AbstractTexture>();
        new SimplePBRLoader().load(new SimpleTexture(location), manager, new PBRTextureLoader.PBRTextureConsumer() {
            @Override public void acceptNormalTexture(AbstractTexture texture) { result.add(texture); }
            @Override public void acceptSpecularTexture(AbstractTexture texture) { result.add(texture); }
        });
        return result;
    }

    private static void compareSimple(ResourceManager indexed, ResourceManager direct, ResourceLocation id, int expected) {
        var a = simple(indexed, id);
        var b = simple(direct, id);
        try {
            require(a.size() == expected && b.size() == expected, "simple map count changed: " + id);
            for (int i = 0; i < a.size(); i++) {
                GlStateManager._bindTexture(a.get(i).getId());
                byte[] pixels = pixels(0, 8, 8);
                int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                int wrap = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
                require(min == GL11.GL_LINEAR && wrap == org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE,
                    "texture metadata was not applied");
                GlStateManager._bindTexture(b.get(i).getId());
                require(Arrays.equals(pixels, pixels(0, 8, 8)), "simple PBR pixels changed");
                require(min == GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER), "filter changed");
                require(wrap == GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S), "wrap changed");
            }
        } finally {
            for (var texture : a) { texture.close(); texture.releaseId(); }
            for (var texture : b) { texture.close(); texture.releaseId(); }
        }
    }

    private static byte[] pixels(int level, int width, int height) {
        var buffer = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            byte[] result = new byte[buffer.capacity()];
            buffer.get(result);
            return result;
        } finally { MemoryUtil.memFree(buffer); }
    }

    private static void compareAtlas(ResourceManager indexed, ResourceManager direct) {
        var atlas = new TextureAtlas(id("atlas"));
        var contents = new SpriteContents(ATLAS, new FrameSize(16, 16), new NativeImage(16, 16, false),
            AnimationMetadataSection.EMPTY);
        var base = new TextureAtlasSprite(id("atlas"), contents, 16, 16, 0, 0) {};
        var loader = new AtlasLoader();
        try {
            for (var type : PBRType.values()) {
                var a = loader.sprite(base, indexed, atlas, type);
                var b = loader.sprite(base, direct, atlas, type);
                require(a != null && b != null, "atlas map missing: " + type);
                try {
                    require(a.contents().width() == 16 && a.contents().height() == 16, "frame size changed");
                    require(Arrays.equals(a.contents().getUniqueFrames().toArray(), new int[]{0, 1}), "animation lost");
                    compareFrames(a, b);
                } finally { a.contents().close(); b.contents().close(); }
            }
        } finally { contents.close(); atlas.close(); atlas.releaseId(); }
    }

    private static void compareFrames(TextureAtlasSprite a, TextureAtlasSprite b) {
        int first = TextureUtil.generateTextureId(), second = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(first, 1, 16, 16);
        a.uploadFirstFrame();
        TextureUtil.prepareImage(second, 1, 16, 16);
        b.uploadFirstFrame();
        var at = a.createTicker();
        var bt = b.createTicker();
        require(at != null && bt != null, "animated ticker missing");
        var distinctFrames = new java.util.HashSet<Integer>();
        try {
            for (int frame = 0; frame < 6; frame++) {
                for (int mip = 0; mip <= 1; mip++) {
                    GlStateManager._bindTexture(first);
                    byte[] expected = pixels(mip, 16 >> mip, 16 >> mip);
                    if (mip == 0) distinctFrames.add(Arrays.hashCode(expected));
                    GlStateManager._bindTexture(second);
                    require(Arrays.equals(expected, pixels(mip, 16 >> mip, 16 >> mip)), "animated/mip PBR pixels changed");
                }
                org.embeddedt.embeddium.api.render.texture.SpriteUtil.markSpriteActive(a);
                org.embeddedt.embeddium.api.render.texture.SpriteUtil.markSpriteActive(b);
                GlStateManager._bindTexture(first); at.tickAndUpload();
                GlStateManager._bindTexture(second); bt.tickAndUpload();
            }
            require(distinctFrames.size() >= 3, "PBR animation/interpolation did not advance");
        } finally { at.close(); bt.close(); TextureUtil.releaseTextureId(first); TextureUtil.releaseTextureId(second); }
    }

    private static final class AtlasLoader extends AtlasPBRLoader {
        PBRTextureAtlasSprite sprite(TextureAtlasSprite base, ResourceManager manager, TextureAtlas atlas, PBRType type) {
            return createPBRSprite(base, manager, atlas, 16, 16, 1, type);
        }
    }
}
