package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

@FunctionalInterface
public interface ValueSource<T> {
    T get(Config config);
}
