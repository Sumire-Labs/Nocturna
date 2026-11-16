package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.mojang.blaze3d.pipeline.RenderTarget;

import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.Minecraft.class)
public class MixinMinecraft {
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = "getMainRenderTarget",
            at = @At("RETURN"))
    private RenderTarget sodiumExtra$target(RenderTarget nativeTarget) {
        return PaniniProjection.INSTANCE.worldTarget(nativeTarget);
    }
}
