package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.control.*;

import net.minecraft.resources.ResourceLocation;

public final class BooleanOptionBuilder
        extends StatefulOptionBuilder<Boolean, BooleanOptionBuilder> {
    BooleanOptionBuilder(ConfigBuilder b, ResourceLocation id) {
        super(b, id);
    }

    public Class<Boolean> type() {
        return Boolean.class;
    }

    public Control<Boolean> control(ExtraOption<Boolean> option) {
        return new TickBoxControl(option);
    }
}
