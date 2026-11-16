// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.adaptive_sync;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(com.mojang.blaze3d.platform.Window.class)
public class MixinWindow {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "updateVsync",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V",
                            remap = false))
    private void sodiumExtra$interval(
            int interval,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (interval == 1
                && SodiumExtraClientMod.options().extraSettings.useAdaptiveSync
                && org.embeddedt.embeddium.impl.extra.compat.ExtraPlatform
                        .isAdaptiveSyncSupported()) {
            org.lwjgl.glfw.GLFW.glfwGetError((org.lwjgl.PointerBuffer) null);
            original.call(-1);
            if (org.lwjgl.glfw.GLFW.glfwGetError((org.lwjgl.PointerBuffer) null)
                    == org.lwjgl.glfw.GLFW.GLFW_NO_ERROR) {
                org.embeddedt.embeddium.impl.extra.compat.ExtraPlatform.recordSwapInterval(-1);
                return;
            }
            original.call(1);
            org.embeddedt.embeddium.impl.extra.compat.ExtraPlatform.recordSwapInterval(1);
        } else {
            original.call(interval);
            org.embeddedt.embeddium.impl.extra.compat.ExtraPlatform.recordSwapInterval(interval);
        }
    }
}
