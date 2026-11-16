package org.embeddedt.embeddium.impl.gui.rso;

import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig;
import me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfigEntryPoint;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.gui.modern.SodiumGameOptionPages;
import org.embeddedt.embeddium.impl.gui.modern.framework.ModernDrawContext;
import org.embeddedt.embeddium.impl.gui.options.CommonOptionPages;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.StorageEventHandler;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.structure.ConfigBuilder;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.taumc.celeritas.api.options.structure.OptionFlag;

import java.util.*;

/**
 * Maps the existing Nocturna/legacy option objects into RSO; it does not import Sodium 0.9 code.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NocturnaOptionsSource {
    private static final EnumSet<OptionFlag> pendingFlags = EnumSet.noneOf(OptionFlag.class);
    private static final Set<String> LABELED_GROUPS =
            Set.of(
                    "rendering",
                    "window",
                    "indicators",
                    "graphics",
                    "mipmaps",
                    "details",
                    "chunk_updates",
                    "rendering_culling",
                    "cpu_saving",
                    "sorting",
                    "lighting");

    public static Screen screen(Screen previous) {
        return ReeseSodiumOptionsConfig.config().isEnabled()
                ? new SodiumVideoOptionsScreen(previous)
                : new org.embeddedt.embeddium.impl.gui.modern.EmbeddiumVideoOptionsScreen(previous);
    }

    public static List<ModOptions> create() {
        discardNativeEffects();
        var nativePages =
                new ArrayList<>(
                        List.of(
                                SodiumGameOptionPages.general(),
                                SodiumGameOptionPages.quality(),
                                CommonOptionPages.performance(Celeritas.options()),
                                SodiumGameOptionPages.advanced()));
        org.taumc.celeritas.api.OptionGUIConstructionEvent.BUS.post(
                new org.taumc.celeritas.api.OptionGUIConstructionEvent(nativePages));
        var groups = new LinkedHashMap<String, List<Page>>();
        var states = new HashMap<String, Config>();
        var saves =
                new IdentityHashMap<
                        org.taumc.celeritas.api.options.structure.OptionStorage<?>,
                        StorageEventHandler>();
        int pageIndex = 0;
        for (var page : nativePages) {
            int groupIndex = 0;
            String mod = page.getId().getModId();
            if (mod.equals("celeritas")) mod = "embeddium";
            var state = states.computeIfAbsent(mod, id -> new Config());
            var pageGroups = new ArrayList<OptionGroup>();
            for (var group : page.getGroups()) {
                var options = new ArrayList<Option>();
                int optionIndex = 0;
                for (var option : group.getOptions())
                    options.add(
                            adapt(
                                    option,
                                    state,
                                    saves,
                                    mod
                                            + "/"
                                            + pageIndex
                                            + "/"
                                            + groupIndex
                                            + "/"
                                            + optionIndex++));
                groupIndex++;
                Component label =
                        LABELED_GROUPS.contains(group.getId().getPath())
                                ? Component.translatable(
                                        "nocturna.rso.group." + group.getId().getPath())
                                : Component.empty();
                pageGroups.add(new OptionGroup(label, List.copyOf(options)));
            }
            groups.computeIfAbsent(mod, id -> new ArrayList<>())
                    .add(new Page(text(page.getName()), pageGroups));
            pageIndex++;
        }
        if (org.embeddedt.embeddium.impl.render.ShaderModBridge.isShaderModPresent()) {
            groups.computeIfAbsent("embeddium", id -> new ArrayList<>())
                    .add(
                            new ExternalPage(
                                    Component.translatable("options.iris.shaderPackSelection"),
                                    previous -> {
                                        if (org.embeddedt.embeddium.impl.render.ShaderModBridge
                                                        .openShaderScreen(previous)
                                                instanceof Screen shaderScreen)
                                            Minecraft.getInstance().setScreen(shaderScreen);
                                    }));
        }
        var result = new ArrayList<ModOptions>();
        for (var entry : groups.entrySet()) {
            var container =
                    net.minecraftforge.fml.ModList.get().getModContainerById(entry.getKey());
            String name =
                    container.map(c -> c.getModInfo().getDisplayName()).orElse(entry.getKey());
            String version = container.map(c -> c.getModInfo().getVersion().toString()).orElse("");
            var logo =
                    org.embeddedt.embeddium.impl.loader.common.ModLogoUtil.registerLogo(
                            entry.getKey());
            result.add(
                    new ModOptions(
                            entry.getKey(),
                            name,
                            version,
                            logo,
                            false,
                            ColorTheme.DEFAULT,
                            List.copyOf(entry.getValue())));
        }
        var builder = new ConfigBuilder();
        new ReeseSodiumOptionsConfigEntryPoint().registerConfigLate(builder);
        result.add(builder.build());
        return List.copyOf(result);
    }

    private static Option adapt(
            org.taumc.celeritas.api.options.structure.Option nativeOption,
            Config state,
            IdentityHashMap<
                            org.taumc.celeritas.api.options.structure.OptionStorage<?>,
                            StorageEventHandler>
                    saves,
            String fallbackId) {
        var nativeId = nativeOption.getId();
        ResourceLocation id =
                !org.taumc.celeritas.api.options.OptionIdentifier.isPresent(nativeId)
                        ? new ResourceLocation("embeddium", "anonymous/" + fallbackId)
                        : new ResourceLocation(
                                nativeId.getModId().equals("celeritas")
                                        ? "embeddium"
                                        : nativeId.getModId(),
                                nativeId.getPath());
        Object value = nativeOption.getValue();
        var control = nativeOption.getControl();
        StatefulOption option;
        if (control instanceof org.taumc.celeritas.api.options.control.TickBoxControl) {
            option = new BooleanOption(id, state);
        } else if (control
                instanceof org.taumc.celeritas.api.options.control.SliderControl slider) {
            var integer = new IntegerOption(id, state);
            integer.stepped =
                    new org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option
                            .SteppedValidator(
                            (int) field(slider, "min"),
                            (int) field(slider, "max"),
                            (int) field(slider, "interval"));
            var formatter =
                    (org.taumc.celeritas.api.options.control.ControlValueFormatter)
                            field(slider, "mode");
            integer.formatter = v -> text(formatter.format(v));
            option = integer;
        } else if (control instanceof org.taumc.celeritas.api.options.control.CyclingControl cycler
                && value instanceof Enum<?> e) {
            var enumeration = new EnumOption(id, state, e.getDeclaringClass());
            Object[] allowed = (Object[]) field(cycler, "allowedValues");
            var labels = cycler.getNames();
            enumeration.names =
                    v -> {
                        for (int i = 0; i < allowed.length; i++)
                            if (allowed[i] == v) return text(labels[i]);
                        throw new IllegalArgumentException("Unavailable enum value");
                    };
            enumeration.validator =
                    v -> {
                        for (var candidate : allowed) if (candidate == v) return v;
                        return allowed[0];
                    };
            option = enumeration;
        } else {
            option = new StatefulOption(id, state);
            option.control =
                    new org.embeddedt.embeddium.impl.gui.rso.NativeControlAdapter(
                            nativeOption, option);
        }
        option.name = text(nativeOption.getName());
        option.tooltip = text(nativeOption.getTooltip());
        if (nativeOption.getImpact() != null)
            option.impact =
                    org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option
                            .OptionImpact.valueOf(nativeOption.getImpact().name());
        option.enabled = nativeOption::isAvailable;
        option.getter =
                () -> {
                    nativeOption.reset();
                    return nativeOption.getValue();
                };
        option.setter =
                v -> {
                    nativeOption.setValue(v);
                    nativeOption.applyChanges();
                    pendingFlags.addAll(nativeOption.getFlags());
                };
        option.storage =
                saves.computeIfAbsent(
                        nativeOption.getStorage(),
                        storage -> () -> storage.save(Set.copyOf(pendingFlags)));
        var defaultValue =
                org.embeddedt.embeddium.impl.gui.rso.OptionDefaults.resolve(nativeOption);
        if (defaultValue.isPresent()) option.defaultValue = config -> defaultValue.get();
        else {
            option.state = null;
            // The legacy API has no default/validator metadata. Retain its value semantics.
            if (option instanceof IntegerOption)
                option.validator = java.util.function.UnaryOperator.identity();
        }
        return option;
    }

    public static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read Nocturna option metadata: " + name, e);
            }
        }
        throw new IllegalStateException("Missing Nocturna option metadata: " + name);
    }

    public static Component text(org.embeddedt.embeddium.impl.gui.framework.TextComponent value) {
        return new ModernDrawContext(null, Minecraft.getInstance().font).compile(value);
    }

    private static void applySideEffects(Collection<OptionFlag> flags) {
        var client = Minecraft.getInstance();
        if (client.level != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD))
                client.levelRenderer.allChanged();
            else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE))
                client.levelRenderer.needsUpdate();
        }
        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels().get());
            client.delayTextureReload();
        }
        if (flags.contains(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD)) {
            try {
                net.irisshaders.iris.Iris.reload();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void flushNativeEffects() {
        var flags = EnumSet.copyOf(pendingFlags);
        pendingFlags.clear();
        applySideEffects(flags);
    }

    public static void discardNativeEffects() {
        pendingFlags.clear();
    }
}
