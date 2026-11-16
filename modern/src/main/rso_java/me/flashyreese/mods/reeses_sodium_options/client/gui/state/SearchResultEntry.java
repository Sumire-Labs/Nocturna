package me.flashyreese.mods.reeses_sodium_options.client.gui.state;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Option;
import net.minecraft.resources.ResourceLocation;

public record SearchResultEntry(String tabKey, ResourceLocation optionId, Option option) {
}
