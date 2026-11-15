import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {existsSync,readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';

const revision='26f000239eb8ddf7ee191f0ddf369147e3c92ef6';
const upstream=resolve(process.argv.slice(2).find(arg=>!arg.startsWith('--'))??'.git/research/celeritas');
assert.equal(execFileSync('git',['-C',upstream,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),revision);
assert.equal(execFileSync('git',['-C',upstream,'status','--porcelain','--untracked-files=no'],{encoding:'utf8'}).trim(),'');
const files=execFileSync('git',['-C',upstream,'ls-files','-z','common','modern','buildSrc','plugins','gradle','gradlew','gradlew.bat'],{encoding:'utf8'}).split('\0').filter(Boolean);
const normalized=path=>readFileSync(path).toString('binary').replaceAll('\r\n','\n');
const hash=s=>createHash('sha256').update(s,'binary').digest('hex');
const changes=[];let identical=0;
for(const file of files) {
    const original=normalized(resolve(upstream,file));
    const current=existsSync(file)?normalized(file):null;
    if(original===current)identical++;
    else changes.push({path:file,upstream:hash(original),local:current===null?null:hash(current)});
}
const manifestPath='docs/audits/celeritas-migration-delta.json';
const imported = new Set(files);
const additions=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z',
    'common','modern','buildSrc','plugins','gradle','gradlew','gradlew.bat'],{encoding:'utf8'})
    .split('\0').filter(file=>file&&!imported.has(file)&&existsSync(file)).sort()
    .map(path=>({path,local:hash(normalized(path))}));
if(process.argv.includes('--record')) {
    writeFileSync(manifestPath,JSON.stringify({revision,identical,changes,additions},null,2)+'\n');
} else {
    const expected=JSON.parse(readFileSync(manifestPath,'utf8'));
    assert.deepEqual({revision,identical,changes,additions},expected,'Celeritas source delta changed; review it before updating the manifest');
}
console.log(`${identical} upstream files unchanged; ${changes.length} intentional upstream deltas.`);
console.log(`${additions.length} additional compatibility/build/test files pinned.`);
console.log(changes.map(c=>c.path).join('\n'));
