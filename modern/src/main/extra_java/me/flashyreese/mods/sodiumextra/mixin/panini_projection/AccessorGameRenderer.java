package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface AccessorGameRenderer {
    @Accessor("effectActive")
    boolean sodiumExtra$isEffectActive();
}
