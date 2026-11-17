package org.embeddedt.embeddium.impl.build;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VersionTokenTest {
    @Test
    void java17ConstantAndInlinedAssignmentMatchProcessedModMetadata() throws Exception {
        var loader = getClass().getClassLoader();
        var version = new AtomicReference<String>();
        var inlined = new AtomicInteger();
        try (var input = loader.getResourceAsStream("org/embeddedt/embeddium/impl/Celeritas.class")) {
            assertNotNull(input);
            var reader = new ClassReader(input);
            assertEquals(61, reader.readUnsignedShort(6), "product must stay Java 17");
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                        String signature, Object value) {
                    if (name.equals("BUILD_VERSION")) version.set((String) value);
                    return null;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                          String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) {
                            if (value instanceof String text) {
                                assertFalse(text.contains("@{"), "unreplaced Java token");
                                if (text.equals(version.get())) inlined.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertNotNull(version.get());
        assertNotEquals("unspecified", version.get());
        assertTrue(version.get().matches("\\d+\\.\\d+\\.\\d+.*"), "computed semantic version required");
        assertFalse(version.get().contains("@{"));
        assertTrue(inlined.get() > 0, "javac must inline the replaced constant into initialization");
        try (var input = loader.getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(input);
            var metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("version = \"" + version.get() + "\""));
            assertTrue(metadata.contains("modId = \"embeddium\""));
            assertTrue(metadata.contains("displayName = \"Nocturna\""));
            assertTrue(metadata.contains("version = \"1.8.0+mc1.20.1\""));
            assertFalse(metadata.contains("@{"));
            assertFalse(metadata.contains("$mod_"));
        }
        try (var input = loader.getResourceAsStream("embeddium.mixins.json")) {
            assertNotNull(input);
            var json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("JAVA_17", json.getAsJsonObject().get("compatibilityLevel").getAsString());
        }
    }
}
