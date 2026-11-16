import {execFileSync} from 'node:child_process';
import {mkdirSync,existsSync,writeFileSync} from 'node:fs';
import {join,resolve,delimiter} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
const [jdkArg,mode='run',resourcesArg]=process.argv.slice(2);
if(!jdkArg)throw Error('Usage: node scripts/run-rso-native-input.mjs <JDK17-home> [prepare|run]');
const jdk=resolve(jdkArg),work=join(root,'build/audits/rso-native'),artifacts=join(work,'artifacts'),classes=join(work,'classes');
mkdirSync(artifacts,{recursive:true});mkdirSync(classes,{recursive:true});
const jars=[];
for(const module of ['lwjgl','lwjgl-glfw','lwjgl-opengl']){
    for(const suffix of ['', '-natives-windows','-natives-linux','-natives-macos','-natives-macos-arm64']){
        const file=`${module}-3.3.1${suffix}.jar`,target=join(artifacts,file);
        if(!existsSync(target)){
            const response=await fetch(`https://repo.maven.apache.org/maven2/org/lwjgl/${module}/3.3.1/${file}`);
            if(!response.ok)throw Error(`Could not fetch ${file}: ${response.status}`);
            writeFileSync(target,new Uint8Array(await response.arrayBuffer()));
        }
        jars.push(target);
    }
}
const jna=join(artifacts,'jna-5.10.0.jar');if(!existsSync(jna)){
    const r=await fetch('https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.10.0/jna-5.10.0.jar');if(!r.ok)throw Error('Could not fetch JNA');writeFileSync(jna,new Uint8Array(await r.arrayBuffer()));
}jars.push(jna);
const exe=name=>join(jdk,'bin',name+(process.platform==='win32'?'.exe':''));
execFileSync(exe('javac'),['-encoding','UTF-8','--release','17','-cp',jars.join(delimiter),'-d',classes,
    join(root,'modern/src/main/rso_java/org/embeddedt/embeddium/impl/gui/rso/compat/SdlTextInput.java'),join(root,'scripts/fixtures/RsoNativeInput.java')],{stdio:'inherit'});
const resources=resourcesArg?resolve(resourcesArg):join(root,'modern/versions/1.20.1-forge/build/resources/main');
writeFileSync(join(work,'classpath.txt'),[classes,resources,...jars].join(delimiter));
if(mode!=='prepare')execFileSync(exe('java'),[...(process.platform==='darwin'?['-XstartOnFirstThread']:[]),'-cp',[classes,resources,...jars].join(delimiter),
    'org.embeddedt.embeddium.impl.gui.rso.compat.RsoNativeInput',join(work,process.platform)],{stdio:'inherit'});
console.log('Native input harness prepared in '+work);
