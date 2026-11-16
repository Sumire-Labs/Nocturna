package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.OptionImpact;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control.Control;

public class Option {
    public final ResourceLocation id;
    public Component name, tooltip;
    public OptionImpact impact;
    public Config state;
    public java.util.function.BooleanSupplier enabled = () -> true;
    public java.util.function.BooleanSupplier hidden = () -> false;
    public Control control;

    public Option(ResourceLocation id, Config state) {
        this.id = id;
        this.state = state;
        name = Component.literal(id.getPath());
        tooltip = Component.empty();
    }

    public Component getName() {
        return name;
    }

    public Component getTooltip() {
        return tooltip;
    }

    public OptionImpact getImpact() {
        return impact;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public boolean shouldHideControl() {
        return hidden.getAsBoolean();
    }

    public Control getControl() {
        return control;
    }

    public boolean hasChanged() {
        return false;
    }
}
