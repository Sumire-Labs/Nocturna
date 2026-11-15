import org.objectweb.asm.*;
import java.util.*;
import java.util.zip.*;
import java.nio.file.*;
public class ApiInventory {
 public static void main(String[] args)throws Exception {
  var entries=new ArrayList<String>();
  try(var zip=new ZipFile(args[0])) {
   for(var entry:Collections.list(zip.entries())) {
    String n=entry.getName();
    if(!n.endsWith(".class")||!(n.startsWith("net/caffeinemc/mods/sodium/api/")||n.startsWith("org/embeddedt/embeddium/api/")||n.startsWith("me/jellysquid/mods/sodium/client/gui/options/")||n.startsWith("org/embeddedt/embeddium/client/gui/options/")))continue;
    var reader=new ClassReader(zip.getInputStream(entry));
    if((reader.getAccess()&Opcodes.ACC_PUBLIC)==0)continue;
    String owner=reader.getClassName();
    entries.add("C|"+owner);
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
     public FieldVisitor visitField(int a,String name,String desc,String sig,Object val) {
      if((a&Opcodes.ACC_PUBLIC)!=0)entries.add("F|"+owner+"|"+name+"|"+desc);return null;
     }
     public MethodVisitor visitMethod(int a,String name,String desc,String sig,String[] ex) {
      if((a&Opcodes.ACC_PUBLIC)!=0)entries.add("M|"+owner+"|"+name+"|"+desc);return null;
     }
    },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
   }
  }
  Collections.sort(entries);Files.write(Path.of(args[1]),entries);
 }
}
