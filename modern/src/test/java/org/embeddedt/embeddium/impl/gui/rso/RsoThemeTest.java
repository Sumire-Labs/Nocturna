package org.embeddedt.embeddium.impl.gui.rso;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.embeddedt.embeddium.api.options.RsoModTheme;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RsoThemeTest {
    @Test void usesReferenceDefaultAndAllReferencePresets(){
        assertEquals(0xFFC0FFF1,ColorTheme.DEFAULT.themeLighter);assertEquals(0xFF80AFA5,ColorTheme.DEFAULT.themeDarker);
        int[][] values={{0xFFE494A5,0xFFFFC0CD,0xFFAF808A},{0xFFAB94E4,0xFFD2C0FF,0xFF8D80AF},{0xFFCDE494,0xFFEDFFC0,0xFFA2AF80},{0xFFD394E4,0xFFF1C0FF,0xFFA580AF},{0xFFE4D394,0xFFFFF1C0,0xFFAFA580}};
        for(int i=0;i<values.length;i++){var theme=ColorTheme.forMod("fixture",i);assertArrayEquals(values[i],new int[]{theme.theme,theme.themeLighter,theme.themeDarker});}
    }
    @Test void keepsPerModOverridesIndependent(){
        new RsoModTheme(1,2,3).register("fixture");
        try{var theme=ColorTheme.forMod("fixture",0);assertEquals(1,theme.theme);assertEquals(2,theme.themeLighter);assertEquals(3,theme.themeDarker);assertSame(ColorTheme.DEFAULT,ColorTheme.forMod("embeddium",0));}
        finally{RsoModTheme.unregister("fixture");}
    }
}
