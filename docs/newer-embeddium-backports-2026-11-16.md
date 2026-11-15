# 新しい Minecraft 向け Embeddium からの移植: 第1バッチ

対象: `20.1/forge`、Minecraft 1.20.1／Forge。調査日: 2026-11-16 (JST)。
開始点: `a998f298`。既存の性能改善・翻訳・バイオーム色修正は維持する。

## 移植元と方針

本家 `FiniteReality/embeddium` の最新 refs を確認した。

| ブランチ | 確認した先頭コミット |
| --- | --- |
| 21.1/neoforge | `fc5c69b0b9e39f544bf57792bec3037ae2384b85` |
| 21.4/neoforge | `aa6657df4eaea8bdfa6243233c893207f5b7f8b4` |

今回のコードは LGPL-3.0 の Embeddium のコミットから移植する。
新しい Sodium の実装や Celeritas／Angelica のコードは使用しない。
動作の核を別の簡易アルゴリズムに置き換えず、移植元の関連変更をまとめて適用する。
異なる package／既存 API 名は 1.20.1 側に対応させる。

## 移植した変更

### チャンク用作業バッファの確保と再利用

元コミット: [ccc1876c44610a5b810bf75fe4ea998bba553122](https://github.com/FiniteReality/embeddium/commit/ccc1876c44610a5b810bf75fe4ea998bba553122)
(`Don't grow the capacity of chunk mesh buffers more than is necessary`、FiniteReality)。

- capacity を byte 単位で管理し、頂点数と stride を重複して掛けない。
- 初回の push まで native memory を確保しない。初期確保量は 64 KiB。
- grow の倍増・必要量・最小初期量の計算と `>=` 境界判定を元実装のまま維持する。
- `start` ではデータ数・section・sort analyzer を初期化し、確保済み容量は保持する。
- `destroy` で native memory と capacity をリセットする。

`ChunkMeshBufferBuilder` 全体は、package／import の対応後に元コミットと一致する。
`ChunkBuildBuffers` の初期容量と sort analyzer を選ぶ条件も一致する。
encoder、material、section index、頂点順序、translucency analyzer の呼び出しは省略しない。

### 多層モデルの render-pass 最適化

元コミット: [fcd5137288f869c22ffad23c499c6a490e0f2e3a](https://github.com/FiniteReality/embeddium/commit/fcd5137288f869c22ffad23c499c6a490e0f2e3a)
(`Fix quad render pass optimization bug with certain multi-layer models`、FiniteReality)。
本家では XyCraft Override Observer などの層の上下関係が崩れる問題を扱っている。

元コミットの5ファイル分を移植する。renderer の条件だけを追加する構成にはしない。

- `IS_PASS_OPTIMIZABLE` は全頂点が不透明で、sprite を信頼できる場合に設定する。
- 既存の shading／独自フラグを保ち、geometry フラグの再計算方法も元実装に合わせる。
- sprite の透明度の並びが逆転する多層リストでは pass 移動を無効にする。
- AO／shade が異なる層では reorientation を無効にする。pass の可否とは独立して扱う。
- 各 cull face で得た制約を、cull face 未指定の面にも引き継ぐ。
- sprite 不明、信頼できない sprite、頂点 alpha のある quad は保守的に判定する。
- 設定や shader 使用による最適化無効化、builder 選択、geometry 出力の処理を維持する。
- `BakedQuadView.getTransparencyLevel` と sprite holder の overload も移植する。

1.20.1 向け互換性として、既存の `ModelQuadFlags.getQuadFlags(quad, face)` は残し、
元実装の3引数版へ `existingFlags = 0` で委譲する。元の呼び出し側に新しいフラグを推測して付けない。
`ColorABGR` はこのブランチに既存の `net.caffeinemc.mods.sodium.api.util` を使う。
alpha 抽出の中身は移植元の再配置された API と一致する。

## 同一性と回帰検証

`scripts/verify-embeddium-backports.mjs` は固定した元コミットを `git show` で読み、
package／API 名の対応と空白を正規化して以下を比較する。元コミットがローカルに必要。
不足する場合は、本家の `21.1/neoforge` を fetch してから実行する。

```powershell
node scripts/verify-embeddium-backports.mjs
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat --gradle-user-home D:/DevCache/Gradle/embeddium-legacy build --no-daemon
```

- `ChunkMeshBufferBuilder` 全体、初期容量・sort analyzer の選択。
- quad のフラグ計算・cache・透明度取得と alpha 抽出。
- renderer constructor、透明度判定、照明・層順序判定、builder 選択、quad list、geometry 出力。
- `renderModel` の側面から null cull face への制約伝播。

JUnit は Java 17 で実行し、既存9件と新規13件の計22件が成功した。
新規テストでは以下を確認する。

- Compact／Vanilla-like 両形式で、再確保・section 再利用後の全頂点 byte を直接 encoder 出力と比較。
- 遅延確保、境界サイズ、保持した native buffer、destroy 後の再利用、sort analyzer のリセット。
- 1〜4枚の quad に対する透明度の全120系列、全16通りの2層照明条件。
- 全頂点の alpha、信頼できない／欠落した sprite、フラグ保持と遅延 cache。
- 最適化無効時・継承制約下での判定と、実 builder への pass 振り分け。

renderer のテストは実クラスの policy メソッドを使い、Minecraft の world／lighting constructor は
呼び出さない。Mixin の getter／フラグ処理は test adapter から実装を直接呼ぶ。
したがって、これは通常の Minecraft launcher での Mixin 適用や画面上の見た目の検証ではない。
XyCraft 実機、Oculus／shader pack、mod 独自 renderer を含む視覚的な一致、FPS／frame time は未検証。
コードの一致と対象ロジックの回帰検証を、全 mod 環境での動作保証とは扱わない。

## 今回まとめて取り込まなかった候補

| コミット | 判断 |
| --- | --- |
| `55c9b7fa` / `619ac983` vertex writer の位置修正 | 1.21 の BufferBuilder は `vertexPointer`／`elementsToFill` を持つ。1.20.1 の ByteBuffer／`nextElementByte` 実装に同じ差分だけを適用できない。 |
| `d1bb5162` / `d0d3adea` material 変更前の consumer flush | 前提となる ChunkModelVertexConsumer／`asVertexConsumer` がこのブランチにない。consumer 全体を検討する別バッチとする。 |
| `aa6657df` leashed entity の renderer 修正 | entity culling の呼び出し構造・renderer 引数がこのブランチと異なる。該当行だけの移植は行わない。 |
| `5263007e` adaptive CPU render-ahead fencing | 待機処理と Intel 起動チェックの撤去を一緒に扱う変更。今回は対象外とし、待機・失敗経路を元実装どおり検証できる別バッチに分ける。 |
| `4581023d` constructor 間の metadata 重複計算回避 | 1.20.1 側は既に getter 内で遅延計算する。constructor への guard だけを追加する対象ではない。 |
| Minecraft／NeoForge 更新、Gamma／Phi、package 再配置 | 今回の2件には不要。1.20.1 Forge の基盤を置き換える変更は行わない。 |

これは新版から移植可能な全要素の完了報告ではなく、元の挙動を維持して取り込む第1バッチ。
push・リリース公開は行わない。
