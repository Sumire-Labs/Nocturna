package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.control.*;

import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.extra.compat.config.ConfigState;
import org.embeddedt.embeddium.impl.extra.compat.config.option.*;

import java.util.function.Function;

public final class IntegerOptionBuilder
        extends StatefulOptionBuilder<Integer, IntegerOptionBuilder> {
    public Function<ConfigState, Range> rangeProvider = s -> new Range(0, 100, 1);
    public org.embeddedt.embeddium.impl.extra.compat.config.option.ControlValueFormatter formatter =
            v -> net.minecraft.network.chat.Component.literal(Integer.toString(v));

    IntegerOptionBuilder(ConfigBuilder b, ResourceLocation id) {
        super(b, id);
    }

    public IntegerOptionBuilder setRange(int min, int max, int step) {
        return setRange(new Range(min, max, step));
    }

    public IntegerOptionBuilder setRange(Range v) {
        rangeProvider = s -> v;
        return this;
    }

    public IntegerOptionBuilder setRangeProvider(
            Function<ConfigState, Range> v, ResourceLocation... dependencies) {
        rangeProvider = v;
        return this;
    }

    public IntegerOptionBuilder setValueFormatter(
            org.embeddedt.embeddium.impl.extra.compat.config.option.ControlValueFormatter v) {
        formatter = v;
        return this;
    }

    public Class<Integer> type() {
        return Integer.class;
    }

    public Control<Integer> control(ExtraOption<Integer> option) {
        Range r = rangeProvider.apply(owner);
        return new SliderControl(option, r.min(), r.max(), r.step(), formatter::format);
    }
}
