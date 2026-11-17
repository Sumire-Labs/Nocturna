package org.embeddedt.embeddium.impl.compat.legacy;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.gui.framework.*;
import org.embeddedt.embeddium.impl.gui.modern.framework.ModernDrawContext;
import org.embeddedt.embeddium.impl.gui.modern.framework.ModernInteractionContext;
import org.taumc.celeritas.api.options.structure.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Keeps old option bindings, custom controls and Forge construction events live in the new GUI. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LegacyGuiBridge {
    public record VanillaText(Component component) implements TextComponent { }
    private static final Map<Object,java.lang.ref.WeakReference<Object>> oldToNew = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,java.lang.ref.WeakReference<Object>> newToOld = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,java.lang.ref.WeakReference<Object>> oldStoragesToNew = new WeakHashMap<>();
    private static final Map<Object,java.lang.ref.WeakReference<Object>> newStoragesToOld = new WeakHashMap<>();

    private static synchronized OptionStorage<?> toNewStorage(me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<?> storage) {
        var existing=dereference(oldStoragesToNew.get(storage));
        if(existing instanceof OptionStorage<?> result) return result;
        var result=new OptionStorage<Object>() {
            public Object getData(){return storage.getData();}
            public void save(){storage.save();}
        };
        oldStoragesToNew.put(storage,new java.lang.ref.WeakReference<>(result));return result;
    }
    private static synchronized me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<?> toOldStorage(OptionStorage<?> storage) {
        var existing=dereference(newStoragesToOld.get(storage));
        if(existing instanceof me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<?> result) return result;
        var result=new me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<Object>() {
            public Object getData(){
                Object data=storage.getData();
                return data instanceof org.embeddedt.embeddium.impl.gui.SodiumGameOptions config ? LegacyOptions.view(config) : data;
            }
            public void save(){LegacyOptions.synchronize(Celeritas.options());storage.save();}
        };
        newStoragesToOld.put(storage,new java.lang.ref.WeakReference<>(result));return result;
    }

    public static void install() {
        org.taumc.celeritas.api.OptionGroupConstructionEvent.BUS.addListener(event -> {
            var options = new ArrayList<me.jellysquid.mods.sodium.client.gui.options.Option<?>>();
            for (var option : event.getOptions()) options.add(toOld(option));
            org.embeddedt.embeddium.api.OptionGroupConstructionEvent.BUS.post(
                    new org.embeddedt.embeddium.api.OptionGroupConstructionEvent(oldId(event.getId()),options));
            event.getOptions().clear();
            for (var option : options) event.getOptions().add(toNew(option));
        });
        org.taumc.celeritas.api.OptionPageConstructionEvent.BUS.addListener(event -> {
            var oldEvent = new org.embeddedt.embeddium.api.OptionPageConstructionEvent(oldId(event.getId()),vanilla(event.getTranslationKey()));
            org.embeddedt.embeddium.api.OptionPageConstructionEvent.BUS.post(oldEvent);
            for (var group : oldEvent.getAdditionalGroups()) event.addGroup(toNew(group));
        });
        org.taumc.celeritas.api.OptionGUIConstructionEvent.BUS.addListener(event -> {
            var pages = new ArrayList<me.jellysquid.mods.sodium.client.gui.options.OptionPage>();
            for (var page : event.getPages()) pages.add(toOld(page));
            org.embeddedt.embeddium.api.OptionGUIConstructionEvent.BUS.post(new org.embeddedt.embeddium.api.OptionGUIConstructionEvent(pages));
            event.getPages().clear();
            for (var page : pages) event.getPages().add(toNew(page));
        });
    }

    private static Component vanilla(TextComponent text) {
        return new ModernDrawContext(null, Minecraft.getInstance().font).compile(text);
    }
    private static Class<?> oldType(Class<?> type) {
        return type == org.embeddedt.embeddium.impl.gui.SodiumGameOptions.GraphicsQuality.class
                ? me.jellysquid.mods.sodium.client.gui.SodiumGameOptions.GraphicsQuality.class : type;
    }
    private static Object value(Object value, Class<?> target) {
        return value instanceof Enum<?> e && target.isEnum() ? Enum.valueOf((Class)target,e.name()) : value;
    }
    private static org.embeddedt.embeddium.client.gui.options.OptionIdentifier oldId(org.taumc.celeritas.api.options.OptionIdentifier id) {
        if(id == null) return null;
        String modId=id.getModId().equals("celeritas")?"embeddium":id.getModId();
        var known=org.embeddedt.embeddium.client.gui.options.OptionIdentifier.find(modId,id.getPath());
        if(known!=null&&(untyped(known.getType())||untyped(id.getType())))return known;
        return org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create(
                modId,id.getPath(),oldType(id.getType()));
    }
    private static org.taumc.celeritas.api.options.OptionIdentifier newId(org.embeddedt.embeddium.client.gui.options.OptionIdentifier id) {
        if(id == null) return null;
        Class<?> type=id.getType();
        if(type==me.jellysquid.mods.sodium.client.gui.SodiumGameOptions.GraphicsQuality.class)
            type=org.embeddedt.embeddium.impl.gui.SodiumGameOptions.GraphicsQuality.class;
        var known=org.taumc.celeritas.api.options.OptionIdentifier.find(id.getModId(),id.getPath());
        if(known!=null&&(untyped(known.getType())||untyped(type)))return known;
        return org.taumc.celeritas.api.options.OptionIdentifier.create(id.getModId(),id.getPath(),type);
    }
    private static boolean untyped(Class<?> type){return type==void.class||type==Void.class;}
    private static Object dereference(java.lang.ref.WeakReference<Object> ref) { return ref == null ? null : ref.get(); }
    private static void remember(Object old, Object current) { oldToNew.put(old,new java.lang.ref.WeakReference<>(current)); newToOld.put(current,new java.lang.ref.WeakReference<>(old)); }

    public static me.jellysquid.mods.sodium.client.gui.options.Option toOld(Option option) {
        Object existing=dereference(newToOld.get(option));
        if(existing!=null) return (me.jellysquid.mods.sodium.client.gui.options.Option)existing;
        var proxy=(me.jellysquid.mods.sodium.client.gui.options.Option)Proxy.newProxyInstance(LegacyGuiBridge.class.getClassLoader(),
                new Class[]{me.jellysquid.mods.sodium.client.gui.options.Option.class},(self,method,args)->switch(method.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self==args[0];
                    case "toString" -> "LegacyOption["+option.getId()+"]";
                    case "getId" -> oldId(option.getId());
                    case "getName" -> vanilla(option.getName());
                    case "getTooltip" -> vanilla(option.getTooltip());
                    case "getImpact" -> option.getImpact()==null ? null : me.jellysquid.mods.sodium.client.gui.options.OptionImpact.valueOf(option.getImpact().name());
                    case "getFlags" -> ((Collection<OptionFlag>)option.getFlags()).stream().filter(f->f!=OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD).map(f->me.jellysquid.mods.sodium.client.gui.options.OptionFlag.valueOf(f.name())).toList();
                    case "getValue" -> value(option.getValue(),oldType(option.getValue().getClass()));
                    case "setValue" -> { option.setValue(value(args[0],option.getValue().getClass())); yield null; }
                    case "getStorage" -> toOldStorage(option.getStorage());
                    case "getControl" -> oldControl((me.jellysquid.mods.sodium.client.gui.options.Option)self,option.getControl());
                    default -> Option.class.getMethod(method.getName(),method.getParameterTypes()).invoke(option,args);
                });
        remember(proxy,option); return proxy;
    }

    public static Option toNew(me.jellysquid.mods.sodium.client.gui.options.Option option) {
        Object existing=dereference(oldToNew.get(option));
        if(existing!=null) return (Option)existing;
        var proxy=(Option)Proxy.newProxyInstance(LegacyGuiBridge.class.getClassLoader(),new Class[]{Option.class},(self,method,args)->switch(method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self==args[0];
            case "toString" -> "CompatibleOption["+option.getId()+"]";
            case "getId" -> newId(option.getId());
            case "getName" -> new VanillaText(option.getName());
            case "getTooltip" -> new VanillaText(option.getTooltip());
            case "getImpact" -> option.getImpact()==null ? null : OptionImpact.valueOf(option.getImpact().name());
            case "getFlags" -> ((Collection<me.jellysquid.mods.sodium.client.gui.options.OptionFlag>)option.getFlags()).stream().map(f->OptionFlag.valueOf(f.name())).toList();
            case "getStorage" -> toNewStorage(option.getStorage());
            case "getControl" -> newControl((Option)self,option.getControl());
            case "applyChanges" -> { option.applyChanges(); LegacyOptions.synchronize(Celeritas.options()); yield null; }
            default -> me.jellysquid.mods.sodium.client.gui.options.Option.class.getMethod(method.getName(),method.getParameterTypes()).invoke(option,args);
        });
        remember(option,proxy); return proxy;
    }

    private static me.jellysquid.mods.sodium.client.gui.options.OptionGroup toOld(OptionGroup group) {
        return me.jellysquid.mods.sodium.client.gui.options.OptionGroup.fromCompatibility(oldId(group.getId()),
                ImmutableList.copyOf((List)group.getOptions().stream().map(LegacyGuiBridge::toOld).toList()));
    }
    private static OptionGroup toNew(me.jellysquid.mods.sodium.client.gui.options.OptionGroup group) {
        return OptionGroup.fromCompatibility(newId(group.getId()),(List)group.getOptions().stream().map(LegacyGuiBridge::toNew).toList());
    }
    private static me.jellysquid.mods.sodium.client.gui.options.OptionPage toOld(OptionPage page) {
        var result=me.jellysquid.mods.sodium.client.gui.options.OptionPage.fromCompatibility(oldId(page.getId()),vanilla(page.getName()),
                page.getGroups().stream().map(LegacyGuiBridge::toOld).collect(ImmutableList.toImmutableList()));
        remember(result,page); return result;
    }
    private static OptionPage toNew(me.jellysquid.mods.sodium.client.gui.options.OptionPage page) {
        var existing=dereference(oldToNew.get(page));
        return existing instanceof OptionPage p ? p : OptionPage.fromCompatibility(newId(page.getId()),new VanillaText(page.getName()),page.getGroups().stream().map(LegacyGuiBridge::toNew).toList());
    }

    private static org.taumc.celeritas.api.options.control.Control newControl(Option option,me.jellysquid.mods.sodium.client.gui.options.control.Control old) {
        return new org.taumc.celeritas.api.options.control.Control() {
            public Option getOption(){return option;}
            public int getMaxWidth(){return old.getMaxWidth();}
            public org.taumc.celeritas.api.options.control.ControlElement createElement(org.embeddedt.embeddium.impl.util.Dim2i dim) {
                var widget=old.createElement(new me.jellysquid.mods.sodium.client.util.Dim2i(dim.x(),dim.y(),dim.width(),dim.height()));
                return new LegacyControlElement(option,dim,widget);
            }
        };
    }
    private static final class LegacyControlElement extends org.taumc.celeritas.api.options.control.ControlElement
            implements net.minecraft.client.gui.components.events.GuiEventListener, net.minecraft.client.gui.narration.NarratableEntry {
        private final me.jellysquid.mods.sodium.client.gui.options.control.ControlElement widget;
        private LegacyControlElement(Option option,org.embeddedt.embeddium.impl.util.Dim2i dim,
                me.jellysquid.mods.sodium.client.gui.options.control.ControlElement widget){super(option,dim);this.widget=widget;}
        public void render(DrawContext context,int x,int y,float delta){widget.render(((ModernDrawContext)context).guiGraphics(),x,y,delta);}
        public boolean isMouseOver(double x,double y){return widget.isMouseOver(x,y);}
        public boolean mouseClicked(InteractionContext c,double x,double y,int b){return widget.mouseClicked(x,y,b);}
        public boolean mouseReleased(InteractionContext c,double x,double y,int b){return widget.mouseReleased(x,y,b);}
        public boolean mouseDragged(InteractionContext c,double x,double y,int b,double dx,double dy){return widget.mouseDragged(x,y,b,dx,dy);}
        public boolean mouseScrolled(InteractionContext c,double x,double y,double dx,double dy){return widget.mouseScrolled(x,y,dy);}
        public boolean keyPressed(int key,int scan,int mods){return widget.keyPressed(key,scan,mods);}
        public boolean keyReleased(int key,int scan,int mods){return widget.keyReleased(key,scan,mods);}
        public boolean charTyped(char ch,int mods){return widget.charTyped(ch,mods);}
        public boolean isFocused(){return widget.isFocused();}
        public void setFocused(boolean focused){widget.setFocused(focused);}
        public void setDimensions(org.embeddedt.embeddium.impl.util.Dim2i dim){super.setDimensions(dim);widget.setDimensions(new me.jellysquid.mods.sodium.client.util.Dim2i(dim.x(),dim.y(),dim.width(),dim.height()));}
        public NarrationPriority narrationPriority(){return widget.narrationPriority();}
        public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){widget.updateNarration(output);}
    }
    private static me.jellysquid.mods.sodium.client.gui.options.control.Control oldControl(me.jellysquid.mods.sodium.client.gui.options.Option option,org.taumc.celeritas.api.options.control.Control current) {
        return new me.jellysquid.mods.sodium.client.gui.options.control.Control() {
            public me.jellysquid.mods.sodium.client.gui.options.Option getOption(){return option;}
            public int getMaxWidth(){return current.getMaxWidth();}
            public me.jellysquid.mods.sodium.client.gui.options.control.ControlElement createElement(me.jellysquid.mods.sodium.client.util.Dim2i dim) {
                var widget=current.createElement(new org.embeddedt.embeddium.impl.util.Dim2i(dim.x(),dim.y(),dim.width(),dim.height()));
                return new me.jellysquid.mods.sodium.client.gui.options.control.ControlElement(option,dim) {
                    public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float delta){widget.render(new ModernDrawContext(gui,Minecraft.getInstance().font),x,y,delta);}
                    public boolean isMouseOver(double x,double y){return widget.isMouseOver(x,y);}
                    public boolean mouseClicked(double x,double y,int b){return widget.mouseClicked(ModernInteractionContext.INSTANCE,x,y,b);}
                    public boolean mouseReleased(double x,double y,int b){return widget.mouseReleased(ModernInteractionContext.INSTANCE,x,y,b);}
                    public boolean mouseDragged(double x,double y,int b,double dx,double dy){return widget.mouseDragged(ModernInteractionContext.INSTANCE,x,y,b,dx,dy);}
                    public boolean mouseScrolled(double x,double y,double dy){return widget.mouseScrolled(ModernInteractionContext.INSTANCE,x,y,0,dy);}
                };
            }
        };
    }
}
