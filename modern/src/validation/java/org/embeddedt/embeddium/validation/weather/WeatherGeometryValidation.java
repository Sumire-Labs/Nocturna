package org.embeddedt.embeddium.validation.weather;

import org.embeddedt.embeddium.impl.render.weather.WeatherGlsl;
import org.embeddedt.embeddium.impl.render.weather.WeatherInstancedAttribs;
import net.irisshaders.iris.pipeline.foss_transform.WeatherVertices;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.nio.*;

/** Read back the actual GPU expansion, rather than testing a Java reimplementation alone. */
public final class WeatherGeometryValidation {
    public static void verify() {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        int shader = GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);
        int program = GL20C.glCreateProgram();
        int vao = GL30C.glGenVertexArrays(), corners = GL15C.glGenBuffers(), instances = GL15C.glGenBuffers(), output = GL15C.glGenBuffers();
        try (var stack = MemoryStack.stackPush()) {
            String source = "#version 150\n" + WeatherVertices.declarations()
                    + "out vec4 p;out vec4 c;out vec4 u;out vec4 l;void main(){"
                    + WeatherGlsl.weatherPrologue("iris_", "iris_", "iris_WeatherCorner", "p", "c", "u", "l", "\n")
                    + "gl_Position=p;}";
            GL20C.glShaderSource(shader, source); GL20C.glCompileShader(shader);
            require(GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != 0, GL20C.glGetShaderInfoLog(shader));
            GL20C.glAttachShader(program, shader);
            WeatherVertices.ATTRIBUTES.forEach((name, index) -> GL20C.glBindAttribLocation(program, index, name));
            GL30C.glTransformFeedbackVaryings(program, new CharSequence[]{"p", "c", "u", "l"}, GL30C.GL_INTERLEAVED_ATTRIBS);
            GL20C.glLinkProgram(program);
            require(GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) != 0, GL20C.glGetProgramInfoLog(program));
            GL20C.glUseProgram(program);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(program,"iris_WeatherParams0"),.25f,-64.5f,.75f,.1f);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(program,"iris_WeatherParams1"),.37f,.49f,.25f,8191.25f);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(program,"iris_WeatherParams2"),1023,511,.73f,57.25f);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(program,"iris_WeatherParams3"),.05f,0,0,0);
            GL30C.glBindVertexArray(vao); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER,corners);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER,stack.floats(0,0,1,0,1,1,0,1),GL15C.GL_STATIC_DRAW);
            GL20C.glEnableVertexAttribArray(0); GL20C.glVertexAttribPointer(0,2,GL11C.GL_FLOAT,false,8,0L);
            int count=24;
            var data=stack.malloc(count*80).order(ByteOrder.nativeOrder());
            for(int i=0;i<count;i++) {
                boolean rain=i%3==0, provider=i%3==2;
                WeatherInstancedAttribs.putColumn(data,i,i%7-3,i%5+1,50,78,
                        rain?0:.25f,rain?0:.75f,rain?0:.5f,rain?0:-.25f,
                        0xd00050,-1,rain?3.75f:1,provider?1:0,.2f,.5f,.9f,.8f,
                        rain?32:512,rain?0:.01f,rain?0:.001f,rain?.5f:.3f);
            }
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER,instances); GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER,data,GL15C.GL_STATIC_DRAW);
            WeatherInstancedAttribs.enableInstanceArrays(); WeatherInstancedAttribs.pointInstanceAttribs(0);
            GL30C.glBindBufferBase(GL30C.GL_TRANSFORM_FEEDBACK_BUFFER,0,output);
            GL15C.glBufferData(GL30C.GL_TRANSFORM_FEEDBACK_BUFFER,(long)count*4*16*4,GL15C.GL_STREAM_READ);
            GL11C.glEnable(GL30C.GL_RASTERIZER_DISCARD); GL30C.glBeginTransformFeedback(GL11C.GL_POINTS);
            GL31C.glDrawArraysInstanced(GL11C.GL_POINTS,0,4,count); GL30C.glEndTransformFeedback(); GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
            var result=stack.mallocFloat(count*4*16);GL15C.glGetBufferSubData(GL30C.GL_TRANSFORM_FEEDBACK_BUFFER,0,result);
            for(int i=0;i<count;i++)for(int corner=0;corner<4;corner++) {
                float[] expected=reference(data,i,corner);
                for(int j=0;j<16;j++) {
                    float actual=result.get((i*4+corner)*16+j);
                    float tolerance=2e-5f*Math.max(1,Math.abs(expected[j]));
                    require(Math.abs(expected[j]-actual)<=tolerance,"Weather GPU component "+j+": "+actual+" != "+expected[j]);
                }
            }
            require(GL11C.glGetError()==GL11C.GL_NO_ERROR,"Weather expansion GL error");
            System.out.println("PASS weather GPU expansion: 24 rain/snow/provider instances, all 96 vertices position/color/UV/light match original formulas");
        } finally {
            GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
            GL30C.glBindBufferBase(GL30C.GL_TRANSFORM_FEEDBACK_BUFFER,0,0);
            GL30C.glBindVertexArray(oldVao);GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER,oldBuffer);GL20C.glUseProgram(oldProgram);
            GL15C.glDeleteBuffers(corners);GL15C.glDeleteBuffers(instances);GL15C.glDeleteBuffers(output);
            GL30C.glDeleteVertexArrays(vao);GL20C.glDeleteProgram(program);GL20C.glDeleteShader(shader);
        }
    }

    private static float[] reference(ByteBuffer data,int instance,int corner) {
        float[] a=new float[20];for(int i=0;i<20;i++)a[i]=data.getFloat(instance*80+i*4);
        float cx=corner==1||corner==2?1:0,cy=corner>=2?1:0;
        boolean provider=a[11]>.5f;
        float inverse=.5f/(float)Math.sqrt(a[0]*a[0]+a[1]*a[1]);
        float px=a[0]+.5f+(cx*2-1)*(-a[1]*inverse)+.25f;
        float py=a[2]*(1-cy)+a[3]*cy-64.5f;
        float pz=a[1]+.5f+(cx*2-1)*(a[0]*inverse)+.75f;
        float dx=a[0]+.5f-.37f,dz=a[1]+.5f-.49f;
        float distance=(float)Math.sqrt(dx*dx+dz*dz)*(provider?.05f:.1f);
        float alpha=((1-distance*distance)*a[19]+.5f)*a[15]*(provider?1:.73f);
        float age=provider?57.25f:8191.25f,tick=provider?511:1023;
        float scroll=((tick+a[9])%a[16]+.25f)/a[16]*a[10];
        float u=cx+a[4]+age*a[17]*a[5];
        float v=(a[2]*(1-cy)+a[3]*cy)*.25f+scroll+a[6]+age*a[18]*a[7];
        float lightV=(float)Math.floor(a[8]/65536f);
        return new float[]{px,py,pz,1,quantize(a[12]),quantize(a[13]),quantize(a[14]),quantize(alpha),u,v,0,1,a[8]-lightV*65536,lightV,0,1};
    }
    private static float quantize(float value){return(float)Math.floor(Math.max(0,Math.min(1,value))*255)/255;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
