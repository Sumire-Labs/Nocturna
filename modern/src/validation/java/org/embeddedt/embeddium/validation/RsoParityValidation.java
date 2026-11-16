package org.embeddedt.embeddium.validation;

import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.action.OptionResetAction;
import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import me.flashyreese.mods.reeses_sodium_options.client.gui.widget.BaseWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.api.options.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.ConfigManager;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class RsoParityValidation {
    private static final ResourceLocation ID =
            new ResourceLocation("embeddium_validation", "metadata");
    private static boolean value;
    private static int saves;

    public static void install() {
        org.embeddedt.embeddium.api.OptionGUIConstructionEvent.BUS.addListener(
                event -> {
                    var storage =
                            new me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<
                                    Object>() {
                                public Object getData() {
                                    return this;
                                }

                                public void save() {
                                    saves++;
                                }
                            };
                    var option =
                            me.jellysquid.mods.sodium.client.gui.options.OptionImpl.createBuilder(
                                            boolean.class, storage)
                                    .setId(
                                            org.embeddedt.embeddium.client.gui.options
                                                    .OptionIdentifier.create(
                                                    ID.getNamespace(), ID.getPath(), boolean.class))
                                    .setName(Component.literal("Metadata fixture"))
                                    .setTooltip(Component.literal("Initial tooltip"))
                                    .setBinding((state, next) -> value = next, state -> value)
                                    .setControl(
                                            me.jellysquid.mods.sodium.client.gui.options.control
                                                            .TickBoxControl
                                                    ::new)
                                    .build();
                    new RsoOptionMetadata<Boolean>()
                            .defaultValue(() -> true)
                            .tooltip(
                                    () ->
                                            Component.literal(
                                                    option.getValue()
                                                            ? "pending:on"
                                                            : "pending:off"))
                            .enabled(
                                    () ->
                                            RsoOptionMetadata.pendingValue(
                                                                    new ResourceLocation(
                                                                            "minecraft",
                                                                            "brightness"),
                                                                    Integer.class)
                                                            .orElse(0)
                                                    > 50)
                            .register(ID);
                    new RsoModTheme(0xFF123456, 0xFFABCDEF, 0xFF010203).register(ID.getNamespace());
                    var group =
                            me.jellysquid.mods.sodium.client.gui.options.OptionGroup.createBuilder()
                                    .add(option)
                                    .build();
                    event.addPage(
                            new me.jellysquid.mods.sodium.client.gui.options.OptionPage(
                                    org.embeddedt.embeddium.client.gui.options.OptionIdentifier
                                            .create(ID.getNamespace(), "metadata_page"),
                                    Component.literal("Metadata"),
                                    com.google.common.collect.ImmutableList.of(group)));
                });
    }

    public static void verify(Minecraft mc, SodiumVideoOptionsScreen screen) {
        var options =
                ConfigManager.CONFIG.getModOptions().stream()
                        .flatMap(mod -> mod.pages().stream())
                        .flatMap(page -> page.groups().stream())
                        .flatMap(group -> group.options().stream())
                        .toList();
        var brightness =
                (IntegerOption)
                        options.stream()
                                .filter(
                                        option ->
                                                option.id.toString().equals("minecraft:brightness"))
                                .findFirst()
                                .orElseThrow();
        var target =
                (BooleanOption)
                        options.stream()
                                .filter(option -> option.id.equals(ID))
                                .findFirst()
                                .orElseThrow();
        int initial = brightness.getValidatedValue();
        boolean before = value;
        brightness.modifyValue(70);
        RsoValidation.require(
                target.isEnabled(), "Pending dependency did not enable the legacy option");
        RsoValidation.require(
                target.getTooltip().getString().equals("pending:off"),
                "Initial dynamic tooltip changed");
        RsoValidation.require(
                OptionResetAction.canReset(target),
                "Registered legacy default did not enable reset");
        OptionResetAction.resetToDefault(target);
        RsoValidation.require(
                target.getValidatedValue()
                        && target.getTooltip().getString().equals("pending:on")
                        && !value,
                "Live tooltip or unapplied edit semantics differ");
        ConfigManager.CONFIG.applyAllOptions();
        RsoValidation.require(value && saves > 0, "Legacy metadata transaction was not saved");
        var theme =
                ConfigManager.CONFIG.getModOptions().stream()
                        .filter(mod -> mod.configId().equals(ID.getNamespace()))
                        .findFirst()
                        .orElseThrow()
                        .theme();
        RsoValidation.require(
                theme.theme == 0xFF123456
                        && theme.themeLighter == 0xFFABCDEF
                        && theme.themeDarker == 0xFF010203,
                "Per-mod theme override was lost");
        target.modifyValue(before);
        brightness.modifyValue(initial);
        ConfigManager.CONFIG.applyAllOptions();
        var probe = new ProbeScreen();
        mc.setScreen(probe);
        RsoValidation.require(
                probe.mouseScrolled(20, 20, 3.25, -1.5) && probe.dx == 3.25 && probe.dy == -1.5,
                "Root screen lost horizontal scroll data");
        mc.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
        probe.setFocused(probe.first);
        probe.first.setFocused(true);
        probe.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        RsoValidation.require(probe.getFocused() == probe.second, "Forward Tab focus changed");
        probe.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
        RsoValidation.require(
                probe.getFocused() == probe.first, "Shift+Tab did not use event modifiers");
        probe.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
        RsoValidation.require(probe.getFocused() == probe.second, "Backward Tab wrap changed");
        mc.setScreen(screen);
        System.out.println(
                "PASS RSO parity: horizontal event dispatch, modifier-based Tab wrap, live"
                    + " metadata/defaults, legacy standard rows, theme overrides");
    }

    private static final class ProbeScreen extends RsoScreen {
        BaseWidget first, second;
        double dx, dy;

        ProbeScreen() {
            super(Component.literal("RSO event fixture"));
        }

        protected void init() {
            first =
                    addRenderableWidget(
                            new BaseWidget(new LayoutBounds(0, 0, 100, 30)) {
                                public void extractRenderState(
                                        GuiGraphicsExtractor g, int x, int y, float delta) {}

                                public boolean mouseScrolled(
                                        double x, double y, double horizontal, double vertical) {
                                    dx = horizontal;
                                    dy = vertical;
                                    return true;
                                }
                            });
            second =
                    addRenderableWidget(
                            new BaseWidget(new LayoutBounds(0, 40, 100, 30)) {
                                public void extractRenderState(
                                        GuiGraphicsExtractor g, int x, int y, float delta) {}
                            });
        }
    }
}
