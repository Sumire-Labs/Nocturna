import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';

const revision='7093d1d50514125cf933cd02e1336c69a3147289';
const upstream=resolve(process.argv.slice(2).find(arg=>!arg.startsWith('--'))??'.git/research/rso');
assert.equal(execFileSync('git',['-C',upstream,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),revision);
assert.equal(execFileSync('git',['-C',upstream,'status','--porcelain','--untracked-files=no'],{encoding:'utf8'}).trim(),'');
const files=execFileSync('git',['-C',upstream,'ls-files','-z','common/src/main/java','common/src/main/resources','LICENSE.txt'],{encoding:'utf8'}).split('\0').filter(Boolean);
const hash=path=>{
    let contents=readFileSync(path);
    if(/\.(java|json|txt)$/.test(path)) contents=Buffer.from(contents.toString('utf8').replaceAll('\r\n','\n'));
    return createHash('sha256').update(contents).digest('hex');
};
const hookRoot='modern/src/main/modern_java/org/embeddedt/embeddium/impl/mixin/core/gui/';
const replacements={
    MixinOption:[hookRoot+'RsoOptionMixin.java'],
    MixinAbstractWidget:['modern/src/main/rso_java/org/embeddedt/embeddium/impl/gui/rso/compat/sodium/client/gui/options/control/ControlElement.java'],
    MixinSodiumOptionsGUI:[
        'modern/src/main/java/org/embeddedt/embeddium/impl/mixin/features/gui/hooks/settings/OptionsScreenMixin.java',
        'modern/src/main/rso_java/org/embeddedt/embeddium/impl/gui/rso/NocturnaOptionsSource.java',
        'modern/src/main/gui_java/org/embeddedt/embeddium/impl/gui/modern/EmbeddiumVideoOptionsScreen.java'
    ]
};
const entries=files.map(path=>{
    const source={path,sha256:hash(resolve(upstream,path))};
    if(path.includes('/client/controlify/')||path.endsWith('/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint'))
        return {...source,excluded:'Controlify integration excluded by the requested baseline'};
    let targets;
    if(path.includes('/mixin/'))targets=replacements[path.slice(path.lastIndexOf('/')+1,-5)];
    else if(path.endsWith('/reeses-sodium-options.mixins.json'))targets=['modern/src/main/resources/embeddium.mixins.json'];
    else if(path==='LICENSE.txt')targets=['modern/src/main/rso_resources/LICENSE-RSO.txt'];
    else targets=[path.replace('common/src/main/java/','modern/src/main/rso_java/').replace('common/src/main/resources/','modern/src/main/rso_resources/')];
    assert.ok(targets?.length,`Unmapped source: ${path}`);
    return {...source,targets:targets.map(path=>({path,sha256:hash(path)}))};
});
const result={repository:'https://github.com/FlashyReese/reeses-sodium-options',tag:'mc26.3-2.2.5',revision,entries};
const manifest='docs/audits/rso-2.2.5-source.json';
if(process.argv.includes('--record'))writeFileSync(manifest,JSON.stringify(result,null,2)+'\n');
else assert.deepEqual(result,JSON.parse(readFileSync(manifest,'utf8')),'RSO source delta changed; review it before updating the manifest');
console.log(`Verified ${entries.length} RSO source/license/resource entries; ${entries.filter(e=>e.excluded).length} optional Controlify sources excluded.`);
