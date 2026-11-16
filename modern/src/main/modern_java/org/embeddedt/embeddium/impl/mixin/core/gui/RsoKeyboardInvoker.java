package org.embeddedt.embeddium.impl.mixin.core.gui;

import net.minecraft.client.KeyboardHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface RsoKeyboardInvoker {
    @Invoker("keyPress")
    void nocturna$keyPress(long window, int key, int scan, int action, int modifiers);

    @Invoker("charTyped")
    void nocturna$charTyped(long window, int codepoint, int modifiers);
}
