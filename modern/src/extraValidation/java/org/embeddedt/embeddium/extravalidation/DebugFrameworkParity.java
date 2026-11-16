package org.embeddedt.embeddium.extravalidation;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.extra.compat.*;

import java.util.*;

final class DebugFrameworkParity {
    static void screen(Minecraft client) {
        var screen = (ExtraDebugOptionsScreen) client.screen;
        var performance =
                screen.children().stream()
                        .filter(
                                w ->
                                        w instanceof net.minecraft.client.gui.components.Button b
                                                && b.getMessage()
                                                        .getString()
                                                        .equals(
                                                                net.minecraft.client.resources
                                                                        .language.I18n.get(
                                                                        "nocturna.debug.profile.performance")))
                        .map(w -> (net.minecraft.client.gui.components.Button) w)
                        .findFirst()
                        .orElseThrow();
        performance.onPress();
        ExtraValidation.require(
                DebugOverlayManager.state().profile().orElseThrow()
                        == DebugOverlayState.Profile.PERFORMANCE,
                "profile button did not apply performance defaults");
        var search =
                screen.children().stream()
                        .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox)
                        .map(w -> (net.minecraft.client.gui.components.EditBox) w)
                        .findFirst()
                        .orElseThrow();
        search.setValue("sodium-extra:sodium-extra.option.light_updates_warning");
        var statuses =
                screen.children().stream()
                        .filter(
                                w ->
                                        w instanceof net.minecraft.client.gui.components.Button b
                                                && b.getTooltip() != null)
                        .map(w -> (net.minecraft.client.gui.components.Button) w)
                        .toList();
        ExtraValidation.require(
                statuses.size() == 3, "search did not reduce the catalog to one entry");
        statuses.stream()
                .filter(
                        b ->
                                b.getMessage()
                                        .getString()
                                        .contains(
                                                net.minecraft.client.resources.language.I18n.get(
                                                        "sodium-extra.debug.visibility.overlay")))
                .findFirst()
                .orElseThrow()
                .onPress();
        ExtraValidation.require(
                DebugOverlayManager.state().profile().isEmpty()
                        && DebugOverlayManager.state()
                                        .get(
                                                "sodium-extra:sodium-extra.option.light_updates_warning")
                                == DebugOverlayState.Visibility.OVERLAY,
                "visibility button did not create a custom profile");
        boolean reduced = client.options.reducedDebugInfo().get();
        try {
            search.setValue("minecraft:player_position");
            client.options.reducedDebugInfo().set(true);
            screen.resize(client, screen.width, screen.height);
            var forbidden =
                    screen.children().stream()
                            .filter(
                                    w ->
                                            w
                                                            instanceof
                                                            net.minecraft.client.gui.components
                                                                            .Button
                                                                    b
                                                    && b.getTooltip() != null)
                            .map(w -> (net.minecraft.client.gui.components.Button) w)
                            .toList();
            ExtraValidation.require(
                    forbidden.size() == 3 && forbidden.stream().noneMatch(b -> b.active),
                    "reduced-debug permission did not disable the entry controls");
        } finally {
            client.options.reducedDebugInfo().set(reduced);
            DebugOverlayManager.state().loadProfile(DebugOverlayState.Profile.DEFAULT);
            DebugOverlayManager.invalidate();
        }
        System.out.println(
                "PASS debug screen search, profile buttons, custom selection and permission"
                    + " tooltips");
    }

    private static boolean originalDebug;

    static void beginVisuals(Minecraft client) {
        originalDebug = client.options.renderDebug;
        client.options.renderDebug = true;
        for (var entry : DebugOverlayManager.entries())
            if (entry.category().equals("Visualizations"))
                DebugOverlayManager.state()
                        .set(entry.id().toString(), DebugOverlayState.Visibility.OVERLAY);
        DebugOverlayManager.invalidate();
        client.levelRenderer.needsUpdate();
    }

    static void endVisuals(Minecraft client) {
        ExtraValidation.require(
                !DebugVisuals.traversal().isEmpty(),
                "section paths did not capture the live visibility traversal");
        ExtraValidation.require(
                DebugVisuals.traversal().stream().anyMatch(n -> n.incoming() != 0),
                "section paths omitted actual incoming faces");
        ExtraValidation.require(
                org.lwjgl.opengl.GL33C.glGetError() == org.lwjgl.opengl.GL33C.GL_NO_ERROR,
                "debug visualizations produced a GL error");
        client.options.renderDebug = originalDebug;
        DebugOverlayManager.state().loadProfile(DebugOverlayState.Profile.DEFAULT);
        DebugOverlayManager.invalidate();
        System.out.println("PASS debug visualizations and actual async visibility traversal");
    }

    static void catalog() throws Exception {
        Set<String> expected;
        try (var input =
                DebugFrameworkParity.class.getResourceAsStream("/parity/debug26-ids.json")) {
            expected =
                    new HashSet<>(
                            java.util.Arrays.asList(
                                    new com.google.gson.Gson()
                                            .fromJson(
                                                    new String(
                                                            input.readAllBytes(),
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8),
                                                    String[].class)));
        }
        var actual = new HashSet<String>();
        for (var entry : DebugOverlayManager.entries())
            if (entry.id().getNamespace().equals("minecraft")) actual.add(entry.id().toString());
        ExtraValidation.require(
                actual.equals(expected),
                "debug catalog differs from pinned 26.3 identifiers: " + actual);
        ExtraValidation.require(
                DebugOverlayManager.entries().size() == expected.size() + 4,
                "Extra debug entries omitted");
        var state = DebugOverlayManager.state();
        state.loadProfile(DebugOverlayState.Profile.PERFORMANCE);
        ExtraValidation.require(
                state.get("minecraft:fps") == DebugOverlayState.Visibility.ALWAYS,
                "performance profile FPS default differs");
        state.set("minecraft:fps", DebugOverlayState.Visibility.NEVER);
        ExtraValidation.require(state.profile().isEmpty(), "editing did not leave the preset");
        state.loadProfile(DebugOverlayState.Profile.DEFAULT);
        System.out.println(
                "PASS complete 26.3 catalog (46 native + 4 Extra), profiles and custom"
                        + " transitions");
    }

    static void providers(Minecraft client) {
        var state = DebugOverlayManager.state();
        boolean debug = client.options.renderDebug;
        client.options.renderDebug = true;
        try {
            for (var entry : DebugOverlayManager.entries())
                state.set(entry.id().toString(), DebugOverlayState.Visibility.OVERLAY);
            DebugOverlayManager.invalidate();
            ExtraValidation.require(
                    !DebugOverlayManager.left().isEmpty() && !DebugOverlayManager.right().isEmpty(),
                    "full debug providers produced no output");
            for (var entry : DebugOverlayManager.entries()) entry.text().get();
            System.out.println("PASS all text providers against live client/server state");
        } finally {
            state.loadProfile(DebugOverlayState.Profile.DEFAULT);
            client.options.renderDebug = debug;
            DebugOverlayManager.invalidate();
        }
    }
}
