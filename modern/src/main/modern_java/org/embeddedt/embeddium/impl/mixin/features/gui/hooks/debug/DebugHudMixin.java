package org.embeddedt.embeddium.impl.mixin.features.gui.hooks.debug;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.render.CeleritasWorldRenderer;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.embeddedt.embeddium.api.EmbeddiumConstants.RENDERER_NAME;

@Mixin(value = DebugScreenOverlay.class, priority = 1200)
public abstract class DebugHudMixin {
    // Append after Sodium Extra rebuilds the selectable vanilla entries.
    @ModifyReturnValue(method = "getSystemInformation", at = @At("RETURN"))
    private List<String> redirectRightText(List<String> strings) {
        return injectRightF3Text(strings);
    }

    private <T extends List<String>> T injectRightF3Text(T strings) {
        strings.add("");
        strings.add("%s%s (%s)".formatted(ChatFormatting.AQUA, RENDERER_NAME, Celeritas.getVersion()));

        // Embeddium: Show a lot less with reduced debug info
        if(Minecraft.getInstance().showOnlyReducedInfo()) {
           return strings;
        }

        var renderer = CeleritasWorldRenderer.instanceNullable();

        if (renderer != null) {
            strings.addAll(renderer.getDebugStrings());
        }

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }

        return strings;
    }

    @Unique
    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    @Unique
    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }
}
