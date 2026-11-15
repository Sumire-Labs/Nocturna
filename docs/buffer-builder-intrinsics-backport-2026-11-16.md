# BufferBuilder の高速書き込み可否判定の移植

対象: Minecraft 1.20.1／Forge、`20.1/forge`。開始点: `06752aca`。
移植元: [2e56234798852be1ed0988aca570154b56dacbb2](https://github.com/FiniteReality/embeddium/commit/2e56234798852be1ed0988aca570154b56dacbb2)
(`Refuse to use intrinsics if a buffer is not available`、embeddedt)。調査・実装日: 2026-11-16 (JST)。

## 変更内容

元コミットは、CyclopsCore が作る実バッファのない仮の BufferBuilder を高速書き込み経路から除外するもの。
`canUseIntrinsics` の三条件を元の順番で移植した。

1. format description が null ではない。
2. format が単純である。
3. backing buffer が null ではない。

1.21 側の field 名 `embeddiumFormat` を、1.20.1 側の既存名 `format` に対応させる。
backing field の型は 1.21 の ByteBufferBuilder と 1.20.1 の ByteBuffer で異なるが、
今回の条件はいずれも object の null 判定のみ。capacity／remaining／directness の条件は追加しない。

元コミットには constructor 内の null buffer エラーを撤去する変更もある。
このブランチにはそのエラー判定が存在しないため、constructor や format 初期化処理は変更しない。
通常の push、頂点配置、再確保、変換 serializer、vanilla の書き込み処理も変更しない。
新しい Sodium のコードは使用せず、LGPL-3.0 の Embeddium の変更を移植している。

## 呼び出し側の挙動

- `VertexBufferWriter.tryOf`: 判定が false の consumer は null を返す。
- `VertexBufferWriter.of`: 判定が false の consumer は従来の IllegalArgumentException を投げる。
- `SodiumBufferBuilder`: 独自に可否を cache せず、元の builder の判定を使い続ける。

これらの呼び出し側自体は変更しない。自動的な vanilla fallback は追加していない。
null buffer の consumer を渡す呼び出し側は、元の API 契約どおり結果を扱う必要がある。

## 検証

`BufferBuilderIntrinsicsTest` は実際の mixin の判定と getter、既存の writer API と delegate を使う。
テストでは shadow field の状態を設定し、無関係な頂点書き込み・再確保が呼ばれた場合は失敗させる。

- format 不明／単純／非単純と、buffer 不在／heap／direct の組み合わせ。
- 非 null の空 buffer も元実装どおり可否条件を満たすこと。
- buffer の消失・復帰、format 変更後の可否の再評価。
- 公開 writer API と既存 SodiumBufferBuilder delegate への反映。

修正前に新規テスト4件を実行し、null buffer の誤判定に関する3件が失敗することを確認した。
修正後は新規4件と既存22件、計26件が成功した。失敗・スキップは0件。
`verify-embeddium-backports.mjs` は、field 名の対応後に判定メソッド全体が元コミットと
同じであることを検査する。短絡評価の順番も変えない。

```powershell
node scripts/verify-embeddium-backports.mjs
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
./gradlew.bat --gradle-user-home D:/DevCache/Gradle/embeddium-legacy build --no-daemon
```

Java 17 でテスト・ビルドする。Minecraft launcher による Mixin 適用や、実 CyclopsCore を含む
modpack での起動・描画確認は未実施。今回の検証をその統合動作の保証とはしない。
