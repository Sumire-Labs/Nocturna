# Angelica の雨・雪インスタンシング移植

対象は Forge 1.20.1、実行・製品クラスは Java 17。参照元は
[Angelica `996fb54cb05e256ea6039f4e2c8457a221a179b7`](https://github.com/GTNewHorizons/Angelica/tree/996fb54cb05e256ea6039f4e2c8457a221a179b7)。
配列や数式を簡易版に置き換えず、列ごとの情報を GPU 上で四頂点に展開する方式を移植した。

## 維持した処理

- 80 バイト・20 float の列レイアウト、四隅の triangle fan、雨・雪の別レイヤー。
- 列キャッシュの巡回グリッド、revision、20 tick の分散更新、座標ハッシュ、乱数と light cache。
- 雨・雪の UV スクロール、雪の揺らぎ、距離による alpha、色の 8 bit 量子化、lightmap の展開。
- `WeatherParams` の tick wrap と連続時間、カスタム天候 provider の列収集・revision・範囲・入力検証。
- batch の拡張、再アップロード、描画、リソース解放。

`node scripts/verify-angelica-weather-port.mjs` は固定した参照 checkout と比較し、
レイアウト・GLSL 本文・更新処理・batch の主要メソッドが対応することを検査する。
これはホスト依存の renderer 全体が同一ソースであるという検査ではない。

## Forge 1.20.1 への接続

1.20.1 の biome は三次元なので、native の biome・高度補正済み温度・降水種別を使う。
1.7.10 の温度式を代用しない。camera の biome 高度が変わった場合もキャッシュを無効化する。
雨粒の particle・音は Minecraft の `tickRain` を維持する。

Forge の dimension effects に既存の天候描画がある場合はその hook を一度呼ぶ。
新しい `WeatherColumnProvider` を実装する effects は Angelica と同じ列 API で batch に接続できる。
無効設定・非対応 OpenGL・shader variant 生成失敗では、既存の天候経路を使用する。

shader 無効時は runtime の particle fragment shader をリンクし、Minecraft／resource pack の
fog fragment を保持する。shader 有効時は選択済み weather program を同じ transformer・uniform・sampler・
framebuffer・blend 設定で構築し、頂点入力の生成部分を差し替える。
geometry／tessellation stage も保持し、後者には四頂点の patch を渡す。
OpenGL program・VAO・buffer・framebuffer・active texture・patch state を復元する。

## 検証

- キャッシュを凍結した Angelica 参照クラスと 240 frame 分を比較。負座標・移動・world 切替・
  radius・revision・温度・照明・サンプリング回数を含む。
- 全 20 float、無効値、600 列への成長、frame parameter の wrap を unit test で比較。
- GPU transform feedback で rain／snow／provider の 24 instance・96 頂点を読み戻し、
  元の式で計算した位置・色・UV・light と比較。許容誤差は `2e-5` の相対値。
- 実際の world で 441 列を 1 draw、rain／snow provider を 2 draw で確認。
  provider revision、Forge hook の呼び出し回数、無効時の fallback、state 復元を確認。
- native の snowy plains と desert による雪／無降水、shader 有効／無効を確認。
- CCL・Immersive Engineering・BrandonsCore を含む既存の audit client も通過。

初回の単独検証は common 55 件＋modern 41 件、計 96 件を通過。
並行作業の未確定ソースを混ぜない検証 checkout を用いた。最終コミット時の追加検証と
実シェーダーパックの結果は shader compatibility audit に記録する。

全 shader、全設定、全 GPU での画像一致を保証する検査ではない。
shader pack の vertex input 契約との整合は、元の数式の一致とは別に確認する必要がある。
