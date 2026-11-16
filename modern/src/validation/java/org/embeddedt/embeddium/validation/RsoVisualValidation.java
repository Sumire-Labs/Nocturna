package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.platform.NativeImage;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.ConfigManager;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.IntegerOption;

import java.nio.file.*;
import java.util.*;

/** Original MIT RSO sources, adapted to the same 1.20.1 host, rendered by the real client. */
final class RsoVisualValidation {
    private static final String[] SCENARIOS = {
        "general", "quality", "rso-settings", "search", "narrow", "pending-edit", "themes-off"
    };
    private static NativeImage port;
    private static int stage, ticks, scenario;
    private static boolean oldMotion, oldThemes, done;
    private static final List<String> reports = new ArrayList<>();

    static void prepare() {
        if (Boolean.getBoolean("nocturna.compareRsoReference")) {
            oldMotion = ReeseSodiumOptionsConfig.config().isReducedMotion();
            oldThemes = ReeseSodiumOptionsConfig.config().isColorThemes();
            ReeseSodiumOptionsConfig.config().setReducedMotion(true);
        }
    }

    static boolean tick(Minecraft mc, SodiumVideoOptionsScreen original) throws Exception {
        if (done || !Boolean.getBoolean("nocturna.compareRsoReference")) return false;
        if (stage == 0) {
            show(mc, original.rso$previousScreen(), false);
            stage = 1;
            ticks = 0;
            return true;
        }
        if (++ticks < 6) return true;
        if (stage == 1) {
            port = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget());
            port.writeToFile(Path.of("rso-ported-" + SCENARIOS[scenario] + ".png"));
            show(mc, original.rso$previousScreen(), true);
            stage = 2;
            ticks = 0;
            return true;
        }
        try (var reference =
                net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            reference.writeToFile(Path.of("rso-original-" + SCENARIOS[scenario] + ".png"));
            RsoValidation.require(
                    reference.getWidth() == port.getWidth()
                            && reference.getHeight() == port.getHeight(),
                    "Reference UI framebuffer dimensions differ");
            int differences = 0;
            for (int y = 0; y < port.getHeight(); y++)
                for (int x = 0; x < port.getWidth(); x++)
                    if (port.getPixelRGBA(x, y) != reference.getPixelRGBA(x, y)) differences++;
            String report =
                    SCENARIOS[scenario]
                            + ": "
                            + differences
                            + " differing pixels of "
                            + port.getWidth() * port.getHeight();
            reports.add(report);
            Files.write(Path.of("rso-visual-comparison.txt"), reports);
            RsoValidation.require(differences == 0, report);
            System.out.println("PASS original MIT RSO / same Java17-1.20.1 host / " + report);
        } finally {
            port.close();
        }
        if (++scenario < SCENARIOS.length) {
            stage = 0;
            return true;
        }
        ReeseSodiumOptionsConfig.config().setReducedMotion(oldMotion);
        ReeseSodiumOptionsConfig.config().setColorThemes(oldThemes);
        ConfigManager.CONFIG.resetAllOptionsFromBindings();
        mc.setScreen(original);
        done = true;
        return true;
    }

    private static void show(Minecraft mc, Screen parent, boolean reference) throws Exception {
        ReeseSodiumOptionsConfig.config().setColorThemes(scenario != 6);
        mc.setLastInputType(net.minecraft.client.InputType.MOUSE);
        Class<?> type =
                Class.forName(
                        reference
                                ? "org.embeddedt.embeddium.validation.reference.rso.client.gui.SodiumVideoOptionsScreen"
                                : "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen");
        Screen screen = (Screen) type.getConstructor(Screen.class).newInstance(parent);
        var state = type.getDeclaredField("uiState");
        state.setAccessible(true);
        state.set(screen, state.getType().getConstructor().newInstance());
        mc.setScreen(screen);
        Object tabs = type.getMethod("rso$getTabFrame").invoke(screen);
        var choices = (List<?>) tabs.getClass().getMethod("getTabs").invoke(tabs);
        int tab = scenario == 1 ? 1 : scenario == 2 ? choices.size() - 1 : 0;
        tabs.getClass()
                .getMethod("setTab", Optional.class)
                .invoke(tabs, Optional.of(choices.get(tab)));
        if (scenario == 3) {
            Object search = type.getMethod("rso$getSearchTextField").invoke(screen);
            search.getClass().getMethod("write", String.class).invoke(search, "distance");
        }
        if (scenario == 4) screen.resize(mc, 320, 240);
        if (scenario == 5)
            ConfigManager.CONFIG.getModOptions().stream()
                    .flatMap(mod -> mod.pages().stream())
                    .flatMap(page -> page.groups().stream())
                    .flatMap(group -> group.options().stream())
                    .filter(option -> option.id.toString().equals("minecraft:brightness"))
                    .map(option -> (IntegerOption) option)
                    .findFirst()
                    .orElseThrow()
                    .modifyValue(70);
    }
}
