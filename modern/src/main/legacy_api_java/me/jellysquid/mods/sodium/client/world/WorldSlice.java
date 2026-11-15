package me.jellysquid.mods.sodium.client.world;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraftforge.client.model.data.ModelDataManager;
import me.jellysquid.mods.sodium.client.world.biome.BiomeColorSource;
/** Read-only compatibility view of the active Celeritas meshing snapshot. */
public class WorldSlice implements BlockAndTintGetter {
    private final org.embeddedt.embeddium.impl.world.WorldSlice delegate;
    public final ClientLevel world;
    public WorldSlice(ClientLevel world) { this(new org.embeddedt.embeddium.impl.world.WorldSlice(world)); }
    public WorldSlice(org.embeddedt.embeddium.impl.world.WorldSlice delegate) { this.delegate=delegate; this.world=delegate.world; }
    public org.embeddedt.embeddium.impl.world.WorldSlice unwrap() { return delegate; }
    public BlockState getBlockState(BlockPos pos) { return delegate.getBlockState(pos); }
    public BlockState getBlockState(int x,int y,int z) { return delegate.getBlockState(x,y,z); }
    public FluidState getFluidState(BlockPos pos) { return delegate.getFluidState(pos); }
    public float getShade(Direction direction, boolean shaded) { return delegate.getShade(direction,shaded); }
    public float getShade(float x,float y,float z,boolean shaded) { return delegate.getShade(x,y,z,shaded); }
    public LevelLightEngine getLightEngine() { return delegate.getLightEngine(); }
    public int getBrightness(LightLayer layer,BlockPos pos) { return delegate.getBrightness(layer,pos); }
    public int getRawBrightness(BlockPos pos,int darkness) { return delegate.getRawBrightness(pos,darkness); }
    public BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }
    public BlockEntity getBlockEntity(int x,int y,int z) { return delegate.getBlockEntity(x,y,z); }
    public int getBlockTint(BlockPos pos,ColorResolver resolver) { return delegate.getBlockTint(pos,resolver); }
    public int getHeight() { return delegate.getHeight(); }
    public int getMinBuildHeight() { return delegate.getMinBuildHeight(); }
    public ModelDataManager getModelDataManager() { return delegate.getModelDataManager(); }
    public Holder<Biome> getBiomeFabric(BlockPos pos) { return delegate.getBiomeFabric(pos); }
    public boolean hasBiomes() { return delegate.hasBiomes(); }
    public Object getBlockEntityRenderData(BlockPos pos) { return delegate.getBlockEntityRenderData(pos); }
    public int getColor(BiomeColorSource source,int x,int y,int z) {
        ColorResolver resolver = switch(source) {
            case GRASS -> net.minecraft.client.renderer.BiomeColors.GRASS_COLOR_RESOLVER;
            case FOLIAGE -> net.minecraft.client.renderer.BiomeColors.FOLIAGE_COLOR_RESOLVER;
            case WATER -> net.minecraft.client.renderer.BiomeColors.WATER_COLOR_RESOLVER;
        };
        return delegate.getBlockTint(new BlockPos(x,y,z),resolver);
    }
    public static int getLocalBlockIndex(int x,int y,int z) { return org.embeddedt.embeddium.impl.world.WorldSlice.getLocalBlockIndex(x,y,z); }
    public static int getLocalSectionIndex(int x,int y,int z) { return org.embeddedt.embeddium.impl.world.WorldSlice.getLocalSectionIndex(x,y,z); }
}
