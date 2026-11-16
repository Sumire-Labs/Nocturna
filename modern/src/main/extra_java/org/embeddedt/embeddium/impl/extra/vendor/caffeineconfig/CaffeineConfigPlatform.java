package org.embeddedt.embeddium.impl.extra.vendor.caffeineconfig;

public interface CaffeineConfigPlatform {
    void applyModOverrides(CaffeineConfig config, String jsonKey);
}
