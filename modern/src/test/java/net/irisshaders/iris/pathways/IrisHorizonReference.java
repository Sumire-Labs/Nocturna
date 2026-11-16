// Frozen geometry from Iris (LGPL-3.0), bff1e69cb6c5519d8745784aa9c8b92984de67e7.
package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.vertex.VertexConsumer;

final class IrisHorizonReference {
    private static final float TOP = 16.0F;
    private static final float BOTTOM = -16.0F;

    static void buildHorizon(int radius, VertexConsumer consumer) {
        if (radius > 256) {
            radius = 256;
        }

        consumer.vertex(0.0F, BOTTOM, 0.0F).endVertex();

        for (int i = 0; i <= 8; i++) {
            float angle = (float) (-i * Math.PI / 4.0);
            float x = (float) (radius * Math.cos(angle));
            float z = (float) (radius * Math.sin(angle));
            consumer.vertex(x, TOP, z).endVertex();
        }
    }
}
