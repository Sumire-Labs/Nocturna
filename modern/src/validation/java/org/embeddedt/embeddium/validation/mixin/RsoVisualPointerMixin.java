package org.embeddedt.embeddium.validation.mixin;
import org.embeddedt.embeddium.impl.gui.rso.compat.RsoScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=RsoScreen.class,remap=false)
public class RsoVisualPointerMixin {
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,index=2)
    private int fixedX(int value){return Boolean.getBoolean("nocturna.compareRsoReference")?-1000:value;}
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,index=3)
    private int fixedY(int value){return Boolean.getBoolean("nocturna.compareRsoReference")?-1000:value;}
}
