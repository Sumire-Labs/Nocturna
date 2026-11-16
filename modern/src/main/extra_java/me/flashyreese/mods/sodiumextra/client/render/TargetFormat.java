package me.flashyreese.mods.sodiumextra.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.lwjgl.opengl.GL33C;

/**
 * Texture storage is part of a target's identity, including mod-provided floating-point targets.
 */
record TargetFormat(int color, int depth, boolean stencil) {
    static TargetFormat of(RenderTarget target) {
        return new TargetFormat(
                internal(target.getColorTextureId()),
                target.useDepth ? internal(target.getDepthTextureId()) : 0,
                target.isStencilEnabled());
    }

    private static int internal(int texture) {
        int previous = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        try {
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            return GL33C.glGetTexLevelParameteri(
                    GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT);
        } finally {
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, previous);
        }
    }

    RenderTarget create(int width, int height) {
        var target = new Storage(this);
        int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        try {
            target.createBuffers(width, height, false);
            return target;
        } catch (RuntimeException failure) {
            target.destroyBuffers();
            throw failure;
        } finally {
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack);
        }
    }

    private static final class Storage extends RenderTarget {
        private final TargetFormat format;

        Storage(TargetFormat format) {
            super(format.depth != 0);
            this.format = format;
        }

        @Override
        public void createBuffers(int width, int height, boolean clearError) {
            super.createBuffers(width, height, clearError);
            int previous = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            try {
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, getColorTextureId());
                GL33C.glTexImage2D(
                        GL33C.GL_TEXTURE_2D,
                        0,
                        format.color,
                        width,
                        height,
                        0,
                        GL33C.GL_RGBA,
                        GL33C.GL_FLOAT,
                        0L);
                if (useDepth) {
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, getDepthTextureId());
                    int external =
                            format.stencil ? GL33C.GL_DEPTH_STENCIL : GL33C.GL_DEPTH_COMPONENT;
                    int type =
                            format.stencil
                                    ? (format.depth == GL33C.GL_DEPTH32F_STENCIL8
                                            ? GL33C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
                                            : GL33C.GL_UNSIGNED_INT_24_8)
                                    : GL33C.GL_FLOAT;
                    GL33C.glTexImage2D(
                            GL33C.GL_TEXTURE_2D,
                            0,
                            format.depth,
                            width,
                            height,
                            0,
                            external,
                            type,
                            0L);
                    GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, frameBufferId);
                    GL33C.glFramebufferTexture2D(
                            GL33C.GL_FRAMEBUFFER,
                            GL33C.GL_DEPTH_ATTACHMENT,
                            GL33C.GL_TEXTURE_2D,
                            getDepthTextureId(),
                            0);
                    if (format.stencil)
                        GL33C.glFramebufferTexture2D(
                                GL33C.GL_FRAMEBUFFER,
                                GL33C.GL_STENCIL_ATTACHMENT,
                                GL33C.GL_TEXTURE_2D,
                                getDepthTextureId(),
                                0);
                }
                checkStatus();
            } finally {
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, previous);
            }
        }

        @Override
        public boolean isStencilEnabled() {
            return format.stencil;
        }
    }
}
