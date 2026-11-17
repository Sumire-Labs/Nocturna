import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

// Pass the pinned upstream checkout as the first argument when it is outside .git/research.
const reference = path.resolve(process.argv[2] ?? '.git/research/sodium-extra');
const manifest = JSON.parse(fs.readFileSync('docs/audits/sodium-extra-port.json', 'utf8'));
const sourceRoot = path.join(reference, 'common/src/main');
const java = 'modern/src/main/extra_java/';
const extra = 'me/flashyreese/mods/sodiumextra/';
const lf = text => text.replaceAll('\r\n', '\n');
const hash = text => crypto.createHash('sha256').update(lf(text)).digest('hex');
const walk = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? walk(path.join(dir, entry.name)) : [path.join(dir, entry.name)]);
assert.equal(execFileSync('git', ['rev-parse', 'HEAD'], { cwd: reference, encoding: 'utf8' }).trim(), manifest.commit);

for (const entry of manifest.files) {
    const source = fs.readFileSync(path.join(sourceRoot, 'java', entry.source), 'utf8');
    assert.equal(hash(source), entry.sha256, `Upstream drift: ${entry.source}`);
    for (const port of entry.ports) assert.ok(fs.existsSync(port), `Missing port: ${port}`);
    if (!entry.ports.length) {
        assert.match(entry.source, /client\/render\/vertex\/formats\//);
        const name = path.basename(entry.source, '.java');
        const callers = walk(path.join(sourceRoot, 'java')).filter(file => !file.endsWith(`${name}.java`))
            .filter(file => new RegExp(`\\b${name}\\b`).test(fs.readFileSync(file, 'utf8')));
        assert.equal(callers.length, 0, `Previously unused declaration gained callers: ${name}`);
    }
}

function tokens(source) {
    source = lf(source).replace(/^package .*?;$/mg, '').replace(/^import .*?;$/mg, '');
    // Preserve Java string/char contents while ignoring formatting and comments.
    const pieces = source.match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\*[\s\S]*?\*\/|\/\/[^\n]*|\S/g) ?? [];
    return pieces.filter(piece => !piece.startsWith('//') && !piece.startsWith('/*')).join('');
}
function adapt(source) {
    return source.replace(/\bIdentifier\b/g, 'ResourceLocation')
        .replaceAll('ResourceLocation.parse(', 'new ResourceLocation(')
        .replaceAll('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')
        .replaceAll('org.embeddedt.embeddium.impl.extra.compat.ExtraMath.clamp(', 'Math.clamp(');
}
function removeAdaptiveProbe(source) {
    const start = source.indexOf('public static boolean isAdaptiveSyncSupported()');
    if (start < 0) return source;
    const body = source.indexOf('{', start);
    let depth = 1, end = body + 1;
    for (; depth; end++) { if (source[end] === '{') depth++; else if (source[end] === '}') depth--; }
    return source.slice(0, body) + '{ return nativeProbe; }' + source.slice(end);
}
const pure = ['client/FrameCounter.java', 'client/config/ConfigFileIO.java', 'client/config/SodiumExtraConfigKeys.java',
    'client/config/SodiumExtraGameOptions.java', 'client/util/ToastFilter.java', 'common/util/IdentifierSerializer.java',
    'common/util/ControlValueFormatterExtended.java', 'common/util/Utils.java', 'compat/IrisCompat.java'];
for (const name of pure) {
    let upstream = fs.readFileSync(path.join(sourceRoot, 'java', extra, name), 'utf8');
    let port = fs.readFileSync(java + extra + name, 'utf8');
    if (name.endsWith('SodiumExtraGameOptions.java')) { upstream = removeAdaptiveProbe(upstream); port = removeAdaptiveProbe(port); }
    assert.equal(tokens(adapt(port)), tokens(adapt(upstream)), `Behavior drift: ${name}`);
}

