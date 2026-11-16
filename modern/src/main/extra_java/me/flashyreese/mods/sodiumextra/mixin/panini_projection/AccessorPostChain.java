package me.flashyreese.mods.sodiumextra.mixin.panini_projection;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.*;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PostChain.class)
public interface AccessorPostChain {
    @Accessor("passes")
    java.util.List<PostPass> sodiumExtra$getPasses();

    @Accessor("fullSizedTargets")
    java.util.List<RenderTarget> sodiumExtra$getFullSizedTargets();

    @Accessor("screenTarget")
    RenderTarget sodiumExtra$getScreenTarget();

    @Mutable
    @Accessor("screenTarget")
    void sodiumExtra$setScreenTarget(RenderTarget target);
}
