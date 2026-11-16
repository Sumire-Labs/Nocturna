package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.taumc.celeritas.api.options.control.SliderControl;

@Mixin(value = SliderControl.class, remap = false)
public interface AccessorSliderRange {
    @Accessor("max")
    int sodiumExtra$getMax();
}