let config = fs.readFileSync(path.join(sourceRoot, 'java', extra, 'client/config/SodiumExtraConfig.java'), 'utf8');
config = adapt(config).replaceAll('ResourceLocation.DEFAULT_NAMESPACE', '"minecraft"')
    .replaceAll('.identifier()', '.location()')
    .replaceAll('ComponentUtils.isTranslationResolvable(translatable)', 'net.minecraft.client.resources.language.I18n.exists(key)')
    .replaceAll('new DebugOptionsScreen(', 'new ExtraDebugOptionsScreen(')
    .replaceAll('.setScreenAndShow(', '.setScreen(')
    .replaceAll('Minecraft.getInstance().invalidateSurfaceConfiguration();', 'org.embeddedt.embeddium.impl.extra.compat.ExtraPlatform.updateVsync();');
const configPort = fs.readFileSync(java + extra + 'client/config/SodiumExtraConfig.java', 'utf8');
assert.equal(tokens(adapt(configPort)).replaceAll('StatefulOptionBuilder<?,?>', 'StatefulOptionBuilder<?>'), tokens(config), 'Option bindings, defaults, conditions, ranges or grouping changed');

function animationGroups(source) {
    const compact = tokens(adapt(source));
    const matches = [...compact.matchAll(/\(\)->SodiumExtraClientMod\.options\(\)\.animationSettings\.(\w+)/g)];
    return Object.fromEntries(matches.map((match, index) => [match[1],
        [...compact.slice(match.index, matches[index + 1]?.index).matchAll(/newResourceLocation\("([^"]+)","([^"]+)"\)/g)].map(id => id[1] + ':' + id[2])
    ]));
}
const originalAnimations = fs.readFileSync(path.join(sourceRoot, 'java', extra, 'mixin/animation/MixinTextureAtlas.java'), 'utf8');
const portAnimations = fs.readFileSync(java + 'org/embeddedt/embeddium/impl/extra/compat/ExtraAnimations.java', 'utf8');
assert.deepEqual(animationGroups(portAnimations), animationGroups(originalAnimations), 'Animation category ids changed');

let assets = 0;
const brandDisplayText = value => typeof value === 'string'
    ? value.replaceAll('Sodium Extra', 'Nocturna Extra')
        .replaceAll('Disabled while Iris shader packs or Prevent Shaders are active.',
            'Disabled while a shader pack is active in Nocturna Shaders or Prevent Shaders is enabled.')
    : value;
for (const file of walk(path.join(sourceRoot, 'resources/assets/sodium-extra'))) {
    const relative = path.relative(path.join(sourceRoot, 'resources'), file);
    const target = path.join('modern/src/main/extra_resources', relative);
    assert.ok(fs.existsSync(target), `Missing asset: ${relative}`);
    if (/lang[\\/](en_us|ja_jp)\.json$/.test(relative)) {
        const original = JSON.parse(fs.readFileSync(file, 'utf8')), port = JSON.parse(fs.readFileSync(target, 'utf8'));
        for (const [key, value] of Object.entries(original)) assert.equal(port[key], brandDisplayText(value), `Translation changed: ${key}`);
        for (const key of Object.keys(port).filter(key => !(key in original))) assert.match(key, /^sodium-extra\.debug\.visibility\./);
    } else if (/lang[\\/].*\.json$/.test(relative)) {
        const original = JSON.parse(fs.readFileSync(file, 'utf8')), port = JSON.parse(fs.readFileSync(target, 'utf8'));
        assert.deepEqual(port, Object.fromEntries(Object.entries(original).map(([key, value]) => [key, brandDisplayText(value)])), `Translation changed: ${relative}`);
    } else if (file.endsWith('.png')) assert.deepEqual(fs.readFileSync(target), fs.readFileSync(file), `Image changed: ${relative}`);
    else assert.equal(lf(fs.readFileSync(target, 'utf8')), lf(fs.readFileSync(file, 'utf8')), `Asset changed: ${relative}`);
    assets++;
}
console.log(`PASS ${manifest.files.length} upstream Java mappings; full option DSL/defaults; 9 retained algorithms; animation categories; ${assets} assets`);
