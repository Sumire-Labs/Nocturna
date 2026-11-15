# Embeddium 1.20.1 Forge: upstream review

調査日: 2026-11-16 (JST)。対象ブランチ: `20.1/forge`。
開始点: `dee0ebde6aea8b158613cd3b8ea5cdf0c1f6a9bf`。

## 調査範囲

[本家](https://github.com/FiniteReality/embeddium) の open PR 16 件と open issue
148 件を取得した。issue はタイトルによる一次選別を行い、下記の候補は本文と関連コードまで確認した。
すべての issue を再現・解決したという意味ではない。
本家の `20.1/forge` は開始点と同じコミットだった。

今回の取り込み元は Embeddium の PR と既存コードのみ。新しい Sodium のソースは取得・移植していない。
`COPYING`、`COPYING.LESSER` と既存のライセンス表記は維持する。
移植コミットには元の作者と upstream の URL／コミットを記録する。

## 採用した PR

| PR | 内容 | レビュー・適用時の調整 |
| --- | --- | --- |
| [#575](https://github.com/FiniteReality/embeddium/pull/575) | チャンクアップロード準備で stream と空リストの割り当てを削減 | 分類・転送の順序、null index の扱いを確認。外部 mod のバイナリ互換性のため、従来の `GlBufferArena.upload(CommandList, Stream)` も残した。 |
| [#576](https://github.com/FiniteReality/embeddium/pull/576) | 複製セクションキャッシュの期限切れ走査を短縮 | `acquire` が最終使用順への移動と同じフレーム時刻の設定を行うことを確認。期限切れエントリが先頭に集まる既存の不変条件を利用する。5 秒／512 件の制限は維持。 |
| [#578](https://github.com/FiniteReality/embeddium/pull/578) | 頂点 serializer のキャッシュヒットで read lock を省略 | 公開後に変更しない snapshot を volatile で公開する。生成時の write lock は維持。初期登録、追加後の可視性、並行アクセスで同じ serializer が返ることをテスト。 |
| [#580](https://github.com/FiniteReality/embeddium/pull/580) | 64 要素以上の半透明ソートに安定 radix sort を使用 | 同順位、符号付きゼロ、無限大、NaN fallback、scratch の保持上限、4 スレッドを検証。既存ソートと独立した安定ソートの両方と比較した。コードスタイルを調整。 |
| [#345](https://github.com/FiniteReality/embeddium/pull/345) | 韓国語を追加 | sodium 側に重複していた embeddium のキーを除去。未翻訳部分は英語 fallback を使う。 |
| [#487](https://github.com/FiniteReality/embeddium/pull/487)、[#488](https://github.com/FiniteReality/embeddium/pull/488) | スペイン語の設定画面を補完 | 既存訳を維持して追加。JSON、キー、書式引数を検証。 |
| [#567](https://github.com/FiniteReality/embeddium/pull/567) | ロシア語を追加 | このブランチの英語キーと対応することを検証。 |

性能向上率や FPS 向上は測定していない。PR 作者のマイクロベンチマーク値を、このフォークの測定結果として扱わない。

## 今回は取り込まない PR

| PR | 判断 |
| --- | --- |
| [#549](https://github.com/FiniteReality/embeddium/pull/549) | 説明にある AMD の環境変更に加え、全 GPU の multi-draw を単発 draw に変更し、高度な staging buffer を常時無効化し、複数の既定値も変更する。AMD 限定の修正としてそのまま適用できない。NVIDIA workaround と command-line hook の所有権・復元も含め、対象ハードウェアでの切り分けとコード来歴の確認が必要。 |
| [#577](https://github.com/FiniteReality/embeddium/pull/577) | weighted model の重みを constructor で固定する案。保持するリストの不変性と、モデルを書き換える mod との契約を別途確認してから扱う。 |
| [#579](https://github.com/FiniteReality/embeddium/pull/579) | sprite の frame duration キャッシュ。Ticker の constructor／tick 注入を変更するため、補間・resource reload・アニメーション拡張 mod を含むクライアント検証を別バッチで行う。 |
| [#581](https://github.com/FiniteReality/embeddium/pull/581) | region ごとの draw cache と draw merge。native pointer の公開 API、全 mutation 経路の invalidation、追加 heap 使用量、Oculus shadow pass を含む影響が大きい。実クライアントでの描画同一性とメモリ・frame time の測定後に判断する。 |
| [#566](https://github.com/FiniteReality/embeddium/pull/566) | 1.21.4 向けのロシア語。1.20.1 用の #567 を採用する。 |
| [#555](https://github.com/FiniteReality/embeddium/pull/555) | 1.21.1 向けのポルトガル語。namespace 構成が異なるため、1.20.1 向けに分割して訳文を確認する作業を保留。 |
| [#502](https://github.com/FiniteReality/embeddium/pull/502) | 1.21.4 向け簡体字更新。1.20.1 に残る compact vertex／translucent sorting の既存訳をそのまま削除しないよう、別途適合が必要。 |
| [#329](https://github.com/FiniteReality/embeddium/pull/329) | 1.21 向け繁体字。既存キーとの照合、namespace の分割、説明にある外部翻訳元のライセンス・帰属確認を保留。 |

## issue から実装した修正

[#516](https://github.com/FiniteReality/embeddium/issues/516) と
[#517](https://github.com/FiniteReality/embeddium/issues/517) は、Polytone などで変更したバイオームの色が
反映されないという報告。

Forge 47.3.0 の `coremods/field_to_method.js` は、Biome の既存の `specialEffects` 読み出しを
`getModifiedSpecialEffects` にリダイレクトする。しかし、後から適用される Embeddium の overwrite は
再び直接フィールドを読むため、この仕組みを迂回する。
草の色・modifier と葉の色の取得を `getSpecialEffects()` 経由に変更した。
getter の返す情報を交換した後も新しい override を使うことと、grass modifier を適用することをテストした。

これは草・葉の special effects の読み出しに対する修正。Polytone と resource pack を使った
実クライアントでの検証は未実施で、両 issue 全体の解決、sky／fog や climate の変更への対応までは主張しない。

## 継続調査する issue

| issue | 今回確認したこと／次に必要なこと |
| --- | --- |
| [#537](https://github.com/FiniteReality/embeddium/issues/537)、[#543](https://github.com/FiniteReality/embeddium/issues/543)、[#550](https://github.com/FiniteReality/embeddium/issues/550)、[#561](https://github.com/FiniteReality/embeddium/issues/561)、[#563](https://github.com/FiniteReality/embeddium/issues/563) | AMD の不可視地形報告。#549 を一括適用せず、driver 版・機種・各変更単体で再現を調べる。 |
| [#558](https://github.com/FiniteReality/embeddium/issues/558) | 外部 AMD fix 追加時の multiplayer 接続問題という報告。今回その外部 mod は導入していない。 |
| [#535](https://github.com/FiniteReality/embeddium/issues/535) | `setSectionDirtyWithNeighbors` は既に overwrite され、セクションの無効化と rebuild に到達する。呼び出し時の座標・スレッド・更新データの最小再現例が必要。 |
| [#539](https://github.com/FiniteReality/embeddium/issues/539) | RenderType universe は指数的に増え、現在の mask にも上限がある。上限値だけの変更では対応できず、追加 RenderType を作る mod を使った別設計・検証が必要。 |
| [#510](https://github.com/FiniteReality/embeddium/issues/510) | 提示ログは `Duplicate mods found`。Forge 47.4.x 全体の不具合と断定できる証拠はない。重複 mod の特定が先。 |
| [#574](https://github.com/FiniteReality/embeddium/issues/574) | 設定画面を開くと落ちる報告。外部 crash log は取得できず、原因を特定できていない。 |
| [#531](https://github.com/FiniteReality/embeddium/issues/531)、[#427](https://github.com/FiniteReality/embeddium/issues/427)、[#415](https://github.com/FiniteReality/embeddium/issues/415)、[#540](https://github.com/FiniteReality/embeddium/issues/540)、[#505](https://github.com/FiniteReality/embeddium/issues/505) | 本文の互換性報告を確認。該当 mod を含む再現環境と crash log の詳細解析が必要。原因未特定の回避コードは追加しない。 |
| [#449](https://github.com/FiniteReality/embeddium/issues/449)、[#208](https://github.com/FiniteReality/embeddium/issues/208) | atlas／compact vertex の精度が関わる可能性のある白い点の報告。resource pack と GPU を揃えて調べる必要がある。 |
| [#582](https://github.com/FiniteReality/embeddium/issues/582)、[#455](https://github.com/FiniteReality/embeddium/issues/455)、[#452](https://github.com/FiniteReality/embeddium/issues/452) | 報告対象は 1.21.1。1.20.1 での再現を確認してから扱う。 |

## 検証

JDK 17、Minecraft 1.20.1、Forge 47.3.0、Gradle wrapper 8.10 を使用。
PC 共通の Gradle init script が Gradle 8.10 にない `daemonLogs` 設定を参照したため、
ユーザー共通設定は変更せず、専用の Gradle user home を指定した。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat --gradle-user-home D:/DevCache/Gradle/embeddium-legacy build --no-daemon
```

`build` は再難読化した Forge jar と sources jar を生成し、JUnit の回帰テストを実行する。
結果: `BUILD SUCCESSFUL`、JUnit 9 件成功、失敗・スキップ 0 件。
テスト依存は配布 jar に含めない。
Forge の開発用 jar には remap 前の署名が残るため、JUnit 専用に署名を除いたコピーを
`build/test-runtime` に生成する。配布用 jar、ダウンロードした依存、通常のゲーム起動設定は変更しない。
バイオームのテストでは Minecraft のレジストリを初期化するが、Mixin／Forge のゲームランチャーは起動しない。
翻訳は JSON の重複キー、英語に存在しないキー、`%s`／`{2}` などの引数を検査する。
不足分は Minecraft の通常の英語 fallback に任せる。

ゲーム起動、既存の GameTest クライアント、GPU ごとの描画、Oculus／Polytone の統合検証は未実施。
リリース前にこれらを実施する。GitHub へのコメント・PR 作成・push は行わない。
