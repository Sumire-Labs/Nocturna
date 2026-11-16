package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

public class OptionBuilder<T> {
    protected final Option option;

    public OptionBuilder(Option option) {
        this.option = option;
    }

    public OptionBuilder<T> setName(Component name) {
        option.name = name;
        return this;
    }

    public OptionBuilder<T> setTooltip(Component tooltip) {
        option.tooltip = tooltip;
        return this;
    }

    @SuppressWarnings("unchecked")
    private StatefulOption<T> value() {
        return (StatefulOption<T>) option;
    }

    public OptionBuilder<T> setDefaultValue(T value) {
        value().defaultValue = config -> value;
        return this;
    }

    public OptionBuilder<T> setBinding(
            java.util.function.Consumer<T> setter, java.util.function.Supplier<T> getter) {
        value().getter = getter;
        value().setter = setter;
        return this;
    }

    public OptionBuilder<T> setStorageHandler(
            org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.StorageEventHandler
                    handler) {
        value().storage = handler;
        return this;
    }

    public OptionBuilder<T> setApplyHook(
            java.util.function.Consumer<
                            org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config
                                    .ConfigState>
                    hook) {
        value().applyHook = hook;
        return this;
    }

    public OptionBuilder<T> setRange(int min, int max, int step) {
        ((IntegerOption) option).stepped =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option
                        .SteppedValidator(min, max, step);
        return this;
    }

    @SuppressWarnings("unchecked")
    public OptionBuilder<T> setValueFormatter(
            java.util.function.Function<Integer, Component> formatter) {
        ((IntegerOption) option).formatter = formatter;
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public OptionBuilder<T> setElementNameProvider(
            java.util.function.Function<T, Component> names) {
        ((EnumOption) option).names = names;
        return this;
    }

    public OptionBuilder<T> setScreenConsumer(
            java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> action) {
        ((ExternalButtonOption) option).action = action;
        return this;
    }

    public Option build() {
        return option;
    }
}
