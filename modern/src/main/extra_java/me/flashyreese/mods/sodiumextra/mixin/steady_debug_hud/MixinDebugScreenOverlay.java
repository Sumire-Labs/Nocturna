// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.steady_debug_hud;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.gui.components.DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {
    @Unique private long sodiumExtra$nextTime;
    @Unique private boolean sodiumExtra$rebuild = true;
    @Unique
    private java.util.List<String> sodiumExtra$left = java.util.List.of(),
            sodiumExtra$right = java.util.List.of();

    @Inject(method = "render", at = @At("HEAD"))
    private void sodiumExtra$refresh(CallbackInfo ci) {
        var e = SodiumExtraClientMod.options().extraSettings;
        long now = net.minecraft.Util.getMillis();
        if (!e.steadyDebugHud) sodiumExtra$rebuild = true;
        else if (now > sodiumExtra$nextTime) {
            sodiumExtra$rebuild = true;
            sodiumExtra$nextTime = now + e.steadyDebugHudRefreshInterval * 50L;
        } else sodiumExtra$rebuild = false;
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = "getGameInformation",
            at = @At("RETURN"))
    private java.util.List<String> sodiumExtra$left(java.util.List<String> original) {
        if (sodiumExtra$rebuild) sodiumExtra$left = new java.util.ArrayList<>(original);
        return sodiumExtra$left;
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = "getSystemInformation",
            at = @At("RETURN"))
    private java.util.List<String> sodiumExtra$right(java.util.List<String> original) {
        if (sodiumExtra$rebuild) sodiumExtra$right = new java.util.ArrayList<>(original);
        return sodiumExtra$right;
    }
}
