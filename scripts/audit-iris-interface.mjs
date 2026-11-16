// Static interface inventory, not a shader conformance test or a claim of behavioral equivalence.
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';

const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const reference = path.resolve(option('--reference', '.git/research/iris-1.20.1'));
const output = option('--output', 'docs/audits/iris-interface-inventory.json');
const pinned = 'f8d7bea2ab82d21737e7aa8e4b2fb3a429078106';
const revision = execFileSync('git', ['-C', reference, 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
if (revision !== pinned) throw new Error(`Expected Iris 1.20.1 reference ${pinned}, got ${revision}`);
if (execFileSync('git', ['-C', reference, 'status', '--porcelain', '--untracked-files=no'], {encoding: 'utf8'}).trim())
    throw new Error('The Iris reference has local changes');
const upstreamRoot = path.join(reference, 'src/main/java/net/irisshaders/iris');
const localRoot = 'modern/src/main/shaders_java/net/irisshaders/iris';
const localRoots = [localRoot, 'modern/src/main/shaders_modern_java/net/irisshaders/iris'];
const walk = dir => fs.readdirSync(dir, {withFileTypes: true}).flatMap(e =>
    e.isDirectory() ? walk(path.join(dir, e.name)) : e.name.endsWith('.java') ? [path.join(dir, e.name)] : []);
const read = (root, file) => fs.readFileSync(path.join(root, file), 'utf8');
const difference = (a, b) => [...a].filter(k => !b.has(k)).sort();
const matches = (text, regex) => new Set([...text.matchAll(regex)].map(m => m[1]));
const compare = (a, b) => ({referenceCount: a.size, localCount: b.size,
    missingLocally: difference(a, b), additionalLocally: difference(b, a)});
function uniforms(roots) {
    const result = new Set();
    for (const file of roots.flatMap(walk)) {
        for (const match of fs.readFileSync(file, 'utf8').matchAll(/\.uniform(\w+)\s*\(\s*(?:[^,\n]+,\s*)?"([A-Za-z_]\w*)"/g))
            result.add(`${match[2]}:${match[1]}`);
    }
    return result;
}
const flags = root => matches(read(root, 'features/FeatureFlags.java'), /^\s*([A-Z][A-Z_]+)\(\(\) ->/gm);
const macros = root => matches(read(root, 'gl/shader/StandardMacros.java'), /define\(standardDefines, "([A-Z][A-Z_0-9]+)"/g);
const directives = root => matches(read(root, 'shaderpack/properties/ShaderProperties.java'),
    /handle\w+Directive\(key, value, "([^"]+)"/g);
const hashes = root => Object.fromEntries(['features/FeatureFlags.java', 'gl/shader/StandardMacros.java',
    'shaderpack/properties/ShaderProperties.java', 'uniforms/CommonUniforms.java', 'uniforms/IrisExclusiveUniforms.java']
    .map(file => [file, createHash('sha256').update(read(root, file).replaceAll('\r\n', '\n')).digest('hex')]));
const report = {
    reference: {repository: 'https://github.com/IrisShaders/Iris', branch: '1.20.1', revision},
    limitations: [
        'Literal Java declaration inventory only; conditional branches, dynamic names, values, types and update timing need separate review.',
        'Includes internal uniforms. Counts are not a conformance percentage.',
        'Directives inventory covers handle*Directive calls only; prefix handlers and nested directives are not exhaustive.',
        'Does not execute reference Iris, validate every program, or certify image equivalence.'
    ],
    featureFlags: compare(flags(upstreamRoot), flags(localRoot)),
    literalMacros: compare(macros(upstreamRoot), macros(localRoot)),
    literalUniformRegistrations: compare(uniforms([upstreamRoot]), uniforms(localRoots)),
    literalPropertyHandlers: compare(directives(upstreamRoot), directives(localRoot)),
    fingerprints: {reference: hashes(upstreamRoot), local: hashes(localRoot)}
};
fs.mkdirSync(path.dirname(output), {recursive: true});
fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({...report, fingerprints: undefined}, null, 2));
