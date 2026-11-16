package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config;

public interface ConfigEntryPoint {
    void registerConfigLate(
            org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure.ConfigBuilder
                    builder);
}
