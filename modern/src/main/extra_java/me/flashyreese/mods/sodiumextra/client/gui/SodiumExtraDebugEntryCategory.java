// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.client.gui;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.extra.compat.DebugEntryCategory;

public class SodiumExtraDebugEntryCategory {
    public static final DebugEntryCategory SODIUM_EXTRA_DEBUG_CATEGORY =
            new DebugEntryCategory(Component.literal("Sodium Extra"), 0F);
}
