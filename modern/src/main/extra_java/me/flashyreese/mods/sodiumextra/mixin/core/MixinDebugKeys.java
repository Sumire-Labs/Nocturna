package me.flashyreese.mods.sodiumextra.mixin.core;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.KeyboardHandler.class)
public class MixinDebugKeys {
    @WrapOperation(
            method = "handleDebugKeys",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;setRenderHitBoxes(Z)V"))
    private void nocturna$hitboxes(
            net.minecraft.client.renderer.entity.EntityRenderDispatcher renderer,
            boolean value,
            Operation<Void> original) {
        original.call(
                renderer,
                org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.state()
                        .toggle(
                                "minecraft:entity_hitboxes",
                                net.minecraft.client.Minecraft.getInstance().options.renderDebug));
    }
}
