// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import org.embeddedt.embeddium.impl.extra.compat.DebugEntryCategory;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenDisplayer;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SodiumExtraDebugEntryCoords implements DebugScreenEntry {
    @Override
    public void display(
            @NotNull DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        if (entity != null) {
            Vec3 pos = minecraft.getCameraEntity().position();
            Component text =
                    Component.translatable(
                            "sodium-extra.overlay.coordinates",
                            String.format("%.2f", pos.x),
                            String.format("%.2f", pos.y),
                            String.format("%.2f", pos.z));
            displayer.addLine(text.getString());
        }
    }

    @Override
    public @NotNull DebugEntryCategory category() {
        return SodiumExtraDebugEntryCategory.SODIUM_EXTRA_DEBUG_CATEGORY;
    }
}
