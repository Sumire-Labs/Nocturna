package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = net.minecraft.client.renderer.FogRenderer.class, priority = 1300)
public class MixinFogRenderer {
    @Inject(method = "setupNoFog", at = @At("HEAD"))
    private static void nocturna$disable(CallbackInfo ci) {
        ExtraFog.disable();
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "setupFog",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraftforge/client/ForgeHooksClient;onFogRender(Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/client/Camera;FFFFLcom/mojang/blaze3d/shaders/FogShape;)V",
                            remap = false))
    private static void sodiumExtra$setup(
            net.minecraft.client.renderer.FogRenderer.FogMode mode,
            net.minecraft.world.level.material.FogType type,
            net.minecraft.client.Camera camera,
            float delta,
            float distance,
            float start,
            float end,
            com.mojang.blaze3d.shaders.FogShape shape,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        ExtraFog.setup(camera, mode, distance, false, delta);
        float changedStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(),
                changedEnd = com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        var changedShape = com.mojang.blaze3d.systems.RenderSystem.getShaderFogShape();
        original.call(
                mode,
                type,
                camera,
                delta,
                distance,
                changedStart,
                changedEnd,
                com.mojang.blaze3d.systems.RenderSystem.getShaderFogShape());
        if (mode == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN
                && (changedStart != com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart()
                        || changedEnd != com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()
                        || changedShape
                                != com.mojang.blaze3d.systems.RenderSystem.getShaderFogShape()))
            ExtraFog.acceptForgeOverride(
                    com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(),
                    com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
    }
}
