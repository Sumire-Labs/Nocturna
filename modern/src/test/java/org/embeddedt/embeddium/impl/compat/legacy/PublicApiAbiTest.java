package org.embeddedt.embeddium.impl.compat.legacy;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen descriptors read from the pre-migration beta.95 product jar, not recompiled against the adapters. */
class PublicApiAbiTest {
    @Test void retainsPublishedClassesAndMemberDescriptors() throws Exception {
        var missing=new ArrayList<String>();
        var members=new HashMap<String,Set<String>>();
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/legacy-public-api.txt"),StandardCharsets.UTF_8))) {
            for(String line;(line=reader.readLine())!=null;) {
                // The baseline is a production SRG jar; these inherited Minecraft overrides are named in dev.
                line=line.replace("|m_264064_|","|nextFocusPath|").replace("|m_264198_|","|getRectangle|")
                        .replace("|m_5953_|","|isMouseOver|").replace("|m_88315_|","|render|");
                String[] parts=line.split("\\|");String owner=parts[1];
                if(!members.containsKey(owner)) {
                    var set=new HashSet<String>();members.put(owner,set);
                    try(var bytes=getClass().getClassLoader().getResourceAsStream(owner+".class")) {
                        if(bytes!=null)new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                            public FieldVisitor visitField(int a,String n,String d,String s,Object v){if((a&Opcodes.ACC_PUBLIC)!=0)set.add("F|"+owner+"|"+n+"|"+d);return null;}
                            public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if((a&Opcodes.ACC_PUBLIC)!=0)set.add("M|"+owner+"|"+n+"|"+d);return null;}
                        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                        if(bytes==null)missing.add("C|"+owner);
                    }
                }
                if(!parts[0].equals("C")&&!members.get(owner).contains(line))missing.add(line);
            }
        }
        assertEquals(List.of(),missing,"Missing old public API descriptors");
    }
}
