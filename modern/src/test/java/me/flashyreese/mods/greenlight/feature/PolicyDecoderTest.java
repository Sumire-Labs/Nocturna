// Greenlight 0.1.0 upstream policy conformance tests (LGPL-3.0).
package me.flashyreese.mods.greenlight.feature;

import static me.flashyreese.mods.greenlight.feature.TestSupport.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import org.junit.jupiter.api.Test;

class PolicyDecoderTest {
    private static final Codec<Integer> N_CODEC = Codec.INT.fieldOf("n").codec();

    @Test
    void fromCodecDecodesValidSettings() throws Exception {
        PolicyDecoder<Integer> decoder = PolicyDecoder.fromCodec(N_CODEC);
        assertEquals(7, decoder.decode(settings("n", 7)));
    }

    @Test
    void fromCodecThrowsOnInvalidSettings() {
        PolicyDecoder<Integer> decoder = PolicyDecoder.fromCodec(N_CODEC);
        assertThrows(Exception.class, () -> decoder.decode(new JsonObject()));
    }
}
