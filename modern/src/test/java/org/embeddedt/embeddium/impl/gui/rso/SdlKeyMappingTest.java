package org.embeddedt.embeddium.impl.gui.rso;
import org.embeddedt.embeddium.impl.gui.rso.compat.SdlKeyMapping;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SdlKeyMappingTest {
    @Test void preservesPhysicalLettersFunctionKeysKeypadAndModifiers(){
        for(int i=0;i<26;i++)assertEquals(65+i,SdlKeyMapping.glfwKey(4+i));
        assertEquals(258,SdlKeyMapping.glfwKey(43));assertEquals(257,SdlKeyMapping.glfwKey(40));
        assertEquals(335,SdlKeyMapping.glfwKey(88));assertEquals(263,SdlKeyMapping.glfwKey(80));
        assertEquals(340,SdlKeyMapping.glfwKey(225));assertEquals(347,SdlKeyMapping.glfwKey(231));
        for(int i=0;i<12;i++)assertEquals(290+i,SdlKeyMapping.glfwKey(58+i));
        assertEquals(-1,SdlKeyMapping.glfwKey(0));
    }
}
