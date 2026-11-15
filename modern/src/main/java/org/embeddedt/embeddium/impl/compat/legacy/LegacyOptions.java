package org.embeddedt.embeddium.impl.compat.legacy;

import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A live view: only fields actually changed through the old API are written back. */
public final class LegacyOptions {
    private static SodiumGameOptions view;
    private static org.embeddedt.embeddium.impl.gui.SodiumGameOptions source;
    private static final Map<String, Object> previous = new HashMap<>();

    public static synchronized SodiumGameOptions view(org.embeddedt.embeddium.impl.gui.SodiumGameOptions config) {
        if (view == null) view = SodiumGameOptions.defaults();
        synchronize(config);
        return view;
    }

    public static synchronized void synchronize(org.embeddedt.embeddium.impl.gui.SodiumGameOptions config) {
        if (view == null) return;
        boolean replaced = source != config;
        source = config;
        try {
            for (String section : new String[]{"quality", "advanced", "performance", "notifications"}) {
                Object oldSection = SodiumGameOptions.class.getField(section).get(view);
                Object newSection = config.getClass().getField(section).get(config);
                for (Field field : oldSection.getClass().getFields()) {
                    Field nativeField = newSection.getClass().getField(field.getName());
                    String key = section + "." + field.getName();
                    Object oldValue = field.get(oldSection);
                    if (!replaced && !Objects.equals(oldValue, previous.get(key))) {
                        nativeField.set(newSection, convert(oldValue, nativeField.getType()));
                    } else {
                        field.set(oldSection, convert(nativeField.get(newSection), field.getType()));
                    }
                    previous.put(key, field.get(oldSection));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Incompatible configuration field", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(Object value, Class<?> type) {
        return value instanceof Enum<?> e ? Enum.valueOf((Class)type, e.name()) : value;
    }

    public static synchronized boolean save(SodiumGameOptions options) throws IOException {
        if (options != view) return false;
        if (options.isReadOnly()) throw new IllegalStateException("Config file is read-only");
        synchronize(source);
        source.writeChanges();
        return true;
    }

    public static synchronized boolean isReadOnly(SodiumGameOptions options) {
        return options == view && source != null && source.isReadOnly();
    }

    public static synchronized boolean setReadOnly(SodiumGameOptions options) {
        if (options != view || source == null) return false;
        source.setReadOnly();
        return true;
    }
}
