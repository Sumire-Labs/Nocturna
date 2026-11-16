package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual client mixins and native renderer streams, including the real Iris shadow-pass lifetime. */
public final class FismValidation {
    private static final int LIGHT = 15728880;
    private static int bannerCases, signCases, shadowPasses;
    private static boolean ready;
    private static final AtomicInteger itemCalls = new AtomicInteger();
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "embeddium_validation");
    private static final RegistryObject<Item> MOD_ITEM = ITEMS.register("fism_foil", () -> new Item(new Item.Properties()) {
        @Override public boolean isFoil(ItemStack stack) { itemCalls.incrementAndGet(); return true; }
    });

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void verify() throws Exception {
        boolean previous = ShadowRenderer.ACTIVE;
        try {
            foil();
            banners();
            signs();
            maps();
            ready = true;
            System.out.println("PASS FISM: " + bannerCases + " banner/shield cases; " + signCases +
                " sign cases; exact normal/base vertex streams; foil and map lifetime");
        } finally { ShadowRenderer.ACTIVE = previous; }
    }

    private static void foil() {
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.UNBREAKING, 1);
        var tag = sword.getTag().copy();
        itemCalls.set(0);
        var custom = new ItemStack(MOD_ITEM.get());
        ShadowRenderer.ACTIVE = false;
        require(sword.hasFoil() && custom.hasFoil() && itemCalls.get() == 1, "normal foil changed");
        ShadowRenderer.ACTIVE = true;
        require(!sword.hasFoil() && !custom.hasFoil() && !ItemStack.EMPTY.hasFoil(), "shadow foil survived");
        require(itemCalls.get() == 1, "shadow foil still called the mod item override");
        ShadowRenderer.ACTIVE = false;
        require(sword.hasFoil() && custom.hasFoil() && itemCalls.get() == 2, "foil did not recover after shadow");
        require(tag.equals(sword.getTag()), "foil optimization changed item NBT");
    }

    private static List<Pair<Holder<BannerPattern>, DyeColor>> patterns(DyeColor base, int count) {
        var list = new ArrayList<Pair<Holder<BannerPattern>, DyeColor>>();
        list.add(Pair.of(BuiltInRegistries.BANNER_PATTERN.getHolderOrThrow(BannerPatterns.BASE), base));
        for (int i = 1; i < count; i++) {
            var key = (i & 1) == 0 ? BannerPatterns.CROSS : BannerPatterns.BORDER;
            list.add(Pair.of(BuiltInRegistries.BANNER_PATTERN.getHolderOrThrow(key), DyeColor.byId(i % 16)));
        }
        return list;
    }

    private static Trace banner(boolean banner, List<Pair<Holder<BannerPattern>, DyeColor>> patterns,
                                boolean glint, boolean shadow) {
        ShadowRenderer.ACTIVE = shadow;
        var models = Minecraft.getInstance().getEntityModels();
        ModelPart part = banner ? models.bakeLayer(ModelLayers.BANNER).getChild("flag")
            : new ShieldModel(models.bakeLayer(ModelLayers.SHIELD)).plate();
        part.xRot = -0.2F;
        var pose = new PoseStack();
        pose.translate(0.125, -0.25, 0.5);
        var trace = new Trace();
        BannerRenderer.renderPatterns(pose, trace, LIGHT, OverlayTexture.NO_OVERLAY, part,
            banner ? ModelBakery.BANNER_BASE : ModelBakery.SHIELD_BASE, banner, patterns, glint);
        require(pose.clear(), "banner changed pose stack depth");
        return trace;
    }

    private static void banners() {
        for (boolean isBanner : new boolean[]{true, false}) {
            for (var color : DyeColor.values()) {
                for (int count : new int[]{1, 3, 20}) {
                    for (boolean foil : new boolean[]{false, true}) {
                        var list = patterns(color, count);
                        var before = banner(isBanner, list, foil, false);
                        var actual = banner(isBanner, list, foil, true);
                        // Iris retains the plain substrate outside submitPatterns and one dyed BASE layer.
                        var reference = banner(isBanner, List.of(list.get(0)), false, false);
                        same(reference, actual, "Iris base layer " + color + " banner=" + isBanner);
                        same(before, banner(isBanner, list, foil, false), "normal banner after shadow");
                        require(actual.draws.size() == 2 && actual.vertices() > 0, "base cloth/dye missing");
                        if (count > 1) require(before.vertices() > actual.vertices(), "decorations were not removed");
                        bannerCases++;
                    }
                }
            }
            var reordered = patterns(DyeColor.RED, 3);
            java.util.Collections.swap(reordered, 0, 2);
            same(banner(isBanner, patterns(DyeColor.RED, 1), false, false),
                banner(isBanner, reordered, true, true), "reordered BASE");
            // An empty legacy list has no dye argument. Its plain substrate must still be kept.
            same(banner(isBanner, List.of(), false, false), banner(isBanner, List.of(), true, true), "empty list");
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void text(SignBlockEntity sign, String name, SignText value) throws Exception {
        var field = SignBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(sign, value);
    }

    private static Trace sign(SignBlockEntity sign, boolean shadow) {
        ShadowRenderer.ACTIVE = shadow;
        var pose = new PoseStack();
        var trace = new Trace();
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(sign);
        require(renderer != null, "missing sign renderer");
        renderer.render(sign, 0.25F, pose, trace, LIGHT, OverlayTexture.NO_OVERLAY);
        require(pose.clear(), "sign changed pose stack depth");
        return trace;
    }

    private static void signs() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            for (boolean glowing : new boolean[]{false, true}) {
                SignBlockEntity sign = kind == 2
                    ? new HangingSignBlockEntity(BlockPos.ZERO, Blocks.OAK_HANGING_SIGN.defaultBlockState())
                    : new SignBlockEntity(BlockPos.ZERO,
                        (kind == 0 ? Blocks.OAK_SIGN : Blocks.OAK_WALL_SIGN).defaultBlockState());
                var blank = new SignText();
                text(sign, "frontText", blank); text(sign, "backText", blank);
                var reference = sign(sign, false);
                for (boolean front : new boolean[]{true, false}) {
                    var words = new SignText().setMessage(0, Component.literal(front ? "Front" : "Back"))
                        .setHasGlowingText(glowing);
                    text(sign, front ? "frontText" : "backText", words);
                }
                var before = sign(sign, false);
                require(before.vertices() > reference.vertices(), "normal sign text was not drawn");
                same(reference, sign(sign, true), "shadow sign model " + kind + " glowing=" + glowing);
                same(before, sign(sign, false), "normal sign text after shadow");
                signCases++;
            }
        }
    }

    private static void maps() throws Exception {
        int id = 2147483000;
        var data = MapItemSavedData.createFresh(0, 0, (byte) 0, true, false, Level.OVERWORLD);
        Arrays.fill(data.colors, (byte) 42);
        data.addClientSideDecorations(List.of(new MapDecoration(MapDecoration.Type.TARGET_X,
            (byte) 0, (byte) 0, (byte) 0, Component.literal("Marker"))));
        try (var renderer = new MapRenderer(Minecraft.getInstance().getTextureManager())) {
            ShadowRenderer.ACTIVE = true;
            var skipped = new Trace();
            renderer.render(new PoseStack(), skipped, id, data, true, LIGHT);
            require(skipped.vertices() == 0 && ((java.util.Map<?, ?>) field(renderer, "maps")).isEmpty(),
                "shadow created a map texture or geometry");
            renderer.update(id, data);
            var instance = ((java.util.Map<?, ?>) field(renderer, "maps")).get(id);
            renderer.render(new PoseStack(), skipped, id, data, true, LIGHT);
            require((boolean) field(instance, "requiresUpload"), "shadow consumed the pending map update");
            ShadowRenderer.ACTIVE = false;
            var normal = new Trace();
            renderer.render(new PoseStack(), normal, id, data, true, LIGHT);
            require(normal.vertices() > 4 && !(boolean) field(instance, "requiresUpload"),
                "normal map/decorations did not upload and draw");
            ShadowRenderer.ACTIVE = true;
            renderer.render(new PoseStack(), skipped, id, data, false, LIGHT);
            require(skipped.vertices() == 0, "shadow map or decoration survived");
            ShadowRenderer.ACTIVE = false;
            var after = new Trace();
            renderer.render(new PoseStack(), after, id, data, true, LIGHT);
            same(normal, after, "normal map after shadow");
        }
    }

    public static void realShadowPass() {
        if (!ready || shadowPasses++ != 0) return;
        require(IrisApiV0Impl.INSTANCE.isRenderingShadowPass(), "shadow hook ran outside the shadow pass");
        require(!new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).hasFoil(), "real shadow pass retained foil");
        var trace = banner(true, patterns(DyeColor.BLUE, 3), false, true);
        require(trace.draws.size() == 2 && trace.vertices() > 0, "real shadow pass lost the banner base");
    }

    public static void finish() throws Exception {
        require(ready && shadowPasses > 0, "no real Iris shadow pass was checked");
        require(!ShadowRenderer.ACTIVE, "shadow state leaked into normal rendering");
        Files.writeString(Path.of("fism-validation.txt"), "PASS Java17; " + bannerCases +
            " banner/shield normal/base vertex-stream cases; " + signCases +
            " standing/wall/hanging sign normal+glowing cases; map upload/decorations; item overrides/NBT; " +
            "real Iris shadow passes=" + shadowPasses + "\n");
    }

    public static void shaderFixture(Path root) throws Exception {
        Files.writeString(root.resolve("shadow.vsh"), """
            #version 120
            varying vec2 texcoord; varying vec4 color;
            void main(){gl_Position=ftransform();texcoord=gl_MultiTexCoord0.xy;color=gl_Color;}
            """);
        Files.writeString(root.resolve("shadow.fsh"), """
            #version 120
            uniform sampler2D texture; varying vec2 texcoord; varying vec4 color;
            const int shadowMapResolution=128;
            const float shadowDistance=32.0;
            void main(){gl_FragData[0]=texture2D(texture,texcoord)*color;}
            """);
        Files.writeString(root.resolve("gbuffers_terrain.fsh"), """
            #version 120
            uniform sampler2D texture; uniform sampler2D shadowtex0;
            varying vec2 texcoord; varying vec4 color;
            /* DRAWBUFFERS:0 */
            void main(){gl_FragData[0]=texture2D(texture,texcoord)*color;
            gl_FragData[0].rgb*=0.99+0.01*texture2D(shadowtex0,vec2(0.5)).r;}
            """);
    }

    private static void same(Trace expected, Trace actual, String message) {
        require(expected.draws.size() == actual.draws.size(), message + ": draw count");
        for (int i = 0; i < expected.draws.size(); i++) {
            var a = expected.draws.get(i); var b = actual.draws.get(i);
            require(a.type.equals(b.type) && a.data.equals(b.data), message + ": material/vertex data " + i);
        }
    }

    private static final class Trace implements MultiBufferSource {
        final List<Draw> draws = new ArrayList<>();
        @Override public VertexConsumer getBuffer(RenderType type) {
            var draw = new Draw(type); this.draws.add(draw); return draw;
        }
        int vertices() { return this.draws.stream().mapToInt(d -> d.data.size() / 16).sum(); }
    }

    private static final class Draw implements VertexConsumer {
        final RenderType type;
        final IntArrayList data = new IntArrayList();
        final int[] vertex = new int[16];
        Draw(RenderType type) { this.type = type; }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.vertex[0] = Float.floatToRawIntBits((float) x);
            this.vertex[1] = Float.floatToRawIntBits((float) y);
            this.vertex[2] = Float.floatToRawIntBits((float) z); return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            this.vertex[3] = r; this.vertex[4] = g; this.vertex[5] = b; this.vertex[6] = a; return this;
        }
        @Override public VertexConsumer uv(float u, float v) {
            this.vertex[7] = Float.floatToRawIntBits(u); this.vertex[8] = Float.floatToRawIntBits(v); return this;
        }
        @Override public VertexConsumer overlayCoords(int u, int v) {
            this.vertex[9] = u; this.vertex[10] = v; return this;
        }
        @Override public VertexConsumer uv2(int u, int v) {
            this.vertex[11] = u; this.vertex[12] = v; return this;
        }
        @Override public VertexConsumer normal(float x, float y, float z) {
            this.vertex[13] = Float.floatToRawIntBits(x); this.vertex[14] = Float.floatToRawIntBits(y);
            this.vertex[15] = Float.floatToRawIntBits(z); return this;
        }
        @Override public void endVertex() { for (int value : this.vertex) this.data.add(value); }
        @Override public void defaultColor(int r, int g, int b, int a) { this.color(r, g, b, a); }
        @Override public void unsetDefaultColor() {}
    }
}
