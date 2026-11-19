package net.irisshaders.iris.pathways.scaling;

import net.irisshaders.iris.pathways.scaling.temporal.TemporalPackConfig;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class TemporalPackConfigTest {
    private static Properties contract() {
        var p = new Properties();
        p.setProperty("nocturna.upscaling.version", "1");
        p.setProperty("nocturna.upscaling.colorSpace", "srgb");
        p.setProperty("nocturna.upscaling.motionConvention", "previousMinusCurrentUnjitteredUv");
        p.setProperty("nocturna.upscaling.jitter", "nocturnaTemporalJitter");
        p.setProperty("nocturna.upscaling.replacesTaa", "nocturnaTemporalEnabled");
        p.setProperty("nocturna.upscaling.motionVectors", "colortex7");
        return p;
    }
    @Test void ordinaryIrisPacksStayOutsideTemporalRendering() {
        assertNull(TemporalPackConfig.parse(new Properties()));
        var p = contract(); p.remove("nocturna.upscaling.replacesTaa");
        assertNull(TemporalPackConfig.parse(p));
    }
    @Test void requiresExplicitKnownConventions() {
        for (String key : contract().stringPropertyNames()) {
            var p = contract(); p.setProperty(key, "unknown");
            assertNull(TemporalPackConfig.parse(p), key);
        }
        assertEquals(7, TemporalPackConfig.parse(contract()).motionBuffer());
    }
    @Test void cannotAddressOutsideIrisColorTargets() {
        for (String buffer : new String[]{"colortex-1", "colortex16", "colortex999999999999999", "depthtex0", "colortex07"}) {
            var p = contract(); p.setProperty("nocturna.upscaling.motionVectors", buffer);
            assertNull(TemporalPackConfig.parse(p));
        }
        var p = contract(); p.setProperty("nocturna.upscaling.motionVectors", "colortex15");
        assertEquals(15, TemporalPackConfig.parse(p).motionBuffer());
    }
    @Test void preservesOldSerializedModeNamesAndDefaults() {
        assertEquals(UpscaleMode.FSR, UpscaleMode.valueOf("FSR"));
        assertEquals(0, UpscaleMode.FSR.temporalFamily());
        assertEquals(0, UpscaleMode.BILINEAR.temporalFamily());
        assertEquals(4, UpscaleMode.FSR4_1.temporalFamily());
    }
}
