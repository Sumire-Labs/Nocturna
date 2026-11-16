package me.flashyreese.mods.sodiumextra.mixin.core;

import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SectionLattice.class, remap = false)
public interface AccessorDebugLattice {
    @Accessor("visitState")
    long[] nocturna$getMainVisits();
}
