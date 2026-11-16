package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ModOptionsBuilder {
    public ResourceLocation icon;
    public final List<OptionPageBuilder> pages = new ArrayList<>();
    public final Map<ResourceLocation, StatefulOptionBuilder<?, ?>>
            replacements = new LinkedHashMap<>(),
            overlays = new LinkedHashMap<>();

    public ModOptionsBuilder setIcon(ResourceLocation v) {
        icon = v;
        return this;
    }

    public ModOptionsBuilder addPage(OptionPageBuilder v) {
        pages.add(v);
        return this;
    }

    public ModOptionsBuilder registerOptionReplacement(
            ResourceLocation id, StatefulOptionBuilder<?, ?> v) {
        replacements.put(id, v);
        return this;
    }

    public ModOptionsBuilder registerOptionOverlay(
            ResourceLocation id, StatefulOptionBuilder<?, ?> v) {
        overlays.put(id, v);
        return this;
    }
}
