package me.flashyreese.mods.sodiumextra.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.mixin.panini_projection.AccessorGameRenderer;
import me.flashyreese.mods.sodiumextra.mixin.panini_projection.AccessorLevelRenderer;
import me.flashyreese.mods.sodiumextra.mixin.panini_projection.AccessorPostChain;
import me.flashyreese.mods.sodiumextra.mixin.panini_projection.AccessorPostPass;
import me.flashyreese.mods.sodiumextra.mixin.panini_projection.AccessorRenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Sodium Extra beta.4's frame lifecycle on the 1.20.1 RenderTarget backend. The original GLSL
 * implements the projection, adaptive 4x4 filter and mask restoration. World color, depth, outlines
 * and Fabulous targets are resolved before the native hand/HUD.
 */
public final class PaniniProjection implements AutoCloseable {
    public static final PaniniProjection INSTANCE = new PaniniProjection();
    private RenderTarget worldTarget, worldOutlineTarget, handTarget, swapTarget, nativeMain;
    private PaniniProgram program;
    private boolean renderingWorld, pending, handRendering, preserveWorldDepth, shaderFailed;
    private boolean warnedShader;
    private int failedQuality, windowWidth, windowHeight, limitedWidth, limitedHeight;
    private float strength, horizontalExtent, verticalExtent, frameScale = 1;
    private final List<Runnable> restoreChains = new ArrayList<>();
    private final IdentityHashMap<RenderTarget, RenderTarget> worldAuxTargets =
            new IdentityHashMap<>();
    private PostChain auxiliaryChain;

    public void begin(Minecraft minecraft, RenderTarget main, Matrix4f projection) {
        abort();
        var settings = SodiumExtraClientMod.options().extraSettings;
        int quality = settings.paniniProjectionQuality;
        if (windowWidth != main.width || windowHeight != main.height) resize();
        windowWidth = main.width;
        windowHeight = main.height;
        if (failedQuality == quality || shaderFailed) return;
        if (!isEnabled()) {
            releaseTargets();
            return;
        }
        // Temporary bypasses retain reusable targets, matching the upstream lifecycle.
        var renderer = (AccessorLevelRenderer) minecraft.levelRenderer;
        if (minecraft.player == null
                || minecraft.player.isScoping()
                || minecraft.gameRenderer.isPanoramicMode()
                || renderer.sodiumExtra$getCapturedFrustum() != null
                || main.width <= 0
                || main.height <= 0
                || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(
                                minecraft.getWindow().getWindow(),
                                org.lwjgl.glfw.GLFW.GLFW_ICONIFIED)
                        != 0) return;
        horizontalExtent = 1 / Math.abs(projection.m00());
        verticalExtent = 1 / Math.abs(projection.m11());
        if (!Float.isFinite(horizontalExtent) || !Float.isFinite(verticalExtent)) return;
        strength = settings.paniniProjectionStrength / 100.0F;
        if (program == null) {
            try {
                program = new PaniniProgram();
            } catch (IOException | RuntimeException ex) {
                shaderFailed = true;
                if (!warnedShader)
                    SodiumExtraClientMod.logger()
                            .warn("Unable to load Panini projection shaders", ex);
                warnedShader = true;
                releaseTargets();
                return;
            }
        }
        float requestedScale = quality / 100.0F;
        int maxTextureSize = RenderSystem.maxSupportedTextureSize();
        float scale =
                Math.min(
                        requestedScale, (float) maxTextureSize / Math.max(main.width, main.height));
        int width = Math.min(maxTextureSize, (int) Math.ceil(main.width * scale));
        int height = Math.min(maxTextureSize, (int) Math.ceil(main.height * scale));
        if (scale < requestedScale
                && (limitedWidth != main.width || limitedHeight != main.height)) {
            SodiumExtraClientMod.logger()
                    .warn(
                            "Panini world resolution is limited to {}x{} by the GPU's maximum"
                                    + " texture size of {}",
                            width,
                            height,
                            maxTextureSize);
            limitedWidth = main.width;
            limitedHeight = main.height;
        }
        nativeMain = main;
        preserveWorldDepth =
                minecraft.gameRenderer.currentEffect() != null
                        && ((AccessorGameRenderer) minecraft.gameRenderer)
                                .sodiumExtra$isEffectActive();
        try {
            prepareTargets(renderer.sodiumExtra$getEntityOutlineTarget(), width, height);
            PostChain transparency = renderer.sodiumExtra$getTransparencyChain();
            if (transparency != null && worldTarget != null)
                bindWorldChain(transparency, main, worldTarget, width, height);
        } catch (RuntimeException ex) {
            failedQuality = quality;
            restoreChains();
            releaseTargets();
            SodiumExtraClientMod.logger()
                    .warn(
                            "Unable to allocate {}x{} Panini world targets; disabling Panini until"
                                    + " the quality setting or window size changes",
                            width,
                            height,
                            ex);
            main.bindWrite(true);
            return;
        }
        failedQuality = 0;
        renderingWorld = pending = true;
        frameScale = worldTarget != null ? (float) worldTarget.width / main.width : 1;
        if (worldTarget != null) {
            worldTarget.clear(Minecraft.ON_OSX);
            worldTarget.bindWrite(true);
        }
    }

