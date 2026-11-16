package org.embeddedt.embeddium.impl.extra.compat;

@FunctionalInterface
public interface StorageEventHandler {
    void afterSave();
}
