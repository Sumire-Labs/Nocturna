// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.reduce_resolution_on_mac;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(com.mojang.blaze3d.platform.Window.class)
public class MixinWindow {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",
                            remap = false))
    private long sodiumExtra$retina(
            java.util.function.IntSupplier width,
            java.util.function.IntSupplier height,
            java.util.function.Supplier<String> title,
            java.util.function.LongSupplier monitor,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Long> original) {
        if (net.minecraft.client.Minecraft.ON_OSX
                && SodiumExtraClientMod.options().extraSettings.reduceResolutionOnMac)
            org.lwjgl.glfw.GLFW.glfwWindowHint(
                    org.lwjgl.glfw.GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER,
                    org.lwjgl.glfw.GLFW.GLFW_FALSE);
        return original.call(width, height, title, monitor);
    }
}
