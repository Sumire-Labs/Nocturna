package org.embeddedt.embeddium.impl.mixin.core.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;

import org.embeddedt.embeddium.impl.gui.rso.compat.RsoScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class RsoMouseScrollMixin {
    @WrapOperation(
            method = "onScroll",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDD)Z"))
    private boolean nocturna$scrollBothAxes(
            Screen screen,
            double x,
            double y,
            double amount,
            Operation<Boolean> original,
            long window,
            double horizontal,
            double vertical) {
        if (screen instanceof RsoScreen rso) {
            var options = Minecraft.getInstance().options;
            boolean discrete = options.discreteMouseScroll().get();
            double sensitivity = options.mouseWheelSensitivity().get();
            return rso.mouseScrolled(
                    x,
                    y,
                    (discrete ? Math.signum(horizontal) : horizontal) * sensitivity,
                    (discrete ? Math.signum(vertical) : vertical) * sensitivity);
        }
        return original.call(screen, x, y, amount);
    }
}
