package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import net.minecraft.network.chat.Component;

import java.util.*;

public final class OptionGroupBuilder {
    public Component name = Component.empty();
    public final List<StatefulOptionBuilder<?, ?>> options = new ArrayList<>();

    public OptionGroupBuilder setName(Component v) {
        name = v;
        return this;
    }

    public OptionGroupBuilder addOption(StatefulOptionBuilder<?, ?> v) {
        options.add(v);
        return this;
    }
}
