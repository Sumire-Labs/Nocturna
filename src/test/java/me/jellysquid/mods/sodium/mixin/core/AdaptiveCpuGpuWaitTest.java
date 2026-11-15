package me.jellysquid.mods.sodium.mixin.core;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import org.embeddedt.embeddium.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.*;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the compiled production methods, redirecting only their external GL, yield and game services. */
@Timeout(10)
public class AdaptiveCpuGpuWaitTest {
    private static Class<?> harnessClass;
    private Object harness;
    private LongArrayFIFOQueue fences;
    private Execution execution;

    @BeforeAll
    static void createHarness() throws Exception {
        MinecraftBootstrap.initialize();
        harnessClass = createHarness(true);
    }

    private static Class<?> createHarness(boolean emulateGpu) throws Exception {
        String original = Type.getInternalName(MinecraftClientMixin.class);
        String renamed = original + (emulateGpu ? "_AdaptiveWaitTestHarness" : "_NativeWaitTestHarness");
        var writer = new ClassWriter(0);
        var services = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        String hooks = Type.getInternalName(Hooks.class);
                        if (emulateGpu && owner.equals("org/lwjgl/opengl/GL32C") &&
                                List.of("glClientWaitSync", "glDeleteSync", "glFenceSync").contains(method)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, method, desc, false);
                        } else if (emulateGpu && owner.equals("java/lang/Thread") && method.equals("yield")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "yieldThread", desc, false);
                        } else if (owner.equals("net/minecraft/client/Minecraft") && method.equals("getInstance")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "getMinecraft", desc, false);
                        } else if (owner.equals("net/minecraft/client/Minecraft") && method.equals("getProfiler")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "getProfiler",
                                    "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/util/profiling/ProfilerFiller;", false);
                        } else if (owner.equals("me/jellysquid/mods/sodium/client/SodiumClientMod") && method.equals("options")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooks, "options", desc, false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, desc, itf);
                        }
                    }
                };
            }
        };
        try (var input = resource(original + ".class")) {
            new ClassReader(input).accept(new ClassRemapper(services, new SimpleRemapper(original, renamed)), 0);
        }
        return new HarnessLoader().define(renamed.replace('/', '.'), writer.toByteArray());
    }

    @BeforeEach
    void setUp() throws Exception {
        this.execution = new Execution();
        Hooks.CURRENT.set(this.execution);
        this.harness = harnessClass.getConstructor().newInstance();
        var field = harnessClass.getDeclaredField("fences");
        field.setAccessible(true);
        this.fences = (LongArrayFIFOQueue) field.get(this.harness);
    }

    @AfterEach
    void clearHooks() {
        Hooks.CURRENT.remove();
    }

    @Test
    void doesNotWaitAtOrBelowTheConfiguredLimit() throws Throwable {
        for (int size : new int[] {0, 1, 3}) {
            this.fences.clear();
            this.execution.calls.clear();
            for (int i = 0; i < size; i++) this.fences.enqueue(i + 1);
            frame("preRender");
            assertEquals(List.of(push(), pop()), this.execution.calls);
            assertEquals(size, this.fences.size());
        }
    }

    @Test
    void eitherSuccessStatusDeletesTheFenceWithoutYielding() throws Throwable {
        for (int result : new int[] {GL32C.GL_ALREADY_SIGNALED, GL32C.GL_CONDITION_SATISFIED}) {
            this.fences.clear();
            this.execution.calls.clear();
            pending(1, 10, 11);
            results(result);
            frame("preRender");
            assertEquals(List.of(push(), waitCall(10, 1000), delete(10), pop()), this.execution.calls);
            assertEquals(11, this.fences.firstLong());
        }
    }

    @Test
    void retriesShortTimeoutsBeforeDeletingASatisfiedFence() throws Throwable {
        pending(1, 10, 11);
        results(GL32C.GL_TIMEOUT_EXPIRED, GL32C.GL_TIMEOUT_EXPIRED, GL32C.GL_CONDITION_SATISFIED);
        frame("preRender");
        assertEquals(List.of(push(), waitCall(10, 1000), waitCall(10, 1000),
                waitCall(10, 1000), delete(10), pop()), this.execution.calls);
    }

    @Test
    void theFortiethShortWaitCanFinishWithoutYielding() throws Throwable {
        pending(1, 10, 11);
        timeouts(39);
        results(GL32C.GL_ALREADY_SIGNALED);
        frame("preRender");
        var expected = shortPhase(10, 40);
        expected.add(delete(10));
        expected.add(pop());
        assertEquals(expected, this.execution.calls);
    }

    @Test
    void yieldsBeforeEachLongerWaitAfterFortyShortTimeouts() throws Throwable {
        pending(1, 10, 11);
        timeouts(42);
        results(GL32C.GL_CONDITION_SATISFIED);
        frame("preRender");
        var expected = shortPhase(10, 40);
        for (int i = 0; i < 3; i++) {
            expected.add(yieldCall());
            expected.add(waitCall(10, 10_000));
        }
        expected.add(delete(10));
        expected.add(pop());
        assertEquals(expected, this.execution.calls);
    }

    @Test
    void drainsOnlyExcessFencesInFifoOrder() throws Throwable {
        pending(2, 20, 21, 22, 23, 24);
        results(GL32C.GL_ALREADY_SIGNALED, GL32C.GL_CONDITION_SATISFIED,
                GL32C.GL_TIMEOUT_EXPIRED, GL32C.GL_ALREADY_SIGNALED);
        frame("preRender");
        assertEquals(List.of(push(), waitCall(20, 1000), delete(20), waitCall(21, 1000), delete(21),
                waitCall(22, 1000), waitCall(22, 1000), delete(22), pop()), this.execution.calls);
        assertEquals(2, this.fences.size());
        assertEquals(23, this.fences.dequeueLong());
        assertEquals(24, this.fences.dequeueLong());
    }

    @Test
    void zeroRenderAheadLimitWaitsForAllQueuedFences() throws Throwable {
        pending(0, 20, 21);
        results(GL32C.GL_ALREADY_SIGNALED, GL32C.GL_ALREADY_SIGNALED);
        frame("preRender");
        assertEquals(List.of(push(), waitCall(20, 1000), delete(20),
                waitCall(21, 1000), delete(21), pop()), this.execution.calls);
        assertTrue(this.fences.isEmpty());
    }

    @Test
    void shortWaitFailurePreservesTheUpstreamExceptionAndCleanupBehavior() {
        pending(1, 30, 31);
        results(GL32C.GL_WAIT_FAILED);
        var failure = assertThrows(RuntimeException.class, () -> frame("preRender"));
        assertEquals("Failed to wait on fence object", failure.getMessage());
        assertEquals(List.of(push(), waitCall(30, 1000)), this.execution.calls);
        assertEquals(1, this.fences.size());
        assertEquals(31, this.fences.firstLong());
    }

    @Test
    void yieldedWaitFailureDoesNotDeleteTheFailedFenceOrPopTheProfiler() {
        pending(1, 30, 31);
        timeouts(40);
        results(GL32C.GL_WAIT_FAILED);
        var failure = assertThrows(RuntimeException.class, () -> frame("preRender"));
        assertEquals("Failed to wait on fence object", failure.getMessage());
        var expected = shortPhase(30, 40);
        expected.add(yieldCall());
        expected.add(waitCall(30, 10_000));
        assertEquals(expected, this.execution.calls);
        assertEquals(31, this.fences.firstLong());
    }

    @Test
    void reevaluatesTheRenderAheadLimitBetweenFences() throws Throwable {
        pending(1, 10, 11, 12, 13);
        this.execution.limitAfterDelete = 3;
        results(GL32C.GL_ALREADY_SIGNALED);
        frame("preRender");
        assertEquals(List.of(push(), waitCall(10, 1000), delete(10), pop()), this.execution.calls);
        assertEquals(3, this.fences.size());
    }

    @Test
    void postRenderEnqueuesTheNewFenceForTheNextFrame() throws Throwable {
        pending(0);
        this.execution.createdFence = 77;
        frame("postRender");
        assertEquals(List.of(create()), this.execution.calls);
        assertEquals(77, this.fences.firstLong());
        results(GL32C.GL_ALREADY_SIGNALED);
        frame("preRender");
        assertEquals(List.of(create(), push(), waitCall(77, 1000), delete(77), pop()), this.execution.calls);
        assertTrue(this.fences.isEmpty());
    }

    @Test
    void failedFenceCreationDoesNotEnqueueAZeroHandle() {
        this.execution.createdFence = 0;
        var failure = assertThrows(RuntimeException.class, () -> frame("postRender"));
        assertEquals("Failed to create fence object", failure.getMessage());
        assertEquals(List.of(create()), this.execution.calls);
        assertTrue(this.fences.isEmpty());
    }

    @Test
    void removesTheCompiledIntelStartupCheckWhileKeepingAdapterProbingAndRtssChecks() throws Exception {
        var loader = getClass().getClassLoader();
        assertNull(loader.getResource("me/jellysquid/mods/sodium/client/compatibility/checks/EarlyDriverScanner.class"));
        var config = Class.forName("me.jellysquid.mods.sodium.client.compatibility.checks.Configuration", false, loader);
        assertThrows(NoSuchFieldException.class, () -> config.getDeclaredField("WIN32_DRIVER_INTEL_GEN7"));
        assertNotNull(config.getDeclaredField("WIN32_RTSS_HOOKS"));
        var calls = new ArrayList<String>();
        try (var input = resource("me/jellysquid/mods/sodium/client/SodiumPreLaunch.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("onPreLaunch")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            calls.add(owner + "." + method);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(List.of("net/minecraftforge/fml/loading/FMLLoader.getDist",
                "me/jellysquid/mods/sodium/client/compatibility/environment/probe/GraphicsAdapterProbe.findAdapters",
                "me/jellysquid/mods/sodium/client/compatibility/workarounds/Workarounds.init"), calls);
    }

    @Test
    @Tag("opengl")
    void createsAndWaitsForRealGpuFencesInAHiddenContext() throws Throwable {
        var errors = GLFWErrorCallback.createPrint(System.err);
        var previousCallback = GLFW.glfwSetErrorCallback(errors);
        long window = MemoryUtil.NULL;
        try {
            assertTrue(GLFW.glfwInit(), "GLFW initialization");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            window = GLFW.glfwCreateWindow(64, 64, "Embeddium fence test", MemoryUtil.NULL, MemoryUtil.NULL);
            assertNotEquals(MemoryUtil.NULL, window);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Fence test OpenGL: " + GLContextInfo.create());
            this.harness = createHarness(false).getConstructor().newInstance();
            var field = this.harness.getClass().getDeclaredField("fences");
            field.setAccessible(true);
            this.fences = (LongArrayFIFOQueue) field.get(this.harness);
            this.execution.options.advanced.cpuRenderAheadLimit = 1;
            for (int frame = 0; frame < 4; frame++) {
                GL11C.glClearColor(frame / 4.0f, 0, 0, 1);
                GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                frame("postRender");
            }
            assertEquals(4, this.fences.size());
            frame("preRender");
            assertEquals(1, this.fences.size());
            this.execution.options.advanced.cpuRenderAheadLimit = 0;
            frame("preRender");
            assertTrue(this.fences.isEmpty());
            assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
            assertEquals(List.of(push(), pop(), push(), pop()), this.execution.calls);
        } finally {
            if (window != MemoryUtil.NULL) {
                while (!this.fences.isEmpty()) GL32C.glDeleteSync(this.fences.dequeueLong());
                GL.setCapabilities(null);
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            GLFW.glfwSetErrorCallback(previousCallback);
            errors.free();
        }
    }

    private void pending(int limit, long... handles) {
        this.execution.options.advanced.cpuRenderAheadLimit = limit;
        for (long handle : handles) this.fences.enqueue(handle);
    }

    private void results(int... statuses) {
        for (int status : statuses) this.execution.results.add(status);
    }

    private void timeouts(int count) {
        for (int i = 0; i < count; i++) results(GL32C.GL_TIMEOUT_EXPIRED);
    }

    private void frame(String name) throws Throwable {
        var method = this.harness.getClass().getDeclaredMethod(name, boolean.class, CallbackInfo.class);
        method.setAccessible(true);
        try {
            method.invoke(this.harness, false, new CallbackInfo("runTick", false));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static InputStream resource(String path) {
        var input = AdaptiveCpuGpuWaitTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return input;
    }

    private record Call(String operation, long handle, int flags, long timeout) { }
    private static Call push() { return new Call("push:wait_for_gpu", 0, 0, 0); }
    private static Call pop() { return new Call("pop", 0, 0, 0); }
    private static Call waitCall(long fence, long timeout) {
        return new Call("wait", fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
    }
    private static Call delete(long fence) { return new Call("delete", fence, 0, 0); }
    private static Call yieldCall() { return new Call("yield", 0, 0, 0); }
    private static Call create() { return new Call("create", 0, GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0); }
    private static ArrayList<Call> shortPhase(long fence, int count) {
        var calls = new ArrayList<Call>();
        calls.add(push());
        for (int i = 0; i < count; i++) calls.add(waitCall(fence, 1000));
        return calls;
    }

    private static final class Execution {
        final SodiumGameOptions options = new SodiumGameOptions();
        final ArrayDeque<Integer> results = new ArrayDeque<>();
        final List<Call> calls = new ArrayList<>();
        long createdFence = 99;
        Integer limitAfterDelete;
    }

    /** API-only substitutions. The fencing loops and branches remain the production bytecode. */
    public static final class Hooks {
        private static final ThreadLocal<Execution> CURRENT = new ThreadLocal<>();

        public static Minecraft getMinecraft() { return null; }
        public static SodiumGameOptions options() { return CURRENT.get().options; }
        public static ProfilerFiller getProfiler(Minecraft ignored) {
            return (ProfilerFiller) Proxy.newProxyInstance(ProfilerFiller.class.getClassLoader(),
                    new Class<?>[] {ProfilerFiller.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "push" -> CURRENT.get().calls.add(new Call("push:" + args[0], 0, 0, 0));
                            case "pop" -> CURRENT.get().calls.add(pop());
                            default -> throw new AssertionError("Unexpected profiler call: " + method.getName());
                        }
                        return null;
                    });
        }

        public static int glClientWaitSync(long fence, int flags, long timeout) {
            var state = CURRENT.get();
            state.calls.add(new Call("wait", fence, flags, timeout));
            assertFalse(state.results.isEmpty(), "Unexpected wait beyond the scheduled GPU responses");
            return state.results.removeFirst();
        }

        public static void glDeleteSync(long fence) {
            var state = CURRENT.get();
            state.calls.add(delete(fence));
            if (state.limitAfterDelete != null) {
                state.options.advanced.cpuRenderAheadLimit = state.limitAfterDelete;
                state.limitAfterDelete = null;
            }
        }

        public static long glFenceSync(int condition, int flags) {
            var state = CURRENT.get();
            state.calls.add(new Call("create", 0, condition, flags));
            return state.createdFence;
        }

        public static void yieldThread() { CURRENT.get().calls.add(yieldCall()); }
    }

    private static final class HarnessLoader extends ClassLoader {
        HarnessLoader() { super(AdaptiveCpuGpuWaitTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }
}
