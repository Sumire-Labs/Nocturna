package org.embeddedt.embeddium.impl.extra.compat;

import com.google.gson.*;

import me.flashyreese.mods.sodiumextra.client.config.ConfigFileIO;

import java.nio.file.*;
import java.util.*;

/**
 * Profile/custom transitions and serialized statuses are independent of rendering and screen
 * lifetime.
 */
public final class DebugOverlayState {
    public enum Visibility {
        NEVER,
        OVERLAY,
        ALWAYS;

        public boolean visible(boolean overlay) {
            return this == ALWAYS || this == OVERLAY && overlay;
        }
    }

    public enum Profile {
        DEFAULT,
        PERFORMANCE
    }

    private final Path path;
    private final Map<String, Visibility> statuses = new LinkedHashMap<>();
    private Profile profile;
    private static final String LIGHT = "sodium-extra:sodium-extra.option.light_updates_warning";

    public DebugOverlayState(Path path) {
        this.path = path;
        loadProfile(Profile.DEFAULT, false);
        load();
    }

    public Visibility get(String id) {
        return statuses.getOrDefault(id, Visibility.NEVER);
    }

    public Optional<Profile> profile() {
        return Optional.ofNullable(profile);
    }

    public void set(String id, Visibility value) {
        profile = null;
        statuses.put(id, value);
        save();
    }

    public boolean toggle(String id) {
        return toggle(id, false);
    }

    public boolean toggle(String id, boolean overlay) {
        var previous = get(id);
        var value =
                previous == Visibility.ALWAYS || previous == Visibility.OVERLAY && overlay
                        ? Visibility.NEVER
                        : overlay ? Visibility.OVERLAY : Visibility.ALWAYS;
        set(id, value);
        return value != Visibility.NEVER;
    }

    public void loadProfile(Profile value) {
        loadProfile(value, true);
    }

    private void loadProfile(Profile value, boolean persist) {
        profile = value;
        statuses.clear();
        String[] entries =
                value == Profile.DEFAULT
                        ? new String[] {
                            "3d_crosshair",
                            "game_version",
                            "tps",
                            "fps",
                            "memory",
                            "system_specs",
                            "player_position",
                            "player_section_position",
                            "simple_performance_impactors"
                        }
                        : new String[] {
                            "tps", "gpu_utilization", "memory", "simple_performance_impactors"
                        };
        for (String id : entries) statuses.put("minecraft:" + id, Visibility.OVERLAY);
        if (value == Profile.PERFORMANCE) statuses.put("minecraft:fps", Visibility.ALWAYS);
        statuses.put(LIGHT, Visibility.OVERLAY);
        if (persist) save();
    }

    private void load() {
        if (!Files.exists(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("profile"))
                loadProfile(
                        Profile.valueOf(json.get("profile").getAsString().toUpperCase(Locale.ROOT)),
                        false);
            else if (json.has("custom")) {
                statuses.clear();
                profile = null;
                for (var entry : json.getAsJsonObject("custom").entrySet())
                    statuses.put(entry.getKey(), decode(entry.getValue().getAsString()));
            }
        } catch (Exception failure) {
            me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod.logger()
                    .warn("Could not load debug profile; retaining defaults", failure);
            loadProfile(Profile.DEFAULT, false);
        }
    }

    private static Visibility decode(String value) {
        return switch (value) {
            case "always_on", "ALWAYS" -> Visibility.ALWAYS;
            case "in_overlay", "OVERLAY" -> Visibility.OVERLAY;
            case "never", "NEVER" -> Visibility.NEVER;
            default -> throw new IllegalArgumentException("Unknown debug status " + value);
        };
    }

    public void save() {
        var json = new JsonObject();
        json.addProperty("version", 1);
        if (profile != null) json.addProperty("profile", profile.name().toLowerCase(Locale.ROOT));
        else {
            var custom = new JsonObject();
            statuses.forEach(
                    (id, value) ->
                            custom.addProperty(
                                    id,
                                    switch (value) {
                                        case ALWAYS -> "always_on";
                                        case OVERLAY -> "in_overlay";
                                        case NEVER -> "never";
                                    }));
            json.add("custom", custom);
        }
        try {
            ConfigFileIO.writeStringAtomically(
                    path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception failure) {
            me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod.logger()
                    .warn("Could not save debug profile", failure);
        }
    }

    public void importLegacy(Path legacy) {
        if (Files.exists(path) || !Files.exists(legacy)) return;
        try (var reader = Files.newBufferedReader(legacy)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : json.entrySet())
                statuses.put(entry.getKey(), decode(entry.getValue().getAsString()));
            profile = null;
            save();
        } catch (Exception failure) {
            me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod.logger()
                    .warn("Could not migrate legacy Extra debug visibility", failure);
        }
    }
}
