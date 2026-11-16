package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

import java.util.*;

public final class OptionGroupBuilder {
    private Component name = Component.empty();
    private final List<Option> options = new ArrayList<>();

    public OptionGroupBuilder setName(Component name) {
        this.name = name;
        return this;
    }

    public OptionGroupBuilder addOption(OptionBuilder<?> option) {
        options.add(option.build());
        return this;
    }

    public OptionGroup build() {
        return new OptionGroup(name, List.copyOf(options));
    }
}
