package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config;

public interface ConfigState {
    long epoch();

    void invalidate();
}
