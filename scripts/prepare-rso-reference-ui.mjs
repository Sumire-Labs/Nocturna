import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const [sourceArg,outputArg]=process.argv.slice(2);if(!outputArg)throw Error('Usage: node scripts/prepare-rso-reference-ui.mjs <upstream checkout> <generated source dir>');
const source=resolve(sourceArg),output=resolve(outputArg);
if(execFileSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'}).trim()!=='7093d1d50514125cf933cd02e1336c69a3147289')throw Error('Unexpected RSO reference revision');
const entries=JSON.parse(readFileSync(join(root,'docs/audits/rso-2.2.5-source.json'),'utf8')).entries
    .filter(e=>e.path.startsWith('common/src/main/java/me/flashyreese/mods/reeses_sodium_options/client/')&&!e.excluded);
for(const entry of entries){
    const rel=entry.path.split('/reeses_sodium_options/')[1],target=join(output,rel);
    const text=readFileSync(join(source,entry.path),'utf8').replaceAll('\r\n','\n');
    if(createHash('sha256').update(text).digest('hex')!==entry.sha256)throw Error('Reference source changed: '+entry.path);
    mkdirSync(dirname(target),{recursive:true});writeFileSync(target,text);
}
const patch=readFileSync(join(root,'scripts/fixtures/rso-1.20.1-host.patch'),'utf8').replaceAll('\r\n','\n');
execFileSync('git',['apply','--unsafe-paths','--directory='+output,'-'],{cwd:root,input:patch});
const original='me.flashyreese.mods.reeses_sodium_options',reference='org.embeddedt.embeddium.validation.reference.rso';
for(const entry of entries){
    const file=join(output,entry.path.split('/reeses_sodium_options/')[1]);
    let text=readFileSync(file,'utf8').replaceAll(original,reference);
    if(file.endsWith('SodiumVideoOptionsScreen.java'))text=text
        .replace('import org.embeddedt.embeddium.impl.gui.rso.compat.GuiEventListener;','import net.minecraft.client.gui.components.events.GuiEventListener;')
        .replace('import org.embeddedt.embeddium.impl.gui.rso.compat.ContainerEventHandler;','import net.minecraft.client.gui.components.events.ContainerEventHandler;')
        .replaceAll('focused.keyPressed(event)','org.embeddedt.embeddium.impl.gui.rso.compat.NativeGuiEvents.keyPressed(focused,event)');
    for(const shared of ['client.gui.option.OptionExtended','client.gui.option.OptionStateProvider','client.gui.SodiumWidgetDimensions','client.config.ReeseSodiumOptionsConfig'])
        text=text.replaceAll(reference+'.'+shared,original+'.'+shared);
    writeFileSync(file,text);
}
console.log('Prepared '+entries.length+' original RSO MIT sources with the recorded Java17/Minecraft1.20.1 host adapter.');
