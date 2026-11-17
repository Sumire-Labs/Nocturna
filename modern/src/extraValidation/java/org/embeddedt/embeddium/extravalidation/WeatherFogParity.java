package org.embeddedt.embeddium.extravalidation;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;

import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.embeddedt.embeddium.impl.render.weather.WeatherRenderer;
import org.lwjgl.opengl.GL33C;

final class WeatherFogParity {
    static void verify(Minecraft mc) throws Exception {
        WeatherRenderer.clear();
        mc.level.setRainLevel(1);
        ExtraFog.hasTerrain = true;
        ExtraFog.activeMode = FogRenderer.FogMode.FOG_TERRAIN;
        ExtraFog.cloudPass = false;
        ExtraFog.terrain.environmentalStart = 0;
        ExtraFog.terrain.environmentalEnd = 1;
        ExtraFog.terrain.renderDistanceStart = 57.6F;
        ExtraFog.terrain.renderDistanceEnd = 64;
        var position = mc.gameRenderer.getMainCamera().getPosition();
        ExtraValidation.require(
                WeatherRenderer.render(
                        mc.gameRenderer.lightTexture(),
                        200,
                        .5F,
                        position.x,
                        position.y,
                        position.z),
                "native weather fell back");
        Class<?> type = Class.forName("org.embeddedt.embeddium.impl.render.weather.WeatherShader");
        var field = type.getDeclaredField("vanilla");
        field.setAccessible(true);
        int program = field.getInt(null);
        ExtraValidation.require(program != 0, "native weather program absent");
        int enabled = GL33C.glGetUniformLocation(program, "nocturna_FogEnabled"),
                ranges = GL33C.glGetUniformLocation(program, "nocturna_FogRanges");
        ExtraValidation.require(
                enabled >= 0 && GL33C.glGetUniformi(program, enabled) == 1,
                "native weather shader ignores the independent environment fog channel");
        float[] values = new float[4];
        GL33C.glGetUniformfv(program, ranges, values);
        ExtraValidation.require(
                values[0] == 0 && values[1] == 1 && values[3] == 64,
                "native weather fog ranges differ from terrain");
        ExtraValidation.require(
                GL33C.glGetError() == GL33C.GL_NO_ERROR,
                "weather/fog integration generated a GL error");
        pixels(mc, position);
    }

    private static void pixels(Minecraft mc, net.minecraft.world.phys.Vec3 position) {
        var projection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var inverse = new org.joml.Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        float[] color = RenderSystem.getShaderFogColor().clone();
        var poses = RenderSystem.getModelViewStack();
        poses.pushPose();
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(128, 128, true, false);
        try (var transfer = new PixelTransferState()) {
            poses.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setInverseViewRotationMatrix(new org.joml.Matrix3f());
            RenderSystem.setProjectionMatrix(
                    new org.joml.Matrix4f().setOrtho(-10, 10, -10, 10, -100, 100),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderFogColor(0, 1, 0, 1);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableScissor();
            target.setClearColor(0, 0, 0, 0);
            target.clear(false);
            target.bindWrite(true);
            ExtraValidation.require(
                    WeatherRenderer.render(
                            mc.gameRenderer.lightTexture(),
                            201,
                            .5F,
                            position.x,
                            position.y,
                            position.z),
                    "weather pixel fixture fell back");
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, target.frameBufferId);
            var pixels = org.lwjgl.system.MemoryUtil.memAlloc(128 * 128 * 4);
            int covered = 0, wrong = 0;
            try {
                GL33C.glReadPixels(0, 0, 128, 128, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
                for (int i = 0; i < pixels.capacity(); i += 4)
                    if (Byte.toUnsignedInt(pixels.get(i + 3)) > 0) {
                        covered++;
                        if (Byte.toUnsignedInt(pixels.get(i)) > 1
                                || Byte.toUnsignedInt(pixels.get(i + 2)) > 1
                                || Byte.toUnsignedInt(pixels.get(i + 1)) == 0) wrong++;
                    }
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(pixels);
            }
            ExtraValidation.require(
                    covered > 20 && wrong == 0,
                    "environment fog did not reach actual rain pixels: covered="
                            + covered
                            + ", wrong="
                            + wrong
                            + ", columns="
                            + WeatherRenderer.lastColumns
                            + ", draws="
                            + WeatherRenderer.lastDrawCalls
                            + ", gl="
                            + GL33C.glGetError());
            System.out.println(
                    "PASS protected weather fog GPU readback: "
                            + covered
                            + " fragments, no unfogged fragments");
        } finally {
            poses.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setInverseViewRotationMatrix(inverse);
            RenderSystem.setShaderFogColor(color[0], color[1], color[2], color[3]);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
