package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

import java.util.*;

/** Pending values stay separate from the live bindings until Apply. */
public final class ExtraOption<T> implements Option<T> {
    public final StatefulOptionBuilder<T, ?> spec;
    private T original, value;
    private final OptionStorage<?> storage;
    private Control<T> control;

    ExtraOption(StatefulOptionBuilder<T, ?> spec) {
        this.spec = spec;
        storage = spec.owner.storage(spec.storage);
        reset();
    }

    public OptionIdentifier<T> getId() {
        return OptionIdentifier.create(spec.id, spec.type());
    }

    public Component getName() {
        return spec.name;
    }

    public Component getTooltip() {
        return spec.tooltip;
    }

    public OptionImpact getImpact() {
        return spec.impact == null ? null : OptionImpact.valueOf(spec.impact.name());
    }

    public Control<T> getControl() {
        if (control == null) control = new HiddenControl<>(this, spec.control(this));
        return control;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T v) {
        value = Objects.requireNonNull(v);
    }

    public T getDefaultValue() {
        return spec.defaultValue;
    }

    public void resetToDefault() {
        setValue(getDefaultValue());
    }

    public void reset() {
        original = value = Objects.requireNonNull(spec.getter.get());
    }

    public OptionStorage<?> getStorage() {
        return storage;
    }

    public boolean isAvailable() {
        return spec.enabled && spec.enabledProvider.apply(spec.owner);
    }

    public boolean hasChanged() {
        return !Objects.equals(original, value);
    }

    public void applyChanges() {
        if (hasChanged()) {
            spec.setter.accept(value);
            original = value;
        }
    }

    public Collection<OptionFlag> getFlags() {
        return spec.flags.stream().map(f -> OptionFlag.valueOf(f.name())).toList();
    }
}
