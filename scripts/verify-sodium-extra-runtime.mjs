import fs from 'node:fs';
import assert from 'node:assert/strict';

// Source mappings are not behavior tests. Require the actual Java 17 client run
// to reach every regression category, and reject errors swallowed by Forge GUI.
const log = fs.readFileSync(process.argv[2] ?? '.git/extra-parity-client-final.log', 'utf8');
const checks = [
    'PASS Panini GPU color/depth/mask oracle at strengths 0/25/100',
    'PASS terrain shader combines independent environment/render channels with the pinned linear fade',
    'PASS vanilla shader fog import preserves independent spherical/cylindrical channels',
    'PASS atlas-only animation clock freeze and independent water/lava controls',
    'PASS typed RSO Extra controls, default reset, pending dependencies, group labels and screen construction',
    'PASS complete 26.3 catalog (46 native + 4 Extra), profiles and custom transitions',
    'PASS debug screen search, profile buttons, custom selection and permission tooltips',
    'PASS independent fog channels; in-place/scissor color; Forge hit callbacks; HDR formats',
    'PASS native particles, biome tints, light-update warning, instant sneak and queued-toast filtering',
    'PASS all text providers against live client/server state',
    'PASS debug visualizations and actual async visibility traversal',
    'PASS resource JSON passes/uniforms, replacement vertex, SamplerInfo and whitespace-independent hand depth',
    'PASS Java17;',
    'BUILD SUCCESSFUL',
];
for (const check of checks) assert.ok(log.includes(check), `Client did not complete: ${check}`);
assert.doesNotMatch(log, /\bFAIL\b|\/ERROR\]|Exception in thread|A fatal error has been detected/);
console.log(`PASS ${checks.length - 1} client regression checkpoints; no swallowed runtime errors`);
