package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

import java.util.*;

public final class OptionPageBuilder {
    private Component name = Component.empty();
    private final List<OptionGroup> groups = new ArrayList<>();

    public OptionPageBuilder setName(Component name) {
        this.name = name;
        return this;
    }

    public OptionPageBuilder addOptionGroup(OptionGroupBuilder group) {
        groups.add(group.build());
        return this;
    }

    public Page build() {
        return new Page(name, List.copyOf(groups));
    }
}
