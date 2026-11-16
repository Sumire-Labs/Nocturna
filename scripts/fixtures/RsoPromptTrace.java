import java.lang.reflect.*;
import java.util.*;

/** Exercises only public prompt behavior; implementation classes are supplied at runtime. */
public class RsoPromptTrace {
    public static void main(String[] args)throws Exception{
        boolean reference=args[0].equals("reference");
        String root=reference?"net.caffeinemc.mods.sodium.":"org.embeddedt.embeddium.impl.gui.rso.compat.sodium.";
        var type=Class.forName(root+"client.gui.prompt.ScreenPrompt");
        var parentType=Class.forName(root+"client.gui.prompt.ScreenPromptable");
        var actionType=Class.forName(root+"client.gui.prompt.ScreenPrompt$Action");
        var component=Class.forName("net.minecraft.network.chat.Component");
        Object label=component.getMethod("literal",String.class).invoke(null,"Action");
        Object action=actionType.getConstructor(component,Runnable.class).newInstance(label,(Runnable)()->{});
        Object[] current={null};
        Object parent=Proxy.newProxyInstance(parentType.getClassLoader(),new Class<?>[]{parentType},(proxy,method,values)->{
            return switch(method.getName()){
                case "setPrompt"->{current[0]=values[0];yield null;}
                case "getPrompt"->current[0];
                case "getDimensions"->method.getReturnType().getConstructor(int.class,int.class,int.class,int.class).newInstance(0,0,640,360);
                default->throw new UnsupportedOperationException(method.getName());
            };
        });
        Object prompt=type.getConstructor(parentType,List.class,int.class,int.class,actionType).newInstance(parent,List.of(),320,190,action);
        // A prompt's layout needs only a host object, not a running renderer or a font.
        // Real drawing/input is also checked in the separate Forge client validation.
        var minecraft=Class.forName("net.minecraft.client.Minecraft");
        if(minecraft.getMethod("getInstance").invoke(null)==null){
            var unsafeType=Class.forName("sun.misc.Unsafe");var unsafeField=unsafeType.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            Object host=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafeField.get(null),minecraft);
            var instance=minecraft.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,host);
        }
        type.getMethod("init").invoke(prompt);
        for(Object widget:(List<?>)type.getMethod("getWidgets").invoke(prompt)){
            Object bounds=widget.getClass().getMethod("getDimensions").invoke(widget);
            var b=bounds.getClass();
            System.out.println("TRACE button:"+b.getMethod("x").invoke(bounds)+","+b.getMethod("y").invoke(bounds)+","+b.getMethod("width").invoke(bounds)+","+b.getMethod("height").invoke(bounds));
        }
        Class<?> key=Class.forName(reference?"net.minecraft.client.input.KeyEvent":"org.embeddedt.embeddium.impl.gui.rso.compat.KeyEvent");
        var constructor=key.getConstructor(int.class,int.class,int.class);
        var press=type.getMethod("keyPressed",key);
        int[][] keys=reference?new int[][]{{43,9},{40,13},{44,32},{4,97},{41,27}}:new int[][]{{258,0},{257,0},{32,0},{65,0},{256,0}};
        String[] names={"Tab","Enter","Space","A","Escape"};
        for(int i=0;i<keys.length;i++){
            type.getMethod("setFocused",boolean.class).invoke(prompt,true);
            boolean handled=(boolean)press.invoke(prompt,constructor.newInstance(keys[i][0],keys[i][1],0));
            System.out.println(names[i]+":"+handled+":"+(current[0]==prompt));
        }
    }
}
