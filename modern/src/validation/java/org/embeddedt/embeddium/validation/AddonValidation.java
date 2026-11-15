package org.embeddedt.embeddium.validation;

import codechicken.lib.render.block.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import java.util.concurrent.atomic.AtomicInteger;

/** Loaded only when the optional compatibility runtime is present. */
public final class AddonValidation {
    public static final AtomicInteger rendered=new AtomicInteger();
    public static void install() {
        BlockRenderingRegistry.registerRenderer(Blocks.GRASS_BLOCK,new ICCBlockRenderer() {
            public boolean canHandleBlock(BlockAndTintGetter world,BlockPos pos,BlockState state,RenderType layer) {return true;}
            public void renderBlock(BlockState state,BlockPos pos,BlockAndTintGetter world,PoseStack stack,VertexConsumer consumer,RandomSource random,ModelData data,RenderType layer) {
                if(world.getBlockState(pos)!=state)throw new AssertionError("CCL snapshot mismatch");
                // Emit an actual quad through the consumer supplied by the Celeritas bridge.
                for(float[] point:new float[][]{{0,0,0},{1,0,0},{1,1,0},{0,1,0}}) {
                    consumer.vertex(point[0],point[1],point[2]).color(255,255,255,255).uv(0,0)
                        .uv2(0xF000F0).normal(0,0,-1).endVertex();
                }
                rendered.incrementAndGet();
            }
        });
    }
    public static void verify() {
        if(rendered.get()==0)throw new AssertionError("CCL renderer was not invoked");
    }

    public static void verifyWireGeometry() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        var config=org.embeddedt.embeddium.impl.render.CeleritasWorldRenderer.instance().getRenderPassConfiguration();
        var buffers=new org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers(config);
        buffers.init(new org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData<>(),0);
        try {
            var a=mc.player.blockPosition();
            var connection=new blusunrize.immersiveengineering.api.wires.Connection(
                blusunrize.immersiveengineering.api.wires.WireType.COPPER,
                new blusunrize.immersiveengineering.api.wires.ConnectionPoint(a,0),
                new blusunrize.immersiveengineering.api.wires.ConnectionPoint(a.offset(4,0,0),0),
                new net.minecraft.world.phys.Vec3(.5,.5,.5),new net.minecraft.world.phys.Vec3(.5,.5,.5));
            var material=config.getMaterialForRenderType(RenderType.solid());
            var builder=buffers.get(material);
            org.embeddedt.embeddium.impl.modern.compat.immersive.ImmersiveConnectionRenderer.renderSegments(
                builder,material,new blusunrize.immersiveengineering.api.wires.WireCollisionData.ConnectionSegments(
                    connection,0,blusunrize.immersiveengineering.api.wires.Connection.RENDER_POINTS_PER_WIRE),mc.level,0,0,0);
            int vertices=builder.getVertexBuffer(org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED).count();
            if(vertices<=0||vertices%4!=0)throw new AssertionError("IE wire geometry was not emitted: "+vertices);
            System.out.println("PASS IE copper wire geometry: "+vertices+" vertices, compact="+org.embeddedt.embeddium.impl.Celeritas.options().performance.useCompactVertexFormat);
        } finally {buffers.destroy();}
    }
}
