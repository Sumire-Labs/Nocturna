package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.ModOptions;

import java.util.List;

record TabGroup(String id, ModOptions modOptions, List<Tab<?>> tabs) {
}
