import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const [verificationPath = '.git/gameplay-review-current-head.log',
    lifecyclePath = '.git/gameplay-review-current-head.log', mode] = process.argv.slice(2);
const verification = fs.readFileSync(verificationPath, 'utf8');
const lifecycle = fs.readFileSync(lifecyclePath, 'utf8');
const cases = JSON.parse(fs.readFileSync('modern/versions/1.20.1-forge/run/gameplay-review.json', 'utf8'));
assert.deepEqual(cases.failed, []);
assert.equal(new Set(cases.passed).size, 13);
assert.match(lifecycle, /PASS gameplay workflow: 13 cases; Java 17/);
for (const [name, log] of [['verification', verification], ['lifecycle', lifecycle]]) {
    assert.match(log, /BUILD SUCCESSFUL/, `${name} did not finish`);
    assert.doesNotMatch(log, /REPRO gameplay|FAIL gameplay|java\.lang\.AssertionError|Error rendering overlay|Encountered an unexpected exception|A fatal error|> Task [^\r\n]* FAILED/);
}
for (const marker of [
    'PASS matrix uniform reuse:', 'PASS shader cache: cold/hit pixel identity',
    'PASS Iris horizon: 384 exact color/depth comparisons', 'PASS FISM: 192 banner/shield cases',
    'PASS weather GPU expansion:', 'PASS IE copper wire geometry: 256 vertices, compact=false',
    'PASS IE copper wire geometry: 256 vertices, compact=true',
    'PASS full shader pack warm reload: zero stage compiles',
    'PASS RSO parity:', 'PASS SDL-to-Minecraft/Forge dispatch:',
    'PASS ordinary Minecraft addon controls:', 'PASS unavailable native input:',
    'PASS Java17 Forge: RSO registry',
    'PASS resource JSON passes/uniforms, replacement vertex, SamplerInfo',
    'PASS independent fog channels; in-place/scissor color; Forge hit callbacks; HDR formats',
    'PASS debug visualizations and actual async visibility traversal',
    'mixins.codechickenlib', 'mixins.brandonscore', 'immersiveengineering.refmap',
    ':runAuditClient',
]) assert.ok(verification.includes(marker), `Missing runtime evidence: ${marker}`);
const pixels = [...lifecycle.matchAll(/PASS protected weather fog GPU readback: (\d+) fragments, no unfogged fragments/g)].map(m => Number(m[1]));
assert.ok(pixels.length >= 2 && pixels.every(n => n > 20), 'Weather pixels were not checked in both worlds');

let tests = 0;
for (const dir of ['common/build/test-results/test', 'modern/versions/1.20.1-forge/build/test-results/test']) {
    for (const file of fs.readdirSync(dir).filter(f => /^TEST-.*\.xml$/.test(f))) {
        const header = fs.readFileSync(`${dir}/${file}`, 'utf8').match(/<testsuite\s[^>]+>/)[0];
        const value = key => Number(header.match(new RegExp(`${key}="(\\d+)"`))?.[1] ?? 0);
        assert.equal(value('failures') + value('errors') + value('skipped'), 0, file);
        tests += value('tests');
    }
}
assert.ok(tests >= 177, 'Updated unit tests did not run');
for (const name of ['me.flashyreese.mods.greenlight.feature.ConnectionEpochTest',
    'org.embeddedt.embeddium.impl.extra.compat.config.structure.CloudRangeOptionTest'])
    assert.ok(fs.existsSync(`modern/versions/1.20.1-forge/build/test-results/test/TEST-${name}.xml`));
const java17Classes = Number([...lifecycle.matchAll(/Verified (\d+) product classes for Java 17/g)].at(-1)?.[1]);
assert.ok(java17Classes >= 4500);

if (mode === '--record') {
    const before = ['.git/gameplay-review-repro.log', '.git/gameplay-review-third-pass.log']
        .flatMap(path => [...fs.readFileSync(path, 'utf8').matchAll(/^REPRO gameplay: (.+)$/gm)].map(m => m[1].trim()));
    const logs = [...new Set([verificationPath, lifecyclePath])].map(path => ({ path,
        sha256: crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex') }));
    fs.writeFileSync('docs/audits/gameplay-review-2026-11-17.json', JSON.stringify({
        recordedAt: new Date().toISOString(), reviewBase: 'd68c0d27',
        checkedHead: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
        branch: '20.1/forge', java: 'Temurin 17.0.20.1+1', forge: '47.3.0',
        platform: 'Windows 11 / AMD Radeon 8060S / OpenGL 4.6',
        addonFiles: { codechickenlib: '5753868', immersiveEngineering: '4782978', brandonsCore: '5422013' },
        tests, failures: 0, errors: 0, skipped: 0, java17Classes,
        workflow: cases, weatherCoveredFragments: pixels,
        baselineFailures: [...new Set(before)],
        regressionScopes: ['native/compact/async terrain and old APIs', 'shader cache cold/hit/rejection/compute/warm pack',
            'horizon and FISM GPU oracles', 'rain/snow and provider draws', 'RSO input and frontend transitions',
            'Extra config/resources/Panini/Fabulous', 'world lifecycle and HUD state', 'all loaded mixin targets'],
        logs, limits: ['Physical Linux/macOS execution is not covered.',
            'Addon coverage includes CCL callbacks, IE wire geometry and loaded BrandonsCore targets, not every addon item.',
            'The workflows do not establish correctness for every possible mod pack or scene.'],
    }, null, 2) + '\n');
}
console.log(`PASS ${tests} unit tests, 13 gameplay cases, ${java17Classes} Java 17 classes and all recorded renderer/UI checks`);