    public static boolean isEnabled() {
        var settings = SodiumExtraClientMod.options().extraSettings;
        return settings.paniniProjection
                && settings.paniniProjectionStrength > 0
                && !settings.preventShaders
                && !me.flashyreese.mods.sodiumextra.compat.IrisCompat.isShaderPackInUse();
    }

    public boolean isRenderingWorld() {
        return renderingWorld;
    }

    public float worldScale() {
        return renderingWorld ? frameScale : 1;
    }

    public RenderTarget worldTarget(RenderTarget nativeTarget) {
        return renderingWorld && worldTarget != null ? worldTarget : nativeTarget;
    }

    public RenderTarget outlineTarget(RenderTarget nativeTarget) {
        return renderingWorld && worldOutlineTarget != null ? worldOutlineTarget : nativeTarget;
    }

    public RenderTarget auxiliaryTarget(RenderTarget nativeTarget) {
        return renderingWorld
                ? worldAuxTargets.getOrDefault(nativeTarget, nativeTarget)
                : nativeTarget;
    }

    public int worldWidth(int nativeWidth) {
        return renderingWorld && worldTarget != null ? worldTarget.width : nativeWidth;
    }

    public int worldHeight(int nativeHeight) {
        return renderingWorld && worldTarget != null ? worldTarget.height : nativeHeight;
    }

    public void finish(Minecraft minecraft, float partialTicks) {
        if (!pending) return;
        renderingWorld = false;
        try {
            projectColor(worldTarget != null ? worldTarget : nativeMain, nativeMain, false);
            if (preserveWorldDepth)
                program.draw(
                        worldTarget,
                        nativeMain,
                        strength,
                        horizontalExtent,
                        verticalExtent,
                        false,
                        true,
                        -1);
            var renderer = (AccessorLevelRenderer) minecraft.levelRenderer;
            if (renderer.sodiumExtra$hasEntityOutline()) {
                RenderTarget nativeOutline = renderer.sodiumExtra$getEntityOutlineTarget();
                projectColor(
                        worldOutlineTarget != null ? worldOutlineTarget : nativeOutline,
                        nativeOutline,
                        true);
                PostChain outline = renderer.sodiumExtra$getEntityEffect();
                if (outline != null) outline.process(partialTicks);
            }
        } finally {
            pending = false;
            restoreChains();
            nativeMain.bindWrite(true);
        }
    }

    /**
     * Native 1.20 clears hand depth in the main FBO. Give it a separate attachment before that
     * clear.
     */
    public void beginHand() {
        if (!preserveWorldDepth || nativeMain == null || worldTarget == null) return;
        if (!matches(handTarget, nativeMain.width, nativeMain.height, nativeMain)) {
            if (handTarget != null) handTarget.destroyBuffers();
            handTarget = createTarget(nativeMain.width, nativeMain.height, nativeMain);
        }
        nativeMain.bindWrite(true);
        attachDepth(handTarget);
        handRendering = true;
    }

