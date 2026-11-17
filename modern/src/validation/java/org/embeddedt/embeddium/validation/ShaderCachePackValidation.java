package org.embeddedt.embeddium.validation;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.embeddedt.embeddium.impl.util.PlatformUtil;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/** A synthetic LGPL test fixture; no third-party shader pack is downloaded or redistributed. */
public final class ShaderCachePackValidation {
    private static final String NAME = "embeddium-cache-validation";
    private static final AtomicInteger compilations = new AtomicInteger();
    private static byte[] configuration;
    private static boolean existed;
    private static Path configPath;
    private static boolean binarySupported;

    public static void recordCompilation(String name) {
        compilations.incrementAndGet();
    }

    public static void prepare() throws Exception {
        binarySupported = ProgramBinaryCache.isSupported();
        configPath = PlatformUtil.getConfigDir().resolve("embeddium-shaders.properties");
        existed = Files.exists(configPath);
        if (existed) configuration = Files.readAllBytes(configPath);
        Path root = Iris.getShaderpacksDirectory().resolve(NAME).resolve("shaders");
        Files.createDirectories(root);
        Files.writeString(root.resolve("gbuffers_basic.vsh"), """
                #version 120
                varying vec4 color;
                void main(){gl_Position=ftransform(); color=gl_Color;}
                """);
        Files.writeString(root.resolve("gbuffers_basic.fsh"), """
                #version 120
                varying vec4 color;
                /* DRAWBUFFERS:0 */
                void main(){gl_FragData[0]=color;}
                """);
        Files.writeString(root.resolve("gbuffers_terrain.vsh"), """
                #version 120
                varying vec2 texcoord;
                varying vec4 color;
                void main(){gl_Position=ftransform(); texcoord=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy; color=gl_Color;}
                """);
        Files.writeString(root.resolve("gbuffers_terrain.fsh"), """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 color;
                /* DRAWBUFFERS:0 */
                void main(){gl_FragData[0]=texture2D(texture,texcoord)*color;}
                """);
        Files.writeString(root.resolve("final.vsh"), """
                #version 120
                varying vec2 texcoord;
                void main(){gl_Position=ftransform(); texcoord=gl_MultiTexCoord0.xy;}
                """);
        Files.writeString(root.resolve("final.fsh"), """
                #version 120
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                void main(){gl_FragColor=texture2D(colortex0,texcoord);}
                """);
        FismValidation.shaderFixture(root);
        Files.writeString(root.resolve("final.csh"), "#version 430\nlayout(local_size_x=1)in; const ivec3 workGroups=ivec3(1); void main(){}\n");
        var config = Iris.getIrisConfig();
        config.setShaderPackName(NAME); config.setShadersEnabled(true); config.setSaveCompiledShaders(true);
        compilations.set(0);
        Iris.reload();
        Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
        requirePipeline();
    }

    public static void reloadWarm() throws Exception {
        requirePipeline();
        if (compilations.get() == 0) throw new AssertionError("Cold pack did not compile any shaders");
        // Export jobs are asynchronous. Wait for their completion before asserting a fully warm reload.
        var field = net.irisshaders.iris.cache.ShaderCacheWorkers.class.getDeclaredField("inFlight");
        field.setAccessible(true);
        var active = (AtomicInteger) field.get(null);
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (active.get() != 0) {
            if (System.nanoTime() > deadline) throw new AssertionError("Pack cache writes timed out");
            Thread.sleep(5);
        }
        System.out.println("Cold validation pack compiled " + compilations.get() + " shader stages");
        compilations.set(0);
        RetainedProgramsValidation.beginWarmPack();
        Iris.reload();
        requirePipeline();
    }

    public static void verifyWarm() {
        requirePipeline();
        if (!binarySupported) {
            System.out.println("PASS full shader pack: source-compilation fallback on a driver without program binaries");
            return;
        }
        if (compilations.get() != 0) throw new AssertionError("Warm pack recompiled " + compilations.get() + " shader stages");
        System.out.println("PASS full shader pack warm reload: zero stage compiles, including terrain programs");
    }

    private static void requirePipeline() {
        if (Iris.getCurrentPack().isEmpty() || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline)) {
            throw new AssertionError("Validation shader pack did not create an Iris rendering pipeline");
        }
    }

    public static void restore() throws Exception {
        if (configPath == null) return;
        if (existed) Files.write(configPath, configuration);
        else Files.deleteIfExists(configPath);
        ProgramBinaryCache.setEnabled(false);
    }
}
