package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.resources.ResourceLocation;

public final class BooleanOption extends StatefulOption<Boolean> {
    public BooleanOption(ResourceLocation id, Config state) {
        super(id, state);
        control =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control
                        .TickBoxControl(this);
    }
}
