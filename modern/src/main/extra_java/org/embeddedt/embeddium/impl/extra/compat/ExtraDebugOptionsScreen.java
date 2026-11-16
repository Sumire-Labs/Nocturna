package org.embeddedt.embeddium.impl.extra.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.config.ConfigFileIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Backports the debug-entry visibility controls used by Extra's More HUD Overlays button. */
public final class ExtraDebugOptionsScreen extends Screen {
    private static final Map<ResourceLocation, DebugScreenEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Visibility> VISIBILITY = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;
    private final Screen parent;

    public ExtraDebugOptionsScreen() {
        super(Component.translatable("sodium-extra.option.more_hud_overlays"));
        this.parent = Minecraft.getInstance().screen;
        load();
    }

    public enum Visibility {
        NEVER,
        OVERLAY,
        ALWAYS;

        public boolean visible(boolean overlayVisible) {
            return this == ALWAYS || this == OVERLAY && overlayVisible;
        }
    }

    private static Path path() {
        return PlatformRuntimeInformation.getInstance()
                .getConfigDirectory()
                .resolve("sodium-extra-debug.json");
    }

    private static synchronized void load() {
        if (loaded) return;
        SodiumExtraClientMod.registerAll(ENTRIES::put);
        // Extra's NeoForge entrypoint registers this in both built-in overlay profiles.
        VISIBILITY.put(
                "sodium-extra:sodium-extra.option.light_updates_warning", Visibility.OVERLAY);
        if (Files.exists(path())) {
            try (var reader = Files.newBufferedReader(path())) {
                var json = GSON.fromJson(reader, com.google.gson.JsonObject.class);
                if (json != null)
                    for (var entry : json.entrySet()) {
                        if (ENTRIES.keySet().stream()
                                .anyMatch(id -> id.toString().equals(entry.getKey()))) {
                            try {
                                VISIBILITY.put(
                                        entry.getKey(),
                                        Visibility.valueOf(entry.getValue().getAsString()));
                            } catch (IllegalArgumentException | IllegalStateException ignored) {
                                // Unrecognized entries retain the same hidden default as
                                // unconfigured debug entries.
                            }
                        }
                    }
            } catch (IOException | com.google.gson.JsonParseException ex) {
                SodiumExtraClientMod.logger().warn("Could not read Extra debug entry settings", ex);
            }
        }
        loaded = true;
    }

    private static void save() {
        try {
            ConfigFileIO.writeStringAtomically(
                    path(), GSON.toJson(VISIBILITY) + System.lineSeparator());
        } catch (IOException ex) {
            SodiumExtraClientMod.logger().warn("Could not save Extra debug entry settings", ex);
        }
    }

    @Override
    protected void init() {
        int row = 0;
        for (var entry : ENTRIES.entrySet()) {
            String id = entry.getKey().toString();
            var button =
                    CycleButton.<Visibility>builder(
                                    v ->
                                            Component.translatable(
                                                    "sodium-extra.debug.visibility."
                                                            + v.name().toLowerCase(Locale.ROOT)))
                            .withValues(Visibility.values())
                            .withInitialValue(VISIBILITY.getOrDefault(id, Visibility.NEVER))
                            .create(
                                    width / 2,
                                    48 + row++ * 28,
                                    150,
                                    20,
                                    Component.empty(),
                                    (control, value) -> {
                                        VISIBILITY.put(id, value);
                                        save();
                                    });
            button.active = entry.getValue().isAllowed(minecraft.showOnlyReducedInfo());
            addRenderableWidget(button);
        }
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), b -> onClose())
                        .bounds(width / 2 - 100, height - 32, 200, 20)
                        .build());
    }

    @Override
    public void render(GuiGraphics gui, int x, int y, float delta) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 16, 0xffffffff);
        int row = 0;
        for (var entry : ENTRIES.entrySet()) {
            var label = Component.translatable(entry.getKey().getPath());
            gui.drawString(font, label, Math.max(8, width / 2 - 210), 54 + row++ * 28, 0xffffffff);
        }
        super.render(gui, x, y, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    public static void renderOverlays(GuiGraphics gui) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.options.hideGui || client.options.renderDebug) return;
        load();
        List<String> lines = new ArrayList<>(), priority = new ArrayList<>();
        DebugScreenDisplayer display = new DebugScreenDisplayer(lines, priority);
        ENTRIES.forEach(
                (id, entry) -> {
                    if (VISIBILITY
                                    .getOrDefault(id.toString(), Visibility.NEVER)
                                    .visible(client.options.renderDebug)
                            && entry.isAllowed(client.showOnlyReducedInfo())) {
                        entry.display(display, client.level, null, null);
                    }
                });
        priority.addAll(lines);
        int y = 2;
        for (String line : priority) {
            gui.fill(1, y - 1, client.font.width(line) + 3, y + client.font.lineHeight, 0x90505050);
            gui.drawString(client.font, line, 2, y, 0xffffffff, false);
            y += client.font.lineHeight;
        }
    }

    public static List<String> appendDebugLines(List<String> nativeLines) {
        Minecraft client = Minecraft.getInstance();
        load();
        List<String> lines = new ArrayList<>(), priority = new ArrayList<>();
        var display = new DebugScreenDisplayer(lines, priority);
        ENTRIES.forEach(
                (id, entry) -> {
                    if (VISIBILITY.getOrDefault(id.toString(), Visibility.NEVER).visible(true)
                            && entry.isAllowed(client.showOnlyReducedInfo())) {
                        entry.display(display, client.level, null, null);
                    }
                });
        priority.addAll(nativeLines);
        priority.addAll(lines);
        return priority;
    }
}
