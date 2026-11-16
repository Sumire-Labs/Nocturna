package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure;

import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

import java.util.*;

public final class ConfigBuilder {
    private final Config config = new Config();
    private final List<Page> pages = new ArrayList<>();
    private ResourceLocation icon;

    public ConfigBuilder registerOwnModOptions() {
        return this;
    }

    public ConfigBuilder setNonTintedIcon(ResourceLocation icon) {
        this.icon = icon;
        return this;
    }

    public ConfigBuilder addPage(OptionPageBuilder page) {
        pages.add(page.build());
        return this;
    }

    public OptionPageBuilder createOptionPage() {
        return new OptionPageBuilder();
    }

    public OptionGroupBuilder createOptionGroup() {
        return new OptionGroupBuilder();
    }

    public OptionBuilder<Boolean> createBooleanOption(ResourceLocation id) {
        return new OptionBuilder<>(new BooleanOption(id, config));
    }

    public OptionBuilder<Integer> createIntegerOption(ResourceLocation id) {
        return new OptionBuilder<>(new IntegerOption(id, config));
    }

    public <T extends Enum<T>> OptionBuilder<T> createEnumOption(
            ResourceLocation id, Class<T> type) {
        return new OptionBuilder<>(new EnumOption<>(id, config, type));
    }

    public OptionBuilder<Void> createExternalButtonOption(ResourceLocation id) {
        return new OptionBuilder<>(new ExternalButtonOption(id, config));
    }

    public ModOptions build() {
        return new ModOptions(
                "reeses-sodium-options",
                "Reese's Sodium Options",
                "2.2.5",
                icon,
                false,
                org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme.DEFAULT,
                List.copyOf(pages));
    }
}
