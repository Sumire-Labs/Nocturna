import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const root = process.argv[2] ?? '.git/research/angelica';
const revision = '996fb54cb05e256ea6039f4e2c8457a221a179b7';
const target = 'modern/src/main/shaders_java/net/irisshaders/iris/';
const normalized = text => text.replace(/^\/\/ Ported from Angelica[^\n]*\n/, '').replace(/\s+/g, '');
function upstream(path) {
    return execFileSync('git', ['-C', root, 'show', `${revision}:${path}`], {encoding:'utf8'})
        .replaceAll('com.gtnewhorizons.angelica.glsm.shader', 'net.irisshaders.iris.cache')
        .replaceAll('com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers', 'net.irisshaders.iris.cache.ShaderCacheWorkers')
        .replaceAll('AngelicaWorkers', 'ShaderCacheWorkers');
}
const dir = 'glsm/src/main/java/com/gtnewhorizons/angelica/glsm/';
for (const file of ['ShaderDiskCache.java','ShaderCacheIO.java','ShaderCacheWorkers.java','ProgramBinaryCache.java']) {
    let expected = upstream(dir + (file === 'ShaderCacheWorkers.java' ? 'threading/AngelicaWorkers.java' : 'shader/' + file));
    let actual = readFileSync(target + 'cache/' + file, 'utf8');
    if (file === 'ShaderCacheIO.java') expected = expected.replace('import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;', '').replace('@Lwjgl3Aware', '');
    if (file === 'ShaderCacheWorkers.java') expected = expected
        .replace('package com.gtnewhorizons.angelica.glsm.threading;', 'package net.irisshaders.iris.cache;')
        .replace('import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;', '')
        .replace('GLSMConfig.workerThreadCount', 'Integer.getInteger("embeddium.shaderCacheThreads", 0)')
        .replaceAll('Angelica-Worker', 'Embeddium-ShaderCache-Worker').replace('getLogger("Angelica")', 'getLogger("ShaderCache")');
    if (file === 'ProgramBinaryCache.java') {
        expected = expected
            .replace('import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;', 'import org.lwjgl.system.MemoryUtil;')
            .replace('import com.gtnewhorizons.angelica.glsm.GLSMPrograms;', 'import com.mojang.blaze3d.platform.GlStateManager;')
            .replace('import com.gtnewhorizons.angelica.glsm.RenderSystem;', '')
            .replace('import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;', '')
            .replace('import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;', '')
            .replaceAll('MemoryUtilities.', 'MemoryUtil.')
            .replaceAll('RenderSystem.supportsProgramBinary()', 'ProgramBinaryAccess.supported()')
            .replaceAll('ShaderManager.isEnabled()', 'false /* No legacy fixed-function emulation on Forge 1.20.1. */')
            .replaceAll('GLSMPrograms.glProgramParameteri', 'ProgramBinaryAccess.parameter')
            .replaceAll('GLSMPrograms.glProgramBinary', 'ProgramBinaryAccess.load')
            .replaceAll('GLSMPrograms.glGetProgramBinary', 'ProgramBinaryAccess.read')
            .replaceAll('GLSMPrograms.', 'GlStateManager.')
            .replaceAll('RENDER_BACKEND.getString', 'org.lwjgl.opengl.GL11.glGetString')
            .replaceAll('RenderSystem.isGLES()', 'false').replaceAll('RENDER_BACKEND.getMinGLSLVersion()', '150');
        actual = actual.replace(/public static boolean isSupported\(\)\s*\{\s*return ProgramBinaryAccess.supported\(\);\s*\}/, '');
    }
    assert.equal(normalized(actual), normalized(expected), `${file}: unreviewed behavior difference`);
    console.log(`Matched full Angelica cache implementation: ${file}`);
}
let expected = upstream('src/main/java/net/coderbot/iris/shaderpack/preprocessor/PreprocessedSourceCache.java')
    .replaceAll('net.coderbot.iris','net.irisshaders.iris').replace('net.irisshaders.iris.shaderpack.StringPair','net.irisshaders.iris.helpers.StringPair')
    .replaceAll('define.getKey()', 'define.key()').replaceAll('define.getValue()', 'define.value()');
assert.equal(normalized(readFileSync(target+'shaderpack/preprocessor/PreprocessedSourceCache.java','utf8')), normalized(expected));
expected = upstream('src/main/java/net/coderbot/iris/shaderpack/include/IncludeProcessor.java').replaceAll('net.coderbot.iris','net.irisshaders.iris');
assert.equal(normalized(readFileSync(target+'shaderpack/include/IncludeProcessor.java','utf8')), normalized(expected));
console.log(`Matched preprocessing keys and recursive include hashes at ${revision}`);
