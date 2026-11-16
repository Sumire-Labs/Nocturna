// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.mixin;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.embeddedt.embeddium.impl.extra.vendor.caffeineconfig.AbstractCaffeineConfigMixinPlugin;
import org.embeddedt.embeddium.impl.extra.vendor.caffeineconfig.CaffeineConfig;

public class SodiumExtraMixinConfigPlugin extends AbstractCaffeineConfigMixinPlugin {

    private static final String MIXIN_PACKAGE_ROOT = "me.flashyreese.mods.sodiumextra.mixin.";

    @Override
    protected CaffeineConfig createConfig() {
        return SodiumExtraClientMod.mixinConfig();
    }

    @Override
    protected String mixinPackageRoot() {
        return MIXIN_PACKAGE_ROOT;
    }
}
