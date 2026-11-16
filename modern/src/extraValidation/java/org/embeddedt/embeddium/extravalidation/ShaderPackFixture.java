package org.embeddedt.embeddium.extravalidation;

import net.irisshaders.iris.Iris;

import java.nio.file.Files;

final class ShaderPackFixture {
    private static java.nio.file.Path configuration;
    private static byte[] previous;
    private static boolean existed;

    static void prepare() throws Exception {
        configuration =
                org.embeddedt.embeddium.impl.util.PlatformUtil.getConfigDir()
                        .resolve("embeddium-shaders.properties");
        existed = Files.exists(configuration);
        if (existed) previous = Files.readAllBytes(configuration);
        Iris.getIrisConfig().setShadersEnabled(false);
        Iris.getIrisConfig().save();
        Iris.reload();
    }

    static void restore() throws Exception {
        if (configuration == null) return;
        if (existed) Files.write(configuration, previous);
        else Files.deleteIfExists(configuration);
    }

    static void enable() throws Exception {
        var shaders =
                Iris.getShaderpacksDirectory()
                        .resolve("nocturna-extra-validation")
                        .resolve("shaders");
        Files.createDirectories(shaders);
        Files.writeString(
                shaders.resolve("gbuffers_basic.vsh"),
                "#version 120\n"
                    + "varying vec4 color;void main(){gl_Position=ftransform();color=gl_Color;}\n");
        Files.writeString(
                shaders.resolve("gbuffers_basic.fsh"),
                "#version 120\n"
                        + "varying vec4 color;/* DRAWBUFFERS:0 */\n"
                        + "void main(){gl_FragData[0]=color;}\n");
        Files.writeString(
                shaders.resolve("gbuffers_terrain.vsh"),
                "#version 120\n"
                    + "varying vec2 texcoord;varying vec4 color;void"
                    + " main(){gl_Position=ftransform();texcoord=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;color=gl_Color;}\n");
        Files.writeString(
                shaders.resolve("gbuffers_terrain.fsh"),
                "#version 120\n"
                        + "uniform sampler2D texture;varying vec2 texcoord;varying vec4 color;/*"
                        + " DRAWBUFFERS:0 */\n"
                        + "void main(){gl_FragData[0]=texture2D(texture,texcoord)*color;}\n");
        Files.writeString(
                shaders.resolve("final.vsh"),
                "#version 120\n"
                        + "varying vec2 texcoord;void"
                        + " main(){gl_Position=ftransform();texcoord=gl_MultiTexCoord0.xy;}\n");
        Files.writeString(
                shaders.resolve("final.fsh"),
                "#version 120\n"
                        + "uniform sampler2D colortex0;varying vec2 texcoord;void"
                        + " main(){gl_FragColor=texture2D(colortex0,texcoord);}\n");
        Iris.getIrisConfig().setShaderPackName("nocturna-extra-validation");
        Iris.getIrisConfig().setShadersEnabled(true);
        Iris.getIrisConfig().save();
        Iris.reload();
        Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
        ExtraValidation.require(
                me.flashyreese.mods.sodiumextra.compat.IrisCompat.isShaderPackInUse(),
                "shader fixture did not activate Iris");
    }
}
