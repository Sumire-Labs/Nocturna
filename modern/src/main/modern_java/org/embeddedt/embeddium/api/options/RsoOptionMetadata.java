package org.embeddedt.embeddium.api.options;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.ConfigManager;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.StatefulOption;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Optional metadata for existing Embeddium/Celeritas options. Register during GUI construction. */
public final class RsoOptionMetadata<T> {
    private static final ConcurrentHashMap<ResourceLocation, RsoOptionMetadata<?>> ENTRIES =
            new ConcurrentHashMap<>();
    private Supplier<T> defaultValue;
    private Supplier<Component> name, tooltip;
    private BooleanSupplier enabled = () -> true, hideControl = () -> false;
    private UnaryOperator<T> validator;

    public RsoOptionMetadata<T> defaultValue(Supplier<T> value) {
        defaultValue = java.util.Objects.requireNonNull(value);
        return this;
    }

    public RsoOptionMetadata<T> name(Supplier<Component> value) {
        name = java.util.Objects.requireNonNull(value);
        return this;
    }

    public RsoOptionMetadata<T> tooltip(Supplier<Component> value) {
        tooltip = java.util.Objects.requireNonNull(value);
        return this;
    }

    public RsoOptionMetadata<T> enabled(BooleanSupplier value) {
        enabled = java.util.Objects.requireNonNull(value);
        return this;
    }

    public RsoOptionMetadata<T> hideControl(BooleanSupplier value) {
        hideControl = java.util.Objects.requireNonNull(value);
        return this;
    }

    public RsoOptionMetadata<T> validator(UnaryOperator<T> value) {
        validator = java.util.Objects.requireNonNull(value);
        return this;
    }

    public void register(ResourceLocation id) {
        ENTRIES.put(java.util.Objects.requireNonNull(id), this);
    }

    public static void unregister(ResourceLocation id) {
        ENTRIES.remove(id);
    }

    public static Optional<RsoOptionMetadata<?>> find(ResourceLocation id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    public Supplier<T> defaultValue() {
        return defaultValue;
    }

    public Supplier<Component> name() {
        return name;
    }

    public Supplier<Component> tooltip() {
        return tooltip;
    }

    public BooleanSupplier enabled() {
        return enabled;
    }

    public BooleanSupplier hideControl() {
        return hideControl;
    }

    public UnaryOperator<T> validator() {
        return validator;
    }

    /**
     * Reads a pending edit so availability/default/tooltip suppliers can depend on other options.
     */
    public static <T> Optional<T> pendingValue(ResourceLocation id, Class<T> type) {
        return ConfigManager.CONFIG.getModOptions().stream()
                .flatMap(mod -> mod.pages().stream())
                .flatMap(page -> page.groups().stream())
                .flatMap(group -> group.options().stream())
                .filter(option -> option.id.equals(id) && option instanceof StatefulOption<?>)
                .map(option -> ((StatefulOption<?>) option).getValidatedValue())
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }
}
