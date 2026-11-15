// Compare the ported algorithms with the pinned LGPL Embeddium sources, allowing only namespace/API name mapping.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

const allocationCommit = 'ccc1876c44610a5b810bf75fe4ea998bba553122';
const layeringCommit = 'fcd5137288f869c22ffad23c499c6a490e0f2e3a';
const bufferEligibilityCommit = '2e56234798852be1ed0988aca570154b56dacbb2';
const adaptiveFencingCommit = '5263007efef1749898f9172a7f918930c34b6250';
const upstreamRoot = 'src/main/java/org/embeddedt/embeddium/impl/';
const localRoot = 'src/main/java/me/jellysquid/mods/sodium/';

function upstream(commit, path) {
    return execFileSync('git', ['show', `${commit}:${upstreamRoot}${path}`], { encoding: 'utf8' });
}

function normalize(source) {
    return source
        .replaceAll('org.embeddedt.embeddium.impl.render.chunk.sorting.', 'org.embeddedt.embeddium.render.chunk.sorting.')
        .replaceAll('org.embeddedt.embeddium.impl.render.chunk.sprite.', 'KEEP_SPRITE_PACKAGE.')
        .replaceAll('org.embeddedt.embeddium.impl.mixin.', 'me.jellysquid.mods.sodium.mixin.')
        .replaceAll('org.embeddedt.embeddium.impl.', 'me.jellysquid.mods.sodium.client.')
        .replaceAll('KEEP_SPRITE_PACKAGE.', 'org.embeddedt.embeddium.impl.render.chunk.sprite.')
        .replaceAll('Embeddium.options()', 'SodiumClientMod.options()')
        .replace(/\s+/g, '');
}

function method(source, signature) {
    const start = source.indexOf(signature);
    assert.notEqual(start, -1, `Missing method: ${signature}`);
    const open = source.indexOf('{', start);
    let depth = 1;
    for (let i = open + 1; i < source.length; i++) {
        if (source[i] === '{') depth++;
        if (source[i] === '}' && --depth === 0) return source.slice(open, i + 1);
    }
    throw new Error(`Unclosed method: ${signature}`);
}

function compareMethods(upstreamPath, localPath, signatures) {
    const reference = upstream(layeringCommit, upstreamPath);
    const port = readFileSync(localPath, 'utf8');
    for (const signature of signatures) {
        assert.equal(normalize(method(port, signature)), normalize(method(reference, signature)), signature);
        console.log(`Identical upstream algorithm: ${signature}`);
    }
}

const meshPath = 'render/chunk/vertex/builder/ChunkMeshBufferBuilder.java';
assert.equal(normalize(readFileSync(`${localRoot}client/${meshPath}`, 'utf8')),
    normalize(upstream(allocationCommit, meshPath)), meshPath);
console.log('Identical upstream class: ChunkMeshBufferBuilder');

