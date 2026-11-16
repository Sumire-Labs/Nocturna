package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;

    @WrapMethod(method = "renderLevel")
    private void sodiumExtra$world(
            float delta, long deadline, PoseStack poses, Operation<Void> original) {
        try {
            original.call(delta, deadline, poses);
        } finally {
            PaniniProjection.INSTANCE.abort();
        }
    }

    @Inject(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V",
                            shift = At.Shift.AFTER))
    private void sodiumExtra$begin(CallbackInfo ci) {
        PaniniProjection.INSTANCE.begin(
                minecraft, minecraft.getMainRenderTarget(), RenderSystem.getProjectionMatrix());
    }

    @Inject(
            method = "renderLevel",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private void sodiumExtra$finish(float delta, long deadline, PoseStack poses, CallbackInfo ci) {
        PaniniProjection.INSTANCE.finish(minecraft, delta);
    }

    @Inject(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void sodiumExtra$handDepth(CallbackInfo ci) {
        PaniniProjection.INSTANCE.beginHand();
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V"))
    private void sodiumExtra$hand(
            GameRenderer renderer,
            PoseStack poses,
            net.minecraft.client.Camera camera,
            float delta,
            Operation<Void> original) {
        try {
            original.call(renderer, poses, camera, delta);
        } finally {
            PaniniProjection.INSTANCE.endHand();
        }
    }

    @Inject(
            method = {"close", "reloadShaders"},
            at = @At("HEAD"))
    private void sodiumExtra$close(CallbackInfo ci) {
        PaniniProjection.INSTANCE.close();
    }

    @Inject(
            method = {"resize", "resetData"},
            at = @At("HEAD"))
    private void sodiumExtra$resize(CallbackInfo ci) {
        PaniniProjection.INSTANCE.resize();
    }
}
