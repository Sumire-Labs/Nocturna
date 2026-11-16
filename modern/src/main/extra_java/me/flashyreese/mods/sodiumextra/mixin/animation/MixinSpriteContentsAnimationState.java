package me.flashyreese.mods.sodiumextra.mixin.animation;

import me.flashyreese.mods.sodiumextra.common.util.AnimationStateExtended;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The 1.20.1 atlas animation state is TextureAtlasSprite's ticker wrapper. */
@Mixin(targets = "net.minecraft.client.renderer.texture.TextureAtlasSprite$1")
public class MixinSpriteContentsAnimationState implements AnimationStateExtended {
    @Unique private TextureAtlasSprite sodiumExtra$sprite;

    public TextureAtlasSprite sodium_extra$getSprite() {
        return sodiumExtra$sprite;
    }

    public void sodium_extra$setSprite(TextureAtlasSprite sprite) {
        sodiumExtra$sprite = sprite;
    }
}
