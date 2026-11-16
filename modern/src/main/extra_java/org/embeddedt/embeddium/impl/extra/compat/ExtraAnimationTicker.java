package org.embeddedt.embeddium.impl.extra.compat;

import me.flashyreese.mods.sodiumextra.common.util.AnimationStateExtended;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Adapts custom Forge ticker implementations to the atlas animation-state contract. */
public final class ExtraAnimationTicker
        implements TextureAtlasSprite.Ticker, AnimationStateExtended {
    private final TextureAtlasSprite.Ticker delegate;
    private TextureAtlasSprite sprite;

    public ExtraAnimationTicker(TextureAtlasSprite.Ticker delegate) {
        this.delegate = delegate;
    }

    public TextureAtlasSprite sodium_extra$getSprite() {
        return sprite;
    }

    public void sodium_extra$setSprite(TextureAtlasSprite value) {
        sprite = value;
    }

    public void tickAndUpload() {
        delegate.tickAndUpload();
    }

    public void close() {
        delegate.close();
    }
}
