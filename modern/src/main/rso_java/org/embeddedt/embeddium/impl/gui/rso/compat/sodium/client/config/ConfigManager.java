package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.ConfigState;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.StorageEventHandler;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Nocturna binding registry for RSO's configuration calls; no Sodium 0.9 implementation is bundled.
 */
public final class ConfigManager {
    public static final ConfigManager CONFIG = new ConfigManager();
    private List<ModOptions> mods = List.of();

    public void beginScreenSession() {
        mods = org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource.create();
    }

    public List<ModOptions> getModOptions() {
        return mods;
    }

    private List<StatefulOption<?>> values() {
        return mods.stream()
                .flatMap(m -> m.pages().stream())
                .flatMap(p -> p.groups().stream())
                .flatMap(g -> g.options().stream())
                .filter(o -> o instanceof StatefulOption<?>)
                .<StatefulOption<?>>map(o -> (StatefulOption<?>) o)
                .toList();
    }

    public void resetAllOptionsFromBindings() {
        values().forEach(StatefulOption::reset);
    }

    public boolean anyOptionChanged() {
        return values().stream().anyMatch(StatefulOption::hasChanged);
    }

    public void invalidateGlobalRebuildDependents() {
        var states = Collections.newSetFromMap(new IdentityHashMap<Config, Boolean>());
        for (var option : values()) if (option.state != null) states.add(option.state);
        states.forEach(Config::invalidate);
    }

    public void applyAllOptions() {
        var changed = values().stream().filter(StatefulOption::hasChanged).toList();
        var storages =
                Collections.newSetFromMap(new IdentityHashMap<StorageEventHandler, Boolean>());
        var hooks = new ArrayList<Map.Entry<Consumer<ConfigState>, Config>>();
        try {
            for (var option : changed) {
                option.apply();
                if (option.storage != null) storages.add(option.storage);
                if (option.applyHook != null)
                    hooks.add(
                            new AbstractMap.SimpleImmutableEntry<>(option.applyHook, option.state));
            }
            storages.forEach(StorageEventHandler::save);
            org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource.flushNativeEffects();
            for (var hook : hooks) hook.getKey().accept(hook.getValue());
            invalidateGlobalRebuildDependents();
        } finally {
            org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource.discardNativeEffects();
        }
    }
}
