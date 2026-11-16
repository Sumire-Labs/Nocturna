import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const upstream = process.argv[2] ?? '.git/research/angelica';
const commit = 'd876a203da9b46ee5b5048171b8e5e4deb9bb967';
const mixins = 'src/mixin/java/com/gtnewhorizons/angelica/mixins/early/celeritas/terrain/';
const local = 'common/src/main/java/org/embeddedt/embeddium/impl/';
const original = path => execFileSync('git', ['-C', upstream, 'show', `${commit}:${path}`], {encoding: 'utf8'});
const source = path => readFileSync(local + path, 'utf8');
const normalize = text => text.replaceAll('this.', '').replaceAll('angelica$buf', 'buffer').replace(/\s+/g, '');

function method(text, signature) {
    const start = text.indexOf(signature);
    assert.notEqual(start, -1, `Missing ${signature}`);
    const open = text.indexOf('{', start);
    let depth = 1;
    for (let i = open + 1; i < text.length; i++) {
        if (text[i] === '{') depth++;
        if (text[i] === '}' && --depth === 0) return text.slice(open, i + 1);
    }
    throw new Error(`Unclosed ${signature}`);
}

for (const [kind, count] of [['3f', 9], ['4f', 16]]) {
    const before = original(mixins + `MixinGlUniformMatrix${kind}.java`);
    const after = source(`gl/shader/uniform/GlUniformMatrix${kind}.java`);
    assert.equal(normalize(method(after, 'void set(')), normalize(method(before, 'void set(')));
    assert.ok(before.includes(`BufferUtils.createFloatBuffer(${count})`));
    assert.ok(normalize(after).includes(`ByteBuffer.allocateDirect(${count}*Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()`));
}
const pool = original('src/main/java/com/gtnewhorizons/angelica/rendering/celeritas/RenderListPool.java');
const port = source('render/chunk/lists/RenderListPool.java');
assert.equal(normalize(method(port, 'ChunkRenderList[] beginSearch(')), normalize(method(pool, 'ChunkRenderList[] beginSearch(')));
assert.equal(normalize(method(port, 'ChunkRenderList acquire(')), normalize(method(pool, 'ChunkRenderList acquire(')
    .replace('((ChunkRenderListReuse) list).angelica$reset()', 'list.reset()')));
assert.equal(normalize(method(source('render/chunk/lists/ChunkRenderList.java'), 'void reset(')),
    normalize(method(original(mixins + 'MixinChunkRenderList.java'), 'void angelica$reset(')));
assert.ok(normalize(port).includes('ChunkRenderList[][]slots={newChunkRenderList[0],newChunkRenderList[0]}'));
console.log(`Matched Angelica ${commit}: mat3/mat4 upload bodies, two-slot pooling, region identity check and all reset counters.`);
