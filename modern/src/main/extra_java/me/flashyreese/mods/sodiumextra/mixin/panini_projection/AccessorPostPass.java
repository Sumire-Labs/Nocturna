package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.PostPass.class)
public interface AccessorPostPass {
    @Mutable
    @Accessor("inTarget")
    void sodiumExtra$setInTarget(RenderTarget target);

    @Mutable
    @Accessor("outTarget")
    void sodiumExtra$setOutTarget(RenderTarget target);

    @Accessor("auxAssets")
    java.util.List<java.util.function.IntSupplier> sodiumExtra$getAuxAssets();

    @Accessor("auxWidths")
    java.util.List<Integer> sodiumExtra$getAuxWidths();

    @Accessor("auxHeights")
    java.util.List<Integer> sodiumExtra$getAuxHeights();

    @Accessor("shaderOrthoMatrix")
    org.joml.Matrix4f sodiumExtra$getOrthoMatrix();
}
