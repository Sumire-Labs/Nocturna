import {execFileSync} from 'node:child_process';
import {mkdirSync,readFileSync,writeFileSync} from 'node:fs';
import {resolve,join,delimiter} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const [jdk17,jdk25,referenceCp,portCp,sodiumMod]=process.argv.slice(2);
if(!sodiumMod)throw Error('Usage: node scripts/verify-rso-ui-contracts.mjs <JDK17> <JDK25> <reference-classpath.txt> <port-classpath.txt> <reference-sodium-mod.jar>');
const output=join(root,'build/audits/rso-ui-contracts');mkdirSync(output,{recursive:true});
const exe=(home,name)=>join(home,'bin',name+(process.platform==='win32'?'.exe':''));
for(const fixture of ['RsoThemeTrace','RsoPromptTrace','RsoNarrationTrace']){
    const results=[];
    for(const [mode,jdk,classpath] of [['reference',jdk25,readFileSync(referenceCp,'utf8').trim()+delimiter+resolve(sodiumMod)],['port',jdk17,readFileSync(portCp,'utf8').trim()]]){
        const classes=join(output,mode);mkdirSync(classes,{recursive:true});
        execFileSync(exe(jdk,'javac'),['-encoding','UTF-8','--release','17','-d',classes,join(root,'scripts/fixtures',fixture+'.java')]);
        const raw=execFileSync(exe(jdk,'java'),['-cp',classes+delimiter+classpath,fixture,mode],{encoding:'utf8'}).replaceAll('\r\n','\n');
        const text=raw.split('\n').filter(line=>line.startsWith('TRACE ')).map(line=>line.slice(6)).join('\n')+'\n';
        if(text==='\n')throw Error('The fixture emitted no trace: '+raw);
        writeFileSync(join(output,fixture+'-'+mode+'.txt'),text);results.push(text);
    }
    if(results[0]!==results[1])throw Error(fixture+' differs from the reference runtime');
    console.log('PASS '+fixture+'\n'+results[1].trim());
}
