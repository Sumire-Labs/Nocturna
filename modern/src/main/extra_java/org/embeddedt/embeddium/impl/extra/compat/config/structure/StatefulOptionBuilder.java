package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.extra.compat.StorageEventHandler;
import org.embeddedt.embeddium.impl.extra.compat.config.ConfigState;
import org.embeddedt.embeddium.impl.extra.compat.config.option.*;

import java.util.*;
import java.util.function.*;

/** Builds an Extra option against Nocturna's existing public options API. */
@SuppressWarnings("unchecked")
public abstract class StatefulOptionBuilder<T, B extends StatefulOptionBuilder<T, B>> {
    public final ConfigBuilder owner;
    public final ResourceLocation id;
    public Component name = Component.empty(), tooltip = Component.empty();
    public boolean enabled = true, hideWhenDisabled = true;
    public Function<ConfigState, Boolean> enabledProvider = s -> true;
    public Consumer<T> setter;
    public Supplier<T> getter;
    public T defaultValue;
    public StorageEventHandler storage;
    public OptionImpact impact;
    public final Set<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

    protected StatefulOptionBuilder(ConfigBuilder owner, ResourceLocation id) {
        this.owner = owner;
        this.id = id;
    }

    protected B self() {
        return (B) this;
    }

    public B setName(Component v) {
        name = v;
        return self();
    }

    public B setTooltip(Component v) {
        tooltip = v;
        return self();
    }

    public B setEnabled(boolean v) {
        enabled = v;
        return self();
    }

    public B setEnabledProvider(
            Function<ConfigState, Boolean> v, ResourceLocation... dependencies) {
        enabledProvider = v;
        return self();
    }

    public B setControlHiddenWhenDisabled(boolean v) {
        hideWhenDisabled = v;
        return self();
    }

    public B setBinding(Consumer<T> set, Supplier<T> get) {
        setter = set;
        getter = get;
        return self();
    }

    public B setDefaultValue(T v) {
        defaultValue = v;
        return self();
    }

    public B setStorageHandler(StorageEventHandler v) {
        storage = v;
        return self();
    }

    public B setImpact(OptionImpact v) {
        impact = v;
        return self();
    }

    public B setFlags(OptionFlag... v) {
        Collections.addAll(flags, v);
        return self();
    }

    public abstract Class<T> type();

    public abstract me.jellysquid.mods.sodium.client.gui.options.control.Control<T> control(
            ExtraOption<T> option);
}
