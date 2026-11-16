import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const revision = '82e39b0252a9576190502001ccbadb038c6b5e78';
const root = process.argv[2] ?? '.git/research/iris-latest';
const local = 'modern/src/main/shaders_modern_java/net/irisshaders/iris/mixin/features/shader/shadow_pass/';
const get = name => readFileSync(local + name + '.java', 'utf8');
const upstream = name => execFileSync('git', ['-C', root, 'show',
    `${revision}:common/src/main/java/net/irisshaders/iris/mixin/${name}.java`], {encoding: 'utf8'});
const norm = s => s.replace(/\s+/g, '');
function body(s, method) {
    const start = s.indexOf(method + '(');
    assert.notEqual(start, -1, method);
    const open = s.indexOf('{', start);
    let depth = 1;
    for (let i = open + 1; i < s.length; i++) {
        if (s[i] === '{') depth++;
        if (s[i] === '}' && --depth === 0) return norm(s.slice(open, i + 1));
    }
    throw new Error(method);
}
for (const [from, to, method, target] of [
    ['AbstractSignRendererMixin', 'FismSignRendererMixin', 'fism$cancelSignTextRendering', 'renderSignText'],
    ['MapRendererMixin', 'FismMapRendererMixin', 'fism$cancelMapRendering', 'render'],
    ['ItemStackMixin', 'FismItemStackMixin', 'fism$cancelGlintRendering', 'hasFoil']
]) {
    assert.equal(body(upstream(from), method), body(get(to), method));
    assert.ok(get(to).includes(`method = "${target}"`));
    assert.ok(get(to).includes('at = @At("HEAD"), cancellable = true'));
}
const banner = norm(get('FismBannerRendererMixin'));
assert.ok(banner.includes('IrisApiV0Impl.INSTANCE.isRenderingShadowPass()'));
for (const value of ['flagMaterial.buffer(bufferSource,RenderType::entitySolid,false)',
    'pattern.getFirst().is(BannerPatterns.BASE)', 'Sheets.getBannerMaterial(BannerPatterns.BASE)',
    'Sheets.getShieldMaterial(BannerPatterns.BASE)', 'color[0],color[1],color[2],1.0F', 'ci.cancel();']) {
    assert.ok(banner.includes(value), value);
}
assert.ok(upstream('BannerRendererMixin').includes('banner ? Sheets.BANNER_PATTERN_BASE : Sheets.SHIELD_PATTERN_BASE'));
assert.ok(readFileSync('modern/src/main/java/org/embeddedt/embeddium/impl/mixin/MixinConfig.java', 'utf8')
    .includes('this.addMixinRule("features.shader.shadow_pass", true)'));
console.log(`Matched Iris FISM cancellation bodies at ${revision}.`);
console.log('The 1.20.1 banner API adapter retains substrate + dyed BASE; client tests compare complete vertex streams.');
