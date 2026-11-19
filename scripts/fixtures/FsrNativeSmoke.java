// SPDX-License-Identifier: LGPL-3.0-or-later
import net.irisshaders.iris.pathways.scaling.temporal.FsrNative;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;

/** Standalone Java 17 / real-driver smoke test, independent of Minecraft and shader-pack fixtures. */
public final class FsrNativeSmoke {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        long window = GLFW.glfwCreateWindow(64, 64, "FSR validation", 0, 0);
        if (window == 0) throw new AssertionError("GLFW context failed");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL=" + GL11C.glGetString(GL11C.GL_RENDERER) + " " + GL11C.glGetString(GL11C.GL_VERSION));
        try {
            FsrNative.load();
            String runtime = FsrNative.runtime().toString();
            expectInvalid(()->FsrNative.acquire(0));
            expectInvalid(()->FsrNative.provider(-1));
            expectInvalid(()->FsrNative.jitter(Long.MAX_VALUE,0));
            expectInvalid(()->FsrNative.close(Long.MAX_VALUE));
            FsrNative.close(0);
            int width = args.length > 0 ? Integer.parseInt(args[0]) : 640;
            int height = args.length > 1 ? Integer.parseInt(args[1]) : 360;
            int firstFamily = args.length > 2 ? Integer.parseInt(args[2]) : 2;
            int lastFamily = args.length > 2 ? firstFamily : 4;
            for (int family = firstFamily; family <= lastFamily; family++) {
                long context = FsrNative.create(runtime, width, height, width * 2, height * 2, family);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    System.out.println("Provider=" + FsrNative.provider(context));
                    expectInvalid(()->FsrNative.execute(context,0,0,16,Float.NaN,256,1.2f,0,true));
                    var threadFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
                    var wrongThread=new Thread(()->{
                        expectInvalid(()->FsrNative.acquire(context));
                        expectInvalid(()->FsrNative.jitter(context,0));
                        expectInvalid(()->FsrNative.close(context));
                    },"fsr-wrong-context-validation");
                    wrongThread.setUncaughtExceptionHandler((thread,failure)->threadFailure.set(failure));
                    wrongThread.start();wrongThread.join();
                    if(threadFailure.get()!=null)throw new AssertionError("Wrong-context call was not rejected",threadFailure.get());
                    FloatBuffer color = stack.floats(0.25f, 0.5f, 0.75f, 1);
                    FloatBuffer depth = stack.floats(0.5f);
                    FloatBuffer motion = stack.floats(0, 0);
                    FloatBuffer pixels = org.lwjgl.system.MemoryUtil.memAllocFloat(width * height * 16);
                    try {
                        for (int i = 0; i < 12; i++) {
                            int[] textures = FsrNative.acquire(context);
                            GL44C.glClearTexImage(textures[0], 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, color);
                            GL44C.glClearTexImage(textures[1], 0, GL11C.GL_RED, GL11C.GL_FLOAT, depth);
                            GL44C.glClearTexImage(textures[2], 0, GL30C.GL_RG, GL11C.GL_FLOAT, motion);
                            float[] jitter = FsrNative.jitter(context, i);
                            if (jitter[0] == 0 && jitter[1] == 0) throw new AssertionError("Null jitter");
                            FsrNative.execute(context, jitter[0], jitter[1], 16.6667f, 0.05f, 256, 1.2f, 0, i == 0);
                            GL45C.glGetTextureImage(textures[3], 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, pixels);
                            int p = (height * width * 2 + width) * 4;
                            for (int c = 0; c < 3; c++) {
                                float actual = pixels.get(p + c);
                                if (!Float.isFinite(actual) || Math.abs(actual - color.get(c)) > 0.03f)
                                    throw new AssertionError("FSR " + family + " frame " + i + " channel " + c + ": " + actual);
                            }
                            if (GL11C.glGetError() != GL11C.GL_NO_ERROR) throw new AssertionError("GL error");
                        }
                        System.out.println("PASS FSR " + family + ": 12 real SDK dispatches; ring reuse; finite constant-color pixels");
                    } finally { org.lwjgl.system.MemoryUtil.memFree(pixels); }
                } finally { FsrNative.close(context); }
                expectInvalid(()->FsrNative.acquire(context));
                expectInvalid(()->FsrNative.jitter(context,0));
                expectInvalid(()->FsrNative.provider(context));
                expectInvalid(()->FsrNative.close(context));
                System.out.println("PASS invalid/closed native handles and invalid dispatch rejection");
            }
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void expectInvalid(Runnable operation) {
        try {operation.run();throw new AssertionError("Invalid native call was accepted");}
        catch(IllegalStateException expected) { }
    }
}
