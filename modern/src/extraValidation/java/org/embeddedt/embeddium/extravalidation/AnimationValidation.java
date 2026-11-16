package org.embeddedt.embeddium.extravalidation;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.common.util.AnimationStateExtended;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.lang.reflect.Field;
import java.util.List;

final class AnimationValidation {
    static void verify(Minecraft client) throws Exception {
        TextureAtlas atlas = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        Field states = TextureAtlas.class.getDeclaredField("animatedTextures");
        states.setAccessible(true);
        var ticks = (List<?>) states.get(atlas);
        Object water = raw(ticks, new ResourceLocation("minecraft", "block/water_still"));
        Object lava = raw(ticks, new ResourceLocation("minecraft", "block/lava_still"));
        var settings = SodiumExtraClientMod.options().animationSettings;
        boolean all = settings.animation,
                waterEnabled = settings.water,
                lavaEnabled = settings.lava;
        try {
            settings.animation = false;
            long beforeWater = clock(water), beforeLava = clock(lava);
            for (int i = 0; i < 3; i++) atlas.cycleAnimationFrames();
            ExtraValidation.require(
                    beforeWater == clock(water) && beforeLava == clock(lava),
                    "disabled animation advanced its clock");
            settings.animation = true;
            settings.water = false;
            settings.lava = true;
            for (int i = 0; i < 3; i++) atlas.cycleAnimationFrames();
            ExtraValidation.require(
                    beforeWater == clock(water), "disabled water advanced its clock");
            ExtraValidation.require(
                    beforeLava != clock(lava), "disabling water stopped unrelated lava");
            System.out.println(
                    "PASS atlas-only animation clock freeze and independent water/lava controls");
        } finally {
            settings.animation = all;
            settings.water = waterEnabled;
            settings.lava = lavaEnabled;
        }
    }

    private static Object raw(List<?> states, ResourceLocation id) throws Exception {
        for (Object state : states) {
            if (state instanceof AnimationStateExtended extended
                    && extended.sodium_extra$getSprite().contents().name().equals(id)) {
                for (Field field : state.getClass().getDeclaredFields())
                    if (SpriteTicker.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field.get(state);
                    }
            }
        }
        throw new AssertionError("missing native animation state: " + id);
    }

    private static long clock(Object ticker) throws Exception {
        Field frame = ticker.getClass().getDeclaredField("frame"),
                sub = ticker.getClass().getDeclaredField("subFrame");
        frame.setAccessible(true);
        sub.setAccessible(true);
        return frame.getInt(ticker) * 100000L + sub.getInt(ticker);
    }
}
