// Execute Iris's complete original compatibility transformer in an isolated Java 17 oracle.
// No original Iris class or oracle dependency is added to the product or its runtime classpath.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
const args = process.argv.slice(2);
const option = (key, fallback) => args.includes(key) ? args[args.indexOf(key) + 1] : fallback;
const reference = path.resolve(option('--reference', '.git/research/iris-1.20.1'));
const directory = path.resolve(option('--work-dir', '.git/research/iris-varying-oracle'));
const javaHome = option('--java-home', process.env.JAVA_HOME);
if (!javaHome) throw Error('Specify a Java 17+ JDK with --java-home');
const revision = 'f8d7bea2ab82d21737e7aa8e4b2fb3a429078106';
if (execFileSync('git', ['-C', reference, 'rev-parse', 'HEAD'], {encoding:'utf8'}).trim() !== revision) throw Error('Wrong Iris revision');
if (execFileSync('git', ['-C', reference, 'status', '--porcelain', '--untracked-files=no'], {encoding:'utf8'}).trim()) throw Error('Iris reference has local changes');
const file = 'src/main/java/net/irisshaders/iris/pipeline/transform/transformer/CompatibilityTransformer.java';
const original = fs.readFileSync(path.join(reference, file), 'utf8').replaceAll('\r\n', '\n');
const hash = createHash('sha256').update(original).digest('hex');
if (hash !== '638df97fb2c809aafd2606635768d46e3b58db6e1d1db23e333ebda32c8cc7a0') throw Error('Original transformer was modified');
function write(file, contents) {fs.mkdirSync(path.dirname(file), {recursive:true}); fs.writeFileSync(file, contents);}
const sourceRoot = path.join(directory, 'src');
const source = (file, contents) => write(path.join(sourceRoot, file), contents);
// Package relocation only. The complete original algorithm and matcher/template definitions are unchanged.
source('oracle/original/pipeline/transform/transformer/CompatibilityTransformer.java', original.replaceAll('net.irisshaders.iris', 'oracle.original'));
source('oracle/original/pipeline/transform/PatchShaderType.java', fs.readFileSync(path.join(reference, 'src/main/java/net/irisshaders/iris/pipeline/transform/PatchShaderType.java'), 'utf8').replaceAll('net.irisshaders.iris', 'oracle.original'));
// Host-only adapters: stage identity, diagnostics and configuration. transformGrouped uses name for logs only.
source('oracle/original/gl/shader/ShaderType.java', 'package oracle.original.gl.shader; public enum ShaderType {VERTEX,GEOMETRY,TESSELATION_CONTROL,TESSELATION_EVAL,FRAGMENT,COMPUTE}');
source('oracle/original/pipeline/transform/parameter/Parameters.java', 'package oracle.original.pipeline.transform.parameter; import oracle.original.pipeline.transform.PatchShaderType; public class Parameters {public PatchShaderType type; public String name;}');
source('oracle/original/Iris.java', 'package oracle.original; public class Iris {public static final org.apache.logging.log4j.Logger logger=org.apache.logging.log4j.LogManager.getLogger(); public static Config getIrisConfig(){return new Config();} public static class Config {public boolean areDebugOptionsEnabled(){return false;}}}');
source('Oracle.java', `
import java.nio.file.*;import java.util.*;
import io.github.douira.glsl_transformer.ast.node.*;
import io.github.douira.glsl_transformer.ast.print.*;
import io.github.douira.glsl_transformer.ast.query.*;
import io.github.douira.glsl_transformer.ast.transform.*;
import oracle.original.pipeline.transform.*;
import oracle.original.pipeline.transform.parameter.*;
import oracle.original.pipeline.transform.transformer.*;
public class Oracle {
 public static void main(String[] args)throws Exception {
  try(var cases=Files.list(Path.of(args[0]))) {
   for(var dir:cases.filter(Files::isDirectory).sorted().toList()) {
    var parser=new ASTParser();parser.getLexer().version=Version.fromNumber(450);
    var trees=new EnumMap<PatchShaderType,TranslationUnit>(PatchShaderType.class);
    for(var stage:PatchShaderType.values()) {
     var input=dir.resolve(stage.name()+".in");
     if(Files.exists(input))trees.put(stage,parser.parseTranslationUnit(RootSupplier.PREFIX_UNORDERED_ED_EXACT,Files.readString(input)));
    }
    var parameters=new Parameters();parameters.name=dir.getFileName().toString();
    CompatibilityTransformer.transformGrouped(parser,trees,parameters);
    for(var e:trees.entrySet())Files.writeString(dir.resolve(e.getKey().name()+".ref"),ASTPrinter.print(PrintType.INDENTED,e.getValue()));
   }
  }
 }
}`);
const vertex = '#version 450 core\nvoid main(){gl_Position=vec4(0,0,0,1);}';
const fixtures = [];
const numericTypes = ['float','int','uint','bool','double'];
for (const prefix of ['', 'i', 'u', 'b', 'd']) for (const size of [2,3,4]) numericTypes.push(`${prefix}vec${size}`);
for (const prefix of ['', 'd']) for (const columns of [2,3,4]) {
 numericTypes.push(`${prefix}mat${columns}`);
 for (const rows of [2,3,4]) numericTypes.push(`${prefix}mat${columns}x${rows}`);
}
for (const type of numericTypes) {
    fixtures.push({name:`numeric-${type}`, stages:{VERTEX:vertex, FRAGMENT:`#version 450 core\nin ${type} value;void main(){${type} copy=value;}`}});
}
fixtures.push(
    {name:'mellow-minimal',stages:{VERTEX:vertex,FRAGMENT:'#version 450 core\nin vec3 ViewPos;out vec4 color;void main(){color=vec4(ViewPos,1);}'}},
    {name:'qualifiers',stages:{VERTEX:vertex,FRAGMENT:'#version 450 core\nlayout(location=3) flat highp in ivec3 index;out vec4 color;void main(){color=vec4(index,1);}'}},
    {name:'local-and-prefix',stages:{VERTEX:'#version 450 core\nvoid helper(){vec3 ViewPos;ViewPos=vec3(1);}void main(){vec3 ViewPosition;ViewPosition=vec3(2);helper();gl_Position=vec4(0,0,0,1);}',FRAGMENT:'#version 450 core\nin vec3 ViewPos;out vec4 color;void main(){color=vec4(ViewPos,1);}'}},
    {name:'multiple',stages:{VERTEX:vertex,FRAGMENT:'#version 450 core\nin vec3 first, second;out vec4 color;void main(){color=vec4(first+second,1);}'}},
    {name:'assigned-and-unused',stages:{VERTEX:'#version 450 core\nout vec3 value;void main(){value=vec3(4);gl_Position=vec4(0,0,0,1);}',FRAGMENT:'#version 450 core\nin vec3 value, unused;out vec4 color;void main(){color=vec4(value,1);}'}},
    {name:'declared-unassigned',stages:{VERTEX:'#version 450 core\nout vec3 value;void main(){gl_Position=vec4(0,0,0,1);}',FRAGMENT:'#version 450 core\nin vec3 value;out vec4 color;void main(){color=vec4(value,1);}'}},
    {name:'declared-double-unassigned',stages:{VERTEX:'#version 450 core\nout dmat2x3 value;void main(){gl_Position=vec4(0,0,0,1);}',FRAGMENT:'#version 450 core\nin dmat2x3 value;void main(){dmat2x3 copy=value;}'}},
    {name:'non-numeric',stages:{VERTEX:vertex,FRAGMENT:'#version 450 core\nstruct Payload{vec3 color;};in Payload value;void main(){vec3 copy=value.color;}'}},
    {name:'geometry-array',stages:{VERTEX:vertex,GEOMETRY:'#version 450 core\nlayout(points) in;layout(points,max_vertices=1) out;in vec3 value[];void main(){gl_Position=vec4(value[0],1);EmitVertex();EndPrimitive();}'}}
);
const fixtureRoot = path.join(directory,'fixtures');
for (const fixture of fixtures) for (const [stage,text] of Object.entries(fixture.stages)) write(path.join(fixtureRoot,fixture.name,stage+'.in'),text);
const artifacts = ['io/github/douira/glsl-transformer/2.0.1/glsl-transformer-2.0.1.jar','org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar','org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar','org/apache/logging/log4j/log4j-core/2.19.0/log4j-core-2.19.0.jar'];
const jars=[];
for(const artifact of artifacts) {
 const target=path.join(directory,'lib',path.basename(artifact)),url='https://repo.maven.apache.org/maven2/'+artifact;
 const digest=(await (await fetch(url+'.sha1')).text()).trim().split(/\s/)[0];
 if(!fs.existsSync(target))write(target,Buffer.from(await (await fetch(url)).arrayBuffer()));
 if(createHash('sha1').update(fs.readFileSync(target)).digest('hex')!==digest)throw Error('Artifact checksum mismatch: '+artifact);
 jars.push(target);
}
const walk=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);
const classes=path.join(directory,'classes');fs.mkdirSync(classes,{recursive:true});
const extension=process.platform==='win32'?'.exe':'';
execFileSync(path.join(javaHome,'bin','javac'+extension),['--release','17','-cp',jars.join(path.delimiter),'-d',classes,...walk(sourceRoot)],{stdio:'inherit'});
execFileSync(path.join(javaHome,'bin','java'+extension),['-cp',[classes,...jars].join(path.delimiter),'Oracle',fixtureRoot],{stdio:'inherit'});
for(const fixture of fixtures) fixture.expected=Object.fromEntries(Object.keys(fixture.stages).map(stage=>[stage,fs.readFileSync(path.join(fixtureRoot,fixture.name,stage+'.ref'),'utf8')]));
const output=option('--output','modern/src/test/resources/iris-varying-reference.json');
write(output,JSON.stringify({reference:{revision,file,sha256:hash},fixtures},null,2)+'\n');
console.log('Executed complete Iris transformer for '+fixtures.length+' fixtures -> '+output);
