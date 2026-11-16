package org.embeddedt.embeddium.extravalidation;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.gui.rso.NocturnaOptionsSource;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;

import java.util.*;

final class RsoIntegration {
    static void verify(Minecraft client) {
        var mods = NocturnaOptionsSource.create();
        var extra =
                mods.stream()
                        .filter(mod -> mod.configId().equals("sodium-extra"))
                        .findFirst()
                        .orElseThrow();
        ExtraValidation.require(
                extra.name().equals("Sodium Extra") && extra.pages().size() == 5,
                "Extra RSO section metadata differs");
        var options = new ArrayList<Option>();
        extra.pages()
                .forEach(page -> page.groups().forEach(group -> options.addAll(group.options())));
        var advanced = (BooleanOption) find(options, "advanced_fog_settings");
        var dimension = (BooleanOption) find(options, "multi_dimension_fog");
        advanced.modifyValue(true);
        ExtraValidation.require(
                dimension.isEnabled(), "RSO pending Advanced did not enable dimension controls");
        ExtraValidation.require(
                !me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod.options()
                        .renderSettings
                        .fogSettings
                        .advanced,
                "RSO pending state leaked into live config");
        advanced.resetToDefault();
        ExtraValidation.require(
                !advanced.getValidatedValue() && !dimension.isEnabled(),
                "RSO default reset did not refresh dependencies");
        for (var option : options) {
            ExtraValidation.require(
                    option instanceof BooleanOption
                            || option instanceof IntegerOption
                            || option instanceof EnumOption<?>
                            || option instanceof ExternalButtonOption,
                    "Extra lost its typed RSO control: " + option.id);
            if (option instanceof StatefulOption<?> value)
                ExtraValidation.require(
                        value.getDefaultValue() != null,
                        "RSO reset metadata missing: " + option.id);
        }
        ExtraValidation.require(
                extra.pages().stream()
                        .flatMap(page -> page.groups().stream())
                        .anyMatch(group -> !group.name().getString().isEmpty()),
                "RSO group titles missing");
        var screen = NocturnaOptionsSource.screen(client.screen);
        client.setScreen(screen);
        ExtraValidation.require(
                screen
                        instanceof
                        me.flashyreese.mods.reeses_sodium_options.client.gui
                                .SodiumVideoOptionsScreen,
                "RSO screen did not open");
        client.setScreen(null);
        System.out.println(
                "PASS typed RSO Extra controls, default reset, pending dependencies, group labels"
                        + " and screen construction");
    }

    private static Option find(List<Option> options, String path) {
        return options.stream()
                .filter(option -> option.id.getPath().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
