// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.prevent_shaders;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public class MixinGameRenderer {
    @Unique private boolean nocturna$spectator;
    @Shadow private net.minecraft.client.renderer.PostChain postEffect;

    @Inject(
            method = {"loadEffect", "shutdownEffect"},
            at = @At("HEAD"))
    private void nocturna$manual(CallbackInfo ci) {
        nocturna$spectator = false;
    }

    @Inject(method = "togglePostEffect", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$prevent(CallbackInfo ci) {
        if (SodiumExtraClientMod.options().extraSettings.preventShaders
                && (nocturna$spectator || postEffect == null)) ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "checkEntityPostEffect",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;loadEffect(Lnet/minecraft/resources/ResourceLocation;)V"))
    private void sodiumExtra$spectator(
            net.minecraft.client.renderer.GameRenderer renderer,
            net.minecraft.resources.ResourceLocation effect,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!SodiumExtraClientMod.options().extraSettings.preventShaders) {
            original.call(renderer, effect);
            nocturna$spectator = postEffect != null;
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "checkEntityPostEffect",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraftforge/client/ForgeHooksClient;loadEntityShader(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/GameRenderer;)V",
                            remap = false))
    private void sodiumExtra$forgeSpectator(
            net.minecraft.world.entity.Entity entity,
            net.minecraft.client.renderer.GameRenderer renderer,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!SodiumExtraClientMod.options().extraSettings.preventShaders) {
            original.call(entity, renderer);
            nocturna$spectator = postEffect != null;
        }
    }
}
