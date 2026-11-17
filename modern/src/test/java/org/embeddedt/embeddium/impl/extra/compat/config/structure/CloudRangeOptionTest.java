package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.api.options.structure.StandardOptions;

class CloudRangeOptionTest {
    @Test
    void cloudRangeKeepsItsStandardTokenAcrossBothFrontends() {
        var standard = StandardOptions.Option.CLOUD_RANGE;
        int[] value = {128};
        var spec =
                new ConfigBuilder()
                        .createIntegerOption(new ResourceLocation("minecraft", "cloud_range"))
                        .setDefaultValue(128)
                        .setBinding(v -> value[0] = v, () -> value[0]);
        var option = new ExtraOption<>(spec);
        var nativeOption = LegacyGuiBridge.toNew(option);
        assertSame(standard, nativeOption.getId());
        assertEquals(Integer.class, option.getId().getType());
        assertEquals(Integer.class, spec.type());
        assertEquals(128, option.getDefaultValue());
        nativeOption.setValue(64);
        option.applyChanges();
        assertEquals(64, value[0]);
        assertSame(option, LegacyGuiBridge.toOld(nativeOption));
    }
}
