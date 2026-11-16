// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.render.block.entity;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.blockentity.BeaconRenderer.class)
public class MixinBeaconRenderer {
    @Unique private net.minecraft.world.level.block.entity.BeaconBlockEntity sodiumExtra$beacon;

    @Inject(
            method =
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$render(
            net.minecraft.world.level.block.entity.BeaconBlockEntity beacon,
            float tick,
            com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource buffers,
            int light,
            int overlay,
            CallbackInfo ci) {
        sodiumExtra$beacon = beacon;
        if (!SodiumExtraClientMod.options().renderSettings.beaconBeam) ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method =
                    "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/blockentity/BeaconRenderer;renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;FJII[F)V"))
    private void sodiumExtra$height(
            com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource buffers,
            float tick,
            long time,
            int offset,
            int height,
            float[] color,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (height == 1024 && SodiumExtraClientMod.options().renderSettings.limitBeaconBeamHeight)
            height =
                    sodiumExtra$beacon.getLevel().getMaxBuildHeight()
                            - (sodiumExtra$beacon.getBlockPos().getY() + offset);
        original.call(poses, buffers, tick, time, offset, height, color);
    }
}
