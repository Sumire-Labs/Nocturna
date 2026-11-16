// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package org.embeddedt.embeddium.impl.render.weather;
import java.nio.*;import java.util.Arrays;import net.minecraft.client.Minecraft;import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.*;import org.embeddedt.embeddium.api.weather.WeatherColumnSink;
final class WeatherBatch implements WeatherColumnSink {

        private static final int STRIDE = WeatherInstancedAttribs.STRIDE;
        private static final int MIN_CAPACITY = 256;

        private ByteBuffer data;
        private int capacity;
        int count;
        private ResourceLocation[] textures = new ResourceLocation[4];
        private int[] firsts = new int[4];
        private int layers;
        private int originX;
        private int originZ;
        private float source;
        private int period;
        private float uDriftScale;
        private float vDriftScale;
        private float fadeScale;
        private int buffer;
        private int bufferCapacity;

        void begin(int originX, int originZ, float source) {
            this.originX = originX;
            this.originZ = originZ;
            this.source = source;
            Arrays.fill(textures, 0, layers, null);
            layers = 0;
            count = 0;
        }

        void upload() {
            if (count == 0) return;
            if (buffer == 0) buffer = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            if (bufferCapacity < count) {
                bufferCapacity = capacity;
                GL15C.glBufferData(GL15.GL_ARRAY_BUFFER, (long) bufferCapacity * STRIDE, GL15.GL_DYNAMIC_DRAW);
            }
            data.position(0);
            data.limit(count * STRIDE);
            GL15C.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, data);
            data.clear();
            GL15C.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        int draw(Minecraft mc, WeatherShader shader) {
            int draws = 0;
            for (int layer = 0; layer < layers; layer++) {
                final int first = firsts[layer];
                final int layerCount = (layer + 1 < layers ? firsts[layer + 1] : count) - first;
                if (layerCount == 0) continue;
                shader.bind(textures[layer]);

                GL15C.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                WeatherInstancedAttribs.pointInstanceAttribs((long) first * STRIDE);
                GL15C.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                GL31C.glDrawArraysInstanced(shader.drawMode(), 0, WeatherQuadMesh.VERTEX_COUNT, layerCount);
                shader.unbind();
                draws++;
            }
            return draws;
        }

        void release() {
            begin(0, 0, 0.0F);
            data = null;
            capacity = 0;
            if (buffer != 0) {
                GL15C.glDeleteBuffers(buffer);
                buffer = 0;
            }
            bufferCapacity = 0;
        }

        @Override
        public void beginLayer(ResourceLocation texture, int scrollPeriod, float uDriftScale, float vDriftScale, float fadeScale) {
            if (texture == null) throw new IllegalArgumentException("Weather layer texture must not be null");
            if (scrollPeriod < 1 || scrollPeriod > WeatherParams.TICK_WRAP || (scrollPeriod & scrollPeriod - 1) != 0) {
                throw new IllegalArgumentException("Weather layer scroll period must be a power of two between 1 and " + WeatherParams.TICK_WRAP);
            }
            if (!Float.isFinite(uDriftScale) || !Float.isFinite(vDriftScale) || !Float.isFinite(fadeScale)) {
                throw new IllegalArgumentException("Weather layer scales must be finite");
            }
            if (layers == textures.length) {
                textures = Arrays.copyOf(textures, layers * 2);
                firsts = Arrays.copyOf(firsts, layers * 2);
            }
            textures[layers] = texture;
            firsts[layers] = count;
            layers++;
            period = scrollPeriod;
            this.uDriftScale = uDriftScale;
            this.vDriftScale = vDriftScale;
            this.fadeScale = fadeScale;
        }

        @Override
        public void column(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
                           int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a) {
            if (layers == 0) throw new IllegalStateException("Weather column written before beginLayer");
            if (brightness < 0 || brightness >= 1 << 24) {
                throw new IllegalArgumentException("Weather column brightness must be a packed lightmap coordinate below 2^24");
            }
            put(x, z, bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale, r, g, b, a);
        }

        void put(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift, int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a) {
            if (count == capacity) grow();
            WeatherInstancedAttribs.putColumn(data, count, x - originX, z - originZ, bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale, source, r, g, b, a, period, uDriftScale, vDriftScale, fadeScale);
            count++;
        }

        private void grow() {
            final int grownCapacity = Math.max(MIN_CAPACITY, capacity * 2);
            final ByteBuffer grown = ByteBuffer.allocateDirect(grownCapacity * STRIDE).order(ByteOrder.nativeOrder());
            if (count > 0) {
                data.position(0);
                data.limit(count * STRIDE);
                grown.put(data);
                grown.clear();
            }
            data = grown;
            capacity = grownCapacity;
        }
    }
