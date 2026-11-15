# 適応的 CPU／GPU 待機と Intel 起動時チェック撤去の移植

対象: Minecraft 1.20.1／Forge、`20.1/forge`。開始点: `9f09f818`。
調査・実装日: 2026-11-16 (JST)。
移植元: [5263007efef1749898f9172a7f918930c34b6250](https://github.com/FiniteReality/embeddium/commit/5263007efef1749898f9172a7f918930c34b6250)
(`Use adaptive algorithm for CPU render-ahead fencing`、FiniteReality)。

LGPL-3.0 の Embeddium の4ファイル分の変更を移植する。新しい Sodium のコードは使用しない。
待機処理だけ、または Intel チェック撤去だけを独立して取り込む構成にはしない。

## 原実装から維持する動作

- frame の先頭で、fence の数が `cpuRenderAheadLimit` を超えている間だけ古いものから取り出す。
  queue の初期容量は8。設定値と同数なら待たず、0なら前の frame の全 fence を待つ。
- 取り出した fence に対し、最大40回、timeout 1,000ns で `glClientWaitSync` を呼ぶ。
  すべての待機で `GL_SYNC_FLUSH_COMMANDS_BIT` を指定する。
- 短時間待機で終わらない場合、`Thread.yield()` の後に timeout 10,000ns で待つ処理を繰り返す。
  この後半段階に独自の回数制限・総時間制限は追加しない。
- `GL_ALREADY_SIGNALED` と `GL_CONDITION_SATISFIED` は成功。fence を一度削除し、queue の条件確認に戻る。
  fence 間では設定値を再取得する。
- `GL_WAIT_FAILED` は元と同じ `RuntimeException("Failed to wait on fence object")` を投げる。
  元実装にはこの失敗時の fence 削除や profiler pop がないため、独自の finally cleanup は追加しない。
- frame 終了時は `GL_SYNC_GPU_COMMANDS_COMPLETE`、flags 0 で fence を作って queue に追加する。
  handle 0 の場合は `RuntimeException("Failed to create fence object")` を投げ、queue に追加しない。

原文のコメントには短時間待機について「40ms total」とあるが、指定値の合計は
1,000ns × 40 = 40,000ns = 40µs。実際の経過時間には呼び出しなどの overhead が加わる。
コメントから値を推測して変更せず、元コードの数値・分岐・順番を維持した。

## Intel 起動時チェックの撤去

元コミットと同様に以下をまとめて削除した。

- pre-launch からの `EarlyDriverScanner.scanDrivers()` 呼び出しと import。
- `Configuration.WIN32_DRIVER_INTEL_GEN7` 設定項目。
- `EarlyDriverScanner` クラス全体。Intel の版数を照合して message box と `System.exit(1)` に進む経路を削除する。

`sodium.checks.win32.intelGen7` はこの処理で参照されなくなる。
adapter の検出、既存 workaround 初期化、RTSS チェック、起動後の driver 検査は維持する。
`WindowsDriverStoreVersion` などの周辺 utility は元コミットで撤去されていないため残す。

1.20.1 への対応は package 名、`Embeddium.options()` → `SodiumClientMod.options()`、
pre-launch クラス名、NeoForge → Forge の名前空間の対応のみ。待機アルゴリズムを別 helper に置き換えない。

## 検証

`scripts/verify-embeddium-backports.mjs` で、元コミットと以下の一致を確認する。

- `preRender`／`postRender` のメソッド全体。
- queue の初期化。
- `onPreLaunch` の処理順序、Configuration 全体、EarlyDriverScanner の source 削除。

JUnit は Java 17 で実行する。通常は既存26件と新規13件の計39件が成功した。
新規テストは、コンパイルした製品の mixin クラスをテスト用 class loader に読み込み、
外部 GL 関数、`Thread.yield`、Minecraft／profiler／options の呼び出し先だけをテスト hook に変更する。
queue 処理、loop、counter、分岐、timeout、例外は製品 bytecode のまま実行し、別の待機アルゴリズムを
テスト内で再実装しない。

確認する経路:

- limit 以下、limit と同数、0、FIFO、途中で設定値が変わる場合。
- 両成功 status、短時間待機の再試行、40回目での成功。
- 40回 timeout の後の yield と長い待機、それぞれの呼び出し順・flags・timeout。
- 短時間段階／yield 段階での失敗、元の例外・queue・削除・profiler の状態。
- fence の作成成功と handle 0 の失敗、次 frame での処理。
- compiled class に Intel scanner／設定 field がなく、adapter 検出・workaround 初期化・RTSS 設定が残ること。

任意の実 OpenGL テストは `-PtestOpenGL` で有効にする。GL 関数と `Thread.yield` を置換しない別の
test harness を使い、非表示 GLFW context で4個の fence を生成し、limit 1、続いて0で処理する。
このテストを含む build は全40件成功、失敗・スキップ0件。OpenGL error は発生しなかった。

```powershell
node scripts/verify-embeddium-backports.mjs
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat --gradle-user-home D:/DevCache/Gradle/embeddium-legacy build -PtestOpenGL --no-daemon
```

実機: `ATI Technologies Inc.`／`AMD Radeon(TM) 8060S Graphics`／
`4.6.0 Compatibility Profile Context 26.10.1.261008`。
実 GPU テストでも Minecraft／profiler／options は隔離しており、ゲームの runTick 注入そのものは起動しない。
Intel Gen7 実機、Minecraft の実ワールド、他 mod との組み合わせ、GPU stall 中の実 scheduler、
FPS／frame time の比較は未検証。原実装の一致・制御した応答列・現在の AMD 実機での正常動作の検証と区別する。
