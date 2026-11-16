package me.flashyreese.mods.sodiumextra.mixin.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.embeddedt.embeddium.impl.extra.compat.DebugVisuals;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture on the search thread and publish an immutable result; never read its mutable lattice from
 * rendering.
 */
@Mixin(value = OcclusionCuller.class, remap = false)
public class MixinDebugTraversal {
    @Shadow @Final private SectionLattice lattice;
    @Shadow private long[] visitState;
    @Unique private List<DebugVisuals.Node> nocturna$nodes;

    @Inject(method = "findVisible", at = @At("HEAD"))
    private void nocturna$begin(
            OcclusionCuller.Visitor visitor,
            Viewport viewport,
            long[] visits,
            float distance,
            int regions,
            boolean occlusion,
            boolean clamp,
            boolean record,
            int frame,
            CallbackInfo ci) {
        nocturna$nodes =
                DebugVisuals.captureRequested
                                && visits
                                        == ((AccessorDebugLattice) (Object) lattice)
                                                .nocturna$getMainVisits()
                        ? new ArrayList<>()
                        : null;
    }

    @WrapOperation(
            method = {"process", "initWithinWorld"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller$Visitor;visit(IIIIIIIZ)V"))
    private void nocturna$node(
            OcclusionCuller.Visitor visitor,
            int index,
            int region,
            int local,
            int x,
            int y,
            int z,
            int metadata,
            boolean visible,
            Operation<Void> original) {
        original.call(visitor, index, region, local, x, y, z, metadata, visible);
        if (nocturna$nodes != null)
            nocturna$nodes.add(
                    new DebugVisuals.Node(x, y, z, (int) (visitState[index] & 63), visible));
    }

    @Inject(method = "findVisible", at = @At("RETURN"))
    private void nocturna$publish(CallbackInfo ci) {
        if (nocturna$nodes != null) {
            DebugVisuals.publish(nocturna$nodes);
            nocturna$nodes = null;
        }
    }
}