    public void endHand() {
        if (!handRendering) return;
        handRendering = false;
        nativeMain.bindWrite(true);
        attachDepth(nativeMain);
        program.draw(
                worldTarget,
                nativeMain,
                strength,
                horizontalExtent,
                verticalExtent,
                false,
                true,
                handTarget.getDepthTextureId());
    }

    private void attachDepth(RenderTarget target) {
        GL33C.glFramebufferTexture2D(
                GL33C.GL_FRAMEBUFFER,
                GL33C.GL_DEPTH_ATTACHMENT,
                GL33C.GL_TEXTURE_2D,
                target.getDepthTextureId(),
                0);
        if (nativeMain.isStencilEnabled())
            GL33C.glFramebufferTexture2D(
                    GL33C.GL_FRAMEBUFFER,
                    GL33C.GL_STENCIL_ATTACHMENT,
                    GL33C.GL_TEXTURE_2D,
                    target.getDepthTextureId(),
                    0);
    }

    public void abort() {
        boolean restore = renderingWorld || pending || handRendering;
        renderingWorld = pending = false;
        if (handRendering) {
            handRendering = false;
            nativeMain.bindWrite(true);
            attachDepth(nativeMain);
        }
        restoreChains();
        if (restore && nativeMain != null) nativeMain.bindWrite(true);
        preserveWorldDepth = false;
        frameScale = 1;
    }

    public void resize() {
        abort();
        releaseTargets();
        failedQuality = limitedWidth = limitedHeight = 0;
    }

    @Override
    public void close() {
        resize();
        if (program != null) program.close();
        program = null;
        shaderFailed = false;
    }

    private void prepareTargets(RenderTarget outline, int width, int height) {
        if (width == nativeMain.width && height == nativeMain.height && !preserveWorldDepth) {
            releaseWorldTargets();
            return;
        }
        if (matches(worldTarget, width, height, nativeMain)
                && (outline == null
                        ? worldOutlineTarget == null
                        : matches(worldOutlineTarget, width, height, outline))) return;
        releaseWorldTargets();
        worldTarget = createTarget(width, height, nativeMain);
        if (outline != null) worldOutlineTarget = createTarget(width, height, outline);
    }

    private static boolean matches(RenderTarget target, int width, int height, RenderTarget model) {
        return target != null
                && target.width == width
                && target.height == height
                && target.useDepth == model.useDepth
                && target.isStencilEnabled() == model.isStencilEnabled();
    }

    private static RenderTarget createTarget(int width, int height, RenderTarget model) {
        RenderTarget target = new RenderTarget(model.useDepth) {};
        try {
            target.createBuffers(width, height, Minecraft.ON_OSX);
            if (model.isStencilEnabled()) target.enableStencil();
            float[] clear = ((AccessorRenderTarget) model).sodiumExtra$getClearColor();
            target.setClearColor(clear[0], clear[1], clear[2], clear[3]);
            target.setFilterMode(GL33C.GL_LINEAR);
            return target;
        } catch (RuntimeException ex) {
            target.destroyBuffers();
            throw ex;
        }
    }

    private void releaseTargets() {
        releaseWorldTargets();
        if (swapTarget != null) swapTarget.destroyBuffers();
        swapTarget = null;
    }

    private void releaseWorldTargets() {
        if (worldTarget != null) worldTarget.destroyBuffers();
        if (worldOutlineTarget != null) worldOutlineTarget.destroyBuffers();
        if (handTarget != null) handTarget.destroyBuffers();
        worldTarget = worldOutlineTarget = handTarget = null;
        worldAuxTargets.values().forEach(RenderTarget::destroyBuffers);
        worldAuxTargets.clear();
        auxiliaryChain = null;
    }