const referenceBuildBuffers = upstream(allocationCommit, 'render/chunk/compile/ChunkBuildBuffers.java');
const portBuildBuffers = readFileSync(`${localRoot}client/render/chunk/compile/ChunkBuildBuffers.java`, 'utf8');
const allocation = /vertexBuffers\[facing\] = new ChunkMeshBufferBuilder\([^;]+;/;
assert.equal(normalize(portBuildBuffers.match(allocation)?.[0] ?? ''),
    normalize(referenceBuildBuffers.match(allocation)?.[0] ?? 'MISSING'));
console.log('Identical upstream initial capacity and sort-state selection');

compareMethods('model/quad/properties/ModelQuadFlags.java',
    `${localRoot}client/model/quad/properties/ModelQuadFlags.java`,
    ['getQuadFlags(ModelQuadView quad, Direction face, int existingFlags)']);
compareMethods('mixin/core/model/quad/BakedQuadMixin.java',
    `${localRoot}mixin/core/model/quad/BakedQuadMixin.java`, ['int getFlags()', 'SpriteTransparencyLevel getTransparencyLevel()']);
const referenceColor = execFileSync('git', ['show',
    `${layeringCommit}:src/main/java/org/embeddedt/embeddium/api/util/ColorABGR.java`], { encoding: 'utf8' });
const portColor = readFileSync('src/main/java/net/caffeinemc/mods/sodium/api/util/ColorABGR.java', 'utf8');
assert.equal(normalize(method(portColor, 'int unpackAlpha(')), normalize(method(referenceColor, 'int unpackAlpha(')));
console.log('Identical existing ColorABGR alpha extraction');
compareMethods('render/chunk/compile/pipeline/BlockRenderer.java',
    `${localRoot}client/render/chunk/compile/pipeline/BlockRenderer.java`,
    ['BlockRenderer(ColorProviderRegistry', 'SpriteTransparencyLevel getQuadTransparencyLevel(',
        'void checkQuadsHaveSameLightingConfig(',
        'ChunkModelBuilder chooseOptimalBuilder(', 'void renderQuadList(', 'void writeGeometry(']);

// The model entry points use different version-specific APIs, but side-to-null-face policy propagation must match.
const referenceRenderer = upstream(layeringCommit, 'render/chunk/compile/pipeline/BlockRenderer.java');
const portRenderer = readFileSync(`${localRoot}client/render/chunk/compile/pipeline/BlockRenderer.java`, 'utf8');
function facePolicy(source) {
    const body = method(source, 'void renderModel(');
    return body.slice(body.indexOf('var nullCullfaceFlags'));
}
assert.equal(normalize(facePolicy(portRenderer)), normalize(facePolicy(referenceRenderer)));
console.log('Identical upstream side/null-face policy propagation');

const writerPath = 'mixin/core/render/immediate/consumer/BufferBuilderMixin.java';
const referenceWriter = upstream(bufferEligibilityCommit, writerPath);
const portWriter = readFileSync(`${localRoot}${writerPath}`, 'utf8');
assert.equal(normalize(method(portWriter, 'boolean canUseIntrinsics()')),
    normalize(method(referenceWriter, 'boolean canUseIntrinsics()').replaceAll('this.embeddiumFormat', 'this.format')));
console.log('Identical upstream BufferBuilder intrinsic eligibility, including condition order');

const frameMixinPath = 'mixin/core/MinecraftClientMixin.java';
const referenceFrameMixin = upstream(adaptiveFencingCommit, frameMixinPath);
const portFrameMixin = readFileSync(`${localRoot}${frameMixinPath}`, 'utf8');
for (const signature of ['void preRender(', 'void postRender(']) {
    assert.equal(normalize(method(portFrameMixin, signature)), normalize(method(referenceFrameMixin, signature)));
    console.log(`Identical upstream frame fencing: ${signature}`);
}
const fenceQueue = /private final LongArrayFIFOQueue fences = [^;]+;/;
assert.equal(normalize(portFrameMixin.match(fenceQueue)?.[0] ?? ''),
    normalize(referenceFrameMixin.match(fenceQueue)?.[0] ?? 'MISSING'));

const referencePreLaunch = upstream(adaptiveFencingCommit, 'EmbeddiumPreLaunch.java')
    .replaceAll('net.neoforged.', 'net.minecraftforge.');
const portPreLaunch = readFileSync(`${localRoot}client/SodiumPreLaunch.java`, 'utf8');
assert.equal(normalize(method(portPreLaunch, 'void onPreLaunch()')),
    normalize(method(referencePreLaunch, 'void onPreLaunch()')));
const configPath = 'compatibility/checks/Configuration.java';
assert.equal(normalize(readFileSync(`${localRoot}client/${configPath}`, 'utf8')),
    normalize(upstream(adaptiveFencingCommit, configPath)));
assert.equal(existsSync(`${localRoot}client/compatibility/checks/EarlyDriverScanner.java`), false);
console.log('Identical upstream queue initialization and Intel startup-check removal');
