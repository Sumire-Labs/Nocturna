package net.irisshaders.iris.pathways.scaling;

import net.irisshaders.iris.pathways.scaling.temporal.TemporalCamera;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemporalCameraTest {
    @Test void extractsActualDepthPlanesAndFovAcrossRenderDistancesAndZooms() {
        for(float near:new float[]{0.05f,0.1f})for(float far:new float[]{192,512,2048})for(float fov:new float[]{0.2f,1.2f,2.0f}) {
            var camera=TemporalCamera.fromProjection(new Matrix4f().perspective(fov,16f/9,near,far));
            assertEquals(near,camera.near(),0.000001);
            assertEquals(far,camera.far(),far*0.005);
            assertEquals(fov,camera.verticalFov(),0.000001);
        }
    }
    @Test void rejectsUnsupportedDepthBeforeDispatch() {
        assertThrows(IllegalArgumentException.class,()->TemporalCamera.fromProjection(new Matrix4f()));
        assertThrows(IllegalArgumentException.class,()->TemporalCamera.fromProjection(new Matrix4f().ortho(-1,1,-1,1,0.05f,256)));
        assertThrows(IllegalArgumentException.class,()->TemporalCamera.fromProjection(new Matrix4f().perspective(1.2f,1,0.05f,Float.POSITIVE_INFINITY)));
    }
}
