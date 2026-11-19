package org.embeddedt.embeddium.shaderprobe;

import com.google.gson.GsonBuilder;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.embeddium.impl.Celeritas;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Mod("nocturna_shader_probe")
public class ShaderPackSmoke {
    public static final AtomicInteger compiles = new AtomicInteger();
    private final Map<String,Object> result = new LinkedHashMap<>();
    private final Path output = Path.of(System.getProperty("nocturna.probe.result"));
    private final long deadline = System.nanoTime() + 600_000_000_000L;
    private final int sceneOffset = Boolean.getBoolean("nocturna.probe.elevated") ? 124 : 0;
    private int stage, settled;
    private boolean scene;

    public ShaderPackSmoke() {MinecraftForge.EVENT_BUS.register(this);}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new AssertionError("Probe timed out at stage "+stage);
            if(stage==0&&(mc.screen instanceof TitleScreen||mc.screen instanceof AccessibilityOnboardingScreen)) {
                stage=1;
                var pack=Path.of(System.getProperty("nocturna.probe.pack"));
                String mode=System.getProperty("nocturna.probe.upscaleMode", "FSR");
                String scale=System.getProperty("nocturna.probe.renderScale", "NATIVE");
                configureUpscaling(mode,scale);
                result.put("upscaleMode",mode);result.put("renderScale",scale);
                result.put("pack",pack.getFileName().toString());result.put("label",System.getProperty("nocturna.probe.label"));
                result.put("java",System.getProperty("java.version"));
                result.put("gpu",org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER));
                result.put("glVersion",org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION));
                result.put("glContextFlags",org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL30C.GL_CONTEXT_FLAGS));
                result.put("sceneOffset",sceneOffset);
                Files.createDirectories(output.getParent());Files.createDirectories(Iris.getShaderpacksDirectory());
                Files.copy(pack,Iris.getShaderpacksDirectory().resolve(pack.getFileName()),StandardCopyOption.REPLACE_EXISTING);
                configureCache();
                var config=Iris.getIrisConfig();config.setShaderPackName(pack.getFileName().toString());config.setShadersEnabled(true);config.save();
                config.setDebugEnabled(Boolean.getBoolean("nocturna.probe.debug"));config.save();
                mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;
                mc.options.framerateLimit().set(60);
                compiles.set(0);Iris.reload();Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());requirePipeline();
                result.put("coldTitleStageCompiles",compiles.get());
                var name="shader-probe-"+UUID.randomUUID();
                var settings=new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.forceSetScreen(new GenericDirtMessageScreen(Component.literal("Shader compatibility probe")));
                mc.createWorldOpenFlows().createFreshLevel(name,settings,new WorldOptions(1024,false,false),registries->{
                    var dimensions=registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
                    if(sceneOffset==0)return dimensions;
                    var flat=((net.minecraft.world.level.levelgen.FlatLevelSource)dimensions.overworld()).settings();
                    var layers=new ArrayList<>(flat.getLayersInfo());
                    layers.add(1,new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(sceneOffset,Blocks.STONE));
                    var raised=flat.withBiomeAndLayers(layers,flat.structureOverrides(),flat.getBiome());
                    return dimensions.replaceOverworldGenerator(registries,new net.minecraft.world.level.levelgen.FlatLevelSource(raised));
                });
            } else if(stage>0&&stage!=7&&stage<=13&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                if(!scene){scene=true;buildScene(mc);}
                if(++settled<100)return;settled=0;if(stage!=12)requirePipeline();
                if(stage==1) {
                    capture(mc,"dry");result.put("coldTotalStageCompiles",compiles.get());setRain(mc,true);stage=2;
                } else if(stage==2) {
                    capture(mc,"rain-instanced");toggleWeather(false);stage=3;
                } else if(stage==3) {
                    capture(mc,"rain-original");toggleWeather(true);
                    mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getCommands().performPrefixedCommand(
                            mc.getSingleplayerServer().createCommandSourceStack(),"fillbiome -8 "+(-64+sceneOffset)+" -8 24 "+(-48+sceneOffset)+" 24 minecraft:snowy_plains"));stage=4;
                } else if(stage==4) {
                    capture(mc,"snow");
                    if(Boolean.getBoolean("nocturna.probe.quick")) {
                        result.put("success",true);finish(mc);stage=7;
                    } else {compiles.set(0);Iris.reload();requirePipeline();stage=5;}
                } else if(stage==5) {
                    capture(mc,"warm-reload");result.put("warmStageCompiles",compiles.get());
                    try{Iris.getIrisConfig().getClass().getMethod("setSaveCompiledShaders",boolean.class).invoke(Iris.getIrisConfig(),false);}
                    catch(NoSuchMethodException ignored){}
                    compiles.set(0);Iris.reload();requirePipeline();stage=6;
                } else if(stage==6) {
                    capture(mc,"source-reload");result.put("sourceReloadStageCompiles",compiles.get());
                    if(Boolean.getBoolean("nocturna.probe.temporalLifecycle")) {
                        configureUpscaling(System.getProperty("nocturna.probe.upscaleMode"),"NATIVE");stage=8;
                    } else {result.put("success",true);finish(mc);stage=7;}
                } else if(stage==8) {
                    capture(mc,"native-scale");
                    configureUpscaling(System.getProperty("nocturna.probe.upscaleMode"),"QUALITY");stage=9;
                } else if(stage==9) {
                    capture(mc,"restore-scaled");
                    configureUpscaling("FSR","QUALITY");stage=10;
                } else if(stage==10) {
                    capture(mc,"spatial-filter");
                    configureUpscaling(System.getProperty("nocturna.probe.upscaleMode"),"QUALITY");stage=11;
                } else if(stage==11) {
                    capture(mc,"restore-temporal");
                    Iris.getIrisConfig().setShadersEnabled(false);Iris.getIrisConfig().save();Iris.reloadAfterToggle();stage=12;
                } else if(stage==12) {
                    capture(mc,"shaders-off");
                    Iris.getIrisConfig().setShadersEnabled(true);Iris.getIrisConfig().save();Iris.reloadAfterToggle();stage=13;
                } else if(stage==13) {
                    capture(mc,"shaders-on");result.put("success",true);finish(mc);stage=7;
                }
            }
        } catch(Throwable e) {
            e.printStackTrace();result.put("success",false);result.put("stage",stage);result.put("error",e.toString());
            result.put("irisError",Iris.getStoredError().map(Throwable::toString).orElse(""));
            try{finish(mc);}catch(Exception failure){failure.printStackTrace();Runtime.getRuntime().halt(1);}stage=7;
        }
    }

    private void configureCache() throws Exception {
        boolean enabled=Boolean.parseBoolean(System.getProperty("nocturna.probe.cache","true"));
        try {
            var disk=Class.forName("net.irisshaders.iris.cache.ShaderDiskCache");
            disk.getMethod("configure",Path.class,String.class).invoke(null,output.getParent().resolve("cache"),"probe-"+UUID.randomUUID());
            Iris.getIrisConfig().getClass().getMethod("setSaveCompiledShaders",boolean.class).invoke(Iris.getIrisConfig(),enabled);
            result.put("cacheEnabled",enabled);
        }catch(ClassNotFoundException|NoSuchMethodException ignored){result.put("cacheEnabled",false);}
    }

    private static void toggleWeather(boolean enabled) throws Exception {
        try{Celeritas.options().performance.getClass().getField("useInstancedWeather").setBoolean(Celeritas.options().performance,enabled);}
        catch(NoSuchFieldException ignored){}
    }

    private void requirePipeline() {
        if(Iris.getCurrentPack().isEmpty()||!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline))
            throw new AssertionError("Shader pack fell back to vanilla: "+Iris.getStoredError().map(Throwable::toString).orElse("pack loading failed"));
        if(!Objects.equals(result.get("pack"),Iris.getCurrentPackName()))
            throw new AssertionError("Wrong active shader pack: "+Iris.getCurrentPackName());
        result.put("activePack",Iris.getCurrentPackName());
    }

    private void capture(Minecraft mc,String name) throws Exception {
        boolean temporal=false;
        String temporalStatus="Temporal FSR is not present in this renderer";
        try {
            var api=Class.forName("net.irisshaders.iris.pathways.scaling.temporal.TemporalUpscaling");
            temporal=(boolean)api.getMethod("isReady").invoke(null);
            temporalStatus=(String)api.getMethod("status").invoke(null);
        } catch(ClassNotFoundException ignored) { }
        result.put(name+"TemporalActive",temporal);
        result.put(name+"TemporalStatus",temporalStatus);
        String expected=System.getProperty("nocturna.probe.expectTemporal", "");
        boolean expectedActive=expected.equals("active")&&!Set.of("native-scale","spatial-filter","shaders-off").contains(name);
        if(!expected.isEmpty() && temporal!=expectedActive)throw new AssertionError("Unexpected temporal state: "+result.get(name+"TemporalStatus"));
        verifyTemporalUniforms(name,temporal);
        result.put(name+"RainStrength",mc.level.getRainLevel(1));
        result.put(name+"Biome",mc.level.getBiome(mc.player.blockPosition()).unwrapKey().orElseThrow().location().toString());
        try {
            var weather=Class.forName("org.embeddedt.embeddium.impl.render.weather.WeatherRenderer");
            result.put(name+"WeatherColumns",weather.getField("lastColumns").getInt(null));
            result.put(name+"WeatherDraws",weather.getField("lastDrawCalls").getInt(null));
        }catch(ClassNotFoundException ignored){}
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var file=output.resolveSibling(output.getFileName().toString().replace(".json","-"+name+".png"));
            image.writeToFile(file);result.put(name+"Screenshot",file.toString());
        }
        int error=org.lwjgl.opengl.GL11C.glGetError();result.put(name+"GlError",error);
        if(error!=0)throw new AssertionError("OpenGL error "+error+" in "+name);
        System.out.println("SHADER PROBE "+result.get("pack")+" "+name+" compiles="+compiles.get());
    }

    /** Read the real linked program values, not only the CPU enabled flag. */
    private void verifyTemporalUniforms(String name,boolean enabled) throws Exception {
        // This fixture consumes both uniforms in composite; the general pack API does not require that placement.
        if(!Objects.equals(result.get("pack"),"temporal-fsr-pack.zip"))return;
        if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
        if(pipeline.getClass().getMethod("getTemporalPackConfig").invoke(pipeline)==null)return;
        var rendererField=IrisRenderingPipeline.class.getDeclaredField("compositeRenderer");rendererField.setAccessible(true);
        Object renderer=rendererField.get(pipeline);
        var passesField=renderer.getClass().getDeclaredField("passes");passesField.setAccessible(true);
        var programField=Class.forName("net.irisshaders.iris.pipeline.CompositeRenderer$Pass").getDeclaredField("program");
        programField.setAccessible(true);
        boolean checked=false;
        for(Object pass:(Iterable<?>)passesField.get(renderer)) {
            var program=programField.get(pass);
            if(program==null)continue;
            int programId=(int)program.getClass().getMethod("getProgramId").invoke(program);
            int location=org.lwjgl.opengl.GL20C.glGetUniformLocation(programId,"nocturnaTemporalEnabled");
            if(location<0)continue;
            int actual=org.lwjgl.opengl.GL20C.glGetUniformi(programId,location);
            if((actual!=0)!=enabled)throw new AssertionError("Real shader temporal uniform differs from backend state");
            result.put(name+"TemporalUniform",actual);
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
                int jitterLocation=org.lwjgl.opengl.GL20C.glGetUniformLocation(programId,"nocturnaTemporalJitter");
                var offset=stack.mallocFloat(2);
                org.lwjgl.opengl.GL20C.glGetUniformfv(programId,jitterLocation,offset);
                if(enabled && offset.get(0)==0 && offset.get(1)==0)throw new AssertionError("Shader received no jitter");
                if(!enabled && (offset.get(0)!=0 || offset.get(1)!=0))throw new AssertionError("Fallback shader received temporal jitter");
                result.put(name+"TemporalJitter",List.of(offset.get(0),offset.get(1)));
            }
            checked=true;
        }
        if(!checked)throw new AssertionError("Temporal fixture had no live temporal uniforms");
    }

    /** Keep the existing probe usable against Celeritas/Oculus revisions without Nocturna's new API. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void configureUpscaling(String mode,String scale) throws Exception {
        try {
            var settings=Class.forName("net.irisshaders.iris.gui.option.IrisVideoSettings");
            var modeField=settings.getField("upscaleMode");
            var scaleField=settings.getField("renderScale");
            modeField.set(null,Enum.valueOf((Class)modeField.getType(),mode));
            scaleField.set(null,Enum.valueOf((Class)scaleField.getType(),scale));
            Class.forName("net.irisshaders.iris.pathways.scaling.RenderScale").getMethod("onSettingsChanged").invoke(null);
        } catch(ClassNotFoundException|NoSuchFieldException ignored) {
            if(!"FSR".equals(mode)||!"NATIVE".equals(scale))throw new UnsupportedOperationException("Renderer has no requested upscaling API");
        }
    }

    private static void setRain(Minecraft mc,boolean rain) {
        mc.level.setRainLevel(rain?1:0);
        mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setWeatherParameters(rain?0:6000,6000,rain,false));
    }

    private void buildScene(Minecraft mc) {
        mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var level=server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setDayTime(6000);
            level.setWeatherParameters(6000,0,false,false);
            for(int x=0;x<16;x++)for(int z=0;z<12;z++)level.setBlockAndUpdate(new BlockPos(x,-61+sceneOffset,z),Blocks.STONE_BRICKS.defaultBlockState());
            for(int y=-60;y<-54;y++)level.setBlockAndUpdate(new BlockPos(5,y+sceneOffset,5),Blocks.STONE.defaultBlockState());
            for(int x=1;x<4;x++)for(int z=3;z<6;z++)level.setBlockAndUpdate(new BlockPos(x,-60+sceneOffset,z),Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(7,-60+sceneOffset,4),Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(9,-60+sceneOffset,4),Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(10,-60+sceneOffset,4),Blocks.GLOWSTONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(11,-60+sceneOffset,4),Blocks.OAK_LEAVES.defaultBlockState());
            var pig=EntityType.PIG.create(level);if(pig!=null){pig.setPos(6,-60+sceneOffset,7);pig.setNoAi(true);level.addFreshEntity(pig);}
            var player=server.getPlayerList().getPlayers().get(0);player.getAbilities().flying=true;player.getAbilities().mayfly=true;
            player.onUpdateAbilities();player.setYRot(180);player.setXRot(30);player.teleportTo(8.5,-53+sceneOffset,13.5);
            mc.execute(()->{mc.player.setYRot(180);mc.player.setXRot(30);});
        });
    }

    private void finish(Minecraft mc) throws Exception {
        result.put("finishedAt",java.time.Instant.now().toString());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));
        mc.stop();
    }
}