    private void projectColor(RenderTarget source, RenderTarget output, boolean mask) {
        if (source != output) {
            program.draw(
                    source, output, strength, horizontalExtent, verticalExtent, mask, false, -1);
            return;
        }
        if (swapTarget == null
                || swapTarget.width != output.width
                || swapTarget.height != output.height) {
            if (swapTarget != null) swapTarget.destroyBuffers();
            swapTarget = new RenderTarget(false) {};
            try {
                swapTarget.createBuffers(output.width, output.height, Minecraft.ON_OSX);
                swapTarget.setFilterMode(GL33C.GL_LINEAR);
            } catch (RuntimeException failure) {
                swapTarget.destroyBuffers();
                swapTarget = null;
                throw failure;
            }
        }
        program.draw(
                source, swapTarget, strength, horizontalExtent, verticalExtent, mask, false, -1);
        int read = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING),
                draw = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, swapTarget.frameBufferId);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, output.frameBufferId);
            GL33C.glBlitFramebuffer(
                    0,
                    0,
                    output.width,
                    output.height,
                    0,
                    0,
                    output.width,
                    output.height,
                    GL33C.GL_COLOR_BUFFER_BIT,
                    GL33C.GL_NEAREST);
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    private void bindWorldChain(
            PostChain chain, RenderTarget main, RenderTarget world, int width, int height) {
        var access = (AccessorPostChain) chain;
        if (auxiliaryChain != chain) {
            worldAuxTargets.values().forEach(RenderTarget::destroyBuffers);
            worldAuxTargets.clear();
            auxiliaryChain = chain;
        }
        var targets = new IdentityHashMap<RenderTarget, RenderTarget>();
        targets.put(main, world);
        for (RenderTarget target : access.sodiumExtra$getFullSizedTargets()) {
            RenderTarget replacement = worldAuxTargets.get(target);
            if (!matches(replacement, width, height, target)) {
                if (replacement != null) replacement.destroyBuffers();
                replacement = createTarget(width, height, target);
                worldAuxTargets.put(target, replacement);
            }
            targets.put(target, replacement);
        }
        Matrix4f projection = new Matrix4f().setOrtho(0, width, 0, height, 0.1F, 1000);
        List<Runnable> restorePasses = new ArrayList<>();
        restoreChains.add(() -> restorePasses.forEach(Runnable::run));
        for (PostPass pass : access.sodiumExtra$getPasses()) {
            var p = (AccessorPostPass) pass;
            RenderTarget in = pass.inTarget, out = pass.outTarget;
            Matrix4f oldProjection = p.sodiumExtra$getOrthoMatrix();
            restorePasses.add(
                    () -> {
                        p.sodiumExtra$setInTarget(in);
                        p.sodiumExtra$setOutTarget(out);
                        pass.setOrthoMatrix(oldProjection);
                    });
            p.sodiumExtra$setInTarget(targets.getOrDefault(in, in));
            p.sodiumExtra$setOutTarget(targets.getOrDefault(out, out));
            pass.setOrthoMatrix(projection);
            for (int i = 0; i < p.sodiumExtra$getAuxAssets().size(); i++) {
                int index = i;
                IntSupplier supplier = p.sodiumExtra$getAuxAssets().get(i);
                int value = supplier.getAsInt();
                RenderTarget replacement = null;
                boolean depth = false;
                for (var target : targets.entrySet()) {
                    if (value == target.getKey().getColorTextureId()) {
                        replacement = target.getValue();
                        break;
                    }
                    if (value >= 0 && value == target.getKey().getDepthTextureId()) {
                        replacement = target.getValue();
                        depth = true;
                        break;
                    }
                }
                if (replacement != null) {
                    int oldWidth = p.sodiumExtra$getAuxWidths().get(i),
                            oldHeight = p.sodiumExtra$getAuxHeights().get(i);
                    restorePasses.add(
                            () -> {
                                p.sodiumExtra$getAuxAssets().set(index, supplier);
                                p.sodiumExtra$getAuxWidths().set(index, oldWidth);
                                p.sodiumExtra$getAuxHeights().set(index, oldHeight);
                            });
                    p.sodiumExtra$getAuxAssets()
                            .set(
                                    i,
                                    depth
                                            ? replacement::getDepthTextureId
                                            : replacement::getColorTextureId);
                    p.sodiumExtra$getAuxWidths().set(i, width);
                    p.sodiumExtra$getAuxHeights().set(i, height);
                }
            }
        }
    }

    private void restoreChains() {
        try {
            for (int i = restoreChains.size() - 1; i >= 0; --i) restoreChains.get(i).run();
        } finally {
            restoreChains.clear();
        }
    }
}
