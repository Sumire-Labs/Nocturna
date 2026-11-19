package org.embeddedt.embeddium.validation;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action.OptionResetAction;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action.OptionUndoAction;
import me.flashyreese.mods.reeses_sodium_options.client.gui.option.OptionExtended;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource;
import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.ConfigManager;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.util.*;

/** Real Java 17/Forge client checks; this class is never shipped. */
public final class RsoValidation {
    private static int ticks, stage, groups, pages, guis, forgeGuis;
    private static final long deadline = System.nanoTime() + 180_000_000_000L;
    private static Screen previous;
    private static SodiumVideoOptionsScreen screen;
    private static double initialGamma;
    private static int initialWidth, initialHeight;
    private static String clipboard;
    private static byte[] savedRsoConfig;
    private static boolean hadRsoConfig, wasRsoEnabled;
    private static Path rsoConfigPath;

    private static List<Option> options() {
        return ConfigManager.CONFIG.getModOptions().stream()
                .flatMap(m -> m.pages().stream())
                .flatMap(p -> p.groups().stream())
                .flatMap(g -> g.options().stream())
                .toList();
    }

    private static Option option(String id) {
        return options().stream()
                .filter(o -> o.id.toString().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing option " + id));
    }

    public static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void install() {
        rsoConfigPath =
                net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR
                        .get()
                        .resolve("reeses_sodium_options.json");
        try {
            hadRsoConfig = Files.exists(rsoConfigPath);
            if (hadRsoConfig) savedRsoConfig = Files.readAllBytes(rsoConfigPath);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot snapshot validation configuration", failure);
        }
        wasRsoEnabled = ReeseSodiumOptionsConfig.config().isEnabled();
        ReeseSodiumOptionsConfig.config().setEnabled(true);
        RsoParityValidation.install();
        org.embeddedt.embeddium.api.OptionGroupConstructionEvent.BUS.addListener(e -> groups++);
        org.embeddedt.embeddium.api.OptionPageConstructionEvent.BUS.addListener(e -> pages++);
        org.embeddedt.embeddium.api.OptionGUIConstructionEvent.BUS.addListener(e -> guis++);
        MinecraftForge.EVENT_BUS.addListener(
                (org.embeddedt.embeddium.api.OptionGUIConstructionEvent e) -> forgeGuis++);
        MinecraftForge.EVENT_BUS.addListener(RsoValidation::tick);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            if (System.nanoTime() > deadline)
                throw new AssertionError("RSO client timed out at stage " + stage);
            Minecraft mc = Minecraft.getInstance();
            ticks++;
            if (stage == 0
                    && mc.getOverlay() == null
                    && (mc.screen instanceof TitleScreen
                            || mc.screen instanceof AccessibilityOnboardingScreen)) {
                previous = mc.screen;
                initialGamma = mc.options.gamma().get();
                mc.setScreen(NocturnaOptionsSource.screen(previous));
                require(
                        mc.screen instanceof SodiumVideoOptionsScreen,
                        "Video options factory did not select RSO");
                screen = (SodiumVideoOptionsScreen) mc.screen;
                initialWidth = screen.width;
                initialHeight = screen.height;
                RsoVisualValidation.prepare();
                stage = 1;
                ticks = 0;
            } else if (stage == 1 && ticks > 5) {
                if (RsoVisualValidation.tick(mc, screen)) return;
                verifyBindings(mc);
                verifyInput(mc);
                verifyGroupCollapse();
                verifyModGroupCollapse(mc);
                RsoOverlayValidation.verify(mc);
                RsoRowValidation.verify(screen);
                RsoParityValidation.verify(mc, screen);
                RsoSdlValidation.verify(mc, screen);
                RsoAddonWidgetValidation.verify(mc, screen);
                screen.resize(mc, 360, 240);
                stage = 2;
                ticks = 0;
            } else if (stage == 2 && ticks > 5) {
                require(
                        screen.rso$getTabFrame().getSelectedFrame() != null,
                        "Small screen lost its page");
                screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                screen.triggerImmediateNarration(false);
                screen.resize(mc, initialWidth, initialHeight);
                stage = 3;
                ticks = 0;
            } else if (stage == 3 && ticks > 5) {
                try (var image =
                        net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(Path.of("rso-validation.png"));
                }
                var setting = (BooleanOption) option("reeses-sodium-options:tab_header_icons");
                boolean value = setting.getValidatedValue();
                setting.modifyValue(!value);
                ConfigManager.CONFIG.applyAllOptions();
                require(
                        ReeseSodiumOptionsConfig.config().isTabHeaderIcons() == !value,
                        "RSO storage/apply hook failed");
                setting.modifyValue(value);
                ConfigManager.CONFIG.applyAllOptions();
                require(screen.rso$getTabFrame() != null, "RSO apply hook did not rebuild screen");
                screen.onClose();
                require(mc.screen == previous, "Previous screen was not restored");
                mc.setScreen(NocturnaOptionsSource.screen(previous));
                require(mc.screen instanceof SodiumVideoOptionsScreen, "Reopening RSO failed");
                var enabled = (BooleanOption) option("reeses-sodium-options:enabled");
                enabled.modifyValue(false);
                ConfigManager.CONFIG.applyAllOptions();
                require(
                        mc.screen
                                instanceof
                                org.embeddedt.embeddium.impl.gui.modern.EmbeddiumVideoOptionsScreen,
                        "Disabling RSO did not restore the classic screen");
                var controller = NocturnaOptionsSource.field(mc.screen, "controller");
                var classicPages =
                        (List<org.taumc.celeritas.api.options.structure.OptionPage>)
                                NocturnaOptionsSource.field(controller, "pages");
                var reenable =
                        (org.taumc.celeritas.api.options.structure.Option<Boolean>)
                                classicPages.stream()
                                        .flatMap(p -> p.getOptions().stream())
                                        .filter(
                                                o ->
                                                        o.getId()
                                                                        .getModId()
                                                                        .equals(
                                                                                "reeses-sodium-options")
                                                                && o.getId()
                                                                        .getPath()
                                                                        .equals("enabled"))
                                        .findFirst()
                                        .orElseThrow();
                reenable.setValue(true);
                reenable.applyChanges();
                reenable.getStorage().save(Set.of());
                require(
                        mc.screen instanceof SodiumVideoOptionsScreen,
                        "Classic RSO settings could not re-enable the replacement UI");
                screen = (SodiumVideoOptionsScreen) mc.screen;
                screen.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
                screen.rso$getSearchTextField().write("distance");
                clipboard = mc.keyboardHandler.getClipboard();
                screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
                screen.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL);
                stage = 4;
                ticks = 0;
            } else if (stage == 4 && ticks > 10) {
                if (!mc.keyboardHandler.getClipboard().equals("distance")) {
                    screen.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL);
                    ticks = 0;
                    return;
                }
                screen.keyPressed(GLFW.GLFW_KEY_X, 0, GLFW.GLFW_MOD_CONTROL);
                require(
                        screen.rso$getSearchTextField().getText().isEmpty(),
                        "Cut did not remove selection");
                stage = 5;
                ticks = 0;
            } else if (stage == 5 && ticks > 10) {
                screen.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
                if (!screen.rso$getSearchTextField().getText().equals("distance")) {
                    ticks = 0;
                    return;
                }
                mc.keyboardHandler.setClipboard(clipboard);
                mc.options.gamma().set(initialGamma);
                String report =
                        "PASS Java17 Forge: RSO registry, legacy construction events, defaults,"
                            + " undo/apply/storage, Unicode and clipboard input, keyboard focus,"
                            + " row controls, narration, resizing, config rebuild and reopening;"
                            + " options="
                                + options().size();
                Files.writeString(Path.of("rso-validation.txt"), report);
                System.out.println(report);
                restoreRsoConfig();
                mc.stop();
                stage = 6;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                restoreRsoConfig();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                Minecraft.getInstance().options.gamma().set(initialGamma);
                Files.writeString(Path.of("rso-validation.txt"), "FAIL " + failure);
            } catch (Exception ignored) {
            }
            Runtime.getRuntime().halt(1);
        }
    }

    private static void restoreRsoConfig() throws java.io.IOException {
        ReeseSodiumOptionsConfig.config().setEnabled(wasRsoEnabled);
        if (hadRsoConfig) Files.write(rsoConfigPath, savedRsoConfig);
        else Files.deleteIfExists(rsoConfigPath);
    }

    private static void verifyBindings(Minecraft mc) {
        require(System.getProperty("java.specification.version").equals("17"), "Expected Java 17");
        require(
                com.ibm.icu.util.VersionInfo.ICU_VERSION.toString().startsWith("61.1"),
                "RSO replaced Minecraft's ICU runtime: "
                        + com.ibm.icu.util.VersionInfo.ICU_VERSION);
        require(
                groups > 0 && pages > 0 && guis > 0 && forgeGuis > 0,
                "Legacy construction events were lost");
        require(options().size() >= 60, "Incomplete Nocturna/RSO registry");
        for (Option option : options()) {
            require(option instanceof OptionExtended, "Option ID metadata mixin missing");
            if (option instanceof StatefulOption<?> value && value.getDefaultValue() == null) {
                require(option.id.getPath().startsWith("anonymous/"), "Missing built-in default: " + option.id);
                require(option.state == null, "Legacy option without defaults entered the reset transaction");
            }
        }
        var brightness = (IntegerOption) option("minecraft:brightness");
        int applied = brightness.getAppliedValue();
        int modified = applied == 51 ? 52 : 51;
        brightness.modifyValue(modified);
        require(
                brightness.hasChanged() && ConfigManager.CONFIG.anyOptionChanged(),
                "Edits were not tracked");
        require(
                mc.options.gamma().get() == initialGamma,
                "Unapplied edit leaked into Minecraft options");
        require(OptionUndoAction.canUndo(brightness), "Undo overlay did not activate");
        OptionUndoAction.undoChanges(brightness);
        require(
                !brightness.hasChanged() && brightness.getValidatedValue() == applied,
                "Per-option undo failed");
        brightness.modifyValue(modified);
        ConfigManager.CONFIG.resetAllOptionsFromBindings();
        require(
                brightness.getValidatedValue() == applied
                        && !ConfigManager.CONFIG.anyOptionChanged(),
                "Global undo failed");
        brightness.modifyValue(modified);
        ConfigManager.CONFIG.applyAllOptions();
        require(
                Math.abs(mc.options.gamma().get() - modified * .01D) < 1e-12,
                "Apply did not update Minecraft binding");
        require(!brightness.hasChanged(), "Apply left a pending edit");
        if (OptionResetAction.canReset(brightness)) {
            OptionResetAction.resetToDefault(brightness);
            require(
                    Objects.equals(
                            brightness.getValidatedValue(),
                            brightness.getDefaultValue().get(brightness.state)),
                    "Default reset failed");
        }
        brightness.modifyValue(applied);
        ConfigManager.CONFIG.applyAllOptions();
    }

    private static void verifyInput(Minecraft mc) {
        require(screen.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL), "Ctrl+F ignored");
        var search = screen.rso$getSearchTextField();
        require(search.isFocused(), "Ctrl+F did not focus search");
        for (char ch : "distance".toCharArray())
            require(screen.charTyped(ch, 0), "Typed search character was rejected");
        require(search.getText().equals("distance"), "Search text changed");
        require(screen.rso$getTabFrame().getTabs().size() > 0, "Search removed all matching tabs");
        screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        require(
                screen.charTyped('\uD83C', 0) && screen.charTyped('\uDF27', 0),
                "Supplementary text rejected");
        require(search.getText().equals("\uD83C\uDF27"), "Supplementary character split");
        screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        require(search.getText().isEmpty(), "Backspace split a surrogate pair");
        for (char ch : "画質".toCharArray()) screen.charTyped(ch, 0);
        require(search.getText().equals("画質"), "CJK input changed");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(
                search.getText().isEmpty() && mc.screen == screen,
                "Escape did not clear the search first");
    }

    private static List<net.minecraft.client.gui.components.events.GuiEventListener> descendants(
            net.minecraft.client.gui.components.events.GuiEventListener root) {
        var result = new ArrayList<net.minecraft.client.gui.components.events.GuiEventListener>();
        result.add(root);
        if (root
                instanceof
                net.minecraft.client.gui.components.events.ContainerEventHandler container)
            for (var child : container.children()) result.addAll(descendants(child));
        return result;
    }

    private static void verifyGroupCollapse() {
        var state =
                (me.flashyreese.mods.reeses_sodium_options.client.gui.state.OptionsScreenUiState)
                        NocturnaOptionsSource.field(screen, "uiState");
        var label =
                descendants(screen.rso$getTabFrame().getSelectedFrame()).stream()
                        .filter(
                                w ->
                                        w
                                                        instanceof
                                                        me.flashyreese.mods.reeses_sodium_options
                                                                        .client.gui.widget
                                                                        .LabelWidget
                                                                l
                                                && l.collapseKey() != null)
                        .map(
                                w ->
                                        (me.flashyreese.mods.reeses_sodium_options.client.gui.widget
                                                        .LabelWidget)
                                                w)
                        .findFirst()
                        .orElseThrow();
        var key = label.collapseKey();
        require(
                label.mouseClicked(
                        new MouseButtonEvent(label.getCenterX(), label.getCenterY(), 0, 0), false),
                "Group heading did not accept a click");
        require(state.collapsedOptionGroups().contains(key), "Group did not collapse");
        var collapsed =
                descendants(screen.rso$getTabFrame().getSelectedFrame()).stream()
                        .filter(
                                w ->
                                        w
                                                        instanceof
                                                        me.flashyreese.mods.reeses_sodium_options
                                                                        .client.gui.widget
                                                                        .LabelWidget
                                                                l
                                                && key.equals(l.collapseKey()))
                        .map(
                                w ->
                                        (me.flashyreese.mods.reeses_sodium_options.client.gui.widget
                                                        .LabelWidget)
                                                w)
                        .findFirst()
                        .orElseThrow();
        collapsed.setFocused(true);
        require(
                collapsed.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)),
                "Collapsed group did not accept keyboard activation");
        require(!state.collapsedOptionGroups().contains(key), "Group did not expand");
    }

    private static void verifyModGroupCollapse(Minecraft mc) {
        require(new ReeseSodiumOptionsConfig.ConfigData().getTabHeaderCollapseMode()
                        == ReeseSodiumOptionsConfig.TabHeaderCollapseMode.MANUAL,
                "New Nocturna configurations must allow manual mod-header collapse");
        var config = ReeseSodiumOptionsConfig.config();
        var previousMode = config.getTabHeaderCollapseMode();
        config.setTabHeaderCollapseMode(ReeseSodiumOptionsConfig.TabHeaderCollapseMode.MANUAL);
        try {
            screen.resize(mc, screen.width, screen.height);
            var state =
                    (me.flashyreese.mods.reeses_sodium_options.client.gui.state
                                    .OptionsScreenUiState)
                            NocturnaOptionsSource.field(screen, "uiState");
            var header =
                    descendants(screen.rso$getTabFrame()).stream()
                            .filter(
                                    w ->
                                            w
                                                    instanceof
                                                    me.flashyreese.mods.reeses_sodium_options.client
                                                            .gui.widget.TabHeaderWidget)
                            .map(
                                    w ->
                                            (me.flashyreese.mods.reeses_sodium_options.client.gui
                                                            .widget.TabHeaderWidget)
                                                    w)
                            .findFirst()
                            .orElseThrow();
            var mod = (ModOptions) NocturnaOptionsSource.field(header, "modOptions");
            var collapsed = state.manuallyCollapsedTabGroups();
            require(!collapsed.contains(mod.configId()), "Mod header fixture started collapsed");
            require(
                    header.mouseClicked(
                            new MouseButtonEvent(header.getCenterX(), header.getCenterY(), 0, 0),
                            false),
                    "Mod header did not accept click");
            require(collapsed.contains(mod.configId()), "Mod pages did not collapse");
            header.setFocused(true);
            require(
                    header.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)),
                    "Mod header did not accept Space");
            require(!collapsed.contains(mod.configId()), "Mod pages did not expand");
            System.out.println("PASS manual mod-header mouse collapse and keyboard expansion");
        } finally {
            config.setTabHeaderCollapseMode(previousMode);
            screen.resize(mc, screen.width, screen.height);
        }
    }
}
