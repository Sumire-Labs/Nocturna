package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.TextProvider;

import net.minecraft.network.chat.Component;

import org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.StorageEventHandler;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.OptionImpact;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.SteppedValidator;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.taumc.celeritas.api.options.structure.OptionFlag;

import java.util.*;

/** Retains typed controls, reset metadata and pending dependencies in the embedded RSO frontend. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RsoExtraOptions {
    public static Component heading(org.taumc.celeritas.api.options.structure.Option<?> option) {
        var old = LegacyGuiBridge.toOld(option);
        return old instanceof GroupHeading ? old.getName() : null;
    }

    public static Option adapt(
            org.taumc.celeritas.api.options.structure.Option nativeOption,
            Config state,
            IdentityHashMap<
                            org.taumc.celeritas.api.options.structure.OptionStorage<?>,
                            StorageEventHandler>
                    saves,
            Set<OptionFlag> flags) {
        var old = LegacyGuiBridge.toOld(nativeOption);
        if (!(old instanceof ExtraOption extra)) return null;
        var spec = extra.spec;
        Option option;
        if (spec instanceof ExternalButtonOptionBuilder button) {
            var external = new ExternalButtonOption(spec.id, state);
            external.action = button::openScreen;
            option = external;
        } else {
            StatefulOption value;
            if (spec instanceof BooleanOptionBuilder) value = new BooleanOption(spec.id, state);
            else if (spec instanceof IntegerOptionBuilder integer) {
                var numeric = new IntegerOption(spec.id, state);
                var range = integer.rangeProvider.apply(spec.owner);
                numeric.stepped = new SteppedValidator(range.min(), range.max(), range.step());
                numeric.formatter = integer.formatter::format;
                value = numeric;
            } else if (spec instanceof EnumOptionBuilder enumeration) {
                var cycle = new EnumOption(spec.id, state, enumeration.type());
                var allowed = enumeration.allowedValues();
                cycle.names =
                        v ->
                                v instanceof TextProvider text
                                        ? text.getLocalizedName()
                                        : Component.literal(((Enum<?>) v).name());
                cycle.validator = v -> allowed.contains(v) ? v : allowed.iterator().next();
                value = cycle;
            } else throw new IllegalStateException("Unknown Extra control " + spec.id);
            value.getter =
                    () -> {
                        nativeOption.reset();
                        return nativeOption.getValue();
                    };
            value.setter =
                    v -> {
                        nativeOption.setValue(v);
                        nativeOption.applyChanges();
                        flags.addAll(nativeOption.getFlags());
                    };
            value.defaultValue = config -> extra.getDefaultValue();
            value.storage =
                    saves.computeIfAbsent(
                            nativeOption.getStorage(),
                            storage -> () -> storage.save(Set.copyOf(flags)));
            spec.owner.bindUiValue(spec.id, value::getValidatedValue);
            option = value;
        }
        option.name = spec.name;
        option.tooltip = spec.tooltip;
        option.enabled = extra::isAvailable;
        option.hidden = () -> spec.hideWhenDisabled && !extra.isAvailable();
        if (spec.impact != null) option.impact = OptionImpact.valueOf(spec.impact.name());
        return option;
    }
}
