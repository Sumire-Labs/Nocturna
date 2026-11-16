package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.resources.ResourceLocation;

public class StatefulOption<T> extends Option {
    public java.util.function.Supplier<T> getter;
    public java.util.function.Consumer<T> setter;
    public ValueSource<T> defaultValue;
    public java.util.function.UnaryOperator<T> validator =
            java.util.function.UnaryOperator.identity();
    public org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.StorageEventHandler
            storage;
    public java.util.function.Consumer<
                    org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.ConfigState>
            applyHook;
    private T modified;
    private boolean changed;
    private T applied;
    private boolean initialized;

    public StatefulOption(ResourceLocation id, Config state) {
        super(id, state);
    }

    public T getValidatedValue() {
        return validator.apply(changed ? modified : getAppliedValue());
    }

    public T getAppliedValue() {
        if (!initialized) {
            applied = getter.get();
            initialized = true;
        }
        return applied;
    }

    public void modifyValue(T value) {
        modified = java.util.Objects.requireNonNull(value);
        changed = !java.util.Objects.equals(value, getAppliedValue());
        if (!changed) modified = null;
        if (state != null) state.invalidate();
    }

    public boolean hasChanged() {
        return changed;
    }

    public ValueSource<T> getDefaultValue() {
        return defaultValue;
    }

    public void resetToDefault() {
        modifyValue(defaultValue.get(state));
    }

    public boolean isValueAllowed(T value) {
        return value != null && java.util.Objects.equals(value, validator.apply(value));
    }

    public void reset() {
        applied = getter.get();
        initialized = true;
        modified = null;
        changed = false;
        if (state != null) state.invalidate();
    }

    public void apply() {
        if (changed) {
            setter.accept(getValidatedValue());
            reset();
        }
    }
}
