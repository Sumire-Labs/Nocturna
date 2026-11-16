// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.sky;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.*;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.renderer.ShaderInstance;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$end(CallbackInfo ci) {
        if (!SodiumExtraClientMod.options().detailSettings.sky) ci.cancel();
    }

    @WrapOperation(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
                            ordinal = 0))
    private void sodiumExtra$sky(
            VertexBuffer buffer,
            Matrix4f model,
            Matrix4f projection,
            ShaderInstance shader,
            Operation<Void> original) {
        if (SodiumExtraClientMod.options().detailSettings.sky)
            original.call(buffer, model, projection, shader);
    }

    @WrapOperation(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
                            ordinal = 1))
    private void sodiumExtra$stars(
            VertexBuffer buffer,
            Matrix4f model,
            Matrix4f projection,
            ShaderInstance shader,
            Operation<Void> original) {
        if (SodiumExtraClientMod.options().detailSettings.stars)
            original.call(buffer, model, projection, shader);
    }

    @WrapOperation(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",
                            ordinal = 0))
    private void sodiumExtra$quad0(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        if (SodiumExtraClientMod.options().detailSettings.sun) original.call(buffer);
        else buffer.release();
    }

    @WrapOperation(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",
                            ordinal = 1))
    private void sodiumExtra$quad1(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        if (SodiumExtraClientMod.options().detailSettings.sun) original.call(buffer);
        else buffer.release();
    }

    @WrapOperation(
            method = "renderSky",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",
                            ordinal = 2))
    private void sodiumExtra$quad2(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        if (SodiumExtraClientMod.options().detailSettings.moon) original.call(buffer);
        else buffer.release();
    }
}
