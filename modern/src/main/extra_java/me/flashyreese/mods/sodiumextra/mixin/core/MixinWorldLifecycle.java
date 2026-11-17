package me.flashyreese.mods.sodiumextra.mixin.core;

import org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager;
import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class MixinWorldLifecycle {
    // The old Celeritas search has joined by RETURN; it cannot publish stale nodes after the reset.
    @Inject(method = "setLevel", at = @At("RETURN"))
    private void nocturna$world(CallbackInfo ci) {
        DebugOverlayManager.clearWorld();
        ExtraFog.hasTerrain = false;
        ExtraFog.cloudPass = false;
        ExtraFog.disable();
    }
}
