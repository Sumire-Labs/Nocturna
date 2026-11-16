# Iris / shader-pack 互換性調査

## 判定の意味

**Iris 本家仕様への 100% 準拠は確認できておらず、実際に既存の差分がある。**
「パックがロードできる」「対象シーンが描画できる」「本家と全条件で同じ結果になる」は別の判定である。
今回の検査は Forge 1.20.1、Java 17、AMD Radeon 8060S、OpenGL 4.6 の一環境で実行する。
Nocturna は Celeritas の Iris 派生実装を内蔵する。別の Iris / Oculus を追加した構成ではない。

## 比較した一次資料

- [Iris 1.20.1 branch](https://github.com/IrisShaders/Iris/tree/f8d7bea2ab82d21737e7aa8e4b2fb3a429078106)、
  固定 revision `f8d7bea2ab82d21737e7aa8e4b2fb3a429078106`。build の基底バージョンは 1.7.6。
  branch snapshot と配布バイナリの同一性まで確認したものではない。
- [Iris ShaderDoc](https://github.com/IrisShaders/ShaderDoc/blob/ddf473726710c6504422d192784c1652f8aece98/iris-features.md)。
- [現在の Iris Docs](https://github.com/IrisShaders/docs/tree/98f84cbe456e7e367afe788885869be46681be8c)、
  特に [feature flags](https://shaders.properties/current/reference/shadersproperties/flags/) と
  [weather directive](https://shaders.properties/current/reference/shadersproperties/features/#weather)。
- 取り込み元 Celeritas `26f000239eb8ddf7ee191f0ddf369147e3c92ef6`。

新しい Minecraft 向けの仕様追加は、1.20.1 当時の仕様への回帰と区別する。
新しい Sodium のソースは取り込んでいない。

## 確認できた仕様差

| 項目 | Iris 1.20.1 参照 / 現在の仕様 | Nocturna の状態 | 来歴 |
|---|---|---|---|
| feature flags | 1.20.1 参照に 10 機能＋UNKNOWN | 同じ列挙。ハードウェア条件あり | Celeritas と一致 |
| `IRIS_VERSION` | 1.20.1 参照が定義 | 未定義 | Celeritas からの既存差分 |
| `constantMood` | 1.20.1 参照が登録 | 登録なし | 同上 |
| `currentSelectedBlockId` / `currentSelectedBlockPos` | 1.20.1 参照が登録 | 登録なし | 同上 |
| `skipAllRendering` | 1.20.1 参照が property を解釈 | 対応する解釈なし | 同上 |
| `IRIS_HAS_CONNECTED_TEXTURES` | Fabric の Continuity 導入時に定義 | 同じ条件の定義なし | loader / mod 接続の差。常時定義してよいものではない |
| `CAN_DISABLE_WEATHER` / `weather` | 現在の仕様に存在 | 未対応 | 最新仕様との差。雨・雪移植前から未対応 |
| `FADE_VARIABLE` / `mc_chunkFade` | 現在の仕様に存在 | 未対応 | 同上 |
| `TEXTURE_FILTERING` | 現在の仕様に存在 | 未対応 | 同上 |

`IRIS_VERSION` に Nocturna 自身の `0.3.32` を入れることや、未実装 flag を有効と申告することは
互換性の修正にはならない。実装していない optional flag は無効のままにし、パックの fallback を使う。
required flag は未対応ならロードを拒否するのが契約である。

`node scripts/audit-iris-interface.mjs` は上記参照を固定し、literal な flag・macro・uniform 登録・
property handler を列挙する。結果は `docs/audits/iris-interface-inventory.json`。
uniform は内部用も含み、動的な名前や値・更新頻度の等価性を証明しない。件数を準拠率には換算しない。

## 今回変更した経路の確認

雨・雪の variant は、既存 weather program の shader source を既存の Celeritas transformer で変換した後に、
入力の Position / Color / UV / light を Angelica の instance 展開で生成する。
pack の fragment・geometry・tessellation・custom uniform・sampler・framebuffer・blend の構築経路を再利用する。
`rain.depth` の既存の depth-write 方針も保持する。variant 生成に失敗した場合は通常の weather program を使用する。

ただし、入力生成が正しくても shader 全体の本家同等性を自動的に証明したことにはならない。
元の cache / uniform / render-list の移植検証に加え、実パックで cold load、雨の新旧経路、雪、binary-cache reload、
source compile による reload を検査する。compile 数とスクリーンショットを別々に記録する。

## 実パック検証の再現

`modern/src/shaderProbe` は独立した検査 mod であり、製品 jar に含めない。
`scripts/shader-probe.init.gradle` を明示した場合だけ source set / run を作成する。
配布サイトの ZIP を取得し SHA-512 を検証してから、絶対パスで指定する。パックのソース・ZIP は再配布しない。

```powershell
./gradlew.bat --init-script scripts/shader-probe.init.gradle `
  -PshaderProbeSources=D:/path/to/Nocturna/modern/src/shaderProbe `
  -PshaderProbePack=D:/path/to/shader.zip `
  -PshaderProbeResult=D:/path/to/result.json `
  :modern:1.20.1-forge:runShaderProbeClient
```

flat world の固定カメラで stone / cutout / translucent / water / entity / block entity / 光源を描画し、
晴天、雨 instanced、雨 original、snowy plains の雪、warm reload、cache 無効 reload を撮影する。
雨・雪の降水量、biome、instance 数、draw 数、shader-stage compile 数、GL context / error を記録する。
各 stage は 100 client tick 待機する。Java / GPU / pack version を固定した smoke test であり、
別 GPU、全 profile、dimension、PBR resource pack、mod 組み合わせ、pack の全機能は網羅しない。

スクリーンショット同士には時間依存のノイズ・水面・雲・降水位置の差がある。
画像の目視確認を行い、全画素一致とは扱わない。元の幾何計算は別途 transform feedback で比較する。

## 実行結果

検証対象は `ecb65fbc`（雨・雪移植）。pack 検証用 checkout の Java / Gradle ソース 1,643 個が、
この commit と改行・行末空白を除いて一致することも確認した。
配布元・バージョン・SHA-512 は `docs/audits/shader-pack-versions.json`、
測定値と比較実行は `docs/audits/shader-pack-results.json` に保存している。

| パック | バージョン | 晴天・雨の新旧経路・雪 | warm stage compile 数 | 残る問題 |
|---|---|---|---:|---|
| Complementary Reimagined | r5.9.3 | 描画継続 | 0 | 新しい biome / End flash 関連の custom uniform 警告 |
| Complementary Unbound | r5.9.3 | 描画継続 | 0 | 同上 |
| BSL | 10.1.8 | 描画継続 | 0 | 今回のシーンでは新たな表示破綻を確認せず |
| Photon | 1.4 | 描画継続 | 0 | 空に黒い帯。正常とは判定しない |
| Solas | 3.9 | 描画継続 | 0 | 今回のシーンでは新たな表示破綻を確認せず |
| MakeUp Ultra Fast | 9.6 | 描画継続 | 0 | `fixedTaaCorrection` が解決できない。TAA の正しさは未確認 |
| Rethinking Voxels | r0.1-beta9 | 描画継続 | 0 | 新しい biome の custom uniform 警告 |
| Mellow | 3.5 | **ロード失敗** | — | `deferred1.vsh` の `vec3ViewPos` が未定義 |

成功した 7 構成はいずれも雨・雪 441 列 / 1 draw を記録した。
BSL / Photon / Solas / MakeUp / Rethinking Voxels は cache 無効での再コンパイル・描画も確認した。
Complementary の最初の 2 実行は cold source compile と warm binary reload を確認したが、
追加の cache 無効 reload stage は実行していない。
compile 数は title と world の pipeline を含み、unique program 数や性能指標ではない。

### 変更前との切り分け

- Photon: 雨・雪移植前の `5ca22ade` で cache を無効にしても黒帯を再現した。
  `ecb65fbc` で地面を Y=63、カメラを Y=71 に上げても残ったため、低高度だけの現象ではない。
  後続の Horizon cone 移植 `affc0572` でも同じ地表高度・cache 無効で再現した。
  Horizon / sky 経路の追加調査が必要。これを雨・雪の新しい描画経路による回帰とは判定しない。
- MakeUp: `5ca22ade`・cache 無効でも `fixedTaaCorrection` の同じ警告を再現した。
  `vec2(pixelSizeX, pixelSizeY) * 0.51` の式を解決できない。
  関係する `IrisFunctions` は取り込み元 Celeritas と同一であり、今回の変更で追加した式評価処理ではない。
- Mellow: cache 導入前の `c519e578`・cache 無効でも同じ compiler error を再現した。
  debug 出力の `001_deferred1.vsh` に `out vec3ViewPos;` が生成される。
  fragment の入力から不足する vertex 出力を補う変換経路が調査対象。
  Mellow のソースを書き換えて成功扱いにする処置は行っていない。

この比較は、雨・雪と program cache が上記症状を新たに発生させたという証拠がないことを示す。
Celeritas 移行前の Oculus を含む全構成との同等性までは検査していない。
したがって「私たちの変更すべてに回帰がない」「本家仕様と完全に同じ」とは結論しない。

### cache の性能について

この AMD 環境では binary 復元で長く待つ事例も観測した。
例えば Rethinking Voxels の pipeline は warm binary reload 約 70 秒、後続の source reload 約 5.4 秒だった。
制御した性能 benchmark ではなく、他処理やドライバー内 cache の状態も影響する。
**warm compile 0 件を高速化の保証として扱わない。** 保存設定の初期値は OFF のまま。

### 製品と既存互換性の検証

- commit 済みソースだけの checkout で `build` 成功。unit test 116 件、失敗・error・skip は 0。
- 配布 jar の 2,443 class を Java 17 で読める class version と確認。
- `runValidationClient -PcompatRuntime` 成功。CCL・Immersive Engineering・BrandonsCore を導入。
  GPU 天候展開 96 頂点、441 列 / 1 draw、provider / Forge hook / state、native 雪・乾燥 biome、
  IE wire、compact / async terrain、旧 callback を確認。
- 検査用 pack の cold compile は 115 stage、warm reload は terrain を含め 0 stage。

並行作業中の extra など、まだ commit されていない変更はこの判定に含めない。
Horizon 移植後の追加確認は Photon の晴天・雨の新旧経路・雪に限定し、最初の 8 構成の結果とは区別する。
