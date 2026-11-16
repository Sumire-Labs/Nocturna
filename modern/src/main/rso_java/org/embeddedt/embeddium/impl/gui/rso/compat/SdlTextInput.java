package org.embeddedt.embeddium.impl.gui.rso.compat;

import com.sun.jna.*;

import org.lwjgl.glfw.*;
import org.lwjgl.system.Platform;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** SDL's public text-input API on the existing GLFW window. No renderer/context is created. */
final class SdlTextInput {
    interface Host {
        long glfwWindow();

        double coordinateScale();

        Path cacheDirectory();

        void acceptText(String text);

        void acceptKey(int scan, int raw, int action, int modifiers, int keycode);
    }

    private final Host host;
    private final NativeLibrary library;
    private final Memory event = new Memory(128), area = new Memory(16);
    private final Pointer window;
    private final int windowId;
    private final CocoaWindow cocoa;
    private boolean closed;

    SdlTextInput(Host host) {
        this.host = host;
        library = load(host.cacheDirectory());
        voidCall("SDL_SetMainReady");
        if (Platform.get() == Platform.LINUX) bool("SDL_SetHint", "SDL_VIDEO_DRIVER", "x11");
        bool("SDL_SetHint", "SDL_NO_SIGNAL_HANDLERS", "1");
        require(bool("SDL_InitSubSystem", 0x20), "initialize SDL video");
        int properties = integer("SDL_CreateProperties");
        Pointer created = null;
        CocoaWindow cocoaWindow = null;
        try {
            require(
                    bool(
                            "SDL_SetBooleanProperty",
                            properties,
                            "SDL.window.create.external_graphics_context",
                            (byte) 1),
                    "set external graphics ownership");
            long glfw = host.glfwWindow();
            switch (Platform.get()) {
                case WINDOWS ->
                        require(
                                bool(
                                        "SDL_SetPointerProperty",
                                        properties,
                                        "SDL.window.create.win32.hwnd",
                                        new Pointer(GLFWNativeWin32.glfwGetWin32Window(glfw))),
                                "wrap HWND");
                case LINUX ->
                        require(
                                bool(
                                        "SDL_SetNumberProperty",
                                        properties,
                                        "SDL.window.create.x11.window",
                                        GLFWNativeX11.glfwGetX11Window(glfw)),
                                "wrap X11/XWayland window");
                case MACOSX -> {
                    Pointer handle = new Pointer(GLFWNativeCocoa.glfwGetCocoaWindow(glfw));
                    cocoaWindow = new CocoaWindow(handle);
                    require(
                            bool(
                                    "SDL_SetPointerProperty",
                                    properties,
                                    "SDL.window.create.cocoa.window",
                                    handle),
                            "wrap NSWindow");
                }
            }
            created =
                    function("SDL_CreateWindowWithProperties")
                            .invokePointer(new Object[] {properties});
            require(created != null, "wrap GLFW window");
        } catch (Throwable failure) {
            voidCall("SDL_QuitSubSystem", 0x20);
            throw failure;
        } finally {
            voidCall("SDL_DestroyProperties", properties);
        }
        window = created;
        cocoa = cocoaWindow;
        windowId = integer("SDL_GetWindowID", window);
    }

    void start() {
        if (cocoa != null) cocoa.rememberResponder();
        require(bool("SDL_StartTextInput", window), "start text input");
    }

    void stop() {
        if (closed) return;
        require(bool("SDL_StopTextInput", window), "stop text input");
        voidCall("SDL_FlushEvents", 0x302, 0x303);
        if (cocoa != null) cocoa.restoreResponder();
    }

    void area(int x, int y, int x2, int y2) {
        double scale = host.coordinateScale();
        int left = (int) (x * scale), top = (int) (y * scale);
        area.setInt(0, left);
        area.setInt(4, top);
        area.setInt(8, Math.max(1, (int) (x2 * scale) - left));
        area.setInt(12, Math.max(1, (int) (y2 * scale) - top));
        require(bool("SDL_SetTextInputArea", window, area, 0), "position text input");
    }

    void prepare() {
        if (!closed) voidCall("SDL_PumpEvents");
    }

