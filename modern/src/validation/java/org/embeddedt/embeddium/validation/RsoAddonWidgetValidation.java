package org.embeddedt.embeddium.validation;

import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/** Models addons which append ordinary Minecraft controls to the video-settings screen. */
final class RsoAddonWidgetValidation {
    static void verify(Minecraft mc, SodiumVideoOptionsScreen previous) throws Exception {
        var addon = new AddonScreen(previous);
        mc.setScreen(addon);
        addon.setFocused(addon.button);
        addon.button.setFocused(true);
        addon.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        RsoValidation.require(addon.clicks == 1, "Native addon button did not receive Enter");
        RsoValidation.require(
                addon.mouseClicked(addon.width - 4, 30, 0),
                "Native addon text field did not accept a mouse click");
        RsoSdlValidation.commitToFocusedNativeWidget(mc, "é");
        RsoValidation.require(
                addon.field.getValue().equals("é"),
                "Native addon text field did not receive Unicode");
        addon.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        addon.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
        addon.setFocused(addon.button);
        addon.button.setFocused(true);
        addon.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        addon.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
        mc.setScreen(previous);
        verifyUnavailableNativeInput(mc, previous);
        mc.setScreen(previous);
        System.out.println(
                "PASS ordinary Minecraft addon controls: activation, Unicode, Tab and reversible"
                    + " arrow focus");
    }

    private static void verifyUnavailableNativeInput(
            Minecraft mc, SodiumVideoOptionsScreen previous) throws Exception {
        mc.setScreen(previous.rso$previousScreen());
        var factory =
                org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge.class.getDeclaredField(
                        "backendFactory");
        factory.setAccessible(true);
        Object original = factory.get(null);
        int[] attempts = {0};
        factory.set(
                null,
                (java.util.function.Supplier<Object>)
                        () -> {
                            attempts[0]++;
                            throw new UnsatisfiedLinkError("intentional validation failure");
                        });
        try {
            var fallback = new AddonScreen(previous);
            mc.setScreen(fallback);
            RsoValidation.require(
                    !org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge.ownsKeyboard(),
                    "Failed SDL startup still captured keyboard input");
            fallback.mouseClicked(fallback.width - 4, 30, 0);
            ((org.embeddedt.embeddium.impl.mixin.core.gui.RsoKeyboardInvoker) mc.keyboardHandler)
                    .nocturna$charTyped(mc.getWindow().getWindow(), 'f', 0);
            RsoValidation.require(
                    fallback.field.getValue().equals("f"),
                    "Native library failure blocked ordinary keyboard input");
            RsoValidation.require(
                    attempts[0] == 1, "Failed native startup was retried for each focused widget");
            mc.setScreen(previous.rso$previousScreen());
        } finally {
            factory.set(null, original);
        }
        System.out.println("PASS unavailable native input: settings remain usable through GLFW");
    }

    private static final class AddonScreen extends SodiumVideoOptionsScreen {
        Button button;
        EditBox field;
        int clicks;

        AddonScreen(Screen previous) {
            super(previous);
        }

        protected void init() {
            super.init();
            button =
                    addRenderableWidget(
                            Button.builder(Component.literal("Addon"), button -> clicks++)
                                    .bounds(width - 10, 0, 10, 20)
                                    .build());
            field =
                    addRenderableWidget(
                            new EditBox(
                                    font, width - 10, 25, 10, 20, Component.literal("Addon text")));
        }
    }
}
