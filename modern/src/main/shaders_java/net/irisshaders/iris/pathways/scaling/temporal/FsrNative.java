// SPDX-License-Identifier: LGPL-3.0-or-later
package net.irisshaders.iris.pathways.scaling.temporal;

import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Optional, independently built JNI bridge; AMD's proprietary runtime is never part of the mod jar. */
public final class FsrNative {
    public static final String RUNTIME_NAME = "amd_fidelityfx_upscaler_dx12.dll";
    public static final String RUNTIME_SHA256 = "aad4fccccb29084a655f9a4f9f613bd22c8c03fb4a0f8840148cc6543b4ccace";
    private static boolean loaded;

    private FsrNative() { }

    public static void load() throws Exception {
        if (loaded) return;
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                || !System.getProperty("os.arch").equals("amd64")) {
            throw new UnsupportedOperationException("Temporal FSR currently requires Windows x64");
        }
        var caps = GL.getCapabilities();
        if (!caps.OpenGL45 || !caps.GL_EXT_memory_object_win32 || !caps.GL_EXT_semaphore_win32) {
            throw new UnsupportedOperationException("Temporal FSR requires OpenGL 4.5 and EXT memory/semaphore Win32 interop");
        }
        String override = System.getProperty("nocturna.fsr.bridge");
        Path library;
        if (override != null) {
            library = Path.of(override).toAbsolutePath();
        } else {
            byte[] bytes;
            try (var stream = FsrNative.class.getResourceAsStream("/native/windows-x64/nocturna_fsr.dll")) {
                if (stream == null) throw new IOException("The Nocturna FSR bridge is missing");
                bytes = stream.readAllBytes();
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            library = Path.of("config", "nocturna", "native", hash, "nocturna_fsr.dll").toAbsolutePath();
            Files.createDirectories(library.getParent());
            if (!Files.isRegularFile(library) || !MessageDigest.isEqual(bytes, Files.readAllBytes(library))) {
                Files.write(library, bytes);
            }
        }
        System.load(library.toString());
        loaded = true;
    }

    public static Path runtime() throws Exception {
        Path path = Path.of(System.getProperty("nocturna.fsr.runtime",
                Path.of("config", "nocturna", "fsr", RUNTIME_NAME).toString())).toAbsolutePath();
        if (!Files.isRegularFile(path)) throw new IOException("Optional AMD FSR runtime is not installed",
                new java.nio.file.NoSuchFileException(path.toString()));
        String actual;
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[65536];
            for (int size; (size = input.read(block)) != -1;) digest.update(block, 0, size);
            actual = HexFormat.of().formatHex(digest.digest());
        }
        if (!RUNTIME_SHA256.equals(actual)) throw new IOException("AMD FSR runtime hash mismatch",
                new IOException("Expected " + RUNTIME_SHA256 + "; found " + actual + " at " + path));
        return path;
    }

    public static native long create(String runtime, int width, int height, int outputWidth, int outputHeight, int family);
    /** Waits only for reuse of a command allocator from three frames ago; returns color/depth/motion/output GL IDs. */
    public static native int[] acquire(long context);
    public static native String provider(long context);
    public static native float[] jitter(long context, long frame);
    public static native void execute(long context, float jitterX, float jitterY, float milliseconds,
                                      float near, float far, float verticalFovRadians, float sharpness, boolean reset);
    public static native void close(long context);
}
