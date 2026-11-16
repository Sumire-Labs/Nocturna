package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ModOptions(
        String configId,
        String name,
        String version,
        ResourceLocation icon,
        boolean iconMonochrome,
        org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme theme,
        List<Page> pages) {}
