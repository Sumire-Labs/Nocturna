package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.pipeline.RenderTarget;

import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import net.minecraft.client.renderer.*;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Shadow private PostChain entityEffect;

    @WrapOperation(
            method = {"renderLevel", "entityTarget"},
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;entityTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$outline(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.outlineTarget(original.call(renderer));
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/PostChain;process(F)V"))
    private void sodiumExtra$defer(PostChain chain, float delta, Operation<Void> original) {
        if (chain != entityEffect || !PaniniProjection.INSTANCE.isRenderingWorld())
            original.call(chain, delta);
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;translucentTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$translucent(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(original.call(renderer));
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;itemEntityTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$items(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(original.call(renderer));
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;particlesTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$particles(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(original.call(renderer));
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;cloudsTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$clouds(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(original.call(renderer));
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;weatherTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private RenderTarget sodiumExtra$weather(
            LevelRenderer renderer, Operation<RenderTarget> original) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(original.call(renderer));
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = {
                "getTranslucentTarget",
                "getItemEntityTarget",
                "getParticlesTarget",
                "getWeatherTarget",
                "getCloudsTarget"
            },
            at = @At("RETURN"))
    private RenderTarget sodiumExtra$auxiliary(RenderTarget target) {
        return PaniniProjection.INSTANCE.auxiliaryTarget(target);
    }
}
