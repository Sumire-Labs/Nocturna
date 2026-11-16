// Sodium Extra 0.9.6-beta.4, LGPL-3.0, upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.client.util;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;

public final class ToastFilter {
    public static boolean isEnabled(Toast toast) {
        var extraSettings = SodiumExtraClientMod.options().extraSettings;

        return extraSettings.toasts
                && (extraSettings.tutorialToast || !(toast instanceof TutorialToast))
                && (extraSettings.systemToast || !(toast instanceof SystemToast))
                && (extraSettings.recipeToast || !(toast instanceof RecipeToast))
                && (extraSettings.advancementToast || !(toast instanceof AdvancementToast));
    }
}
