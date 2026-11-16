package org.embeddedt.embeddium.impl.mixin.core.gui;

import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionExtended;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionStateProvider;

import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Config;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Option;
import org.spongepowered.asm.mixin.*;

/** RSO 2.2.5's option metadata mixin, retargeted to the Nocturna configuration bridge. */
@Mixin(value = Option.class, remap = false)
public abstract class RsoOptionMixin implements OptionExtended, OptionStateProvider {
    @Shadow @Final public ResourceLocation id;
    @Shadow public Config state;

    public ResourceLocation rso$getId() {
        return id;
    }

    public Config rso$getParentConfig() {
        return state;
    }
}
