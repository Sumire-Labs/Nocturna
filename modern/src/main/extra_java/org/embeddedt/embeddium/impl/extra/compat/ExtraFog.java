package org.embeddedt.embeddium.impl.extra.compat;

import com.mojang.blaze3d.systems.RenderSystem;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;

/** Independent environment/render/sky/cloud channels, projected onto the host's render passes. */
public final class ExtraFog {
    public static final FogData terrain = new FogData();
    public static boolean hasTerrain;
    public static FogRenderer.FogMode activeMode;
    public static boolean cloudPass;

    public static int renderDistanceMax(int fallback) {
        var m = Minecraft.getInstance();
        return m != null
                        && m.options != null
                        && m.options.renderDistance().values() instanceof OptionInstance.IntRange r
                ? Math.max(fallback, r.maxInclusive())
                : fallback;
    }

    public static int cloudRange() {
        return ExtraRendererOptions.cloudRange();
    }

    public static FogData calculate(
            float nativeStart,
            float nativeEnd,
            float renderDistance,
            boolean atmospheric,
            boolean boss,
            FogDistanceHelper.ProtectedFogType type,
            me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions
                            .AtmosphericFogSettings
                    settings,
            int protectedDistance) {
        var fog = new FogData();
        fog.renderDistanceEnd = renderDistance;
        fog.renderDistanceStart = renderDistance - Mth.clamp(renderDistance / 10F, 4F, 64F);
        boolean nativeEnvironment = !atmospheric || boss || nativeEnd < renderDistance;
        fog.environmentalStart = nativeEnvironment ? nativeStart : Float.MAX_VALUE;
        fog.environmentalEnd = nativeEnvironment ? nativeEnd : Float.MAX_VALUE;
        fog.skyEnd = atmospheric ? renderDistance : nativeEnd;
        fog.cloudEnd = cloudRange() * 16F;
        if (atmospheric && !boss) {
            int distance = settings.distanceChunks;
            if (distance == 0) {
                fog.renderDistanceStart =
                        FogDistanceHelper.applyStartMultiplier(fog.renderDistanceStart, settings);
            } else if (FogDistanceHelper.disablesFog(distance)) {
                fog.renderDistanceStart = fog.renderDistanceEnd = Float.MAX_VALUE;
                fog.environmentalStart = fog.environmentalEnd = Float.MAX_VALUE;
            } else {
                fog.renderDistanceStart =
                        fog.environmentalStart = FogDistanceHelper.getStart(settings);
                fog.renderDistanceEnd = fog.environmentalEnd = FogDistanceHelper.getEnd(distance);
                fog.skyEnd = Math.min(fog.environmentalEnd, renderDistance);
            }
            if (settings.cloudFogPercent != 100)
                fog.cloudEnd =
                        Math.min(
                                fog.cloudEnd,
                                FogDistanceHelper.getCloudEnd(settings.cloudFogPercent));
            FogDistanceHelper.applyRenderDistanceShape(fog, settings);
        } else if (type != null) {
            float start =
                    switch (type) {
                        case BLINDNESS, LAVA -> 0.25F;
                        case DARKNESS -> 0.75F;
                        default -> 0F;
                    };
            FogDistanceHelper.applyProtectedGameplayFog(
                    fog,
                    protectedDistance,
                    start,
                    type == FogDistanceHelper.ProtectedFogType.BLINDNESS ? 0.8F : 1F);
        }
        return fog;
    }

    public static void setup(
            Camera camera,
            FogRenderer.FogMode mode,
            float distance,
            boolean thick,
            float partialTick) {
        var m = Minecraft.getInstance();
        if (m.level == null) return;
        var fluid = camera.getFluidInCamera();
        var effect = FogRenderer.getPriorityFogFunction(camera.getEntity(), partialTick);
        boolean atmosphere = fluid == FogType.NONE && effect == null;
        FogDistanceHelper.ProtectedFogType type = null;
        if (fluid == FogType.LAVA) type = FogDistanceHelper.ProtectedFogType.LAVA;
        else if (fluid == FogType.POWDER_SNOW)
            type = FogDistanceHelper.ProtectedFogType.POWDER_SNOW;
        else if (effect != null && effect.getMobEffect() == MobEffects.BLINDNESS)
            type = FogDistanceHelper.ProtectedFogType.BLINDNESS;
        else if (effect != null && effect.getMobEffect() == MobEffects.DARKNESS)
            type = FogDistanceHelper.ProtectedFogType.DARKNESS;
        else if (fluid == FogType.WATER) type = FogDistanceHelper.ProtectedFogType.WATER;
        int protectedDistance =
                type != null && FogDistanceHelper.shouldModifyProtectedGameplayFog()
                        ? FogDistanceHelper.getProtectedGameplayFogDistance(type)
                        : 0;
        var data =
                calculate(
                        RenderSystem.getShaderFogStart(),
                        RenderSystem.getShaderFogEnd(),
                        distance,
                        atmosphere,
                        FogDistanceHelper.isBossFogActive(),
                        type,
                        FogDistanceHelper.getAtmosphericSettings(m.level),
                        protectedDistance);
        activeMode = mode;
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            copy(data, terrain);
            hasTerrain = true;
            RenderSystem.setShaderFogStart(
                    FogDistanceHelper.decodeRenderDistanceStart(
                            data.renderDistanceStart, data.renderDistanceEnd));
            RenderSystem.setShaderFogEnd(
                    FogDistanceHelper.decodeRenderDistanceEnd(
                            data.renderDistanceStart, data.renderDistanceEnd));
        } else {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(data.skyEnd);
        }
    }

    public static void disable() {
        activeMode = null;
    }

    public static void acceptForgeOverride(float start, float end) {
        terrain.environmentalStart = start;
        terrain.environmentalEnd = end;
    }

    private static void copy(FogData a, FogData b) {
        b.renderDistanceStart = a.renderDistanceStart;
        b.renderDistanceEnd = a.renderDistanceEnd;
        b.environmentalStart = a.environmentalStart;
        b.environmentalEnd = a.environmentalEnd;
        b.skyEnd = a.skyEnd;
        b.cloudEnd = a.cloudEnd;
    }
}
