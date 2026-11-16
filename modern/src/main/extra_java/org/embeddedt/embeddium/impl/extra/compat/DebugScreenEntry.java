package org.embeddedt.embeddium.impl.extra.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public interface DebugScreenEntry {
    void display(
            DebugScreenDisplayer displayer,
            Level level,
            LevelChunk clientChunk,
            LevelChunk serverChunk);

    DebugEntryCategory category();

    default boolean isAllowed(boolean reducedDebugInfo) {
        return !reducedDebugInfo;
    }
}
