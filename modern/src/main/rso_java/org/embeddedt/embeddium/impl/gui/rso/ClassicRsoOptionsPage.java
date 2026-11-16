package org.embeddedt.embeddium.impl.gui.rso;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfigEntryPoint;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge.VanillaText;
import org.embeddedt.embeddium.impl.gui.framework.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure.ConfigBuilder;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.*;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import java.util.*;

/** Keeps all RSO preferences accessible when its replacement screen is disabled. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ClassicRsoOptionsPage {
    public static org.taumc.celeritas.api.options.structure.OptionPage create() {
        var builder = new ConfigBuilder();
        new ReeseSodiumOptionsConfigEntryPoint().registerConfigLate(builder);
        var source = builder.build().pages().get(0);
        var saves =
                Collections.newSetFromMap(
                        new IdentityHashMap<
                                org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config
                                        .StorageEventHandler,
                                Boolean>());
        var hooks = new ArrayList<Runnable>();
        var storage =
                new OptionStorage<Object>() {
                    public Object getData() {
                        return this;
                    }

                    public void save() {
                        try {
                            saves.forEach(handler -> handler.save());
                            hooks.forEach(Runnable::run);
                        } finally {
                            saves.clear();
                            hooks.clear();
                        }
                    }
                };
        var groups = new ArrayList<org.taumc.celeritas.api.options.structure.OptionGroup>();
        for (var group : source.groups()) {
            var target = org.taumc.celeritas.api.options.structure.OptionGroup.createBuilder();
            for (var option : group.options()) {
                Class type =
                        option instanceof BooleanOption
                                ? boolean.class
                                : option instanceof IntegerOption
                                        ? int.class
                                        : option instanceof EnumOption<?> enumeration
                                                ? enumeration.getEnumClass()
                                                : boolean.class;
                var entry =
                        OptionImpl.createBuilder(type, storage)
                                .setId(
                                        OptionIdentifier.create(
                                                option.id.getNamespace(),
                                                option.id.getPath(),
                                                type))
                                .setName(new VanillaText(option.getName()))
                                .setTooltip(new VanillaText(option.getTooltip()));
                if (option instanceof StatefulOption value) {
                    entry.setBinding(
                            (state, next) -> {
                                value.modifyValue(next);
                                value.apply();
                                if (value.storage != null) saves.add(value.storage);
                                if (value.applyHook != null)
                                    hooks.add(() -> value.applyHook.accept(value.state));
                            },
                            state -> value.getter.get());
                } else entry.setBinding((state, next) -> {}, state -> false);
                if (option instanceof BooleanOption)
                    entry.setControl(
                            nativeOption ->
                                    new TickBoxControl(
                                            (org.taumc.celeritas.api.options.structure.Option)
                                                    nativeOption));
                else if (option instanceof IntegerOption integer)
                    entry.setControl(
                            nativeOption ->
                                    new SliderControl(
                                            (org.taumc.celeritas.api.options.structure.Option)
                                                    nativeOption,
                                            integer.stepped.min(),
                                            integer.stepped.max(),
                                            integer.stepped.step(),
                                            n -> new VanillaText(integer.formatValue(n))));
                else if (option instanceof EnumOption enumeration) {
                    Object[] values = enumeration.getEnumClass().getEnumConstants();
                    TextComponent[] names = new TextComponent[values.length];
                    for (int i = 0; i < values.length; i++)
                        names[i] = new VanillaText(enumeration.getElementName((Enum) values[i]));
                    entry.setControl(
                            nativeOption ->
                                    new CyclingControl(
                                            (org.taumc.celeritas.api.options.structure.Option)
                                                    nativeOption,
                                            values,
                                            names));
                } else if (option instanceof ExternalButtonOption external)
                    entry.setControl(
                            nativeOption ->
                                    new Control() {
                                        public org.taumc.celeritas.api.options.structure.Option
                                                getOption() {
                                            return (org.taumc.celeritas.api.options.structure
                                                            .Option)
                                                    nativeOption;
                                        }

                                        public int getMaxWidth() {
                                            return 0;
                                        }

                                        public ControlElement createElement(
                                                org.embeddedt.embeddium.impl.util.Dim2i dim) {
                                            return new ControlElement(
                                                    (org.taumc.celeritas.api.options.structure
                                                                    .Option)
                                                            nativeOption,
                                                    dim) {
                                                public boolean mouseClicked(
                                                        InteractionContext context,
                                                        double x,
                                                        double y,
                                                        int button) {
                                                    if (button != 0 || !isMouseOver(x, y))
                                                        return false;
                                                    external.action.accept(
                                                            Minecraft.getInstance().screen);
                                                    return true;
                                                }
                                            };
                                        }
                                    });
                target.add(entry.build());
            }
            groups.add(target.build());
        }
        return new org.taumc.celeritas.api.options.structure.OptionPage(
                OptionIdentifier.create("reeses-sodium-options", "options"),
                new VanillaText(source.name()),
                groups);
    }
}
