package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.control.*;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;

public final class EnumOptionBuilder<E extends Enum<E>>
        extends StatefulOptionBuilder<E, EnumOptionBuilder<E>> {
    private final Class<E> type;
    private EnumSet<E> allowedValues;

    EnumOptionBuilder(ConfigBuilder b, ResourceLocation id, Class<E> type) {
        super(b, id);
        this.type = type;
        allowedValues = EnumSet.allOf(type);
    }

    public EnumOptionBuilder<E> setAllowedValues(EnumSet<E> v) {
        allowedValues = v;
        return this;
    }

    public Class<E> type() {
        return type;
    }

    public EnumSet<E> allowedValues() {
        return EnumSet.copyOf(allowedValues);
    }

    public Control<E> control(ExtraOption<E> option) {
        E[] values =
                allowedValues.toArray(
                        java.util.Arrays.copyOf(type.getEnumConstants(), allowedValues.size()));
        return new CyclingControl<>(option, type, values);
    }
}
