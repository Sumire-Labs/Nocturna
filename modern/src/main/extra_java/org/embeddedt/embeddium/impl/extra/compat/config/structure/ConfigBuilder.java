package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import com.google.common.collect.ImmutableList;

import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;
import org.embeddedt.embeddium.impl.extra.compat.StorageEventHandler;
import org.embeddedt.embeddium.impl.extra.compat.config.ConfigState;

import java.util.*;

public final class ConfigBuilder implements ConfigState {
    private int renderDistanceMax;

    public int integerOptionMax(ResourceLocation id, int fallback) {
        return id.equals(
                        me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper
                                .SODIUM_RENDER_DISTANCE_OPTION_ID)
                ? Math.max(fallback, renderDistanceMax)
                : fallback;
    }

    public final ModOptionsBuilder mod = new ModOptionsBuilder();
    private final Map<ResourceLocation, ExtraOption<?>> options = new LinkedHashMap<>();
    private final Map<ResourceLocation, java.util.function.Supplier<?>> uiValues =
            new LinkedHashMap<>();

    public void bindUiValue(ResourceLocation id, java.util.function.Supplier<?> value) {
        uiValues.put(id, value);
    }

    private final Map<StorageEventHandler, OptionStorage<?>> storages = new IdentityHashMap<>();

    public ModOptionsBuilder registerOwnModOptions() {
        return mod;
    }

    public OptionPageBuilder createOptionPage() {
        return new OptionPageBuilder();
    }

    public OptionGroupBuilder createOptionGroup() {
        return new OptionGroupBuilder();
    }

    public BooleanOptionBuilder createBooleanOption(ResourceLocation id) {
        return new BooleanOptionBuilder(this, id);
    }

    public IntegerOptionBuilder createIntegerOption(ResourceLocation id) {
        return new IntegerOptionBuilder(this, id);
    }

    public <E extends Enum<E>> EnumOptionBuilder<E> createEnumOption(
            ResourceLocation id, Class<E> type) {
        return new EnumOptionBuilder<>(this, id, type);
    }

    public ExternalButtonOptionBuilder createExternalButtonOption(ResourceLocation id) {
        return new ExternalButtonOptionBuilder(this, id);
    }

    public boolean readBooleanOption(ResourceLocation id) {
        var ui = uiValues.get(id);
        if (ui != null) return Boolean.TRUE.equals(ui.get());
        var o = options.get(id);
        return o != null && Boolean.TRUE.equals(o.getValue());
    }

    OptionStorage<?> storage(StorageEventHandler handler) {
        return storages.computeIfAbsent(
                handler,
                h ->
                        new OptionStorage<Object>() {
                            public Object getData() {
                                return h;
                            }

                            public void save() {
                                if (h != null) h.afterSave();
                            }
                        });
    }

    private <T> ExtraOption<T> build(StatefulOptionBuilder<T, ?> spec) {
        var option = new ExtraOption<>(spec);
        options.put(spec.id, option);
        return option;
    }

    public Collection<ExtraOption<?>> getOptions() {
        return Collections.unmodifiableCollection(options.values());
    }

    public void appendTo(List<OptionPage> pages) {
        for (var page : pages)
            for (var option : page.getOptions()) {
                if (option.getId() != null
                        && option.getId()
                                .matches(new ResourceLocation("minecraft", "render_distance"))) {
                    var nativeControl =
                            org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge.toNew(option)
                                    .getControl();
                    if (nativeControl
                            instanceof
                            me.flashyreese.mods.sodiumextra.mixin.fog.AccessorSliderRange range)
                        renderDistanceMax = range.sodiumExtra$getMax();
                }
            }
        // Replace VSync in place, preserving its page/group and all neighboring addon options.
        for (int p = 0; p < pages.size(); p++) {
            OptionPage page = pages.get(p);
            var groups = ImmutableList.<OptionGroup>builder();
            boolean changed = false;
            for (OptionGroup group : page.getGroups()) {
                var next = ImmutableList.<Option<?>>builder();
                for (Option<?> option : group.getOptions()) {
                    if (option.getId() != null
                            && option.getId().matches(new ResourceLocation("minecraft", "vsync"))) {
                        next.add(
                                build(
                                        mod.replacements.get(
                                                new ResourceLocation("sodium:general.vsync"))));
                        changed = true;
                    } else next.add(option);
                    if (option.getId() != null
                            && option.getId()
                                    .matches(new ResourceLocation("minecraft", "clouds"))) {
                        var range =
                                createIntegerOption(
                                                new ResourceLocation("minecraft", "cloud_range"))
                                        .setName(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "nocturna.option.cloud_range"))
                                        .setTooltip(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "nocturna.option.cloud_range.tooltip"))
                                        .setRange(2, 128, 1)
                                        .setDefaultValue(128)
                                        .setBinding(
                                                org.embeddedt.embeddium.impl.extra.compat
                                                                .ExtraRendererOptions
                                                        ::setCloudRange,
                                                org.embeddedt.embeddium.impl.extra.compat
                                                                .ExtraRendererOptions
                                                        ::cloudRange)
                                        .setStorageHandler(
                                                org.embeddedt.embeddium.impl.extra.compat
                                                                .ExtraRendererOptions
                                                        ::save);
                        next.add(build(range));
                        changed = true;
                    }
                }
                groups.add(OptionGroup.fromCompatibility(group.getId(), next.build()));
            }
            if (changed)
                pages.set(
                        p,
                        OptionPage.fromCompatibility(page.getId(), page.getName(), groups.build()));
        }
        int pageIndex = 0;
        for (OptionPageBuilder page : mod.pages) {
            var groups = ImmutableList.<OptionGroup>builder();
            int groupIndex = 0;
            for (OptionGroupBuilder group : page.groups) {
                var list = ImmutableList.<Option<?>>builder();
                if (!group.name.getString().isEmpty())
                    list.add(
                            new GroupHeading(
                                    new ResourceLocation(
                                            "sodium-extra",
                                            "heading_" + pageIndex + "_" + groupIndex),
                                    group.name,
                                    storage(null)));
                for (var spec : group.options) list.add(build(spec));
                groups.add(
                        OptionGroup.fromCompatibility(
                                OptionIdentifier.create(
                                        "sodium-extra",
                                        "page_" + pageIndex + "_group_" + groupIndex++),
                                list.build()));
            }
            pages.add(
                    OptionPage.fromCompatibility(
                            OptionIdentifier.create("sodium-extra", "page_" + pageIndex++),
                            page.name,
                            groups.build()));
        }
    }
}
