package org.embeddedt.embeddium.impl.extra.compat.config;

import org.embeddedt.embeddium.impl.extra.compat.config.structure.ConfigBuilder;

public interface ConfigEntryPoint {
    void registerConfigLate(ConfigBuilder builder);
}
