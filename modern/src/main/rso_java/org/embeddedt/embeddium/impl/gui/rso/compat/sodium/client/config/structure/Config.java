package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

public final class Config
        implements org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.ConfigState {
    private long epoch;

    public long epoch() {
        return epoch;
    }

    public void invalidate() {
        epoch++;
    }
}
