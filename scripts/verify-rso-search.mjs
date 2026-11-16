import {execFileSync} from 'node:child_process';
import {mkdirSync, readdirSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const [upstreamArg, java17, java25, fastutil, icu, upstreamClasspath, portClasspath] = process.argv.slice(2);
if (!portClasspath) throw new Error('Usage: node scripts/verify-rso-search.mjs <RSO checkout> <JDK17> <JDK25> <fastutil.jar> <rso-unicode.jar> <upstream-classpath.txt> <port-classpath.txt>');
const upstream = resolve(upstreamArg);
const commit = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: upstream, encoding: 'utf8'}).trim();
if (commit !== '7093d1d50514125cf933cd02e1336c69a3147289') throw new Error(`Unexpected RSO revision: ${commit}`);
const relative = 'me/flashyreese/mods/reeses_sodium_options/client/search';
const destination = join(root, 'build/audits/rso-search');
const separator = process.platform === 'win32' ? ';' : ':';
const java = (home, command) => join(home, 'bin', command + (process.platform === 'win32' ? '.exe' : ''));
const outputs = [];
for (const [label, source, jdk, extra] of [
    ['upstream-java25', join(upstream, 'common/src/main/java'), java25, []],
    ['nocturna-java17', join(root, 'modern/src/main/rso_java'), java17, [join(root, 'modern/src/main/rso_java/org/embeddedt/embeddium/impl/gui/rso/compat/Unicode16.java')]]
]) {
    const classes = join(destination, label);
    mkdirSync(classes, {recursive: true});
    const sources = readdirSync(join(source, relative)).filter(name => name.endsWith('.java')).map(name => join(source, relative, name));
    const nativeClasspath = readFileSync(label === 'upstream-java25' ? upstreamClasspath : portClasspath, 'utf8').trim();
    const dependencies = [resolve(fastutil), resolve(icu), nativeClasspath].join(separator);
    execFileSync(java(jdk, 'javac'), ['-encoding', 'UTF-8', '-classpath', dependencies, '-d', classes,
        ...sources, ...extra, join(root, 'scripts/fixtures/RsoSearchTrace.java')], {stdio: 'pipe'});
    const output = execFileSync(java(jdk, 'java'), ['-Xmx512m', '-classpath', classes + separator + dependencies, 'RsoSearchTrace'], {encoding: 'utf8'}).replaceAll('\r\n', '\n');
    writeFileSync(join(destination, label + '.txt'), output);
    console.log(label + '\n' + output.trim());
    outputs.push(output);
}
if (outputs[0] !== outputs[1]) throw new Error('RSO search behavior differs; see build/audits/rso-search');
console.log('PASS: normalization, n-grams for every Unicode scalar, and search ranking match the original RSO release.');
