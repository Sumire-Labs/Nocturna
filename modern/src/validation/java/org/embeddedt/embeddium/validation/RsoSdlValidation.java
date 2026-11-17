package org.embeddedt.embeddium.validation;

import com.sun.jna.*;

import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource;
import org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge;
import org.embeddedt.embeddium.impl.mixin.core.gui.RsoKeyboardInvoker;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public final class RsoSdlValidation {
    public static void verify(Minecraft mc, SodiumVideoOptionsScreen screen) throws Exception {
        screen.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
        var field = TextInputBridge.class.getDeclaredField("backend");
        field.setAccessible(true);
        Object backend = field.get(null);
        var library = (NativeLibrary) NocturnaOptionsSource.field(backend, "library");
        Pointer window = (Pointer) NocturnaOptionsSource.field(backend, "window");
        int id = library.getFunction("SDL_GetWindowID").invokeInt(new Object[] {window});
        int oldMods = library.getFunction("SDL_GetModState").invokeInt(new Object[0]);
        try {
            ((RsoKeyboardInvoker) mc.keyboardHandler)
                    .nocturna$charTyped(mc.getWindow().getWindow(), 'x', 0);
            RsoValidation.require(
                    screen.rso$getSearchTextField().getText().isEmpty(),
                    "Duplicate GLFW commit was not suppressed");
            commit(library, id, "画質🌧");
            TextInputBridge.pumpEvents();
            RsoValidation.require(
                    screen.rso$getSearchTextField().getText().equals("画質🌧"),
                    "SDL UTF-8 did not pass through Minecraft/Forge character dispatch");
            library.getFunction("SDL_SetModState").invokeVoid(new Object[] {0x40});
            Memory event = new Memory(128);
            event.clear();
            event.setInt(0, 0x300);
            event.setInt(16, id);
            event.setInt(24, 4);
            event.setInt(28, 97);
            event.setShort(32, (short) 0x40);
            event.setByte(36, (byte) 1);
            library.getFunction("SDL_PushEvent").invokeInt(new Object[] {event});
            TextInputBridge.pumpEvents();
            library.getFunction("SDL_SetModState").invokeVoid(new Object[] {0});
            commit(library, id, "雨");
            TextInputBridge.pumpEvents();
            RsoValidation.require(
                    screen.rso$getSearchTextField().getText().equals("雨"),
                    "SDL logical Ctrl+A did not select the existing text");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        } finally {
            library.getFunction("SDL_SetModState").invokeVoid(new Object[] {oldMods});
        }
        System.out.println(
                "PASS SDL-to-Minecraft/Forge dispatch: duplicate suppression, complete Unicode,"
                        + " logical editing shortcuts");
    }

    static void commitToFocusedNativeWidget(Minecraft mc, String text) throws Exception {
        var field = TextInputBridge.class.getDeclaredField("backend");
        field.setAccessible(true);
        Object backend = field.get(null);
        var library = (NativeLibrary) NocturnaOptionsSource.field(backend, "library");
        Pointer window = (Pointer) NocturnaOptionsSource.field(backend, "window");
        RsoValidation.require(
                (library.getFunction("SDL_TextInputActive").invokeInt(new Object[] {window}) & 255)
                        != 0,
                "Native text field did not activate SDL/IME input");
        int id = library.getFunction("SDL_GetWindowID").invokeInt(new Object[] {window});
        commit(library, id, text);
    }

    private static void commit(NativeLibrary library, int id, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Memory payload = new Memory(bytes.length + 1);
        payload.write(0, bytes, 0, bytes.length);
        payload.setByte(bytes.length, (byte) 0);
        Memory event = new Memory(128);
        event.clear();
        event.setInt(0, 0x303);
        event.setInt(16, id);
        event.setPointer(Native.POINTER_SIZE == 8 ? 24 : 20, payload);
        RsoValidation.require(
                (library.getFunction("SDL_PushEvent").invokeInt(new Object[] {event}) & 255) != 0,
                "SDL rejected a text-input event");
        TextInputBridge.pumpEvents();
    }
}
