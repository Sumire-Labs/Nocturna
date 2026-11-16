// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<
                T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>>
        extends EntityRenderer<T> {
    protected MixinLivingEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$render(
            T entity,
            float yaw,
            float delta,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                && !SodiumExtraClientMod.options().renderSettings.armorStand) {
            super.render(entity, yaw, delta, poses, buffers, light);
            ci.cancel();
        }
    }

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$name(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof net.minecraft.client.player.AbstractClientPlayer
                && !SodiumExtraClientMod.options().renderSettings.playerNameTag)
            cir.setReturnValue(false);
    }
}
