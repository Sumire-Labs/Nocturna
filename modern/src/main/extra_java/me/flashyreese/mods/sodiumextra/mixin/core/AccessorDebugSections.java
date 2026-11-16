package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer.class, remap = false)
public interface AccessorDebugSections {
    @Accessor("renderSectionManager")
    org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager nocturna$getSections();
}
