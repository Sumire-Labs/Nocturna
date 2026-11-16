package org.embeddedt.embeddium.extravalidation;

import org.lwjgl.opengl.GL33C;

/**
 * Resource reloads legitimately leave pixel-transfer offsets; test buffers use tightly packed
 * client memory.
 */
final class PixelTransferState implements AutoCloseable {
    private final int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING),
            pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
    private final int[] keys = {
        GL33C.GL_UNPACK_ROW_LENGTH,
        GL33C.GL_UNPACK_SKIP_PIXELS,
        GL33C.GL_UNPACK_SKIP_ROWS,
        GL33C.GL_PACK_ROW_LENGTH,
        GL33C.GL_PACK_SKIP_PIXELS,
        GL33C.GL_PACK_SKIP_ROWS
    };
    private final int[] values = new int[keys.length];

    PixelTransferState() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        for (int i = 0; i < keys.length; i++) {
            values[i] = GL33C.glGetInteger(keys[i]);
            GL33C.glPixelStorei(keys[i], 0);
        }
    }

    public void close() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
        for (int i = 0; i < keys.length; i++) GL33C.glPixelStorei(keys[i], values[i]);
    }
}
