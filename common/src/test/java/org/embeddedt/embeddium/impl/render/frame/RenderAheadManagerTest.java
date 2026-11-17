package org.embeddedt.embeddium.impl.render.frame;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.taumc.celeritas.lwjgl.LWJGLService;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.IntSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production class, substituting only the GL service and scheduler boundary. */
public class RenderAheadManagerTest {
    private static final String ORIGINAL = Type.getInternalName(RenderAheadManager.class);
    private static final String RENAMED = ORIGINAL + "_Harness";
    private static final Class<?> HARNESS = load();
    static final ThreadLocal<Execution> current = new ThreadLocal<>();
    static class Execution {
        final ArrayDeque<Integer> statuses = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        long nextFence=1;
    }
    public static class Hooks {
        public static LWJGLService service() {
            return (LWJGLService)Proxy.newProxyInstance(LWJGLService.class.getClassLoader(),new Class[]{LWJGLService.class},(p,m,a)-> {
                var e=current.get();
                return switch(m.getName()) {
                    case "glClientWaitSync" -> { e.calls.add("wait:"+a[0]+":"+a[1]+":"+a[2]); yield e.statuses.remove(); }
                    case "glDeleteSync" -> { e.calls.add("delete:"+a[0]); yield null; }
                    case "glFenceSync" -> { e.calls.add("fence:"+a[0]+":"+a[1]); yield e.nextFence; }
                    default -> throw new AssertionError(m.getName());
                };
            });
        }
        public static void yieldThread(){current.get().calls.add("yield");}
    }
    private static Class<?> load() {
        try(var input=RenderAheadManager.class.getResourceAsStream("RenderAheadManager.class")) {
            var writer=new ClassWriter(0);
            var adapter=new ClassVisitor(Opcodes.ASM9,writer) {
                public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)) {
                        public void visitFieldInsn(int op,String owner,String name,String desc) {
                            if(op==Opcodes.GETSTATIC && owner.equals("org/taumc/celeritas/lwjgl/LWJGLServiceProvider") && name.equals("LWJGL"))
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(Hooks.class),"service","()Lorg/taumc/celeritas/lwjgl/LWJGLService;",false);
                            else super.visitFieldInsn(op,owner,name,desc);
                        }
                        public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {
                            if(owner.equals("java/lang/Thread") && name.equals("yield")) super.visitMethodInsn(op,Type.getInternalName(Hooks.class),"yieldThread",desc,false);
                            else super.visitMethodInsn(op,owner,name,desc,itf);
                        }
                    };
                }
            };
            new ClassReader(input).accept(new ClassRemapper(adapter,new SimpleRemapper(ORIGINAL,RENAMED)),0);
            return new ClassLoader(RenderAheadManagerTest.class.getClassLoader()) {
                Class<?> define(){return defineClass(RENAMED.replace('/','.'),writer.toByteArray(),0,writer.toByteArray().length);}
            }.define();
        } catch(Exception e){throw new AssertionError(e);}
    }
    private static void invoke(Object h,String method,Class<?>[] types,Object... args) throws Throwable {
        try{HARNESS.getMethod(method,types).invoke(h,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
    }
    @Test void invalidNegativeConfiguredLimitDrainsExistingFencesWithoutReadingAnEmptyQueue() throws Throwable {
        var e=new Execution();current.set(e);var h=HARNESS.getConstructor().newInstance();
        assertDoesNotThrow(()->invoke(h,"startFrame",new Class[]{int.class},-1));
        invoke(h,"endFrame",new Class[0]);e.statuses.add(37146);
        assertDoesNotThrow(()->invoke(h,"startFrame",new Class[]{IntSupplier.class},(IntSupplier)()->-3));
        assertEquals(List.of("fence:37143:0","wait:1:1:1000","delete:1"),e.calls);
    }
    @Test void preservesFastWaitStatusFlagsAndDeletion() throws Throwable {
        for(int status:new int[]{37146,37148}) {
            var e=new Execution();current.set(e);var h=HARNESS.getConstructor().newInstance();
            invoke(h,"endFrame",new Class[0]);e.statuses.add(status);
            invoke(h,"startFrame",new Class[]{int.class},0);
            assertEquals(List.of("fence:37143:0","wait:1:1:1000","delete:1"),e.calls);
        }
    }
    @Test void yieldsOnlyAfterFortyShortWaitsAndDeletesOnce() throws Throwable {
        var e=new Execution();current.set(e);var h=HARNESS.getConstructor().newInstance();invoke(h,"endFrame",new Class[0]);
        for(int i=0;i<41;i++)e.statuses.add(37147);e.statuses.add(37148);
        invoke(h,"startFrame",new Class[]{int.class},0);
        assertEquals(40,e.calls.stream().filter(x->x.equals("wait:1:1:1000")).count());
        assertEquals(List.of("yield","wait:1:1:10000","yield","wait:1:1:10000","delete:1"),e.calls.subList(41,e.calls.size()));
    }
    @Test void preservesFailuresWithoutDeletingFailedFence() throws Throwable {
        for(int timeouts:new int[]{0,40}) {
            var e=new Execution();current.set(e);var h=HARNESS.getConstructor().newInstance();invoke(h,"endFrame",new Class[0]);
            for(int i=0;i<timeouts;i++)e.statuses.add(37147);e.statuses.add(37149);
            var ex=assertThrows(RuntimeException.class,()->invoke(h,"startFrame",new Class[]{int.class},0));
            assertEquals("Failed to wait on fence object",ex.getMessage());
            assertFalse(e.calls.stream().anyMatch(x->x.startsWith("delete")));
        }
        var e=new Execution();e.nextFence=0;current.set(e);var h=HARNESS.getConstructor().newInstance();
        assertEquals("Failed to create fence object",assertThrows(RuntimeException.class,()->invoke(h,"endFrame",new Class[0])).getMessage());
    }
    @Test void reevaluatesLimitForEveryFenceAndSkipsWithinLimit() throws Throwable {
        var e=new Execution();current.set(e);var h=HARNESS.getConstructor().newInstance();
        for(int i=0;i<3;i++){e.nextFence=i+1;invoke(h,"endFrame",new Class[0]);}
        invoke(h,"startFrame",new Class[]{int.class},3);assertEquals(3,e.calls.size());
        e.statuses.add(37146);int[] queries={0};
        invoke(h,"startFrame",new Class[]{IntSupplier.class},(IntSupplier)()->queries[0]++==0?0:2);
        assertEquals(2,queries[0]);assertEquals(List.of("wait:1:1:1000","delete:1"),e.calls.subList(3,e.calls.size()));
    }
}
