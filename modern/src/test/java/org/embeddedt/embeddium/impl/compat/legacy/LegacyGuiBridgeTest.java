package org.embeddedt.embeddium.impl.compat.legacy;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGuiBridgeTest {
    @Test void typedLegacyIdReusesUntypedNativeToken(){
        var nativeId=org.taumc.celeritas.api.options.OptionIdentifier.create("bridge_review","native_token");
        var old=org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create("bridge_review","native_token",Integer.class);
        assertSame(nativeId,LegacyGuiBridge.toNew(optionWithId(old)).getId());
    }
    @Test void typedNativeIdReusesUntypedLegacyToken(){
        var old=org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create("bridge_review","legacy_token");
        var id=org.taumc.celeritas.api.options.OptionIdentifier.create("bridge_review","legacy_token",Integer.class);
        var proxy=(org.taumc.celeritas.api.options.structure.Option<?>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.taumc.celeritas.api.options.structure.Option.class},(self,method,args)->switch(method.getName()){
            case "getId"->id;case "hashCode"->System.identityHashCode(self);case "equals"->self==args[0];default->throw new AssertionError(method.getName());
        });
        assertSame(old,LegacyGuiBridge.toOld(proxy).getId());
    }
    @Test void genuineTypedIdConflictsStillFail(){
        org.taumc.celeritas.api.options.OptionIdentifier.create("bridge_review","conflict",Boolean.class);
        var old=org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create("bridge_review","conflict",Integer.class);
        assertThrows(IllegalArgumentException.class,()->LegacyGuiBridge.toNew(optionWithId(old)).getId());
    }
    private static Option<?> optionWithId(org.embeddedt.embeddium.client.gui.options.OptionIdentifier<?> id){
        return (Option<?>)Proxy.newProxyInstance(Option.class.getClassLoader(),new Class[]{Option.class},(self,method,args)->switch(method.getName()){
            case "getId"->id;case "hashCode"->System.identityHashCode(self);case "equals"->self==args[0];default->throw new AssertionError(method.getName());
        });
    }
    @Test void sharedOldStorageStillSavesOnceForMultipleChangedOptions() {
        int[] saves={0};
        var storage=new OptionStorage<Object>() {
            public Object getData(){return this;}
            public void save(){saves[0]++;}
        };
        var a=LegacyGuiBridge.toNew(option(storage));var b=LegacyGuiBridge.toNew(option(storage));
        assertSame(a.getStorage(),a.getStorage());assertSame(a.getStorage(),b.getStorage());
        var unique=new HashSet<org.taumc.celeritas.api.options.structure.OptionStorage<?>>();
        unique.add(a.getStorage());unique.add(b.getStorage());unique.forEach(org.taumc.celeritas.api.options.structure.OptionStorage::save);
        assertEquals(1,saves[0]);
    }
    private static Option<?> option(OptionStorage<?> storage) {
        return (Option<?>)Proxy.newProxyInstance(Option.class.getClassLoader(),new Class[]{Option.class},(self,method,args)->switch(method.getName()) {
            case "getStorage" -> storage;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self==args[0];
            default -> throw new AssertionError(method.getName());
        });
    }
}
