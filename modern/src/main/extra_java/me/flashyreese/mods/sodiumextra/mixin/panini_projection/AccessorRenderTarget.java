package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface AccessorRenderTarget {
    @Accessor("clearChannels")
    float[] sodiumExtra$getClearColor();
}
