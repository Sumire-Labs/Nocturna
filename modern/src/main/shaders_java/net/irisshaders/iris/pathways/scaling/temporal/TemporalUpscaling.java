// SPDX-License-Identifier: LGPL-3.0-or-later
package net.irisshaders.iris.pathways.scaling.temporal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.VanillaTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.taumc.celeritas.shaders.CeleritasShaders;

/** Owns the optional backend; no existing pack passes, uniforms or projections are rewritten. */
public final class TemporalUpscaling {
    private static TemporalFsrUpscaler backend;
    private static IrisRenderingPipeline owner;
    private static String attempted = "", status = "Temporal FSR is inactive";
    private static int width, height, outputWidth, outputHeight, family;
    private static long frame, lastTime;
    private static final Vector2f jitter = new Vector2f();
    private static boolean ready, reset;
    private static Vec3 previousCamera;
    private static float previousFov;
    private static TemporalCamera previousProjection;

    private TemporalUpscaling() { }
    public static String status() { return status; }
    public static boolean isReady() { return ready; }

    public static void addUniforms(UniformHolder uniforms) {
        if (Iris.getCurrentPack().map(pack -> pack.getShaderProperties().getTemporalPackConfig() == null).orElse(true)) return;
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "nocturnaTemporalEnabled", () -> ready);
        uniforms.uniform2f(UniformUpdateFrequency.PER_FRAME, "nocturnaTemporalJitter", () -> new Vector2f(jitter));
    }

    /** Prepare before world shaders see enabled/jitter, including the unavailable-runtime fallback. */
    public static void begin(RenderTarget input, RenderTarget output) {
        ready = false;
        jitter.zero();
        int requested = IrisVideoSettings.upscaleMode.temporalFamily();
        if (requested == 0) { close(); return; }
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof IrisRenderingPipeline iris)) { close(); return; }
        var config = iris.getTemporalPackConfig();
        if (config == null || IrisVideoSettings.colorSpace != ColorSpace.SRGB) {
            close();
            status = config == null ? "Pack has no temporal upscaling contract; using FSR 1.0"
                    : "Temporal FSR requires sRGB output; using FSR 1.0";
            return;
        }
        if (owner != iris || width != input.width || height != input.height || outputWidth != output.width
                || outputHeight != output.height || family != requested) {
            close();
            owner = iris; width = input.width; height = input.height;
            outputWidth = output.width; outputHeight = output.height; family = requested;
        }
        String key = width + "x" + height + "/" + outputWidth + "x" + outputHeight + "/" + requested;
        // RenderScale begins before Iris resizes its targets. A resize (or the first pipeline
        // creation) may need one ordinary frame; retry after Iris has prepared matching targets.
        if (backend == null) {
            try { iris.getTemporalMotionTexture(input.width, input.height); }
            catch (IllegalStateException failure) { status = failure.getMessage() + "; using FSR 1.0"; return; }
        }
        if (backend == null && !attempted.equals(key)) {
            attempted = key;
            try {
                backend = new TemporalFsrUpscaler(width, height, outputWidth, outputHeight, family);
                status = "AMD FSR " + backend.provider();
                CeleritasShaders.logger().info("Temporal upscaling enabled: {}", status);
                reset = true; frame = 0; lastTime = 0;
            } catch (Exception | LinkageError failure) {
                status = "Temporal FSR unavailable: " + failure.getMessage() + "; using FSR 1.0";
                CeleritasShaders.logger().warn(status, failure);
            }
        }
        if (backend == null) return;
        try {
            float[] offset = backend.jitter(frame++);
            jitter.set(offset[0], offset[1]);
            ready = true;
        } catch (Exception | LinkageError failure) {
            status = "FSR jitter query failed; using FSR 1.0: " + failure.getMessage();
            CeleritasShaders.logger().error(status, failure);
            backend.close(); backend = null;
        }
    }

    public static boolean resolve(RenderTarget input, RenderTarget output) {
        if (!ready || backend == null) return false;
        if (Iris.getPipelineManager().getPipelineNullable() != owner) { close(); return false; }
        long now = System.nanoTime();
        float delta = lastTime == 0 ? 16.666667f : (now - lastTime) / 1_000_000f;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        try {
            var projection = TemporalCamera.fromProjection(CapturedRenderingState.INSTANCE.getGbufferProjection());
            float fov = projection.verticalFov();
            boolean discontinuity = reset || lastTime == 0 || delta > 1000 || previousCamera == null
                    || previousCamera.distanceToSqr(camera) > 256 || Math.abs(previousFov - fov) > 0.01f
                    || previousProjection == null || Math.abs(previousProjection.far() - projection.far()) > 1
                    || Math.abs(previousProjection.near() - projection.near()) > 0.0001f;
            backend.process(VanillaTargets.colorTexture(input), VanillaTargets.depthTexture(input),
                    owner.getTemporalMotionTexture(input.width, input.height), VanillaTargets.framebuffer(output),
                    jitter.x, jitter.y, delta, projection.near(), projection.far(),
                    fov, Math.max(0, Math.min(100, IrisVideoSettings.upscaleSharpness)) / 100f, discontinuity);
            lastTime = now; previousCamera = camera; previousFov = fov; previousProjection = projection; reset = false;
            return true;
        } catch (Exception | LinkageError failure) {
            status = "Temporal FSR dispatch failed; using FSR 1.0: " + failure.getMessage();
            CeleritasShaders.logger().error(status, failure);
            backend.close(); backend = null; ready = false; jitter.zero();
            return false;
        }
    }

    public static void release(IrisRenderingPipeline pipeline) { if (owner == pipeline) close(); }

    public static void close() {
        ready = false; jitter.zero();
        status = "Temporal FSR is inactive";
        if (backend != null) { backend.close(); backend = null; }
        owner = null; attempted = ""; previousCamera = null; previousProjection = null;
        lastTime = 0; reset = true;
    }
}
