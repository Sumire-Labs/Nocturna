# Angelica の uniform／描画リスト再利用を移植

対象は `20.1/forge`、Minecraft 1.20.1 Forge、実行 Java 17。
開始点は Celeritas 移行コミット `19ddfceb`。

## 移植元と挙動

Angelica の LGPL-3.0 コミット
[`d876a203da9b46ee5b5048171b8e5e4deb9bb967`](https://github.com/GTNewHorizons/Angelica/commit/d876a203da9b46ee5b5048171b8e5e4deb9bb967)
（Jason Mitchell、Cut more per-frame allocations in terrain and rendering / #2193）から、次の2件を移植した。
比較対象4ファイルは、調査時の `996fb54cb05e256ea6039f4e2c8457a221a179b7` でも同一である。

### 行列 uniform の送信用バッファ

- `GlUniformMatrix3f` ごとに9 float、`GlUniformMatrix4f` ごとに16 floatの direct buffer を一つ保持する。
- `set` は元の JOML `get` と同じ列順でバッファを上書きし、同じ uniform location と `transpose=false` で送信する。
- 毎回の MemoryStack push／calloc／pop は不要になる。別の uniform インスタンスとはバッファを共有しない。
- 原実装の `BufferUtils.createFloatBuffer` は、LWJGL バックエンドに依存しない
  `ByteBuffer.allocateDirect(...).order(ByteOrder.nativeOrder()).asFloatBuffer()` に対応させた。
  容量・directness・エンディアン・position／limit をテストする。
- 行列や送信結果のキャッシュではない。同じ値でも送信し、OpenGL の状態を推測して呼び出しを省略しない。

### 描画リストの二世代プール

- Angelica の `RenderListPool`、各マネージャーへのプール接続、collector の取得経路、5 counter の reset を一緒に移植する。
- `generation++ & 1` で2スロットを交互に使う。配列は必要時だけ拡張し、以前の entry を保持する。
- region ID に加え **RenderRegion の参照同一性**を確認し、同じ ID が別の region に割り当て直された場合は新規リストを作る。
- 再利用時は geometry／sprite／entity／dynamic-sort／size の5 counterをリセットする。
  backing array は消去せず、有効な prefix のみ読むという元の規約を保つ。
- 地形と影は、それぞれの `RenderListManager` が独立したプールを持つ。
- 既存の「各マネージャーの未完了探索は一つだけ」という拒否条件を維持し、その検査の後で世代を進める。
  完了／join／公開の順序、探索・再構築順、可視性、描画順は変更しない。
- 原実装の Mixin は、このソースツリーでは対象メソッドへ直接反映する。
  既存の単体 `VisibleChunkCollector` constructor も維持し、プール未設定時は従来どおり新規作成する。

## 検証

`node scripts/verify-angelica-reuse-ports.mjs /path/to/Angelica` で固定コミットと比較する。
namespace、Mixin のフィールド名・cast、`this`／空白の差を対応させたうえで、以下のメソッド全体の一致を確認する。

- mat3／mat4 の `set`
- `RenderListPool.beginSearch`／`acquire`
- 全 counter の reset

Java 17 の JUnit は **common 55件 + Forge 20件 = 75件成功**。追加8件で以下を確認した。

- 行列の全成分・列順・location・transpose、繰り返し更新、別 uniform との分離。
  負のゼロ・Infinity・NaN payload も GL 呼び出し直前で raw bits が一致する。
- 新規リストと再利用リストの全4種類の index 列、正順／逆順、空・変動する80世代の一致。
- 5 counter の reset と backing array の保持、unsigned index、region ID の再割当、独立した地形／影プール。
- 本番 `RenderListManager` と executor で未完了探索を latch で止め、公開中のリストが変化しないことを確認。
  重複探索の拒否がプールの世代を進めないこと、join 後に正しい世代を再利用することも確認。

割り当て量は HotSpot の `ThreadMXBean` で、同じ16 regionのリスト生成・追加・公開を比較した。
各経路2,000探索の warmup 後、各5,000探索を測定した結果は次のとおり。

| 対象部分 | 新規作成 | プール再利用 |
| --- | ---: | ---: |
| 1探索あたり Java heap 割り当て | 18,304.1 B | 0.0 B |

これは region 別リストと配列の部分測定であり、探索全体の割り当てがゼロになるという意味ではない。
プールは2世代分のリスト・配列を保持する。FPS／総フレーム時間の改善率は主張しない。

実クライアント（Temurin 17、Forge 47.3.0、Windows 11、AMD Radeon 8060S / OpenGL 4.6）でも検証した。

- 本番 `GlUniformMatrix3f`／`4f` で各32回更新し、`glGetUniformfv` で実 GPU の値を読み戻して全成分の一致を確認。
- CCL・Immersive Engineering・BrandonsCore を同時導入し、新規 superflat の地形を描画。
  通常頂点形式に加え、compact + `AsyncOcclusionMode.EVERYTHING` の再構築・描画を検証する。
- 旧公開 API、CCL の独自 quad、IE 銅線の256頂点、設定画面・イベントも引き続き検証する。
- `gradlew build` の配布クラス検査、Java 17、公開 ABI 461定義の既存テストも通過する。

シェーダーパックを使った影描画の実機テストは今回行っていない。
影用プールの独立性はコード・単体テストで確認し、実クライアントでは非同期の地形探索を検証する。
標準設定は変更していない。EVERYTHING／compact の切り替えは配布されない検証用 mod 内だけで行う。

## 再現コマンド

Gradle は JDK 25、製品とテストは Java 17 toolchain を使う。

```powershell
node scripts/verify-angelica-reuse-ports.mjs
node scripts/verify-celeritas-base.mjs
./gradlew.bat build -PcompatRuntime :modern:1.20.1-forge:runValidationClient --no-daemon
```

テスト用 mod とアドオンは製品 jar に含まれない。push／公開は行わない。
