# Mellow 3.5 のコンパイル失敗修正

この検証時点に残っていた Photon の黒帯は、後続の型・stage 別カスタムテクスチャ変換の復元で解消した。

対象は Forge 1.20.1、Java 17。
比較元は [Iris 1.20.1 `f8d7bea2ab82d21737e7aa8e4b2fb3a429078106`](https://github.com/IrisShaders/Iris/blob/f8d7bea2ab82d21737e7aa8e4b2fb3a429078106/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/CompatibilityTransformer.java)。

## 原因と変更

Mellow の deferred program は fragment 入力 `ViewPos` に対応する vertex 出力を省略している。
Iris は使用される数値型入力に不足する出力を作成し、`main` の先頭で既定値に初期化する。
従来の Cornea adapter は宣言を組み立てる際に型と名前の間の空白を落とし、
`out vec3ViewPos;` を生成していた。

`CompTransformer` の補完処理を以下のように修正した。

- 入力の型・qualifier を独立した AST に複製し、storage qualifier の `in` だけを `out` に変更する。
  `flat`、precision、layout などを保ち、受け側の AST を変更しない。
- 型と変数名を分離して宣言する。パック名や `ViewPos` という名前に依存する特例は設けない。
- 新しく追加した出力を必ず `main` の先頭で初期化する。同名の local 変数への代入が存在しても、
  新しい global 出力の初期化を省略しない。
- scalar / vector / matrix の初期値を Iris と同じ数値・型の規則で作る。
  bool は false、整数は 0、浮動小数点は 0、vector / matrix は対応する型の zero constructor。
  Cornea が double に生成する無効な `d` suffix を使用しない。
- 使用されない入力と、numeric ではない補完不能な入力は補完対象から除外する。

既存の型一致・未代入出力の初期化にも同じ既定値生成を使用する。
型の異なるステージ間の cast などを含む Iris 全体の互換処理の移植を意味する変更ではない。
以前の仕様調査のその他の差分は `docs/iris-shader-compatibility-audit.md` を参照。

## 本家との比較方法

`scripts/generate-iris-varying-reference.mjs` は、固定した Iris の
**CompatibilityTransformer 全体を実行**して golden fixture を生成する。
元ソースの SHA-256 を検証し、package の移設と host の stage / logger / config adapter だけを用いる。
補完アルゴリズム・matcher・template を短縮した参照実装は作成しない。
使用する glsl-transformer 2.0.1 / ANTLR 4.13.1 は当該 Iris build が指定する版。
reference class と dependency jar は検査用ディレクトリに置き、製品に含めない。

```powershell
node scripts/generate-iris-varying-reference.mjs `
  --reference .git/research/iris-1.20.1 `
  --java-home "C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
```

`CompatibilityVaryingTest` は生成結果と次を比較する。

- 全 44 builtin numeric type の宣言、qualifier、初期値と挿入位置。
- local / prefix 名が似た変数への代入、複数の入力、既存の代入、未使用入力。
- 既存の未初期化出力、non-numeric 入力、geometry の配列入力。
- 入力側の不変性、補完の冪等性、元の function body の順序。

合計 53 reference fixture。独立な初期化文の出力順、同値な zero literal suffix、
`mat2` / `mat2x2` などの同値な型表記は正規化して比較する。
これは補完された interface と既定値の比較であり、Iris 全体の描画結果の完全一致試験ではない。

検査用 source set の既定パスと外部パスに同じ probe が存在する場合の重複コンパイルも修正した。
`scripts/shader-probe.init.gradle` の変更は検査環境だけに適用される。

## 実機での結果

AMD Radeon 8060S / OpenGL 4.6 / Java 17.0.20.1 で確認した。
パックの version / SHA-512 は既存の `docs/audits/shader-pack-versions.json` と同じ。
測定値は `docs/audits/mellow-varying-fix-results.json` に保存した。

| パック | バージョン | 最終コードでの晴天・雨の新旧経路・雪 |
|---|---|---|
| Complementary Reimagined | r5.9.3 | 通過 |
| Complementary Unbound | r5.9.3 | 通過 |
| BSL | 10.1.8 | 通過 |
| Photon | 1.4 | 通過。既存の空の黒帯は残る |
| Solas | 3.9 | 通過 |
| MakeUp Ultra Fast | 9.6 | 通過。既存の TAA uniform 警告は残る |
| Rethinking Voxels | r0.1-beta9 | 通過 |
| Mellow | 3.5 | **コンパイル失敗が解消し、描画まで通過** |

8 構成の最終ログに shader compile / link failure、pipeline fallback、instanced weather fallback はない。
各構成で雨・雪 441 列 / 1 draw と GL error 0 を記録し、スクリーンショットも確認した。
cache 無効の source compile に加え、Mellow では cache 有効の cold / warm / source reload を確認。
warm reload の追加 stage compile は 0、cache 無効 reload は 130 stage で描画を維持した。

最初の回帰検証は `30ecff83` に本修正を適用した固定 checkout で行った。
その後 `3ed7544f` の GUI / FISM などのローカル変更にも統合し、Mellow の実機検証を再実行した。
両 checkout と作業中の `CompTransformer.java` の SHA-256 が一致することも確認した。

- 固定 checkout: `build`、unit test 127 件、Java 17 product class 2,444 個を確認。
- 統合 checkout: `build`、unit test 128 件、Java 17 product class 4,298 個を確認。
- 両方で `runValidationClient -PcompatRuntime` 通過。CCL・Immersive Engineering・BrandonsCore、
  shader cache の GPU readback / warm reload、天候、horizon、compact / async terrain、旧 API を確認。
  統合版では FISM の既存 fixture も通過。

途中の検査には probe の source 重複、比較表記の正規化、Solas の class 読み込みに関する失敗があった。
検査環境を分離し、上表は再実行を含む最終成功結果のみで作成した。
別 GPU、別 profile、全 dimension / mod 組み合わせの網羅試験ではない。
以前の Photon / MakeUp などの問題については引き続き個別の修正が必要。
