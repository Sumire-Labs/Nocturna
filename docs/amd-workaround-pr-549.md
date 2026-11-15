# PR #549: driver 更新を基本対応とする判断

対象: Minecraft 1.20.1／Forge、`20.1/forge`。調査・実装日: 2026-11-16 (JST)。
検討対象: [Embeddium PR #549](https://github.com/FiniteReality/embeddium/pull/549)、
`a8be3ac45736d3a3c9ca36b4917fbbbe54a92b25`、作者 Carnestio (GitHub: Carnesito)。

## 最終判断: 不採用

AMD の公開 driver に修正が入っているため、renderer 側の回避は採用しない。
試験導入した `583d293a` の実装・専用テスト・テスト起動設定は revert した。
通常の共有 index mapping と staging buffer の capability／ユーザー設定による選択に戻している。
AMD 専用の回避フラグと JVM 引数 `embeddium.workaround.amdBufferMapping` も削除した。

driver 側の修正報告:

- RDNA 3／4: AMD 担当者が [2026-03-12 のコメント](https://github.com/GPUOpen-Drivers/AMD-Gfx-Drivers/issues/74#issuecomment-4043515330)
  で公開 driver は修正済みと説明。
- RDNA 2: [2026-11-04 のコメント](https://github.com/GPUOpen-Drivers/AMD-Gfx-Drivers/issues/74#issuecomment-6627668081)
  で Adrenalin 26.10.2 の修正を報告し、issue をクローズ。

利用者は該当 GPU 向けの修正済み driver へ更新することを基本対応とする。
修正済み driver でも問題が続く場合は、GPU・driver・launcher・再現条件を確認して別途調査する。
RDNA 1 や OEM driver など、報告に含まれない構成まで一律に解決済みとは断定しない。

## 原因と方針

[AMD の issue #74](https://github.com/GPUOpen-Drivers/AMD-Gfx-Drivers/issues/74) では、
Minecraft 向け application profile の有無で描画不具合が発生すること、RDNA 世代ごとに
driver 修正の公開時期が異なることが報告されている。
元の調査で指摘された故障点は、共有 index buffer を含む `glMapBufferRange` の異常動作。
PR 全体には、この回避とは別に全 GPU の描画・設定を変える変更も混在している。

Forge 1.20.1 は mod の Window mixin より先に早期ロード画面の GL context を作る場合がある。
そのため、Window constructor 周辺だけで command line を変更しても、既に driver profile が
初期化されているケースには間に合わない。試験実装では context 作成後に mapping 経路を避けた。

## 取り消した試験実装

`GL.createCapabilities` 後、既存の `LateDriverScanner` が取得する `GL_VENDOR` と OS から
`AMD_BUFFER_MAPPING` を選択する実装を試した。Windows 上のネイティブ AMD vendor
(`ATI Technologies Inc.`、`Advanced Micro Devices, Inc.`、`AMD`) のみを対象にしていた。

- 共有 quad index buffer: host memory に同じ index を生成し、`glBufferData` 経由で転送する。
  一時 native memory は転送が例外を投げた場合も解放する。
- staging buffer: `MappedStagingBuffer.isSupported` を false にし、既存の
  `FallbackStagingBuffer` に切り替える。保存された `useAdvancedStagingBuffers` の値は変更しない。
- 別 vendor の context を通知した場合は AMD フラグを解除し、NVIDIA／Wayland の既存フラグを維持する。

adapter の一覧ではなく使用中の context を判定するため、AMD 内蔵 GPU と NVIDIA 外部 GPU の
混在環境でも、NVIDIA context の buffer 経路は変更しない。Linux、Mesa／ANGLE、Intel、
vendor 不明の context も対象外。

この方式では修正済み Windows AMD driver にも回避が有効になり、persistent mapping による転送最適化を
無効にしてしまう。旧 driver のための保守・判定・追加経路を持つ代わりに、driver 更新を基本対応とする。
FPS／frame time の比較は未実施。

## 採用していない変更

| PR の変更 | 今回の扱い |
| --- | --- |
| 全 GPU の multi-draw を個別 draw にする | 採用しない。既存の multi-draw を維持。mapping の故障とは別の変更で、必要性を確認できていない。 |
| block face culling／compact vertices／no-error context の既定値を false にする | 採用しない。各設定の既定値・ユーザー設定を維持。 |
| 全 GPU で staging buffer を強制 fallback にする | 採用しない。Windows／AMD 限定の試験実装も取り消し、従来の capability／設定判定を使う。 |
| 全 GPU の共有 index buffer を host upload にする | 採用しない。Windows／AMD 限定の試験実装も取り消し、従来の mapping 経路を使う。 |
| command-line の ANSI／Unicode 書き換え、Kernel32 拡張 | 採用しない。既存の NVIDIA hook を変更せず、新しい AMD hook も追加しない。native hook の共有・復元や早期 driver 初期化への対応は今回不要になる。 |

mod ID、network 登録、配布先との互換性宣言も変更していない。外部の AMD fix mod は導入していない。

## Sodium・Angelica・Celeritas との比較

比較は下記の snapshot が対象。他ブランチ・将来のリリースでの動作までは含まない。

### Sodium

確認した `dev`: `1d8b33dc01bfa365b0574eae082f15ac5b9be5b4`。
[AMD 処理](https://github.com/CaffeineMC/sodium/blob/1d8b33dc01bfa365b0574eae082f15ac5b9be5b4/common/src/boot/java/net/caffeinemc/mods/sodium/client/compatibility/workarounds/amd/AmdWorkarounds.java)は、
AMD adapter の存在を調べ、Windows command line を一時的に変更して driver の profile 判定を回避する。
NVIDIA adapter もある場合は AMD 側の二重書き換えを避ける。
[WindowsCommandLine](https://github.com/CaffeineMC/sodium/blob/1d8b33dc01bfa365b0574eae082f15ac5b9be5b4/common/src/boot/java/net/caffeinemc/mods/sodium/client/platform/windows/WindowsCommandLine.java)は
Unicode と ANSI の両方を変更・復元する。
[NeoForge の bootstrap](https://github.com/CaffeineMC/sodium/blob/1d8b33dc01bfa365b0574eae082f15ac5b9be5b4/neoforge/src/main/java/net/caffeinemc/mods/sodium/service/SodiumWorkarounds.java)では
早期ウィンドウ初期化を待って復元する処理もある。PR #549 の環境変更部分はこの方式と同形だが、
PR 全体に含まれる buffer／draw／設定変更とは別の手段。

現行 [LICENSE.md](https://github.com/CaffeineMC/sodium/blob/1d8b33dc01bfa365b0574eae082f15ac5b9be5b4/LICENSE.md) は
PolyForm Shield 1.0.0。今回は比較のために実装を閲覧したが、新しい Sodium のコードは本リポジトリに移植していない。

### Celeritas

本家 `stonecutter`: `26f000239eb8ddf7ee191f0ddf369147e3c92ef6`。
確認した[共有 index buffer](https://git.taumc.org/embeddedt/celeritas/src/commit/26f000239eb8ddf7ee191f0ddf369147e3c92ef6/common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/SharedQuadIndexBuffer.java)は
allocate → map → index 生成 → unmap の経路を使う。この箇所には Windows AMD 専用の host upload 分岐はない。
[MappedStagingBuffer](https://git.taumc.org/embeddedt/celeritas/src/commit/26f000239eb8ddf7ee191f0ddf369147e3c92ef6/common/src/main/java/org/embeddedt/embeddium/impl/gl/arena/staging/MappedStagingBuffer.java)は
OpenGL 4.4／ARB_buffer_storage と map／copy 機能に基づいて対応を判定する。
確認した modern の Workarounds には NVIDIA と Wayland のフラグがあり、今回と同じ AMD mapping フラグはない。

### Angelica

`master`: `996fb54cb05e256ea6039f4e2c8457a221a179b7`。
[依存定義](https://github.com/GTNewHorizons/Angelica/blob/996fb54cb05e256ea6039f4e2c8457a221a179b7/gradle/libs.versions.toml)は
Celeritas common `2.5.14-GTNH` を指定し、[build 定義](https://github.com/GTNewHorizons/Angelica/blob/996fb54cb05e256ea6039f4e2c8457a221a179b7/dependencies.gradle.kts)で組み込む。
対応する GTNH Celeritas mirror の `nh-main` は `b2a70f7f28638eb3124a337584567fa72e00452a`。
その共有 index buffer も mapping 方式。
[LWJGL3 backend](https://github.com/GTNewHorizons/Angelica/blob/996fb54cb05e256ea6039f4e2c8457a221a179b7/lwjgl3-backend/src/main/java/com/gtnewhorizons/angelica/lwjgl3/Lwjgl3GLRenderBackend.java)の
map 呼び出しも OpenGL に委譲する。AMD 向け shader-source 回避はあるが、今回の mapping 問題とは別。
確認した地形／OpenGL 経路では #549 相当の profile 回避や AMD mapping の強制無効化は見つからなかった。

試験実装は Embeddium PR #549 の buffer 側の回避を Windows AMD context に限定したもので、
Sodium の環境変更コードや Celeritas／Angelica の実装を移植したものではない。
これらのコード比較だけでは Angelica／Celeritas における不具合の解消状況は判断できない。

## 試験導入時の検証記録

通常の JUnit: 16 件成功。vendor／OS、混在 GPU を想定した context 変更、他の回避フラグの維持、
明示 opt-out、SHORT／INTEGER index の全内容、未変更の mapped 経路、再試行・再拡張を検査する。

実機 OpenGL テストを加えた build: 17 件成功。失敗・スキップ 0 件。
これは取り消し前の結果であり、現在の build にこれらの専用テストは含まれない。

実機の識別文字列:

```text
GL_VENDOR: ATI Technologies Inc.
GL_RENDERER: AMD Radeon(TM) 8060S Graphics
GL_VERSION: 4.6.0 Compatibility Profile Context 26.10.1.261008
```

テストは非表示の GLFW window で context を作り、元の mapping 経路と新しい upload 経路の
SHORT／INTEGER index を GPU から読み戻して比較した。RenderRegionManager が選ぶ mapped／fallback
両 staging の転送結果も読み戻して照合した。OpenGL error は発生しなかった。

これはデータ転送の確認であり、故障 driver での不可視地形の再現・改善確認ではない。
Minecraft の実ワールド、Oculus／shader pack、問題を起こす旧 driver、実 NVIDIA／Intel GPU、
実 Linux 環境での描画検証は未実施。

## 取り消し後の検証

`src` と `build.gradle.kts` は AMD 回避を導入する前の `0616784a` と一致する。
初回に採用した性能改善・翻訳・バイオーム色修正は維持している。
通常の `build` は成功し、既存の JUnit 9 件はすべて成功、失敗・スキップ 0 件。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat --gradle-user-home D:/DevCache/Gradle/embeddium-legacy build --no-daemon
```
