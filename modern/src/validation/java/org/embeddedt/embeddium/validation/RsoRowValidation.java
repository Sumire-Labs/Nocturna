package org.embeddedt.embeddium.validation;

import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.OptionRow;
import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import me.flashyreese.mods.reeses_sodium_options.client.gui.state.OptionStateStore;
import me.flashyreese.mods.reeses_sodium_options.client.gui.state.OptionsScreenUiState;
import me.flashyreese.mods.reeses_sodium_options.client.gui.theme.GuiTheme;
import me.flashyreese.mods.reeses_sodium_options.client.gui.theme.GuiThemes;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.lwjgl.glfw.GLFW;

public final class RsoRowValidation {
    private enum Choice {
        FIRST,
        DISABLED,
        LAST
    }

    public static void verify(Screen screen) throws ReflectiveOperationException {
        var factoryType =
                Class.forName(
                        "me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option.OptionRowFactory");
        var constructor =
                factoryType.getDeclaredConstructor(
                        Screen.class, ColorTheme.class, GuiTheme.class, OptionStateStore.class);
        constructor.setAccessible(true);
        var factory =
                constructor.newInstance(
                        screen,
                        ColorTheme.DEFAULT,
                        GuiThemes.fromSodium(ColorTheme.DEFAULT),
                        new OptionsScreenUiState());
        var create = factoryType.getDeclaredMethod("create", Option.class, LayoutBounds.class);
        create.setAccessible(true);
        var bounds = new LayoutBounds(0, 0, 300, 18);
        var bool = new BooleanOption(new ResourceLocation("validation", "boolean"), new Config());
        bool.getter = () -> false;
        bool.setter = value -> {};
        bool.defaultValue = state -> false;
        var row = (OptionRow) create.invoke(factory, bool, bounds);
        row.setFocused(true);
        RsoValidation.require(
                row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)) && bool.getValidatedValue(),
                "Boolean keyboard toggle failed");
        bool.enabled = () -> false;
        RsoValidation.require(
                !row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0))
                        && bool.getValidatedValue(),
                "Disabled boolean accepted input");
        var integer =
                new IntegerOption(new ResourceLocation("validation", "integer"), new Config());
        integer.getter = () -> 20;
        integer.setter = value -> {};
        integer.defaultValue = state -> 20;
        integer.stepped =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option
                        .SteppedValidator(0, 100, 5);
        row = (OptionRow) create.invoke(factory, integer, bounds);
        row.setFocused(true);
        row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0));
        RsoValidation.require(
                row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0))
                        && integer.getValidatedValue() == 25,
                "Slider keyboard step changed");
        RsoValidation.require(row.handleBackNavigation(), "Slider edit mode did not handle back");
        var enumeration =
                new EnumOption<Choice>(
                        new ResourceLocation("validation", "enum"), new Config(), Choice.class);
        enumeration.getter = () -> Choice.FIRST;
        enumeration.setter = value -> {};
        enumeration.defaultValue = state -> Choice.FIRST;
        enumeration.validator = value -> value == Choice.DISABLED ? Choice.FIRST : value;
        row = (OptionRow) create.invoke(factory, enumeration, bounds);
        row.setFocused(true);
        RsoValidation.require(
                row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0))
                        && enumeration.getValidatedValue() == Choice.LAST,
                "Cycling did not skip unavailable values");
        RsoValidation.require(
                row.mouseClicked(new MouseButtonEvent(150, 9, 1, 0), false)
                        && enumeration.getValidatedValue() == Choice.FIRST,
                "Reverse cycling failed");
        verifyLegacy(screen);
    }

    private static void verifyLegacy(Screen screen) {
        boolean[] stored = {false};
        int[] constructions = {0};
        var storage =
                new me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<Object>() {
                    public Object getData() {
                        return this;
                    }

                    public void save() {}
                };
        var legacy =
                me.jellysquid.mods.sodium.client.gui.options.OptionImpl.createBuilder(
                                boolean.class, storage)
                        .setName(
                                net.minecraft.network.chat.Component.literal(
                                        "Legacy custom control"))
                        .setTooltip(
                                net.minecraft.network.chat.Component.literal(
                                        "Native keyboard and moved geometry"))
                        .setBinding((state, value) -> stored[0] = value, state -> stored[0])
                        .setControl(
                                option ->
                                        new me.jellysquid.mods.sodium.client.gui.options.control
                                                        .Control<
                                                Boolean>() {
                                            public me.jellysquid.mods.sodium.client.gui.options
                                                                    .Option<
                                                            Boolean>
                                                    getOption() {
                                                return option;
                                            }

                                            public int getMaxWidth() {
                                                return 30;
                                            }

                                            public me.jellysquid.mods.sodium.client.gui.options
                                                                    .control.ControlElement<
                                                            Boolean>
                                                    createElement(
                                                            me.jellysquid.mods.sodium.client.util
                                                                            .Dim2i
                                                                    dim) {
                                                constructions[0]++;
                                                return new me.jellysquid.mods.sodium.client.gui
                                                                .options.control.ControlElement<
                                                        Boolean>(option, dim) {
                                                    public boolean keyPressed(
                                                            int key, int scan, int mods) {
                                                        if (key != GLFW.GLFW_KEY_SPACE
                                                                || !isFocused()) return false;
                                                        option.setValue(!option.getValue());
                                                        return true;
                                                    }

                                                    public boolean mouseClicked(
                                                            double x, double y, int button) {
                                                        if (button != 0 || !isMouseOver(x, y))
                                                            return false;
                                                        option.setValue(!option.getValue());
                                                        return true;
                                                    }
                                                };
                                            }
                                        })
                        .build();
        var nativeOption = org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge.toNew(legacy);
        var option =
                new StatefulOption<Boolean>(new ResourceLocation("validation", "legacy"), null);
        option.getter =
                () -> {
                    legacy.reset();
                    return legacy.getValue();
                };
        option.setter =
                value -> {
                    legacy.setValue(value);
                    legacy.applyChanges();
                };
        var control =
                new org.embeddedt.embeddium.impl.gui.rso.NativeControlAdapter(nativeOption, option);
        var element =
                control.createElement(
                        screen,
                        null,
                        new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i(
                                0, 0, 300, 18),
                        ColorTheme.DEFAULT);
        var client = net.minecraft.client.Minecraft.getInstance();
        var previousInputType = client.getLastInputType();
        client.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
        element.setFocused(true);
        boolean handled = element.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0));
        RsoValidation.require(handled, "Legacy custom control ignored the keyboard event");
        RsoValidation.require(
                option.getValidatedValue(),
                "Legacy custom value did not reach the RSO edit transaction; native="
                        + nativeOption.getValue()
                        + " old="
                        + legacy.getValue());
        RsoValidation.require(!stored[0], "Legacy keyboard control applied an uncommitted edit");
        option.reset();
        element.rso$setDimensions(
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i(
                        20, 40, 300, 18));
        RsoValidation.require(
                element.mouseClicked(new MouseButtonEvent(150, 49, 0, 0), false)
                        && option.getValidatedValue(),
                "Moved legacy control lost hit testing");
        option.apply();
        element.rso$setDimensions(
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i(
                        30, 60, 380, 18));
        RsoValidation.require(
                constructions[0] == 1 && element.isFocused(),
                "Resize recreated the legacy control or lost focus");
        RsoValidation.require(stored[0], "Legacy custom binding was not applied");
        client.setLastInputType(previousInputType);
    }
}
