package org.embeddedt.embeddium.impl.gui.rso.compat;

import com.sun.jna.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs the product's unmodified SDL backend against a real GLFW/OpenGL window on each OS. */
public final class RsoNativeInput {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static int pixel(){ByteBuffer value=ByteBuffer.allocateDirect(4);GL11.glClearColor(.25f,.5f,.75f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,value);return value.getInt(0);}
    public static void main(String[]args)throws Exception{
        require(GLFW.glfwInit(),"GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,1);
        long window=GLFW.glfwCreateWindow(320,180,"Nocturna native input validation",0,0);
        require(window!=0,"Could not create native GLFW window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        int expected=pixel();List<String> text=new ArrayList<>();int[] keys={0};
        SdlTextInput backend=null;
        try{
            backend=new SdlTextInput(new SdlTextInput.Host(){
                public long glfwWindow(){return window;} public double coordinateScale(){return 1;}
                public Path cacheDirectory(){return Path.of(args[0],"native-cache");}
                public void acceptText(String value){text.add(value);}
                public void acceptKey(int scan,int raw,int action,int modifiers,int keycode){keys[0]++;}
            });
            require(GLFW.glfwGetCurrentContext()==window&&pixel()==expected,"SDL changed the GLFW graphics context");
            backend.start();backend.area(12,16,13,26);
            Memory rect=new Memory(16),cursor=new Memory(4);
            require((backend.library().getFunction("SDL_GetTextInputArea").invokeInt(new Object[]{backend.handle(),rect,cursor})&255)!=0,"Could not query native text input area");
            require(rect.getInt(0)==12&&rect.getInt(4)==16&&rect.getInt(8)==1&&rect.getInt(12)==10,"Native caret geometry changed");
            int id=backend.library().getFunction("SDL_GetWindowID").invokeInt(new Object[]{backend.handle()});
            byte[] bytes="入力🌧".getBytes(StandardCharsets.UTF_8);Memory payload=new Memory(bytes.length+1);payload.write(0,bytes,0,bytes.length);payload.setByte(bytes.length,(byte)0);
            Memory event=new Memory(128);event.clear();event.setInt(0,0x303);event.setInt(16,id);event.setPointer(Native.POINTER_SIZE==8?24:20,payload);
            require((backend.library().getFunction("SDL_PushEvent").invokeInt(new Object[]{event})&255)!=0,"Could not enqueue native text event");
            backend.pump();require(text.equals(List.of("入力🌧")),"Native UTF-8 commit was lost, split or duplicated");
            event.clear();event.setInt(0,0x300);event.setInt(16,id);event.setInt(24,4);event.setInt(28,97);event.setByte(36,(byte)1);
            backend.library().getFunction("SDL_PushEvent").invokeInt(new Object[]{event});backend.pump();require(keys[0]>0,"Native keyboard event did not reach the host");
            backend.stop();backend.close();backend=null;
            require(GLFW.glfwGetCurrentContext()==window&&pixel()==expected,"Input teardown damaged the graphics context");
            require(GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=0,"Input teardown hid the host window");
            GLFW.glfwSwapBuffers(window);GLFW.glfwPollEvents();
            String result="PASS "+System.getProperty("os.name")+" "+System.getProperty("os.arch")+" Java"+System.getProperty("java.specification.version")+"; real GLFW window; OpenGL "+GL11.glGetString(GL11.GL_VERSION)+"; SDL caret/UTF-8/keyboard/focus lifecycle; host context and visibility preserved";
            Files.createDirectories(Path.of(args[0]));Files.writeString(Path.of(args[0],"native-input.txt"),result);System.out.println(result);
        }finally{if(backend!=null)backend.close();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
