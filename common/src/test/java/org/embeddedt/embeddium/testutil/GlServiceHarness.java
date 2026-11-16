package org.embeddedt.embeddium.testutil;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.taumc.celeritas.lwjgl.LWJGLService;

/** Runs production bytecode with only its external GL service replaced. */
public final class GlServiceHarness {
    private static final ThreadLocal<LWJGLService> CURRENT = new ThreadLocal<>();

    public static LWJGLService service() {
        return CURRENT.get();
    }

    public static void bind(LWJGLService service) {
        CURRENT.set(service);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Class<?> load(Class<?> originalClass) throws Exception {
        String original = Type.getInternalName(originalClass);
        String renamed = original + "_GlServiceHarness";
        var writer = new ClassWriter(0);
        var adapter = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals("org/taumc/celeritas/lwjgl/LWJGLServiceProvider")
                                && field.equals("LWJGL")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(GlServiceHarness.class),
                                    "service", "()Lorg/taumc/celeritas/lwjgl/LWJGLService;", false);
                        } else {
                            super.visitFieldInsn(opcode, owner, field, descriptor);
                        }
                    }
                };
            }
        };
        try (var input = originalClass.getResourceAsStream(originalClass.getSimpleName() + ".class")) {
            new ClassReader(input).accept(new ClassRemapper(adapter, new SimpleRemapper(original, renamed)), 0);
        }
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(originalClass.getClassLoader()) {
            Class<?> define() {
                return defineClass(renamed.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();
    }
}