    void pump() {
        if (closed) return;
        while (!closed && integer("SDL_PeepEvents", event, 1, 2, 0, 0xffff) > 0) {
            int type = event.getInt(0);
            if (event.getInt(16) != windowId) continue;
            if (type == 0x303) {
                Pointer text = event.getPointer(Native.POINTER_SIZE == 8 ? 24 : 20);
                if (text != null) host.acceptText(text.getString(0, "UTF-8"));
            } else if (type == 0x300 || type == 0x301) {
                host.acceptKey(
                        event.getInt(24),
                        event.getShort(34) & 0xffff,
                        type == 0x301 ? 0 : event.getByte(37) != 0 ? 2 : 1,
                        event.getShort(32) & 0xffff,
                        event.getInt(28));
            }
        }
    }

    int modifiers() {
        return modifiers(integer("SDL_GetModState"));
    }

    static int modifiers(int sdl) {
        return ((sdl & 3) != 0 ? 1 : 0)
                | ((sdl & 0xC0) != 0 ? 2 : 0)
                | ((sdl & 0x300) != 0 ? 4 : 0)
                | ((sdl & 0xC00) != 0 ? 8 : 0)
                | ((sdl & 0x2000) != 0 ? 16 : 0)
                | ((sdl & 0x1000) != 0 ? 32 : 0);
    }

    boolean cocoa() {
        return cocoa != null;
    }

    Pointer handle() {
        return window;
    }

    NativeLibrary library() {
        return library;
    }

    void close() {
        if (closed) return;
        stop();
        closed = true;
        voidCall("SDL_DestroyWindow", window);
        if (cocoa != null) cocoa.restoreWindowChain();
        voidCall("SDL_QuitSubSystem", 0x20);
    }

    private Function function(String name) {
        return library.getFunction(name);
    }

    private int integer(String name, Object... args) {
        return function(name).invokeInt(args);
    }

    private boolean bool(String name, Object... args) {
        return (integer(name, args) & 255) != 0;
    }

    private void voidCall(String name, Object... args) {
        function(name).invokeVoid(args);
    }

    private void require(boolean success, String action) {
        if (!success) {
            Pointer message = function("SDL_GetError").invokePointer(new Object[0]);
            throw new IllegalStateException(
                    "Could not "
                            + action
                            + ": "
                            + (message == null
                                    ? "unknown SDL error"
                                    : message.getString(0, "UTF-8")));
        }
    }

    private static NativeLibrary load(Path cache) {
        String os =
                switch (Platform.get()) {
                    case WINDOWS -> "windows";
                    case LINUX -> "linux";
                    case MACOSX -> "macos";
                };
        String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String arch =
                switch (architecture) {
                    case "aarch64", "arm64" -> "arm64";
                    case "x86_64", "amd64" -> "x64";
                    default ->
                            throw new IllegalStateException(
                                    "Unsupported SDL input architecture: " + architecture);
                };
        String name =
                switch (Platform.get()) {
                    case WINDOWS -> "SDL3.dll";
                    case LINUX -> "libSDL3.so";
                    case MACOSX -> "libSDL3.dylib";
                };
        String resource =
                "/org/embeddedt/embeddium/impl/rso/sdl3/"
                        + os
                        + "/"
                        + arch
                        + "/org/lwjgl/sdl/"
                        + name;
        try (InputStream stream = SdlTextInput.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing SDL native resource: " + resource);
            byte[] contents = stream.readAllBytes();
            String hash =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
            Path dir = cache.resolve(hash);
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            if (!Files.exists(target)) {
                Path temporary = Files.createTempFile(dir, "sdl-", ".tmp");
                try {
                    Files.write(temporary, contents);
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return NativeLibrary.getInstance(
                    target.toAbsolutePath().toString(),
                    Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not load the SDL text-input backend", e);
        }
    }

    private static final class CocoaWindow {
        final NativeLibrary objc = NativeLibrary.getInstance("objc");
        final Pointer window, chain;
        Pointer responder;

        CocoaWindow(Pointer window) {
            this.window = window;
            chain = get("nextResponder");
        }

        Pointer selector(String name) {
            return objc.getFunction("sel_registerName").invokePointer(new Object[] {name});
        }

        Pointer get(String name) {
            return objc.getFunction("objc_msgSend")
                    .invokePointer(new Object[] {window, selector(name)});
        }

        void set(String name, Pointer value) {
            var f = objc.getFunction("objc_msgSend");
            Object[] args = {window, selector(name), value};
            if (name.equals("makeFirstResponder:")) f.invokeInt(args);
            else f.invokeVoid(args);
        }

        void rememberResponder() {
            responder = get("firstResponder");
        }

        void restoreResponder() {
            if (responder != null) {
                set("makeFirstResponder:", responder);
                responder = null;
            }
        }

        void restoreWindowChain() {
            set("setNextResponder:", chain);
        }
    }
}
