package me.flashyreese.mods.reeses_sodium_options.client.gui.option;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Config;
import org.jetbrains.annotations.Nullable;

public interface OptionStateProvider {
    @Nullable Config rso$getParentConfig();
}
