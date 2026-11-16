package org.embeddedt.embeddium.impl.render.weather;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;

class WeatherBatchTest {
    @Test void packedDataPreservesAllTwentyFieldsAndPowerOfTwoWrapping() {
        var data = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
        WeatherInstancedAttribs.putColumn(data, 1, -3, 7, 20, 30, .1f, .2f, .3f, .4f,
                0xf000f0, -1, 3.5f, 1, .25f, .5f, .75f, .8f, 32, .01f, .001f, .5f);
        float[] expected = {-3, 7, 20, 30, .1f, .2f, .3f, .4f, 0xf000f0, 31, 3.5f, 1,
                .25f, .5f, .75f, .8f, 32, .01f, .001f, .5f};
        for (int i = 0; i < 20; i++) assertEquals(expected[i], data.getFloat(80 + i * 4));
        assertEquals(0, data.position());
    }

    @Test void layerValidationAndGrowthRetainAllPreviousColumns() throws Exception {
        var batch = new WeatherBatch(); batch.begin(-10, 8, 1);
        assertThrows(IllegalStateException.class, () -> batch.column(0,0,0,1,0,0,0,0,0,0,1,1,1,1,1));
        var rain = new ResourceLocation("test:rain");
        assertThrows(IllegalArgumentException.class, () -> batch.beginLayer(null, 32,0,0,.5f));
        for (int period : new int[]{0,3,2048}) assertThrows(IllegalArgumentException.class, () -> batch.beginLayer(rain,period,0,0,.5f));
        assertThrows(IllegalArgumentException.class, () -> batch.beginLayer(rain,32,Float.NaN,0,.5f));
        batch.beginLayer(rain,32,0,0,.5f);
        assertThrows(IllegalArgumentException.class, () -> batch.column(0,0,0,1,0,0,0,0,1<<24,0,1,1,1,1,1));
        for (int i = 0; i < 600; i++) batch.column(i,10,5,15,0,0,0,0,0xf000f0,i,3,1,1,1,1);
        var field = WeatherBatch.class.getDeclaredField("data"); field.setAccessible(true);
        var data = (ByteBuffer) field.get(batch);
        assertEquals(600, batch.count); assertTrue(data.capacity() >= 600 * 80);
        for (int i = 0; i < 600; i++) {
            assertEquals(i + 10, data.getFloat(i * 80)); assertEquals(2, data.getFloat(i * 80 + 4));
            assertEquals(i & 31, data.getFloat(i * 80 + 36));
        }
        batch.begin(0,0,0); assertEquals(0, batch.count);
        assertSame(data, field.get(batch));
    }

    @Test void frameParamsWrapTicksButPreserveContinuousAgeAndSeparateProviderData() {
        WeatherParams.set(1,2,3,.1f,.2f,.3f,.4f,4097.5f,4097,.8f,-1,12.5f,.02f);
        assertEquals(1, WeatherParams.vanillaTick); assertEquals(1023, WeatherParams.providerTick);
        assertEquals(4097.5f, WeatherParams.age); assertEquals(12.5f, WeatherParams.providerAge);
        assertEquals(.02f, WeatherParams.providerInvRadius);
    }
}
