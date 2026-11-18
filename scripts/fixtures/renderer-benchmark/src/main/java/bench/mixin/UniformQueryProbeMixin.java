package bench.mixin;

import bench.QueryProbe;

import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramUniforms", remap = false)
public class UniformQueryProbeMixin {
    @Redirect(
            method = "isActiveProgramBound",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/lwjgl/opengl/GL11C;glGetInteger(I)I",
                            remap = false),
            require = 0)
    private static int notificationQuery(int parameter) {
        QueryProbe.notifications++;
        return GL11C.glGetInteger(parameter);
    }

    @Redirect(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/lwjgl/opengl/GL11C;glGetInteger(I)I",
                            remap = false),
            require = 0)
    private int updateQuery(int parameter) {
        QueryProbe.updates++;
        return GL11C.glGetInteger(parameter);
    }
}
