package org.embeddedt.embeddium.impl.extra.compat;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Supplier;

/** The complete beta.4 animation id lists and live setting predicates. */
public final class ExtraAnimations {

    private static final Map<Supplier<Boolean>, List<ResourceLocation>> animatedSprites =
            Map.of(
                    () -> SodiumExtraClientMod.options().animationSettings.water,
                            List.of(
                                    new ResourceLocation("minecraft", "block/water_still"),
                                    new ResourceLocation("minecraft", "block/water_flow")),
                    () -> SodiumExtraClientMod.options().animationSettings.lava,
                            List.of(
                                    new ResourceLocation("minecraft", "block/lava_still"),
                                    new ResourceLocation("minecraft", "block/lava_flow")),
                    () -> SodiumExtraClientMod.options().animationSettings.portal,
                            List.of(new ResourceLocation("minecraft", "block/nether_portal")),
                    () -> SodiumExtraClientMod.options().animationSettings.fire,
                            List.of(
                                    new ResourceLocation("minecraft", "block/fire_0"),
                                    new ResourceLocation("minecraft", "block/fire_1"),
                                    new ResourceLocation("minecraft", "block/soul_fire_0"),
                                    new ResourceLocation("minecraft", "block/soul_fire_1"),
                                    new ResourceLocation("minecraft", "block/campfire_fire"),
                                    new ResourceLocation("minecraft", "block/campfire_log_lit"),
                                    new ResourceLocation("minecraft", "block/soul_campfire_fire"),
                                    new ResourceLocation(
                                            "minecraft", "block/soul_campfire_log_lit")),
                    () -> SodiumExtraClientMod.options().animationSettings.blockAnimations,
                            List.of(
                                    new ResourceLocation("minecraft", "block/magma"),
                                    new ResourceLocation("minecraft", "block/lantern"),
                                    new ResourceLocation("minecraft", "block/sea_lantern"),
                                    new ResourceLocation("minecraft", "block/soul_lantern"),
                                    new ResourceLocation("minecraft", "block/kelp"),
                                    new ResourceLocation("minecraft", "block/kelp_plant"),
                                    new ResourceLocation("minecraft", "block/seagrass"),
                                    new ResourceLocation("minecraft", "block/tall_seagrass_top"),
                                    new ResourceLocation("minecraft", "block/tall_seagrass_bottom"),
                                    new ResourceLocation("minecraft", "block/warped_stem"),
                                    new ResourceLocation("minecraft", "block/crimson_stem"),
                                    new ResourceLocation(
                                            "minecraft", "block/blast_furnace_front_on"),
                                    new ResourceLocation("minecraft", "block/smoker_front_on"),
                                    new ResourceLocation("minecraft", "block/stonecutter_saw"),
                                    new ResourceLocation("minecraft", "block/prismarine"),
                                    new ResourceLocation("minecraft", "block/respawn_anchor_top"),
                                    new ResourceLocation("minecraft", "entity/conduit/wind"),
                                    new ResourceLocation(
                                            "minecraft", "entity/conduit/wind_vertical")),
                    () -> SodiumExtraClientMod.options().animationSettings.sculkSensor,
                            List.of(
                                    new ResourceLocation("minecraft", "block/sculk"),
                                    new ResourceLocation(
                                            "minecraft", "block/sculk_catalyst_top_bloom"),
                                    new ResourceLocation(
                                            "minecraft", "block/sculk_catalyst_side_bloom"),
                                    new ResourceLocation(
                                            "minecraft", "block/sculk_shrieker_inner_top"),
                                    new ResourceLocation("minecraft", "block/sculk_vein"),
                                    new ResourceLocation(
                                            "minecraft",
                                            "block/sculk_shrieker_can_summon_inner_top"),
                                    new ResourceLocation(
                                            "minecraft", "block/sculk_sensor_tendril_inactive"),
                                    new ResourceLocation(
                                            "minecraft", "block/sculk_sensor_tendril_active"),
                                    new ResourceLocation("minecraft", "vibration")));

    public static boolean shouldAnimate(ResourceLocation id) {
        if (!SodiumExtraClientMod.options().animationSettings.animation) return false;
        if (id != null)
            for (var entry : animatedSprites.entrySet())
                if (entry.getValue().contains(id)) return entry.getKey().get();
        return true;
    }
}
