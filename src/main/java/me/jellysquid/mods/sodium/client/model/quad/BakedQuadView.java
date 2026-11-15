package me.jellysquid.mods.sodium.client.model.quad;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;

public interface BakedQuadView extends ModelQuadView {
    ModelQuadFacing getNormalFace();

    boolean hasShade();

    void setFlags(int flags);

    @Nullable SpriteTransparencyLevel getTransparencyLevel();
}
