package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.embeddium.api.*;
import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.gui.modern.EmbeddiumVideoOptionsScreen;
import org.embeddedt.embeddium.impl.compat.legacy.LegacyGuiBridge;
import org.embeddedt.embeddium.impl.gui.framework.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Mod("embeddium_validation")
public class MigrationValidation {
    private static final AtomicInteger blocks=new AtomicInteger(),meshes=new AtomicInteger();
    private static int groups,pages,guis,forgeGuis,ticks,stage,settled;
    private static final long deadline=System.nanoTime()+240_000_000_000L;
    private static boolean customValue;
    private static me.jellysquid.mods.sodium.client.gui.options.Option<Boolean> custom;

    public MigrationValidation() {
        FismValidation.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        if (Boolean.getBoolean("nocturna.validateRso")) { RsoValidation.install(); return; }
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener((OptionGUIConstructionEvent e)->forgeGuis++);
        OptionGroupConstructionEvent.BUS.addListener(e->groups++);
        OptionPageConstructionEvent.BUS.addListener(e->pages++);
        OptionGUIConstructionEvent.BUS.addListener(e->{
            guis++;
            var storage=new me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<Object>() {
                public Object getData(){return this;}
                public void save(){}
            };
            custom=me.jellysquid.mods.sodium.client.gui.options.OptionImpl.createBuilder(boolean.class,storage)
                .setId(org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create("embeddium_validation","toggle",boolean.class))
                .setName(Component.literal("Validation toggle").withStyle(net.minecraft.ChatFormatting.GREEN))
                .setTooltip(Component.literal("Test-only legacy control"))
                .setBinding((s,v)->customValue=v,s->customValue)
                .setControl(me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl::new).build();
            var group=me.jellysquid.mods.sodium.client.gui.options.OptionGroup.createBuilder().add(custom).build();
            e.addPage(new me.jellysquid.mods.sodium.client.gui.options.OptionPage(
                org.embeddedt.embeddium.client.gui.options.OptionIdentifier.create("embeddium_validation","page"),Component.literal("Validation"),com.google.common.collect.ImmutableList.of(group)));
        });
        BlockRendererRegistry.instance().registerRenderPopulator((renderers,ctx)->{
            require(ctx.state()==ctx.world().getBlockState(ctx.pos()),"legacy world snapshot disagrees");
            require(ctx.localSlice().getBlockState(ctx.pos())==ctx.state(),"local snapshot disagrees");
            blocks.incrementAndGet();
        });
        ChunkMeshEvent.BUS.addListener(e->e.addMeshAppender(ctx->{
            require(ctx.sodiumBuildBuffers().unwrap()!=null,"missing mesh buffers");
            require(ctx.vertexConsumerProvider()!=null,"missing mesh consumer provider");
            meshes.incrementAndGet();
        }));
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void vertices() {
        var builder=new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        var format=net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry.instance().get(DefaultVertexFormat.POSITION_COLOR);
        var writer=(net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter)builder;
        require(writer.canUseIntrinsics(),"active buffer not eligible");
        try(var stack=MemoryStack.stackPush()) {
            long ptr=stack.nmalloc(4*16);
            for(int i=0;i<16;i++)MemoryUtil.memPutInt(ptr+i*4,0x12340000+i);
            writer.push(stack,ptr,4,format);
            var rendered=builder.end();var data=rendered.vertexBuffer();
            for(int i=0;i<16;i++)require(data.getInt(i*4)==0x12340000+i,"old vertex push changed data");
            rendered.release();
        }
        require(me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter.of(builder)!=null,"legacy writer missing");
    }
    private static void gui(Minecraft mc) {
        mc.setScreen(new EmbeddiumVideoOptionsScreen(mc.screen));
        require(groups>0&&pages>0&&guis>0&&forgeGuis>0,"legacy events not dispatched");
        var wrapped=LegacyGuiBridge.toNew(custom);
        var control=wrapped.getControl().createElement(new org.embeddedt.embeddium.impl.util.Dim2i(0,0,200,20));
        require(control.mouseClicked(org.embeddedt.embeddium.impl.gui.modern.framework.ModernInteractionContext.INSTANCE,190,10,0),"legacy control click ignored");
        wrapped.applyChanges();require(customValue,"legacy control binding ignored");
        require(new org.embeddedt.embeddium.impl.gui.modern.framework.ModernDrawContext(null,mc.font).compile(wrapped.getName()).getStyle().getColor()!=null,"text style lost");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        try {
            if(System.nanoTime()>deadline)throw new AssertionError("validation timed out at stage "+stage);
            var mc=Minecraft.getInstance();ticks++;
            if (ticks % 100 == 0) System.out.println("Validation stage="+stage+" screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));
            if(stage==0&&(mc.screen instanceof TitleScreen||mc.screen instanceof AccessibilityOnboardingScreen)) {
                if(net.minecraftforge.fml.ModList.get().isLoaded("codechickenlib")) AddonValidation.install();
                vertices();MatrixUniformValidation.verify();ShaderCacheValidation.verify();PBRResourceIndexValidation.verify();
                HorizonValidation.verify();
                RetainedProgramsValidation.verify();
                UniformSupplierValidation.verify();
                FismValidation.verify();
                org.embeddedt.embeddium.validation.weather.WeatherGeometryValidation.verify();gui(mc);
                ShaderCachePackValidation.prepare();stage=1;
                mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);
                mc.options.pauseOnLostFocus=false;
                var name="migration-validation-"+UUID.randomUUID();
                var settings=new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.forceSetScreen(new GenericDirtMessageScreen(Component.literal("Validating Nocturna renderer")));
                mc.createWorldOpenFlows().createFreshLevel(name,settings,new WorldOptions(1024,false,false),registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if(stage>=1&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                org.embeddedt.embeddium.validation.weather.WeatherValidation.tick(mc);
                if(++settled<120)return;
                require(blocks.get()>0&&meshes.get()>0,"meshing callbacks not invoked");
                var ground=mc.player.blockPosition().below();
                require(mc.levelRenderer.isChunkCompiled(ground),"terrain section not compiled");
                if(stage==1) {
                    var lines=new net.minecraft.client.gui.components.DebugScreenOverlay(mc) {
                        List<String> rendererLines() {
                            this.block=mc.player.pick(20,0,false);
                            this.liquid=mc.player.pick(20,0,true);
                            return getSystemInformation();
                        }
                    }.rendererLines();
                    require(lines.stream().anyMatch(line->line.toString().contains("Nocturna Renderer (")),"Nocturna renderer name missing from F3");
                    require(lines.stream().noneMatch(line->line.toString().contains("Celeritas Renderer")),"old renderer name in F3");
                    require(lines.stream().anyMatch(line->line.toString().startsWith("Shaderpack: ")),"shader pack missing from F3");
                    require(lines.stream().anyMatch(line->line.toString().startsWith("Direct Buffers: ")),"native buffer memory missing from F3");
                    System.out.println("PASS Nocturna Renderer F3 branding");
                }
                if(net.minecraftforge.fml.ModList.get().isLoaded("immersiveengineering")) AddonValidation.verifyWireGeometry();
                if(stage==1) {
                    ShaderCachePackValidation.reloadWarm();stage=2;settled=0;
                } else if(stage==2) {
                    ShaderCachePackValidation.verifyWarm();
                    RetainedProgramsValidation.verifyWarmPack("reload");
                    RetainedProgramsValidation.offOn();stage=5;settled=0;
                } else if(stage==5) {
                    RetainedProgramsValidation.verifyWarmPack("OFF/ON");
                    Celeritas.options().performance.useCompactVertexFormat=true;
                    Celeritas.options().performance.asyncOcclusionMode=
                        org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode.EVERYTHING;
                    mc.levelRenderer.allChanged();stage=3;settled=0;
                } else {
                    if(net.minecraftforge.fml.ModList.get().isLoaded("codechickenlib")) AddonValidation.verify();
                    HorizonValidation.verifyInWorld();
                    RetainedProgramsValidation.finish();
                    FismValidation.finish();
                    require(System.getProperty("java.specification.version").equals("17"),"wrong Java runtime");
                    var report="PASS Java17; matrix uniform GPU readback; legacy vertex bytes; styled GUI/control/events; vanilla+compact/async terrain; old block/mesh callbacks. blocks="+blocks+" meshes="+meshes;
                    Files.writeString(Path.of("migration-validation.txt"),report);System.out.println(report);
                    ShaderCachePackValidation.restore();mc.stop();stage=4;
                }
            }
        } catch(Throwable failure) {
            failure.printStackTrace();
            try{ShaderCachePackValidation.restore();}catch(Exception ignored){}
            try{Files.writeString(Path.of("migration-validation.txt"),"FAIL "+failure);}catch(Exception ignored){}
            Runtime.getRuntime().halt(1);
        }
    }
}
