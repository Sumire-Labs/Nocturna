package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(com.mojang.blaze3d.vertex.VertexBuffer.class)
public class MixinVertexBuffer {
    @ModifyExpressionValue(
            method = "_drawWithShader",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int sodiumExtra$width(int original) {
        return PaniniProjection.INSTANCE.worldWidth(original);
    }

    @ModifyExpressionValue(
            method = "_drawWithShader",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int sodiumExtra$height(int original) {
        return PaniniProjection.INSTANCE.worldHeight(original);
    }

    @ModifyExpressionValue(
            method = "_drawWithShader",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderLineWidth()F"))
    private float sodiumExtra$lineWidth(float original) {
        return original * PaniniProjection.INSTANCE.worldScale();
    }
}
