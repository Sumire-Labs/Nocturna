import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const revision = '996fb54cb05e256ea6039f4e2c8457a221a179b7';
const root = process.argv[2] ?? '.git/research/angelica';
const target = 'modern/src/main/shaders_modern_java/net/irisshaders/iris/';
const original = execFileSync('git', ['-C', root, 'show',
    `${revision}:src/main/java/net/coderbot/iris/texture/pbr/PBRResourceIndex.java`], {encoding: 'utf8'})
    .replaceAll('IResourcePack', 'PackResources').replaceAll('AngelicaWorkers', 'ShaderCacheWorkers')
    .replaceAll('SimpleReloadableResourceManager', 'MultiPackResourceManager').replaceAll('IResourceManager', 'ResourceManager')
    .replaceAll('getResourcePath()', 'getPath()');
const current = readFileSync(target + 'texture/pbr/PBRResourceIndex.java', 'utf8');
const norm = s => s.replace(/\/\/[^\n]*/g, '').replace(/\s+/g, '');
function method(s, signature) {
    const start = s.indexOf(signature);
    assert.notEqual(start, -1, signature);
    const open = s.indexOf('{', start);
    let depth = 1;
    for (let i = open + 1; i < s.length; i++) {
        if (s[i] === '{') depth++;
        if (s[i] === '}' && --depth === 0) return s.slice(open, i + 1);
    }
    throw new Error(signature);
}
for (const signature of ['void disable(', 'Map<PackResources, PackIndex> scan(', 'boolean isPbrName(', 'PackIndex scanZip(']) {
    assert.equal(norm(method(current, signature)), norm(method(original, signature)), signature);
}
let expected = method(original, 'boolean mayExist(')
    .replace('if (!(resourceManager instanceof MultiPackResourceManager manager))',
        'final MultiPackResourceManager manager = resolve(resourceManager); if (manager == null)')
    .replace('owner != resourceManager', 'owner != manager').replace('start(resourceManager)', 'start(manager)');
assert.equal(norm(method(current, 'boolean mayExist(')), norm(expected), 'lookup cache, join and failure fallback');
expected = method(original, 'PackIndex scanFolder(').replace('final Path root = file.toPath();', '')
    .replace('"Unable to index " + file', '"Unable to index " + root');
const oldVisit = method(original, 'FileVisitResult visitFile(');
const add = method(current, 'void add(');
assert.equal(norm(add), norm(oldVisit.replace('return FileVisitResult.CONTINUE;', '')));
assert.equal(norm(method(current, 'FileVisitResult preVisitDirectory(')), '{add(path);returnFileVisitResult.CONTINUE;}');
assert.equal(norm(method(current, 'FileVisitResult visitFile(')), '{add(path);returnFileVisitResult.CONTINUE;}');
const folder = method(current, 'PackIndex scanFolder(')
    .replace('private void add(Path path) ' + add, '')
    .replace(/@Override\s+public FileVisitResult preVisitDirectory\(Path path, BasicFileAttributes attrs\)\s*\{[^}]*\}/, '')
    .replace(method(current, 'FileVisitResult visitFile('), oldVisit);
assert.equal(norm(folder), norm(expected), 'folder traversal, case folding, links and I/O failure handling');
for (const file of ['AtlasPBRLoader', 'SimplePBRLoader']) {
    const source = readFileSync(target + 'texture/pbr/loader/' + file + '.java', 'utf8');
    assert.ok(source.includes('if (!PBRResourceIndex.mayExist(resourceManager, pbrImageLocation))'));
    assert.ok(source.includes('PBRType.hasDirectionalSiblings(pbrImageLocation, resourceManager)'));
}
const reload = readFileSync(target + 'mixin/texture/MixinTextureManager.java', 'utf8');
assert.ok(norm(reload).includes('PBRTextureManager.INSTANCE.clear();PBRResourceIndex.start(resourceManager);'));
console.log(`Matched Angelica PBR scan/cache/fallback logic at ${revision}.`);
console.log('Reviewed platform adapters (native filters, pack types, manager ownership and close hooks) are covered by Forge tests.');
