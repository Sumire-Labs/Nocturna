// Ported from Iris (LGPL-3.0), 82e39b0252a9576190502001ccbadb038c6b5e78.
package net.irisshaders.iris.mixin.features.shader.shadow_pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BannerRenderer.class)
public abstract class FismBannerRendererMixin {
    @Inject(method = "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;" +
        "Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;" +
        "Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;Z)V",
        at = @At("HEAD"), cancellable = true)
    private static void fism$cancelPatterns(PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, int packedOverlay, ModelPart flagPart,
                                          Material flagMaterial, boolean banner,
                                          List<Pair<Holder<BannerPattern>, DyeColor>> patterns,
                                          boolean glint, CallbackInfo ci) {
        if (IrisApiV0Impl.INSTANCE.isRenderingShadowPass()) {
            // 1.20.1 renders the uncolored cloth here; newer Minecraft submits it before submitPatterns.
            flagPart.render(poseStack, flagMaterial.buffer(bufferSource, RenderType::entitySolid, false),
                packedLight, packedOverlay);
            // The old API encodes the base dye as BASE in the pattern list, rather than a separate argument.
            for (var pattern : patterns) {
                if (pattern.getFirst().is(BannerPatterns.BASE)) {
                    var material = banner ? Sheets.getBannerMaterial(BannerPatterns.BASE)
                        : Sheets.getShieldMaterial(BannerPatterns.BASE);
                    float[] color = pattern.getSecond().getTextureDiffuseColors();
                    flagPart.render(poseStack, material.buffer(bufferSource, RenderType::entityNoOutline),
                        packedLight, packedOverlay, color[0], color[1], color[2], 1.0F);
                    break;
                }
            }
            ci.cancel();
        }
    }
}
