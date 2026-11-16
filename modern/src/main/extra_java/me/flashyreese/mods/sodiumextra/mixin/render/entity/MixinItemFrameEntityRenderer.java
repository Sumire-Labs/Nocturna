// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.render.entity;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.entity.ItemFrameRenderer.class)
public class MixinItemFrameEntityRenderer {
    @Inject(
            method =
                    "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void sodiumExtra$render(CallbackInfo ci) {
        if (!SodiumExtraClientMod.options().renderSettings.itemFrame) ci.cancel();
    }

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ItemFrame;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$name(CallbackInfoReturnable<Boolean> cir) {
        if (!SodiumExtraClientMod.options().renderSettings.itemFrameNameTag)
            cir.setReturnValue(false);
    }
}
