package org.embeddedt.embeddium.impl.extra.compat;

import com.mojang.blaze3d.systems.RenderSystem;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;

/** Native renderer adapter; policy, distance and shape calculations live in the upstream helper. */
public final class ExtraFog {
    public static final FogData terrain = new FogData();
    public static boolean hasTerrain;
    public static float nativeCloudStart, nativeCloudEnd = 2048;
    public static boolean cloudChanged;

    public static int renderDistanceMax(int fallback) {
        Minecraft m = Minecraft.getInstance();
        if (m != null
                && m.options != null
                && m.options.renderDistance().values()
                        instanceof net.minecraft.client.OptionInstance.IntRange r)
            return Math.max(fallback, r.maxInclusive());
        return fallback;
    }

    public static int cloudRange() {
        return Math.max(1, (int) Math.ceil(nativeCloudEnd / 16.0F));
    }

    public static void setup(
            Camera camera,
            FogRenderer.FogMode mode,
            float renderDistance,
            boolean thick,
            float partialTick) {
        var m = Minecraft.getInstance();
        if (m.level == null) return;
        float nativeStart = RenderSystem.getShaderFogStart(),
                nativeEnd = RenderSystem.getShaderFogEnd();
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            nativeCloudStart = nativeStart;
            nativeCloudEnd = nativeEnd;
        }
        var fog = new FogData();
        fog.renderDistanceStart = nativeStart;
        fog.renderDistanceEnd = nativeEnd;
        fog.environmentalStart = Float.MAX_VALUE;
        fog.environmentalEnd = Float.MAX_VALUE;
        fog.skyEnd = nativeEnd;
        fog.cloudEnd = nativeCloudEnd;
        FogType fluid = camera.getFluidInCamera();
        var effect = FogRenderer.getPriorityFogFunction(camera.getEntity(), partialTick);
        boolean atmospheric = fluid == FogType.NONE && effect == null;
        if (atmospheric && !FogDistanceHelper.isBossFogActive()) {
            var settings = FogDistanceHelper.getAtmosphericSettings(m.level);
            int distance = settings.distanceChunks;
            if (distance == 0) {
                if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
                    fog.renderDistanceStart =
                            FogDistanceHelper.applyStartMultiplier(nativeStart, settings);
                    FogDistanceHelper.applyRenderDistanceShape(fog, settings);
                }
            } else if (FogDistanceHelper.disablesFog(distance)) {
                fog.environmentalStart = fog.environmentalEnd = Float.MAX_VALUE;
                if (mode == FogRenderer.FogMode.FOG_TERRAIN)
                    fog.renderDistanceStart = fog.renderDistanceEnd = Float.MAX_VALUE;
            } else {
                fog.environmentalStart = FogDistanceHelper.getStart(settings);
                fog.environmentalEnd = FogDistanceHelper.getEnd(distance);
                fog.skyEnd = Math.min(fog.environmentalEnd, renderDistance);
                if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
                    fog.renderDistanceStart = fog.environmentalStart;
                    fog.renderDistanceEnd = fog.environmentalEnd;
                    FogDistanceHelper.applyRenderDistanceShape(fog, settings);
                }
            }
            if (settings.cloudFogPercent != 100)
                fog.cloudEnd =
                        Math.min(
                                fog.cloudEnd,
                                FogDistanceHelper.getCloudEnd(settings.cloudFogPercent));
        } else if (!atmospheric && FogDistanceHelper.shouldModifyProtectedGameplayFog()) {
            FogDistanceHelper.ProtectedFogType type = null;
            float start = 0, sky = 1;
            if (fluid == FogType.LAVA) {
                type = FogDistanceHelper.ProtectedFogType.LAVA;
                start = 0.25F;
            } else if (fluid == FogType.POWDER_SNOW)
                type = FogDistanceHelper.ProtectedFogType.POWDER_SNOW;
            else if (effect != null && effect.getMobEffect() == MobEffects.BLINDNESS) {
                type = FogDistanceHelper.ProtectedFogType.BLINDNESS;
                start = 0.25F;
                sky = 0.8F;
            } else if (effect != null && effect.getMobEffect() == MobEffects.DARKNESS) {
                type = FogDistanceHelper.ProtectedFogType.DARKNESS;
                start = 0.75F;
            } else if (fluid == FogType.WATER) type = FogDistanceHelper.ProtectedFogType.WATER;
            if (type != null) {
                int distance = FogDistanceHelper.getProtectedGameplayFogDistance(type);
                FogDistanceHelper.applyProtectedGameplayFog(fog, distance, start, sky);
                if (distance != 0) {
                    fog.renderDistanceStart = fog.environmentalStart;
                    fog.renderDistanceEnd = fog.environmentalEnd;
                }
            }
        }
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            copy(fog, terrain);
            hasTerrain = true;
            cloudChanged = fog.cloudEnd != nativeCloudEnd;
            RenderSystem.setShaderFogStart(
                    FogDistanceHelper.decodeRenderDistanceStart(
                            fog.renderDistanceStart, fog.renderDistanceEnd));
            RenderSystem.setShaderFogEnd(
                    FogDistanceHelper.decodeRenderDistanceEnd(
                            fog.renderDistanceStart, fog.renderDistanceEnd));
        } else if (fog.skyEnd != nativeEnd) {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(fog.skyEnd);
        }
    }

    public static void acceptForgeOverride(float start, float end) {
        terrain.renderDistanceStart = start;
        terrain.renderDistanceEnd = end;
        terrain.cloudEnd = end;
        nativeCloudStart = start;
        nativeCloudEnd = end;
        cloudChanged = false;
    }

    private static void copy(FogData from, FogData to) {
        to.renderDistanceStart = from.renderDistanceStart;
        to.renderDistanceEnd = from.renderDistanceEnd;
        to.environmentalStart = from.environmentalStart;
        to.environmentalEnd = from.environmentalEnd;
        to.skyEnd = from.skyEnd;
        to.cloudEnd = from.cloudEnd;
    }
}
