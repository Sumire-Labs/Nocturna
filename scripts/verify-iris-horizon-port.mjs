import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const revision = 'bff1e69cb6c5519d8745784aa9c8b92984de67e7';
const root = process.argv[2] ?? '.git/research/iris-latest';
const original = execFileSync('git', ['-C', root, 'show',
    `${revision}:common/src/main/java/net/irisshaders/iris/pathways/HorizonRenderer.java`], {encoding: 'utf8'});
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
const expected = method(original, 'void buildHorizon(')
    .replace(/consumer\.addVertex\(([^;]+)\);/g, 'consumer.vertex($1).endVertex();');
for (const [path, signature] of [
    ['modern/src/main/shaders_modern_java/net/irisshaders/iris/pathways/HorizonRenderer.java', 'void buildHorizon('],
    ['modern/src/test/java/net/irisshaders/iris/pathways/IrisHorizonReference.java', 'void buildHorizon('],
    ['modern/src/validation/java/org/embeddedt/embeddium/validation/HorizonValidation.java', 'void buildReference(']
]) {
    const source = readFileSync(path, 'utf8');
    assert.equal(norm(method(source, signature)), norm(expected), path);
    for (const name of ['TOP', 'BOTTOM']) {
        const declaration = new RegExp(`float ${name} = ([^;]+);`);
        assert.equal(source.match(declaration)[1], original.match(declaration)[1], name);
    }
}
const source = readFileSync('modern/src/main/shaders_modern_java/net/irisshaders/iris/pathways/HorizonRenderer.java', 'utf8');
assert.ok(source.includes('builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION)'));
assert.ok(norm(source).includes('this.buffer.close();'));
assert.ok(norm(source).includes('buildHorizon(this.currentRenderDistance*16,builder);'));
console.log(`Matched complete Iris horizon geometry, float arithmetic and radius clamp at ${revision}.`);
