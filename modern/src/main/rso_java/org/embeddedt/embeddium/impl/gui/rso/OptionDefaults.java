package org.embeddedt.embeddium.impl.gui.rso;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.embeddedt.embeddium.impl.mixin.core.gui.OptionInstanceAccessor;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionImpl;

import java.util.Optional;

public final class OptionDefaults {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<Object> resolve(Option<?> option) {
        if (!(option instanceof OptionImpl<?, ?>)) return Optional.empty();
        var data = option.getStorage().getData();
        if (data instanceof SodiumGameOptions) {
            var binding =
                    (org.taumc.celeritas.api.options.binding.OptionBinding)
                            NocturnaOptionsSource.field(option, "binding");
            return Optional.ofNullable(binding.getValue(SodiumGameOptions.defaults()));
        }
        var id = option.getId();
        if (id == null || !id.getModId().equals("minecraft")) return Optional.empty();
        var opts = Minecraft.getInstance().options;
        OptionInstance<?> instance =
                switch (id.getPath()) {
                    case "render_distance" -> opts.renderDistance();
                    case "simulation_distance" -> opts.simulationDistance();
                    case "brightness" -> opts.gamma();
                    case "gui_scale" -> opts.guiScale();
                    case "fullscreen" -> opts.fullscreen();
                    case "vsync" -> opts.enableVsync();
                    case "max_frame_rate" -> opts.framerateLimit();
                    case "view_bobbing" -> opts.bobView();
                    case "attack_indicator" -> opts.attackIndicator();
                    case "autosave_indicator" -> opts.showAutosaveIndicator();
                    case "graphics_mode" -> opts.graphicsMode();
                    case "clouds" -> opts.cloudStatus();
                    case "particles" -> opts.particles();
                    case "smooth_lighting" -> opts.ambientOcclusion();
                    case "biome_blend" -> opts.biomeBlendRadius();
                    case "entity_distance" -> opts.entityDistanceScaling();
                    case "entity_shadows" -> opts.entityShadows();
                    case "mipmap_levels" -> opts.mipmapLevels();
                    default -> null;
                };
        if (id.getPath().equals("fullscreen_resolution")) return Optional.of(-1);
        if (instance == null) return Optional.empty();
        Object value = ((OptionInstanceAccessor) (Object) instance).nocturna$getInitialValue();
        if (id.getPath().equals("brightness")) value = (int) ((Double) value / 0.01D);
        if (id.getPath().equals("entity_distance")) value = (int) ((Double) value * 100.0D);
        return Optional.ofNullable(value);
    }
}
