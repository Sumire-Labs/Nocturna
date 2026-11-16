package org.embeddedt.embeddium.extravalidation;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Deque;

final class BasicFeatures {
    static void verify(Minecraft client) throws Exception {
        var options = SodiumExtraClientMod.options();
        var particles = options.particleSettings;
        boolean all = particles.particles, biome = options.detailSettings.biomeColors;
        boolean sneak = options.extraSettings.instantSneak,
                lighting = options.renderSettings.lightUpdates,
                toasts = options.extraSettings.toasts;
        var flame = BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.FLAME);
        Boolean oldFlame = particles.otherMap.get(flame);
        try {
            particles.particles = false;
            ExtraValidation.require(
                    client.particleEngine.createParticle(ParticleTypes.FLAME, 0, 80, 0, 0, 0, 0)
                            == null,
                    "global particle filter ignored");
            particles.particles = true;
            particles.otherMap.put(flame, false);
            ExtraValidation.require(
                    client.particleEngine.createParticle(ParticleTypes.FLAME, 0, 80, 0, 0, 0, 0)
                            == null,
                    "registered particle filter ignored");
            particles.otherMap.put(flame, true);
            ExtraValidation.require(
                    client.particleEngine.createParticle(ParticleTypes.FLAME, 0, 80, 0, 0, 0, 0)
                            != null,
                    "enabled particle was rejected");
            options.detailSettings.biomeColors = false;
            var pos = client.player.blockPosition();
            ExtraValidation.require(
                    BiomeColors.getAverageGrassColor(client.level, pos) == 9551193,
                    "grass tint differs");
            ExtraValidation.require(
                    BiomeColors.getAverageWaterColor(client.level, pos) == 4159204,
                    "water tint differs");
            ExtraValidation.require(
                    BiomeColors.getAverageFoliageColor(client.level, pos) == 5877296,
                    "foliage tint differs");
            options.renderSettings.lightUpdates = false;
            ExtraValidation.require(
                    client.level.getLightEngine().runLightUpdates() == 0,
                    "disabled light-update return value differs");
            var debug =
                    org.embeddedt.embeddium.impl.extra.compat.ExtraDebugOptionsScreen
                            .appendDebugLines(java.util.List.of("native"));
            ExtraValidation.require(
                    debug.get(0)
                            .equals(
                                    Component.translatable("sodium-extra.overlay.light_updates")
                                            .getString()),
                    "default light-update debug warning missing");
            var camera = client.gameRenderer.getMainCamera();
            Field eye = camera.getClass().getDeclaredField("eyeHeight");
            eye.setAccessible(true);
            eye.setFloat(camera, 0);
            options.extraSettings.instantSneak = true;
            camera.tick();
            ExtraValidation.require(
                    eye.getFloat(camera) == camera.getEntity().getEyeHeight(),
                    "instant sneak still interpolated");
            var manager = client.getToasts();
            Field queue = ToastComponent.class.getDeclaredField("queued");
            queue.setAccessible(true);
            var pending = (Deque<?>) queue.get(manager);
            int before = pending.size();
            Toast toast =
                    new Toast() {
                        public Visibility render(
                                GuiGraphics gui, ToastComponent component, long time) {
                            throw new AssertionError("disabled toast rendered");
                        }
                    };
            options.extraSettings.toasts = false;
            manager.addToast(toast);
            ExtraValidation.require(pending.size() == before, "disabled toast was queued");
            options.extraSettings.toasts = true;
            manager.addToast(toast);
            ExtraValidation.require(pending.contains(toast), "enabled custom toast was rejected");
            options.extraSettings.toasts = false;
            manager.render(new GuiGraphics(client, client.renderBuffers().bufferSource()));
            ExtraValidation.require(
                    !pending.contains(toast), "already-queued disabled toast was retained");
            System.out.println(
                    "PASS native particles, biome tints, light-update warning, instant sneak and"
                            + " queued-toast filtering");
        } finally {
            particles.particles = all;
            if (oldFlame == null) particles.otherMap.remove(flame);
            else particles.otherMap.put(flame, oldFlame);
            options.detailSettings.biomeColors = biome;
            options.extraSettings.instantSneak = sneak;
            options.renderSettings.lightUpdates = lighting;
            options.extraSettings.toasts = toasts;
        }
    }
}
