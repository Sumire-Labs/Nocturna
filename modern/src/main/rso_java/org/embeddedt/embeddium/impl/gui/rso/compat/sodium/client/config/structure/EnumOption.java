package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EnumOption<E extends Enum<E>> extends StatefulOption<E> {
    private final Class<E> type;
    public java.util.function.Function<E, Component> names = v -> Component.literal(v.name());

    public EnumOption(ResourceLocation id, Config state, Class<E> type) {
        super(id, state);
        this.type = type;
        control =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control
                                .CyclingControl<
                        E>(this);
    }

    public Class<E> getEnumClass() {
        return type;
    }

    public Component getElementName(E value) {
        return names.apply(value);
    }
}
