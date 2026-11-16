import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const root=process.argv[2]??'.git/research/angelica';
const revision='996fb54cb05e256ea6039f4e2c8457a221a179b7';
const local='modern/src/main/java/org/embeddedt/embeddium/impl/render/weather/';
const get=p=>readFileSync(local+p+'.java','utf8');
const original=p=>execFileSync('git',['-C',root,'show',`${revision}:${p}`],{encoding:'utf8'});
const norm=s=>s.replace(/\s+/g,'');
function method(s,name) {
 const start=s.indexOf(name);assert.notEqual(start,-1,name);
 const open=s.indexOf('{',start);let depth=1;
 for(let i=open+1;i<s.length;i++){
  if(s[i]==='{')depth++;if(s[i]==='}'&&--depth===0)return s.slice(open,i+1);
 }
 throw new Error(name);
}
const ffp='glsm/src/main/java/com/gtnewhorizons/angelica/glsm/ffp/';
const attrib=original(ffp+'WeatherInstancedAttribs.java');
assert.equal(norm(method(get('WeatherInstancedAttribs'),'void putColumn(')),norm(method(attrib,'void putColumn(')));
for(const name of ['OFFSET_COLUMN_SPAN','OFFSET_JITTER','OFFSET_PARAMS','OFFSET_COLOR','OFFSET_LAYER','STRIDE',
 'RAIN_PERIOD','RAIN_SCROLL_BASE','RAIN_FADE','SNOW_PERIOD','SNOW_U_DRIFT','SNOW_V_DRIFT','SNOW_FADE','SOURCE_VANILLA','SOURCE_PROVIDER']){
 const re=new RegExp(`\\b${name}\\s*=\\s*([^;]+);`);
 assert.equal(norm(get('WeatherInstancedAttribs').match(re)[1]),norm(attrib.match(re)[1]),name);
}
assert.equal(norm(method(get('WeatherParams'),'void set(')),norm(method(original(ffp+'WeatherParams.java'),'void set(')));
const glsl=original(ffp+'InstancedGlslHelpers.java');
const template=s=>s.slice(s.indexOf('private static final String WEATHER_PROLOGUE'),s.indexOf('public static String weatherPrologue'));
assert.equal(norm(template(get('WeatherGlsl'))),norm(template(glsl)));
assert.equal(norm(method(get('WeatherGlsl'),'String weatherPrologue(')),norm(method(glsl,'String weatherPrologue(')));
const columns=original('src/main/java/com/gtnewhorizons/angelica/render/WeatherColumns.java');
const adapted=columns.replaceAll('WorldClient','WeatherWorld').replaceAll('BiomeGenBase','WeatherWorld.Biome');
for(const name of ['void update(','int revision(','int slotAt(','int slotOf(','boolean covers(','int lightAtSlot(']){
 assert.equal(norm(method(get('WeatherColumns'),name)),norm(method(adapted,name)),name);
}
for(const name of ['void resize(','void sample(']){
 const body=method(get('WeatherColumns'),name).replace(/biomes = new WeatherWorld.Biome\[slots\];/,'').replace('biomes[slot] = biome;','');
 assert.equal(norm(body),norm(method(adapted,name)),name);
}
const renderer=original('src/main/java/com/gtnewhorizons/angelica/render/WeatherRenderer.java');
for(const name of ['void begin(','void grow(','void beginLayer(','void column(','void put(','void release(','void upload(']){
 assert.equal(norm(method(get('WeatherBatch'),name).replaceAll('GL15C.','GLSMBuffers.')),norm(method(renderer,name)),name);
}
const mappedDraw=method(renderer,'int draw(')
 .replace('mc.getTextureManager().bindTexture(textures[layer]);','shader.bind(textures[layer]);')
 .replace('if (deferred != null) deferred.bindInstancedVariant(Instancing.WEATHER);','')
 .replace('VAOManager.setCurrentVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);','')
 .replaceAll('GLSMBuffers.','GL15C.').replace('GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLE_FAN','GL31C.glDrawArraysInstanced(shader.drawMode()')
 .replace('draws++;','shader.unbind();draws++;');
assert.equal(norm(method(get('WeatherBatch'),'int draw(')),norm(mappedDraw));
console.log(`Matched Angelica ${revision}: weather cache schedule, RNG/hash, instance layout, frame uniforms, full GLSL expansion and batch lifecycle.`);
console.log('Platform adapters (native biomes/light, Forge hooks, shader bindings/fallback) are validated separately by unit and driver tests.');
