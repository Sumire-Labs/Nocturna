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
// An index snapshot lets a shared workspace be audited without including someone else's in-progress edits.
let indexed;
if (process.argv.includes('--index')) {
    const entries=execFileSync('git',['ls-files','--stage','-z'],{encoding:'utf8'}).split('\0').filter(Boolean)
        .map(entry=>({path:entry.slice(entry.indexOf('\t')+1),sha:entry.split(' ')[1]}));
    const objects=execFileSync('git',['cat-file','--batch'],{input:entries.map(e=>e.sha).join('\n')+'\n',maxBuffer:64*1024*1024});
    indexed=new Map();let cursor=0;
    for (const entry of entries) {
        const newline=objects.indexOf(10,cursor);
        const size=Number(objects.subarray(cursor,newline).toString().split(' ')[2]);
        assert.ok(Number.isFinite(size));
        cursor=newline+1;
        indexed.set(entry.path,objects.subarray(cursor,cursor+size).toString('binary').replaceAll('\r\n','\n'));
        cursor+=size+1;
    }
}
const local=path=>indexed?indexed.get(path)??null:existsSync(path)?normalized(path):null;
const changes=[];let identical=0;
for(const file of files) {
    const original=normalized(resolve(upstream,file));
    const current=local(file);
    if(original===current)identical++;
    else changes.push({path:file,upstream:hash(original),local:current===null?null:hash(current)});
}
const manifestPath='docs/audits/celeritas-migration-delta.json';
const imported = new Set(files);
const additions=execFileSync('git',['ls-files','--cached',...(process.argv.includes('--tracked-only')||indexed?[]:['--others','--exclude-standard']),'-z',
    'common','modern','buildSrc','plugins','gradle','gradlew','gradlew.bat'],{encoding:'utf8'})
    .split('\0').filter(file=>file&&!imported.has(file)&&local(file)!==null).sort()
    .map(path=>({path,local:hash(local(path))}));
if(process.argv.includes('--record')) {
    writeFileSync(manifestPath,JSON.stringify({revision,identical,changes,additions},null,2)+'\n');
} else {
    const expected=JSON.parse(readFileSync(manifestPath,'utf8'));
    assert.deepEqual({revision,identical,changes,additions},expected,'Celeritas source delta changed; review it before updating the manifest');
}
console.log(`${identical} upstream files unchanged; ${changes.length} intentional upstream deltas.`);
console.log(`${additions.length} additional compatibility/build/test files pinned.`);
console.log(changes.map(c=>c.path).join('\n'));
