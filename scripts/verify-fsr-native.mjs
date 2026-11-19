import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = path.join(root, 'native/fsr/source-manifest.json');
const names = ['native/fsr/nocturna_fsr.cpp', 'native/fsr/CMakeLists.txt',
  'native/fsr/vendor/api/include/ffx_api.h', 'native/fsr/vendor/api/include/ffx_api_types.h',
  'native/fsr/vendor/api/include/dx12/ffx_api_dx12.h', 'native/fsr/vendor/upscalers/include/ffx_upscale.h',
  'modern/src/main/shaders_resources/native/windows-x64/nocturna_fsr.dll'];
const hashes = Object.fromEntries(names.map(name => {
  const bytes=fs.readFileSync(path.join(root,name));
  const content=name.endsWith('.dll')?bytes:bytes.toString('utf8').replaceAll('\r\n','\n');
  return [name,crypto.createHash('sha256').update(content).digest('hex')];
}));
if (process.argv.includes('--write')) {
  fs.writeFileSync(manifestPath, JSON.stringify({sdkRevision:'7539ae8b6568ad29ed78a753404351daf3b5cca7',
    bridgeLicense:'LGPL-3.0-or-later',sdkHeadersLicense:'MIT',runtimeBundled:false,textLineEndings:'LF',sha256:hashes}, null, 2)+'\n');
} else {
  const manifest = JSON.parse(fs.readFileSync(manifestPath,'utf8'));
  for (const name of names) if (manifest.sha256[name] !== hashes[name]) throw new Error(`FSR bridge source/binary changed: ${name}; rebuild and record manifest`);
}
console.log(`Verified ${names.length} FSR bridge source/binary hashes; external AMD runtime is not bundled`);
