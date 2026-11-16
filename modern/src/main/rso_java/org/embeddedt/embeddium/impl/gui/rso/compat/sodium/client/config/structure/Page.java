package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.network.chat.Component;

import java.util.List;

public class Page {
    private final Component name;
    private final List<OptionGroup> groups;

    public Page(Component name, List<OptionGroup> groups) {
        this.name = name;
        this.groups = List.copyOf(groups);
    }

    public Component name() {
        return name;
    }

    public List<OptionGroup> groups() {
        return groups;
    }
}
