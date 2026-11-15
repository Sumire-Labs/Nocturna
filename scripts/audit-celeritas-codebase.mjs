// Inventory and candidate matching for a pinned codebase audit. This is not an ABI or behavior equivalence checker.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const localRoot = process.cwd();
const celeritasRoot = path.resolve(process.argv[2] ?? '.git/research/celeritas');
const output = path.resolve(process.argv[3] ?? '.git/research/celeritas-audit.json');
const git = (root, args, options = {}) => execFileSync('git', ['-C', root, ...args], options);
const commit = (root, ref) => git(root, ['rev-parse', '--verify', `${ref}^{commit}`], { encoding: 'utf8' }).trim();
const localRef = commit(localRoot, process.argv[4] ?? 'HEAD');
const celeritasRef = commit(celeritasRoot, process.argv[5] ?? 'HEAD');
const files = (root, ref) => git(root, ['ls-tree', '-r', '--name-only', '-z', ref], { encoding: 'utf8' })
    .split('\0').filter(Boolean);
const localFiles = files(localRoot, localRef);
const celeritasFiles = files(celeritasRoot, celeritasRef);

function blobs(root, ref, paths) {
    const data = git(root, ['cat-file', '--batch'], {
        input: paths.map(file => `${ref}:${file}\n`).join(''), maxBuffer: 128 * 1024 * 1024,
    });
    const result = new Map();
    let offset = 0;
    for (const file of paths) {
        const newline = data.indexOf(10, offset);
        const header = data.subarray(offset, newline).toString('utf8');
        const match = header.match(/^[0-9a-f]+ blob (\d+)$/);
        if (!match) throw new Error(`Cannot read ${ref}:${file}: ${header}`);
        const length = Number(match[1]);
        result.set(file, data.subarray(newline + 1, newline + 1 + length).toString('utf8'));
        offset = newline + length + 2;
    }
    return result;
}

// The checked-in Stonecutter active target is modern/1.20.1-forge. Other targets remain in the full inventory.
const targetPrefixes = [
    'common/src/main/java/', 'common/src/lwjglCommon/java/', 'common/src/lwjgl3/java/',
    'modern/src/main/java/', 'modern/src/main/modern_java/', 'modern/src/main/gui_java/',
    'modern/src/main/shaders_java/', 'modern/src/main/shaders_modern_java/',
    'modern/src/main/batching_java/', 'modern/src/main/flywheel_java/',
];
const localPaths = localFiles.filter(file => /^src\/(main|compat|legacy)\/java\/.+\.java$/.test(file));
const targetPaths = celeritasFiles.filter(file => file.endsWith('.java') && targetPrefixes.some(p => file.startsWith(p)));
const localBlobs = blobs(localRoot, localRef, localPaths);
const targetBlobs = blobs(celeritasRoot, celeritasRef, targetPaths);

const aliases = [
    ['me.jellysquid.mods.sodium.mixin.', 'org.embeddedt.embeddium.impl.mixin.'],
    ['me.jellysquid.mods.sodium.client.', 'org.embeddedt.embeddium.impl.'],
    ['net.caffeinemc.mods.sodium.api.', 'org.embeddedt.embeddium.api.'],
    ['org.embeddedt.embeddium.render.', 'org.embeddedt.embeddium.impl.render.'],
    ['org.embeddedt.embeddium.util.', 'org.embeddedt.embeddium.impl.util.'],
    ['SodiumClientMod', 'Celeritas'], ['SodiumWorldRenderer', 'CeleritasWorldRenderer'],
    ['net.minecraftforge.', 'net.neoforged.neoforge.'],
];
const canonical = source => aliases.reduce((text, [from, to]) => text.replaceAll(from, to), source);
const literalsAndComments = /"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/g;
const stripComments = source => source.replace(literalsAndComments,
    token => token.startsWith('//') || token.startsWith('/*') ? '' : token);
const lexical = source => source.match(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|[^\s]/g) ?? [];

