package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.*;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelRenderer.class)
public interface AccessorLevelRenderer {
    @Accessor("entityTarget")
    RenderTarget sodiumExtra$getEntityOutlineTarget();

    @Accessor("entityEffect")
    PostChain sodiumExtra$getEntityEffect();

    @Accessor("transparencyChain")
    PostChain sodiumExtra$getTransparencyChain();

    @Accessor("capturedFrustum")
    net.minecraft.client.renderer.culling.Frustum sodiumExtra$getCapturedFrustum();

    @Invoker("shouldShowEntityOutlines")
    boolean sodiumExtra$hasEntityOutline();
}
