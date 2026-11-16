// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package org.embeddedt.embeddium.impl.render.weather;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * One weather column per instance.
 */
public final class WeatherInstancedAttribs {

    public static final int LOC_COLUMN_SPAN = 1;
    public static final int LOC_JITTER = 2;
    public static final int LOC_PARAMS = 3;
    public static final int LOC_COLOR = 4;
    public static final int LOC_LAYER = 5;

    public static final int OFFSET_COLUMN_SPAN = 0;
    public static final int OFFSET_JITTER = 16;
    public static final int OFFSET_PARAMS = 32;
    public static final int OFFSET_COLOR = 48;
    public static final int OFFSET_LAYER = 64;
    public static final int STRIDE = 80;

    public static final int RAIN_PERIOD = 32;
    public static final float RAIN_SCROLL_BASE = 3.0F;
    public static final float RAIN_FADE = 0.5F;
    public static final int SNOW_PERIOD = 512;
    public static final float SNOW_U_DRIFT = 0.01F;
    public static final float SNOW_V_DRIFT = 0.001F;
    public static final float SNOW_FADE = 0.3F;
    public static final float SOURCE_VANILLA = 0.0F;
    public static final float SOURCE_PROVIDER = 1.0F;

    public static void putColumn(ByteBuffer data, int index, float x, float z, float bottom, float top, float uBase, float uDrift,
        float vBase, float vDrift, float brightness, int scrollOffset, float scrollScale, float source, float r, float g, float b,
        float a, int period, float uDriftScale, float vDriftScale, float fadeScale) {
        final int base = index * STRIDE;
        data.putFloat(base + OFFSET_COLUMN_SPAN, x);
        data.putFloat(base + OFFSET_COLUMN_SPAN + 4, z);
        data.putFloat(base + OFFSET_COLUMN_SPAN + 8, bottom);
        data.putFloat(base + OFFSET_COLUMN_SPAN + 12, top);
        data.putFloat(base + OFFSET_JITTER, uBase);
        data.putFloat(base + OFFSET_JITTER + 4, uDrift);
        data.putFloat(base + OFFSET_JITTER + 8, vBase);
        data.putFloat(base + OFFSET_JITTER + 12, vDrift);
        data.putFloat(base + OFFSET_PARAMS, brightness);
        data.putFloat(base + OFFSET_PARAMS + 4, scrollOffset & (period - 1));
        data.putFloat(base + OFFSET_PARAMS + 8, scrollScale);
        data.putFloat(base + OFFSET_PARAMS + 12, source);
        data.putFloat(base + OFFSET_COLOR, r);
        data.putFloat(base + OFFSET_COLOR + 4, g);
        data.putFloat(base + OFFSET_COLOR + 8, b);
        data.putFloat(base + OFFSET_COLOR + 12, a);
        data.putFloat(base + OFFSET_LAYER, period);
        data.putFloat(base + OFFSET_LAYER + 4, uDriftScale);
        data.putFloat(base + OFFSET_LAYER + 8, vDriftScale);
        data.putFloat(base + OFFSET_LAYER + 12, fadeScale);
    }

    public static void pointInstanceAttribs(long base) {
        GL20C.glVertexAttribPointer(LOC_COLUMN_SPAN, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_COLUMN_SPAN);
        GL20C.glVertexAttribPointer(LOC_JITTER, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_JITTER);
        GL20C.glVertexAttribPointer(LOC_PARAMS, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_PARAMS);
        GL20C.glVertexAttribPointer(LOC_COLOR, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_COLOR);
        GL20C.glVertexAttribPointer(LOC_LAYER, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_LAYER);
    }

    public static void enableInstanceArrays() {
        enableInstanced(LOC_COLUMN_SPAN);
        enableInstanced(LOC_JITTER);
        enableInstanced(LOC_PARAMS);
        enableInstanced(LOC_COLOR);
        enableInstanced(LOC_LAYER);
    }

    private static void enableInstanced(int location) {
        GL20C.glEnableVertexAttribArray(location);
        org.lwjgl.opengl.GL33C.glVertexAttribDivisor(location, 1);
    }

    private WeatherInstancedAttribs() {}
}
