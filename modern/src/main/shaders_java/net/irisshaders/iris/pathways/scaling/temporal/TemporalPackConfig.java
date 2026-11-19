// SPDX-License-Identifier: LGPL-3.0-or-later
package net.irisshaders.iris.pathways.scaling.temporal;

import java.util.Properties;

/** Versioned opt-in contract. Ordinary Iris packs never enter the temporal renderer. */
public record TemporalPackConfig(int motionBuffer) {
    public static TemporalPackConfig parse(Properties properties) {
        if (!"1".equals(properties.getProperty("nocturna.upscaling.version"))) return null;
        // Do not guess a pack's velocity, TAA or projection conventions.
        if (!"srgb".equals(properties.getProperty("nocturna.upscaling.colorSpace"))
                || !"previousMinusCurrentUnjitteredUv".equals(properties.getProperty("nocturna.upscaling.motionConvention"))
                || !"nocturnaTemporalJitter".equals(properties.getProperty("nocturna.upscaling.jitter"))
                || !"nocturnaTemporalEnabled".equals(properties.getProperty("nocturna.upscaling.replacesTaa"))) return null;
        String buffer = properties.getProperty("nocturna.upscaling.motionVectors", "");
        if (!buffer.matches("colortex([0-9]|1[0-5])")) return null;
        return new TemporalPackConfig(Integer.parseInt(buffer.substring("colortex".length())));
    }
}
