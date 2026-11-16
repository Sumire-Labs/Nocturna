package me.flashyreese.mods.sodiumextra.mixin.animation;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.flashyreese.mods.sodiumextra.common.util.AnimationStateExtended;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.embeddedt.embeddium.impl.extra.compat.ExtraAnimationTicker;
import org.embeddedt.embeddium.impl.extra.compat.ExtraAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Same atlas-only boundary as beta.4, before Celeritas advances an invisible sprite's clock. */
@Mixin(TextureAtlas.class)
public class MixinTextureAtlas {
    @WrapWithCondition(
            method = "cycleAnimationFrames",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Ticker;tickAndUpload()V"))
    private boolean sodiumExtra$cycle(TextureAtlasSprite.Ticker state) {
        return state instanceof AnimationStateExtended extended
                && ExtraAnimations.shouldAnimate(
                        extended.sodium_extra$getSprite().contents().name());
    }

    @WrapOperation(
            method = "upload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;createTicker()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Ticker;"))
    private TextureAtlasSprite.Ticker sodiumExtra$state(
            TextureAtlasSprite sprite, Operation<TextureAtlasSprite.Ticker> original) {
        TextureAtlasSprite.Ticker state = original.call(sprite);
        if (state == null) return null;
        if (!(state instanceof AnimationStateExtended)) state = new ExtraAnimationTicker(state);
        ((AnimationStateExtended) state).sodium_extra$setSprite(sprite);
        return state;
    }
}
