// SPDX-License-Identifier: LGPL-3.0-or-later
import net.irisshaders.iris.pathways.scaling.temporal.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Compare Java GL transport with independent CPU-prepared SDK inputs, including motion in both axes. */
public final class FsrMotionReference {
    private static final int W=640,H=360;
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        long window=GLFW.glfwCreateWindow(64,64,"FSR motion transport reference",0,0);
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        try {FsrNative.load();for(int family=2;family<=4;family++)verify(family);}
        finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int texture(int w,int h,int format) {
        int texture=GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(texture,1,format,w,h);
        GL45C.glTextureParameteri(texture,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
        return texture;
    }
    private static float linear(int channel) {
        double c=channel/255.0;
        return (float)(c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4));
    }
    private static int srgb(float channel) {
        double c=Math.max(0,channel);
        return (int)Math.round(Math.max(0,Math.min(1,c<=0.0031308?c*12.92:1.055*Math.pow(c,1/2.4)-0.055))*255);
    }
    private static void verify(int family) throws Exception {
        int color=texture(W,H,GL11C.GL_RGBA8),depth=texture(W,H,GL30C.GL_R32F),motion=texture(W,H,GL30C.GL_RG16F);
        int output=texture(W*2,H*2,GL11C.GL_RGBA8),target=GL45C.glCreateFramebuffers();
        GL45C.glNamedFramebufferTexture(target,GL30C.GL_COLOR_ATTACHMENT0,output,0);
        long reference=FsrNative.create(FsrNative.runtime().toString(),W,H,W*2,H*2,family);
        var encoded=MemoryUtil.memAlloc(W*H*4);
        var decoded=MemoryUtil.memAllocFloat(W*H*4);
        var expected=MemoryUtil.memAllocFloat(W*H*16);
        var actual=MemoryUtil.memAlloc(W*H*16);
        try(var upscaler=new TemporalFsrUpscaler(W,H,W*2,H*2,family)) {
            // Match the production wrapper's one validation dispatch; actual frame zero resets both histories.
            int[] warm=FsrNative.acquire(reference);
            GL44C.glClearTexImage(warm[0],0,GL11C.GL_RGBA,GL11C.GL_FLOAT,new float[]{linear(64),linear(128),linear(191),1});
            GL44C.glClearTexImage(warm[1],0,GL11C.GL_RED,GL11C.GL_FLOAT,new float[]{0.5f});
            GL44C.glClearTexImage(warm[2],0,GL30C.GL_RG,GL11C.GL_FLOAT,new float[]{0,0});
            float[] warmJitter=FsrNative.jitter(reference,0);
            FsrNative.execute(reference,warmJitter[0],warmJitter[1],16.666667f,0.05f,256,1.2f,0,true);
            int largestDifference=0;
            for(int frame=0;frame<8;frame++) {
                float[] jitter=upscaler.jitter(frame);
                encoded.clear();decoded.clear();
                for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                    double wx=x+0.5-jitter[0]-frame*1.25,wy=y+0.5-jitter[1]-frame*0.75;
                    int r=(int)Math.round(128+70*Math.sin(wx*0.065)),g=(int)Math.round(128+65*Math.cos(wy*0.085));
                    int b=(int)Math.round(120+50*Math.sin((wx+wy)*0.055));
                    int offset=(y*W+x)*4;
                    encoded.put(offset,(byte)r).put(offset+1,(byte)g).put(offset+2,(byte)b).put(offset+3,(byte)255);
                    int flipped=((H-1-y)*W+x)*4;
                    decoded.put(flipped,linear(r)).put(flipped+1,linear(g)).put(flipped+2,linear(b)).put(flipped+3,1);
                }
                GL45C.glTextureSubImage2D(color,0,0,0,W,H,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,encoded);
                GL44C.glClearTexImage(depth,0,GL11C.GL_RED,GL11C.GL_FLOAT,new float[]{0.5f});
                GL44C.glClearTexImage(motion,0,GL30C.GL_RG,GL11C.GL_FLOAT,new float[]{-1.25f/W,-0.75f/H});
                upscaler.process(color,depth,motion,target,jitter[0],jitter[1],16.666667f,0.05f,256,1.2f,0,frame==0);
                int[] raw=FsrNative.acquire(reference);
                GL45C.glTextureSubImage2D(raw[0],0,0,0,W,H,GL11C.GL_RGBA,GL11C.GL_FLOAT,decoded);
                GL44C.glClearTexImage(raw[1],0,GL11C.GL_RED,GL11C.GL_FLOAT,new float[]{0.5f});
                GL44C.glClearTexImage(raw[2],0,GL30C.GL_RG,GL11C.GL_FLOAT,new float[]{-1.25f/W,0.75f/H});
                FsrNative.execute(reference,jitter[0],jitter[1],16.666667f,0.05f,256,1.2f,0,frame==0);
                GL45C.glGetTextureImage(raw[3],0,GL11C.GL_RGBA,GL11C.GL_FLOAT,expected);
                GL45C.glGetTextureImage(output,0,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,actual);
                for(int y=16;y<H*2-16;y+=11)for(int x=16;x<W*2-16;x+=13)for(int c=0;c<3;c++) {
                    int got=Byte.toUnsignedInt(actual.get((y*W*2+x)*4+c));
                    int wanted=srgb(expected.get(((H*2-1-y)*W*2+x)*4+c));
                    largestDifference=Math.max(largestDifference,Math.abs(got-wanted));
                }
                if(GL11C.glGetError()!=0)throw new AssertionError("Motion reference GL error");
            }
            if(largestDifference>3)throw new AssertionError("Motion transport diverged from CPU-prepared SDK reference: "+largestDifference);
            System.out.println("PASS FSR "+family+" moving XY scene: CPU/SDK reference max error="+largestDifference+"/255");
        } finally {
            FsrNative.close(reference);
            MemoryUtil.memFree(encoded);MemoryUtil.memFree(decoded);MemoryUtil.memFree(expected);MemoryUtil.memFree(actual);
            GL11C.glDeleteTextures(new int[]{color,depth,motion,output});GL30C.glDeleteFramebuffers(target);
        }
    }
}
