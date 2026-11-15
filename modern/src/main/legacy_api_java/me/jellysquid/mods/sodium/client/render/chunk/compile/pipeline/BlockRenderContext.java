package me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3fc;
public class BlockRenderContext {
    private final org.embeddedt.embeddium.impl.modern.render.chunk.compile.pipeline.BlockRenderContext delegate;
    private final WorldSlice world;
    public BlockRenderContext(WorldSlice world) { this(new org.embeddedt.embeddium.impl.modern.render.chunk.compile.pipeline.BlockRenderContext(world.unwrap())); }
    public BlockRenderContext(org.embeddedt.embeddium.impl.modern.render.chunk.compile.pipeline.BlockRenderContext delegate) { this.delegate=delegate; this.world=new WorldSlice(delegate.world()); }
    public void update(BlockPos pos, BlockPos origin, BlockState state, BakedModel model, long seed, ModelData data, RenderType layer) {
        delegate.update(org.embeddedt.embeddium.impl.modern.render.chunk.compile.pipeline.GeometryCategory.BLOCK,pos,origin,state,seed);
        delegate.model(model); delegate.setModelData(data); delegate.renderLayer(layer);
    }
    public BlockPos pos() { return delegate.pos(); }
    public WorldSlice world() { return world; }
    public BlockAndTintGetter localSlice() { return delegate.localSlice(); }
    public BlockState state() { return delegate.state(); }
    public PoseStack stack() { return delegate.stack(); }
    public BakedModel model() { return delegate.model(); }
    public Vector3fc origin() { return delegate.origin(); }
    public long seed() { return delegate.seed(); }
    public ModelData modelData() { return delegate.modelData(); }
    public RenderType renderLayer() { return delegate.renderLayer(); }
}
