package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class IntegerOption extends StatefulOption<Integer> {
    public org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.SteppedValidator
            stepped =
                    new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option
                            .SteppedValidator(0, 100, 1);
    public java.util.function.Function<Integer, Component> formatter =
            v -> Component.literal(String.valueOf(v));

    public IntegerOption(ResourceLocation id, Config state) {
        super(id, state);
        control =
                new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control
                        .SliderControl(this);
        validator = v -> stepped.getValidatedValue(v, () -> defaultValue.get(state));
    }

    @Override
    public boolean isValueAllowed(Integer value) {
        return value != null && stepped.isValueValid(value);
    }

    public org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.SteppedValidator
            getSteppedValidator() {
        return stepped;
    }

    public Component formatValue(int value) {
        return formatter.apply(value);
    }
}
