package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.debug.DebugRenderer.class)
public class MixinDebugVisuals {
    @Inject(method = "render", at = @At("TAIL"))
    private void nocturna$visuals(
            com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
            double x,
            double y,
            double z,
            CallbackInfo ci) {
        org.embeddedt.embeddium.impl.extra.compat.DebugVisuals.render(
                (net.minecraft.client.renderer.debug.DebugRenderer) (Object) this,
                poses,
                buffers,
                x,
                y,
                z);
    }

    @Inject(method = "switchRenderChunkborder", at = @At("HEAD"), cancellable = true)
    private void nocturna$toggle(CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(
                org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.state()
                        .toggle(
                                "minecraft:chunk_borders",
                                net.minecraft.client.Minecraft.getInstance().options.renderDebug));
    }
}
