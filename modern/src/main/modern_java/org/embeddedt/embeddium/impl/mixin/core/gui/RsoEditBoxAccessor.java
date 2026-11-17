package org.embeddedt.embeddium.impl.mixin.core.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface RsoEditBoxAccessor {
    @Accessor("bordered")
    boolean nocturna$isBordered();

    @Accessor("font")
    Font nocturna$getFont();
}
