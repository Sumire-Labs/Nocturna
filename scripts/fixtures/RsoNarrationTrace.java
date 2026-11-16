import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Compares the original MIT widget's narration, without replacing its implementation. */
public class RsoNarrationTrace {
    public static void main(String[]args)throws Exception{
        var minecraft=Class.forName("net.minecraft.client.Minecraft");
        var unsafeType=Class.forName("sun.misc.Unsafe");var unsafeField=unsafeType.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        Object host=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafeField.get(null),minecraft);
        var instance=minecraft.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,host);
        Class input=Class.forName("net.minecraft.client.InputType");Object keyboard=Enum.valueOf(input,"KEYBOARD_TAB");
        minecraft.getMethod("setLastInputType",input).invoke(host,keyboard);
        var bounds=Class.forName("me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds");
        Object dim=bounds.getConstructor(int.class,int.class,int.class,int.class).newInstance(0,0,100,20);
        var component=Class.forName("net.minecraft.network.chat.Component");Object label=component.getMethod("literal",String.class).invoke(null,"Fixture");
        var type=Class.forName("me.flashyreese.mods.reeses_sodium_options.client.gui.widget.FlatButtonWidget");
        Object widget=type.getConstructor(bounds,component,Runnable.class,boolean.class,boolean.class).newInstance(dim,label,(Runnable)()->{},true,false);
        var output=Class.forName("net.minecraft.client.gui.narration.NarrationElementOutput");
        var entries=new ArrayList<String>();
        Object recorder=Proxy.newProxyInstance(output.getClassLoader(),new Class<?>[]{output},(proxy,method,values)->{
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);
            if(method.getName().equals("nest"))return proxy;
            if(method.getName().equals("add")){
                values[1].getClass().getMethod("getText",Consumer.class).invoke(values[1],(Consumer<String>)(text->entries.add(values[0]+":"+text)));
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        for(boolean focused:new boolean[]{false,true}){
            type.getMethod("setFocused",boolean.class).invoke(widget,focused);entries.clear();
            type.getMethod("updateNarration",output).invoke(widget,recorder);
            System.out.println("TRACE focused="+focused+" priority="+type.getMethod("narrationPriority").invoke(widget));
            entries.forEach(line->System.out.println("TRACE "+line));
        }
    }
}
