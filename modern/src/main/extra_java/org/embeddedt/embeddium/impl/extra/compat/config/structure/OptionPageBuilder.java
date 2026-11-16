package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import net.minecraft.network.chat.Component;

import java.util.*;

public final class OptionPageBuilder {
    public Component name = Component.empty();
    public final List<OptionGroupBuilder> groups = new ArrayList<>();

    public OptionPageBuilder setName(Component v) {
        name = v;
        return this;
    }

    public OptionPageBuilder addOptionGroup(OptionGroupBuilder v) {
        groups.add(v);
        return this;
    }
}
