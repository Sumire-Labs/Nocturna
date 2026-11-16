// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.render.block.entity;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.blockentity.EnchantTableRenderer.class)
public class MixinEnchantingTableBlockEntityRenderer {
    @Inject(
            method =
                    "render(Lnet/minecraft/world/level/block/entity/EnchantmentTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$render(CallbackInfo ci) {
        if (!SodiumExtraClientMod.options().renderSettings.enchantingTableBook) ci.cancel();
    }
}
