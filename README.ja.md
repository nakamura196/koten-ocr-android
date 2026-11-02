# KotenOCR for Android

[English](README.md) | 日本語

古典籍のくずし字と、近代の活字・手書きの日本語を読み取る Android アプリです。OCR はすべて端末の中で動き、インターネット接続は要りません。

国立国会図書館の [NDL古典籍OCR-Lite](https://github.com/ndl-lab/ndlkotenocr-lite) と [NDLOCR-Lite](https://github.com/ndl-lab/ndlocr-lite) のモデルを、ONNX Runtime で動かしています。iOS 版 [KotenOCR](https://github.com/nakamura196/koten-ocr-ios) の Android 版です。

## ダウンロード

- Google Play: まだ公開していません（クローズドテスト中）。[テスターの参加方法](https://kotenocr.ldas.jp/android.html)
- APK: 各版を [GitHub Releases](https://github.com/nakamura196/koten-ocr-android/releases) に置いています
- 紹介ページ: <https://kotenocr.ldas.jp/>

## 機能

- 2 つの OCR モード: 古典籍（くずし字）と近代（活字・手書き）
- カメラまたは写真の選択。ページの自動補正（台形補正）と傾きの自動補正
- 枠つきの結果表示、確信度のスライダー、枠の編集（移動・大きさ変更・追加・分割・結合・読み直し）
- テキスト・TXT・PDF での書き出し。読み取り履歴は端末に保存
- 古文の現代語訳: ご自身の API キーで、クラウドの AI（OpenAI・OpenRouter・Google Gemini）を使います
- 日本語と英語の画面

## OCR モード

| モード | 対象 | 検出 | 認識 |
|---|---|---|---|
| 古典籍 | くずし字 | RTMDet-S | PARSeq（1 モデル） |
| 近代 | 活字・手書き | DEIMv2-S | PARSeq の段階読み（3 モデル） |

## 動作環境

- Android 8.0（API 26）以上
- ビルドには JDK 17 と Android SDK 36（Android Studio を推奨）

## ビルド

ONNX モデル（約 221 MB）はリポジトリに入っていません。OCR エンジンが使うモデルファイルを `model_pack/src/main/assets/models/`（git の管理外）に置いてください。入手先は下の「モデル」の NDL のリポジトリです。iOS 版リポジトリの `setup.sh` は、同じファイルを固定したコミットから取得します。

```bash
git clone https://github.com/nakamura196/koten-ocr-android.git
cd koten-ocr-android

# デバッグビルド
./gradlew assembleDebug

# 単体テスト
./gradlew testDebugUnitTest

# 接続した端末に入れる
./gradlew installDebug
```

リリースビルドには `keystore.properties`（コミットしません）が要ります。`local.properties` の `OPENROUTER_API_KEY` は、翻訳機能の開発用に、デバッグビルドにだけ埋め込まれます。

## モデル

| モード | モデル | 出典 |
|---|---|---|
| 古典籍 | RTMDet-S（文字領域の検出）、PARSeq（文字認識） | [NDL古典籍OCR-Lite](https://github.com/ndl-lab/ndlkotenocr-lite) |
| 近代 | DEIMv2-S（レイアウト検出）、PARSeq（30・50・100 文字用） | [NDLOCR-Lite](https://github.com/ndl-lab/ndlocr-lite) |

## ライセンス

このリポジトリのコードは [MIT License](LICENSE) で公開しています。

- OCR モデルと文字一覧: NDL古典籍OCR-Lite、NDLOCR-Lite（[CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/)、国立国会図書館）
- ONNX Runtime: [MIT License](https://github.com/microsoft/onnxruntime/blob/main/LICENSE)（Microsoft）
- サンプルの頁画像（`app/src/main/assets/samples/`）: 出典はアプリ内の表示のとおりです。国立国会図書館デジタルコレクションと東京大学総合図書館の所蔵で、パブリックドメイン、各館の利用条件に従って使っています

## 引用

[CITATION.cff](CITATION.cff) を見てください。GitHub の「Cite this repository」から引用の書式を取れます。

## 作者

中村 覚（東京大学）
連絡先: nakamura.satoru@mail.u-tokyo.ac.jp

役に立ったら、このリポジトリの Sponsor ボタンから開発を支援していただけます。
