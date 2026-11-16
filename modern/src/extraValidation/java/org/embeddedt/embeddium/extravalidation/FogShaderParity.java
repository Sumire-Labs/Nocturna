package org.embeddedt.embeddium.extravalidation;

import com.mojang.blaze3d.pipeline.*;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;

/**
 * The actual terrain fog helper, evaluated as a fragment shader against independent channel values.
 */
final class FogShaderParity {
    static void verify() throws Exception {
        Class<?> transform =
                Class.forName("me.flashyreese.mods.sodiumextra.client.fog.FogShaderTransformer");
        var helper = transform.getDeclaredField("SHAPE_HELPER");
        helper.setAccessible(true);
        String vertex =
                "#version 330\n"
                    + "void main(){vec2"
                    + " uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(uv*2.0-1.0,0,1);}";
        String fragment =
                "#version 330\n"
                    + "vec2 v_SodiumExtraCylindricalDistance=vec2(0);float v_PlanarDistance=0;\n"
                        + helper.get(null)
                        + "\n"
                        + "out vec4 fragColor;void"
                        + " main(){fragColor=sodium_extra_linear_fog(vec4(0,0,0,1),vec2(96,24),vec4(1),vec2(0,96),vec2(64,128),1);}";
        Class<?> type =
                Class.forName("me.flashyreese.mods.sodiumextra.client.render.EffectGpuProgram");
        var constructor = type.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object program = constructor.newInstance(vertex, fragment);
        var draw = type.getDeclaredMethod("draw", RenderTarget.class, Map.class, boolean.class);
        draw.setAccessible(true);
        var target = new TextureTarget(8, 8, true, false);
        try (var transfer = new PixelTransferState()) {
            draw.invoke(program, target, Map.of(), false);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, target.frameBufferId);
            var pixel = MemoryUtil.memAlloc(4);
            try {
                GL33C.glReadPixels(4, 4, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                ExtraValidation.require(
                        Math.abs(Byte.toUnsignedInt(pixel.get(0)) - 128) <= 1,
                        "fog channels did not combine independently with linear interpolation");
            } finally {
                MemoryUtil.memFree(pixel);
            }
        } finally {
            ((AutoCloseable) program).close();
            target.destroyBuffers();
            net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        System.out.println(
                "PASS terrain shader combines independent environment/render channels with the"
                        + " pinned linear fade");
        vanilla();
    }

    private static void vanilla() throws Exception {
        var client = net.minecraft.client.Minecraft.getInstance();
        String fog;
        try (var reader =
                client.getResourceManager()
                        .openAsReader(
                                new net.minecraft.resources.ResourceLocation(
                                        "minecraft", "shaders/include/fog.glsl"))) {
            fog =
                    com.google.common.io.CharStreams.toString(reader)
                            .replaceAll("(?m)^#version[^\\n]*", "");
        }
        var bridge = org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge.STAGE;
        String vertex, fragment;
        try {
            bridge.set(com.mojang.blaze3d.shaders.Program.Type.VERTEX);
            vertex =
                    "#version 330\n"
                            + org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge.transform(
                                    fog)
                            + "\n"
                            + "void main(){float d=fog_distance(mat4(1),vec3(0,80,60),0);vec2"
                            + " uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(uv*2-1,d*0,1);}";
            bridge.set(com.mojang.blaze3d.shaders.Program.Type.FRAGMENT);
            fragment =
                    "#version 330\n"
                            + org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge.transform(
                                    fog)
                            + "\n"
                            + "out vec4 fragColor;void"
                            + " main(){fragColor=linear_fog(vec4(0,0,0,1),0,0,0,vec4(1));}";
        } finally {
            bridge.remove();
        }
        Class<?> type =
                Class.forName("me.flashyreese.mods.sodiumextra.client.render.EffectGpuProgram");
        var ctor = type.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object program = ctor.newInstance(vertex, fragment);
        var field = type.getDeclaredField("program");
        field.setAccessible(true);
        int id = field.getInt(program);
        var draw = type.getDeclaredMethod("draw", RenderTarget.class, Map.class, boolean.class);
        draw.setAccessible(true);
        var target = new TextureTarget(8, 8, true, false);
        int previous = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
        try (var transfer = new PixelTransferState()) {
            GL33C.glUseProgram(id);
            GL33C.glUniform1i(GL33C.glGetUniformLocation(id, "nocturna_FogEnabled"), 1);
            GL33C.glUniform4f(
                    GL33C.glGetUniformLocation(id, "nocturna_FogRanges"), 0, 200, 64, 128);
            GL33C.glUniformMatrix3fv(
                    GL33C.glGetUniformLocation(id, "nocturna_InverseView"),
                    false,
                    new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1});
            draw.invoke(program, target, Map.of(), false);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, target.frameBufferId);
            var pixel = MemoryUtil.memAlloc(4);
            try {
                GL33C.glReadPixels(4, 4, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                ExtraValidation.require(
                        Math.abs(Byte.toUnsignedInt(pixel.get(0)) - 128) <= 1,
                        "vanilla fog import did not combine environment/render channels");
            } finally {
                MemoryUtil.memFree(pixel);
            }
        } finally {
            GL33C.glUseProgram(previous);
            ((AutoCloseable) program).close();
            target.destroyBuffers();
            client.getMainRenderTarget().bindWrite(true);
        }
        System.out.println(
                "PASS vanilla shader fog import preserves independent spherical/cylindrical"
                        + " channels");
    }
}
