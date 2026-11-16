# Nocturna: シェーダー前処理・コンパイル結果キャッシュ

Minecraft 1.20.1 Forge / Java 17、`20.1/forge`。開始点は `3fda7a44`。
作業中の改名は別コミット `c519e578`。表示名は Nocturna、mod ID は `embeddium`、既存パッケージは維持する。
`origin` は `https://github.com/Sumire-Labs/Nocturna.git` に変更した。push は行わない。

## 原実装と移植範囲

移植元は Angelica の LGPL-3.0 コード、固定リビジョン
[`996fb54cb05e256ea6039f4e2c8457a221a179b7`](https://github.com/GTNewHorizons/Angelica/tree/996fb54cb05e256ea6039f4e2c8457a221a179b7)。
関連する変更は [#2194](https://github.com/GTNewHorizons/Angelica/pull/2194)、
[#2206](https://github.com/GTNewHorizons/Angelica/pull/2206)、[#2213](https://github.com/GTNewHorizons/Angelica/pull/2213)。

キャッシュ本体を簡易な map に置き換えず、次のクラスをまとめて移植した。

- `ShaderDiskCache`: SHA-256 の長さ付きキー、ヘッダー・CRC32、atomic rename、salt による失効、256 MiB 上限。
- `ShaderCacheIO`: 元の文字列・配列・blob 形式、UTF-8、境界チェック。
- `ShaderCacheWorkers`: 元の worker pool、非同期書き込み、in-flight 管理、120秒 idle shutdown。
- `ProgramBinaryCache`: linked program の保存／復元、ドライバー拒否時の削除と miss 化、3 pack の保持、
  削除／非同期書き込みの競合防止、完全生成マーカー用の世代・成功判定。
- `PreprocessedSourceCache` と `IncludeProcessor`: マクロを含むキー、include DAG の再帰的内容ハッシュ。

`scripts/verify-angelica-shader-cache-ports.mjs` は、固定した原典と上記6クラスの全体を比較する。
差として許容するのは、パッケージ・バックエンド呼び出し・record accessor・worker 設定の移植先対応と、
GUI の capability 問い合わせ helper のみ。

### 前処理

GLSL は include 展開後の内容に対応する DAG ハッシュと、渡された全マクロの名前・値をキーにする。
ヒット時は展開・JCPP 処理を省き、保存済み出力を返す。
properties は入力全体と有効な boolean／string option・環境マクロを含める。
ソースや include、option が変われば miss になり、従来の前処理を再実行する。

### リンク済みプログラム

全 shader stage の型・ソース、実際の attribute location、pack 名に加え、
GL vendor／renderer／version／GLSL version を含める。
attribute map は JVM 起動ごとに変わり得る map の走査順を避け、名前順にキーへ入れる。
Angelica の固定属性表に対し、Celeritas では gbuffer と地形の実際の属性表を扱うための対応である。

Forge 1.20.1 の desktop GL では旧 fixed-function emulation と GLES がないため、その platform flag は false、
基準 GLSL は150に対応させる。GL 4.1 core と `ARB_get_program_binary` の両方を扱い、
binary format がない場合は通常コンパイルを使う。

ヒット時はコンパイル・リンク前に復元し、成功した GL handle を既存の sampler／uniform／image 構築へ渡す。
失敗時は原実装どおり handle と該当 entry を捨て、元ソースのコンパイルへ戻る。
miss は Celeritas の既存の非同期 submit／finish を保ち、成功を確認したプログラムだけ保存する。
gbuffer／composite／compute に加え、独自の `GlProgram` を使う地形用プログラムにも接続した。

本バッチは前処理出力と GL binary のディスクキャッシュが対象。
生存 GL handle の二世代保持、全次元の強制プリウォーム、SDL／SPIR-V 変換器は別機能として取り込んでいない。
Celeritas の Cornea AST 変換ディスクキャッシュも、元の無効状態を維持している。
Angelica と Celeritas の shader loader 全体が同一になったという意味ではない。

## 設定・失効

- コンパイル結果保存は **Angelica と同じ既定オフ**。シェーダー選択画面の保存ボタンで有効にする。
  設定は `config/embeddium-shaders.properties` の `saveCompiledShaders=true`。
- 前処理キャッシュは製品 jar の読み込み時に自動設定する。
  `embeddium/shadercache` を使用し、製品 version／jar size／mtime が変われば失効する。
- 開発 class directory は Angelica と同様に自動永続化しない。
  検証用 mod は隔離した `embeddium/shadercache-validation` を明示的に設定する。
- 「保存したコンパイル結果を削除」は GL program 層を削除する。
  削除前の非同期ジョブが後から entry を再作成しないよう、元の delete counter／lock を維持する。
- 非対応ドライバーでは保存ボタンを無効にし、通常コンパイルを続ける。

## 検証

Java 17 の単体テスト **91件（common 55 + Forge 36）** が成功。
原典のディスクキャッシュテストに、次の回帰テストを追加した。

- disabled、cold／warm の同一出力、マクロ・properties option・include 変更時の miss。
- CRC破損・切り詰め・不正 payload、UTF-8、大きい文字列、salt、容量上限、layer の保持。
- jar の version／size／mtime による失効と、開発 directory の非永続化。
- 保存設定の既定オフと round trip。
- 非同期書き込みが完了するまで marker を公開しないこと、失敗・削除・世代交代後の marker 誤公開防止。
- 既存の公開 ABI 461定義、CPU/GPU 待機・描画リスト再利用等の既存テストも成功。

実機は Temurin 17、Forge 47.3.0、Windows 11、AMD Radeon 8060S、OpenGL 4.6。

| 実機検証 | 結果 |
| --- | --- |
| fresh compile と disk binary 復元の offscreen 描画 | 全画素一致 |
| attribute location／fragment source の変更 | miss となり、変更を反映して描画 |
| 意図的に不正な binary format を保存 | ドライバー拒否後にソースへ fallback、元と同じ画素 |
| compute program | cold 保存後に warm hit |
| 合成テスト pack を実際の Iris pipeline で描画 | cold で112 stage compile、同一 pack の warm reload は地形を含め0 stage compile |
| CCL・IE・BrandonsCore 同時導入 | 通常／compact + async terrain、CCL quad、IE 銅線、旧 API 等が成功 |

stage compile 数は、配布されない検証用 Mixin で `GlShader.compile` 自体を計数する。
テスト用 shader pack は小さな自作 fixture。外部 pack のダウンロードや再配布は行っていない。
製品 jar の全2,419クラスが Java 17 以下であること、ライセンス・由来情報があること、
検証用 mod とアドオンが含まれないことも `verifyJava17Jar` で確認する。

全 shader pack／全GPUの保証や、一定倍率の読み込み短縮・FPS改善の主張はしない。
不正バイナリを使う検証中の「Driver rejected ... Recompiling ...」ログは、意図した fallback の確認である。

## 再現

Gradle 自体は JDK 25、製品・テスト・ゲームは Java 17 toolchain。

```powershell
node scripts/verify-angelica-shader-cache-ports.mjs
node scripts/verify-celeritas-base.mjs
./gradlew.bat build -PcompatRuntime :modern:1.20.1-forge:runValidationClient --no-daemon
```