function category(file) {
    if (/(?:shaders|batching|flywheel)_/.test(file) || /net\/(?:irisshaders|coderbot)\//.test(file)) return 'shaders-and-batching';
    if (/bitraster/.test(file)) return 'vendor-bitraster';
    if (/sorting|translucen/i.test(file)) return 'translucency';
    if (/occlusion|visibility|sectiongraph|frustum|entity\/cull/i.test(file)) return 'occlusion-and-culling';
    if (/\/frapi\/|fabric_renderer_indigo|fabric_rendering/i.test(file)) return 'frapi';
    if (/\/model\/light|\/world\/biome|\/chunk\/light|\/biome\//.test(file)) return 'lighting-and-biomes';
    if (/\/render\/chunk\//.test(file)) return 'terrain-and-meshing';
    if (/\/vertex\/|\/memory\/|\/render\/immediate|buffer_builder|immediate\/consumer/.test(file)) return 'vertex-and-immediate';
    if (/\/gl\/|\/lwjgl|LWJGL/.test(file)) return 'gl-and-lwjgl';
    if (/\/api\//.test(file)) return 'public-api';
    if (/\/gui\/|\/options\/|\/config\//.test(file)) return 'gui-and-options';
    if (/compat|taint|integrity/.test(file)) return 'compat-and-diagnostics';
    return 'platform-and-other';
}

function javaInfo(file, raw) {
    const source = stripComments(raw);
    const packageName = source.match(/\bpackage\s+([\w.]+)\s*;/)?.[1];
    const name = path.basename(file, '.java');
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const hasMainType = new RegExp(`\\b(?:class|interface|enum|record)\\s+${escaped}\\b`).test(source);
    const fqn = packageName ? `${packageName}.${name}` : null;
    const normalized = canonical(source);
    return {
        path: file, name, fqn, canonicalFqn: fqn && canonical(fqn), hasMainType, category: category(file),
        lines: raw.split('\n').length,
        hash: createHash('sha256').update(lexical(normalized).join('\u0000')).digest('hex'),
        identifiers: new Set(normalized.match(/[A-Za-z_$][\w$]*/g) ?? []),
    };
}
const localJava = localPaths.map(file => javaInfo(file, localBlobs.get(file)));
const targetJava = targetPaths.map(file => javaInfo(file, targetBlobs.get(file)));
const declared = targetJava.filter(file => file.fqn && file.hasMainType);
const inactive = targetJava.filter(file => !file.fqn || !file.hasMainType);
function similarity(a, b) {
    let intersection = 0;
    for (const token of a.identifiers) if (b.identifiers.has(token)) intersection++;
    return Math.round(1000 * intersection / (a.identifiers.size + b.identifiers.size - intersection)) / 10;
}
const matchedTargets = new Set();
const pairs = localJava.map(local => {
    let candidates = declared.filter(target => target.canonicalFqn === local.canonicalFqn);
    let status = 'canonical-fqn-candidate';
    if (!candidates.length) {
        candidates = declared.filter(target => canonical(target.name) === canonical(local.name));
        status = candidates.length ? 'class-name-candidate' : 'no-top-level-class-candidate';
    }
    return {
        local: local.path, fqn: local.fqn, category: local.category, status,
        candidates: candidates.map(target => {
            matchedTargets.add(target.path);
            return {
                celeritas: target.path, fqn: target.fqn, normalizedLexicalEquality: local.hash === target.hash,
                identifierJaccardPercent: similarity(local, target),
            };
        }).sort((a, b) => b.identifierJaccardPercent - a.identifierJaccardPercent),
    };
});
function counts(values, key) {
    const result = {};
    for (const value of values) {
        const name = key(value);
        result[name] = (result[name] ?? 0) + 1;
    }
    return result;
}
const groups = paths => counts(paths, file => file.match(/^(.+?\/src\/[^/]+\/[^/]+)\//)?.[1]
    ?? file.match(/^(src\/[^/]+\/[^/]+)\//)?.[1] ?? 'build-resources-other');
const summary = {
    localRef, celeritasRef,
    inventory: {
        localFiles: localFiles.length, localJava: localFiles.filter(f => f.endsWith('.java')).length,
        localProductionJava: localJava.length, localGroups: groups(localFiles),
        celeritasFiles: celeritasFiles.length, celeritasJava: celeritasFiles.filter(f => f.endsWith('.java')).length,
        celeritasGroups: groups(celeritasFiles), targetCandidateJava: targetJava.length,
        targetDeclaredTypeCandidates: declared.length, targetWithoutMainType: inactive.length,
    },
    statusCounts: counts(pairs, p => p.status),
    normalizedLexicalEquality: pairs.filter(p => p.candidates.some(c => c.normalizedLexicalEquality)).length,
    localCategories: counts(localJava, p => p.category), targetCategories: counts(declared, p => p.category),
    targetWithoutLocalClassCandidate: declared.filter(file => !matchedTargets.has(file.path)).length,
};
const report = {
    summary,
    methodology: 'Pinned git blobs; complete tracked-file inventory. Target roots selected from build conventions and the checked-in active 1.20.1-forge source. Java comment stripping, top-level-name detection, namespace mapping and identifier similarity are lexical candidate discovery, not semantic/ABI equivalence or a compiler source map. Multiple candidates retained; absence of a candidate does not mean functionality was removed. Generated sources, shaded dependencies and runtime verification are separate.',
    targetPrefixes, aliases, pairs,
    targetWithoutLocalClassCandidate: declared.filter(file => !matchedTargets.has(file.path))
        .map(({ path, fqn, category, lines }) => ({ path, fqn, category, lines })),
    targetWithoutMainType: inactive.map(file => file.path), localFiles, celeritasFiles,
};
mkdirSync(path.dirname(output), { recursive: true });
writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify(summary, null, 2));
