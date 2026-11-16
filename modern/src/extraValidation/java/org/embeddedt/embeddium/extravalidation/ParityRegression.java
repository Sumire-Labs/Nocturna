package org.embeddedt.embeddium.extravalidation;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;
import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.material.FogType;

import org.embeddedt.embeddium.impl.extra.compat.*;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class ParityRegression {
    private static int hitCalls;
    private static boolean customParticle;
    private static final net.minecraftforge.registries.DeferredRegister<
                    net.minecraft.world.level.block.Block>
            BLOCKS =
                    net.minecraftforge.registries.DeferredRegister.create(
                            net.minecraftforge.registries.ForgeRegistries.BLOCKS,
                            "nocturna_extra_validation");
    private static final net.minecraftforge.registries.RegistryObject<
                    net.minecraft.world.level.block.Block>
            HIT =
                    BLOCKS.register(
                            "hit_probe",
                            () ->
                                    new net.minecraft.world.level.block.Block(
                                            net.minecraft.world.level.block.state.BlockBehaviour
                                                    .Properties.of()) {
                                        @Override
                                        public void initializeClient(
                                                java.util.function.Consumer<
                                                                net.minecraftforge.client.extensions
                                                                        .common
                                                                        .IClientBlockExtensions>
                                                        consumer) {
                                            consumer.accept(
                                                    new net.minecraftforge.client.extensions.common
                                                            .IClientBlockExtensions() {
                                                        @Override
                                                        public boolean addHitEffects(
                                                                net.minecraft.world.level.block
                                                                                .state.BlockState
                                                                        state,
                                                                Level level,
                                                                net.minecraft.world.phys.HitResult
                                                                        target,
                                                                net.minecraft.client.particle
                                                                                .ParticleEngine
                                                                        manager) {
                                                            hitCalls++;
                                                            customParticle =
                                                                    manager.createParticle(
                                                                                    net.minecraft
                                                                                            .core
                                                                                            .particles
                                                                                            .ParticleTypes
                                                                                            .FLAME,
                                                                                    target
                                                                                            .getLocation()
                                                                                            .x,
                                                                                    target
                                                                                            .getLocation()
                                                                                            .y,
                                                                                    target
                                                                                            .getLocation()
                                                                                            .z,
                                                                                    0,
                                                                                    0,
                                                                                    0)
                                                                            != null;
                                                            return true;
                                                        }
                                                    });
                                        }
                                    });
    private final List<Map<String, Object>> results = new ArrayList<>();

    public static void register() {
        BLOCKS.register(
                net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static void verify(Minecraft mc) throws Exception {
        var check = new ParityRegression();
        check.fog(mc);
        check.scissor();
        check.blockParticles(mc);
        check.formats();
        Files.writeString(
                Path.of("extra-parity-regressions.json"),
                new com.google.gson.GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(check.results));
        System.out.println(
                "PASS independent fog channels; in-place/scissor color; Forge hit callbacks; HDR"
                        + " formats");
    }

    private void fog(Minecraft mc) {
        var settings = SodiumExtraClientMod.options().renderSettings.fogSettings;
        boolean advanced = settings.advanced,
                allowed = settings.protectedGameplay.enabledWhenAllowed;
        int distance = settings.protectedGameplay.waterDistanceBlocks;
        Camera camera =
                new Camera() {
                    public FogType getFluidInCamera() {
                        return FogType.WATER;
                    }

                    public Entity getEntity() {
                        return mc.player;
                    }
                };
        try {
            settings.advanced = true;
            settings.protectedGameplay.enabledWhenAllowed = true;
            for (int target : new int[] {256, -1}) {
                settings.protectedGameplay.waterDistanceBlocks = target;
                var reference = new FogData();
                reference.renderDistanceStart = 60;
                reference.renderDistanceEnd = 64;
                FogDistanceHelper.applyProtectedGameplayFog(reference, target, 0, 1);
                FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, 64, false, 0);
                ExtraValidation.require(
                        ExtraFog.terrain.renderDistanceEnd == 64
                                && RenderSystem.getShaderFogEnd() == 64,
                        "protected environment replaced render-distance fog");
                results.add(
                        Map.of(
                                "case",
                                "protected-water-" + target,
                                "originalRenderEnd",
                                reference.renderDistanceEnd,
                                "portRenderEnd",
                                ExtraFog.terrain.renderDistanceEnd,
                                "actualGlFogEnd",
                                RenderSystem.getShaderFogEnd()));
            }
        } finally {
            settings.advanced = advanced;
            settings.protectedGameplay.enabledWhenAllowed = allowed;
            settings.protectedGameplay.waterDistanceBlocks = distance;
        }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void blockParticles(Minecraft mc) {
        var settings = SodiumExtraClientMod.options().particleSettings;
        boolean all = settings.particles, breaking = settings.blockBreaking;
        var position = mc.player.blockPosition().below();
        var original = mc.level.getBlockState(position);
        try {
            settings.particles = true;
            settings.blockBreaking = false;
            mc.level.setBlock(position, HIT.get().defaultBlockState(), 3);
            mc.particleEngine.addBlockHitEffects(
                    position,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(position),
                            net.minecraft.core.Direction.UP,
                            position,
                            false));
            ExtraValidation.require(
                    hitCalls == 0 && !customParticle,
                    "Forge custom hit particles bypassed blockBreaking=false");
            results.add(
                    Map.of(
                            "case",
                            "block-breaking-custom-extension",
                            "setting",
                            false,
                            "callbackCalls",
                            hitCalls,
                            "spawned",
                            customParticle,
                            "expectedCallbackCalls",
                            0));
        } finally {
            settings.particles = all;
            settings.blockBreaking = breaking;
            mc.level.setBlock(position, original, 3);
        }
    }

    private void formats() throws Exception {
        var model = new TextureTarget(8, 8, true, false);
        RenderTarget copy = null;
        try {
            GlStateManager._bindTexture(model.getColorTextureId());
            GL33C.glTexImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    GL33C.GL_RGBA16F,
                    8,
                    8,
                    0,
                    GL33C.GL_RGBA,
                    GL33C.GL_FLOAT,
                    (java.nio.ByteBuffer) null);
            int original =
                    GL33C.glGetTexLevelParameteri(
                            GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT);
            GlStateManager._bindTexture(0);
            var create =
                    PaniniProjection.class.getDeclaredMethod(
                            "createTarget", int.class, int.class, RenderTarget.class);
            create.setAccessible(true);
            copy = (RenderTarget) create.invoke(null, 8, 8, model);
            GlStateManager._bindTexture(copy.getColorTextureId());
            int actual =
                    GL33C.glGetTexLevelParameteri(
                            GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT);
            GlStateManager._bindTexture(0);
            ExtraValidation.require(
                    original == actual, "Panini target lost the model internal format");
            results.add(
                    Map.of(
                            "case",
                            "panini-target-format",
                            "modelInternalFormat",
                            original,
                            "copiedInternalFormat",
                            actual,
                            "equal",
                            original == actual));
        } finally {
            if (copy != null) copy.destroyBuffers();
            model.destroyBuffers();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private void scissor() throws Exception {
        var source = new TextureTarget(64, 48, true, false);
        var reference = new TextureTarget(64, 48, true, false);
        var inplace = new TextureTarget(64, 48, true, false);
        Object program = null;
        var projection = new PaniniProjection();
        var bytes = MemoryUtil.memAlloc(64 * 48 * 4);
        try {
            for (int y = 0; y < 48; y++)
                for (int x = 0; x < 64; x++)
                    bytes.put((byte) (x * 4)).put((byte) (y * 5)).put((byte) 32).put((byte) 255);
            bytes.flip();
            for (var target : new RenderTarget[] {source, inplace}) {
                target.setFilterMode(GL33C.GL_LINEAR);
                GlStateManager._bindTexture(target.getColorTextureId());
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
                GlStateManager._bindTexture(0);
            }
            Class<?> type =
                    Class.forName("me.flashyreese.mods.sodiumextra.client.render.PaniniProgram");
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            program = ctor.newInstance();
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
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
            draw.invoke(program, source, reference, 1F, 1.2F, 0.9F, false, false, -1);
            field(projection, "program", program);
            field(projection, "strength", 1F);
            field(projection, "horizontalExtent", 1.2F);
            field(projection, "verticalExtent", 0.9F);
            var method =
                    PaniniProjection.class.getDeclaredMethod(
                            "projectColor", RenderTarget.class, RenderTarget.class, boolean.class);
            method.setAccessible(true);
            GL33C.glEnable(GL33C.GL_SCISSOR_TEST);
            GL33C.glScissor(0, 0, 1, 1);
            method.invoke(projection, inplace, inplace, false);
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
            int expected = pixel(reference, 8, 24), actual = pixel(inplace, 8, 24);
            ExtraValidation.require(
                    expected == actual, "Panini inherited scissor in the in-place path");
            results.add(
                    Map.of(
                            "case",
                            "panini-inplace-scissor",
                            "referenceRed",
                            expected,
                            "inplaceRed",
                            actual,
                            "equal",
                            expected == actual,
                            "glError",
                            GL33C.glGetError()));
        } finally {
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
            projection.close();
            source.destroyBuffers();
            reference.destroyBuffers();
            inplace.destroyBuffers();
            MemoryUtil.memFree(bytes);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private static int pixel(RenderTarget target, int x, int y) {
        var b = MemoryUtil.memAlloc(4);
        try {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL33C.glReadPixels(x, y, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, b);
            return Byte.toUnsignedInt(b.get(0));
        } finally {
            MemoryUtil.memFree(b);
        }
    }
}
