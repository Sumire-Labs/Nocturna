import java.lang.reflect.*;

public class RsoThemeTrace {
    public static void main(String[]args)throws Exception{
        boolean reference=args[0].equals("reference");
        String root=reference?"net.caffeinemc.mods.sodium.":"org.embeddedt.embeddium.impl.gui.rso.compat.sodium.";
        var type=Class.forName(root+"client.gui.ColorTheme");
        Object main=reference?type.getConstructor(int.class).newInstance(0xFF94E4D3):type.getField("DEFAULT").get(null);
        print(type,main);
        for(int i=0;i<5;i++){
            Object value=reference?((Object[])type.getField("PRESETS").get(null))[i]:type.getMethod("forMod",String.class,int.class).invoke(null,"fixture",i);
            print(type,value);
        }
    }
    private static void print(Class<?> type,Object value)throws Exception{
        System.out.printf("TRACE %08X/%08X/%08X%n",type.getField("theme").getInt(value),type.getField("themeLighter").getInt(value),type.getField("themeDarker").getInt(value));
    }
}
