// SPDX-License-Identifier: LGPL-3.0-or-later
import net.irisshaders.iris.pathways.scaling.temporal.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

/** Exercises the production conversion/interop/resolve class, including hostile caller state. */
public final class FsrInteropSmoke {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        long window = GLFW.glfwCreateWindow(64,64,"Nocturna FSR interop validation",0,0);
        GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
        try {
            for(int family=2;family<=4;family++) verify(family,640,360,12);
            verify(4,641,361,4);
            try(var small=new TemporalFsrUpscaler(64,64,128,128,4)) {
                System.out.println("PASS FSR4 small input: transport verified by this driver");
            } catch(IllegalStateException unsupported) {
                if(!unsupported.getMessage().contains("GPU output check"))throw unsupported;
                require(GL11C.glGetError()==0,"Failed creation leaked GL errors");
                System.out.println("PASS FSR4 small input: invalid SDK output rejected before enabling pack jitter");
            }
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static int texture(int width,int height,int format) {
        int id=GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(id,1,format,width,height);
        GL45C.glTextureParameteri(id,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
        return id;
    }
    private static String state() {
        var result=new ArrayList<Integer>();
        int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        for(int name:new int[]{GL20C.GL_CURRENT_PROGRAM,GL13C.GL_ACTIVE_TEXTURE,GL30C.GL_READ_FRAMEBUFFER_BINDING,
                GL30C.GL_DRAW_FRAMEBUFFER_BINDING,GL21C.GL_PIXEL_PACK_BUFFER_BINDING,GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING,
                GL11C.GL_PACK_ALIGNMENT,GL11C.GL_PACK_ROW_LENGTH,GL11C.GL_PACK_SKIP_PIXELS,GL11C.GL_PACK_SKIP_ROWS})result.add(GL11C.glGetInteger(name));
        result.add(GL11C.glGetInteger(GL11C.GL_UNPACK_SWAP_BYTES));
        for(int i=0;i<3;i++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+i);
            result.add(GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
            result.add(GL11C.glGetInteger(GL13C.GL_TEXTURE_BINDING_CUBE_MAP));
            result.add(GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,i));
            for(int name:new int[]{GL42C.GL_IMAGE_BINDING_NAME,GL42C.GL_IMAGE_BINDING_LEVEL,GL42C.GL_IMAGE_BINDING_LAYERED,
                    GL42C.GL_IMAGE_BINDING_LAYER,GL42C.GL_IMAGE_BINDING_ACCESS,GL42C.GL_IMAGE_BINDING_FORMAT})result.add(GL30C.glGetIntegeri(name,i));
        }
        GL13C.glActiveTexture(active);
        for(int cap:new int[]{GL11C.GL_SCISSOR_TEST,GL30C.GL_FRAMEBUFFER_SRGB,GL11C.GL_BLEND,GL11C.GL_DEPTH_TEST})result.add(GL11C.glIsEnabled(cap)?1:0);
        return result.toString();
    }
    private static void verify(int family,int width,int height,int frames) throws Exception {
        int color=texture(width,height,GL11C.GL_RGBA8), depth=texture(width,height,GL30C.GL_R32F), motion=texture(width,height,GL30C.GL_RG16F);
        int output=texture(width*2,height*2,GL11C.GL_RGBA8), framebuffer=GL45C.glCreateFramebuffers();
        GL45C.glNamedFramebufferTexture(framebuffer,GL30C.GL_COLOR_ATTACHMENT0,output,0);
        var source=MemoryUtil.memAlloc(width*height*4);
        try {
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                source.put((byte)Math.round(255*(0.2+0.6*x/(width-1.0))));
                source.put((byte)Math.round(255*(0.2+0.6*y/(height-1.0))));
                source.put((byte)102).put((byte)255);
            }
            source.flip();
            GL45C.glTextureSubImage2D(color,0,0,0,width,height,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,source);
        } finally {MemoryUtil.memFree(source);}
        GL44C.glClearTexImage(depth,0,GL11C.GL_RED,GL11C.GL_FLOAT,new float[]{0.5f});
        GL44C.glClearTexImage(motion,0,GL30C.GL_RG,GL11C.GL_FLOAT,new float[]{0,0});
        int sentinel=texture(1,1,GL11C.GL_RGBA8), cube=GL45C.glCreateTextures(GL13C.GL_TEXTURE_CUBE_MAP), sampler=GL33C.glGenSamplers();
        GL45C.glTextureStorage2D(cube,1,GL11C.GL_RGBA8,1,1);
        int pbo=GL45C.glCreateBuffers(); GL45C.glNamedBufferStorage(pbo,64,0);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,pbo);
        GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,9);GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS,1);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES,1);
        for(int i=0;i<3;i++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+i);
            GL11C.glBindTexture(GL13C.GL_TEXTURE_CUBE_MAP,cube);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,i==0?sentinel:0);
            GL33C.glBindSampler(i,sampler);
            GL42C.glBindImageTexture(i,sentinel,0,false,0,GL15C.GL_READ_ONLY,GL11C.GL_RGBA8);
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,framebuffer);
        GL11C.glEnable(GL11C.GL_SCISSOR_TEST);GL11C.glEnable(GL30C.GL_FRAMEBUFFER_SRGB);
        GL11C.glEnable(GL11C.GL_BLEND);GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glScissor(2,4,19,23);GL11C.glViewport(3,5,31,37);GL11C.glDepthMask(false);
        GL11C.glColorMask(false,true,false,true); GL13C.glActiveTexture(GL13C.GL_TEXTURE3);
        String initial=state();
        var pixels=MemoryUtil.memAlloc(width*height*16);
        try(var fsr=new TemporalFsrUpscaler(width,height,width*2,height*2,family)) {
            require(initial.equals(state()),"Context creation changed GL state");
            for(int frame=0;frame<frames;frame++) {
                float[] jitter=fsr.jitter(frame);
                fsr.process(color,depth,motion,framebuffer,jitter[0],jitter[1],16.666667f,0.05f,256,1.2f,0,frame==0||frame==6);
                require(initial.equals(state()),"Resolve changed GL state");
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,0);
                GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,0);GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS,0);
                GL45C.glGetTextureImage(output,0,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,pixels);
                for(int y:new int[]{height/2,height+height/2})for(int x:new int[]{width/2,width+width/2}) {
                    int index=(y*width*2+x)*4;
                    double[] expected={255*(0.2+0.6*(x+0.5)/(width*2)),255*(0.2+0.6*(y+0.5)/(height*2)),102};
                    for(int c=0;c<3;c++)require(Math.abs(Byte.toUnsignedInt(pixels.get(index+c))-expected[c])<12,
                            "FSR "+family+" gradient/orientation/gamma mismatch frame="+frame+" channel="+c+" got="+Byte.toUnsignedInt(pixels.get(index+c))+" expected="+expected[c]);
                }
                GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,9);GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS,1);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,pbo);
                require(GL11C.glGetError()==0,"OpenGL error after temporal resolve");
            }
            System.out.println("PASS FSR "+fsr.provider()+" "+width+"x"+height+": gradient, sRGB round trip, orientation, state preservation, history reset, ring reuse");
            fsr.close();
            try {fsr.jitter(0);throw new AssertionError("Closed wrapper allowed jitter");}
            catch(IllegalStateException expected) { }
            fsr.close(); // The Java owner remains idempotent.
        } finally {
            MemoryUtil.memFree(pixels);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,0);GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,0);GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS,0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES,0);
            for(int i=0;i<3;i++){GL33C.glBindSampler(i,0);GL42C.glBindImageTexture(i,0,0,false,0,GL15C.GL_READ_ONLY,GL11C.GL_RGBA8);}
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL11C.glDeleteTextures(new int[]{color,depth,motion,output,sentinel,cube});GL30C.glDeleteFramebuffers(framebuffer);
            GL33C.glDeleteSamplers(sampler);GL15C.glDeleteBuffers(pbo);
        }
    }
}
