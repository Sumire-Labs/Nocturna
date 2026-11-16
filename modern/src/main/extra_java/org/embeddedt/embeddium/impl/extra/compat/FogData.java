package org.embeddedt.embeddium.impl.extra.compat;

/** Frame-local fog channels used by the upstream Extra algorithms on the 1.20 renderer. */
public final class FogData {
    public float renderDistanceStart,
            renderDistanceEnd,
            environmentalStart,
            environmentalEnd,
            skyEnd,
            cloudEnd;
}
