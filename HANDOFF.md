# Claude Code 引き継ぎドキュメント — KotenOCR Android v2.1 (`koten-ocr-android`)

## 1. 現在のステータス（直ちに動作検証から開始可能）

- **対象リポジトリ**: `/Users/nakamura/git/hi/miyako/koten-ocr-android`
- **参照元 iOS 版**: `/Users/nakamura/git/hi/miyako/koten-ocr-ios`（v2.1 系と同等の機能・デザインを移植済み）
- **ビルド・単体テスト**:
  - `./gradlew testDebugUnitTest assembleDebug` **成功済み**
  - 生成済み APK: `app/build/outputs/apk/debug/app-debug.apk`
- **エミュレータ・実機状態**:
  - AVD `Pixel_7_API_34` が起動中
  - `com.nakamura196.kotenocr.dev`（`versionName = "2.1.0"`, `versionCode = 6`）を `adb install -r` 済み

---

## 2. 環境パス・すぐ使えるコマンド（探索不要）

### ツールパス
- **JDK 17 (Homebrew)**: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- **Android SDK**: `/Users/nakamura/Library/Android/sdk`
- **adb**: `/Users/nakamura/Library/Android/sdk/platform-tools/adb`
- **emulator**: `/Users/nakamura/Library/Android/sdk/emulator/emulator`

### アプリ起動・再ビルド・ログ確認コマンド
```bash
# 1. エミュレータ接続確認
~/Library/Android/sdk/platform-tools/adb devices

# 2. エミュレータが未起動の場合のみ起動
~/Library/Android/sdk/emulator/emulator -avd Pixel_7_API_34 &

# 3. アプリ起動（Debug ビルドの applicationId は .dev 付き）
~/Library/Android/sdk/platform-tools/adb shell am start -n com.nakamura196.kotenocr.dev/com.nakamura196.kotenocr.MainActivity

# 4. コード修正時の再ビルド＆インストール
cd /Users/nakamura/git/hi/miyako/koten-ocr-android
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew installDebug

# 5. クラッシュ・OCRログ監視
~/Library/Android/sdk/platform-tools/adb logcat -v time | grep -E "com.nakamura196.kotenocr|AndroidRuntime|OCREngine"
```

---

## 3. 実装済み機能一覧（iOS v2.1 パリティ）

### デザインシステム（和紙・墨・朱）
- `ui/theme/Theme.kt`, `ui/theme/KotenStyle.kt`
  - ライト（和紙 `#F6F1E6` / 墨 `#1F1B16` / 朱 `#B8352B` / 金茶 `#9E7B2F`）およびダークモード対応
  - 明朝体（Serif）見出し・角丸カード・繊細な枠線（`KotenCard`）

### 画面・ナビゲーション (`AppState.kt`, `MainActivity.kt`)
1. **起動画面 & 3ステップ・オンボーディング** (`ui/screens/LaunchScreen.kt`, `ui/screens/OnboardingScreen.kt`)
   - 初回起動時に3ページのチュートリアルを表示（`assets/samples/onboarding_sample.jpg` + `onboarding_sample_ocr.json` による実データプレビュー付き）
2. **ホーム画面 & サンプル選択シート** (`ui/screens/HomeScreen.kt`, `ui/screens/SampleSheet.kt`)
   - カメラ撮影 / フォトライブラリ選択 / サンプル資料（竹取物語・源氏物語・菊池寛・柴田宵曲・現代縦書きの5種）
   - 最近の履歴3件のクイックアクセス
3. **カメラ画面** (`ui/screens/CameraScreen.kt`, `util/TiltMonitor.kt`, `util/PerspectiveCrop.kt`)
   - リアルタイム4点矩形検出オーバーレイ、水平・傾きインジケータ、自動シャッター、グリッド表示、ピンチズーム
4. **4点台形補正クロップ & 確認画面** (`ui/screens/CropScreen.kt`, `ui/screens/ConfirmCropScreen.kt`)
   - 4頂点ドラッグ＋ルーペ拡大鏡、90度回転、自動矩形検出リセット、補正後プレビュー確認
5. **OCR 処理中画面** (`ui/screens/ProcessingScreen.kt`)
   - ステップ進捗表示（前処理 → 行検出 → 文字認識 → 読み順整列）およびキャンセル機能
6. **OCR 結果画面 & 矩形編集モード** (`ui/screens/ResultScreen.kt`, `ui/screens/BoxEdit.kt`)
   - **3タブ構成**:
     - `画像`: 検出ボックスオーバーレイ、読み順コネクタ線、ルビ（ふりがな）表示、ピンチズーム、ボトムシートでの行テキスト編集・再OCR
     - `全文`: 縦書き風/横書き全文表示、文字サイズ変更、検索ハイライト、行番号表示、クリップボードコピー
     - `AI翻訳`: 現代語訳・英語・中国語（簡体/繁体）・韓国語へのストリーミング翻訳、難易度（やさしく/標準/逐語訳・学術）、原文対訳表示
   - **矩形編集（Box Edit）**:
     - 移動・リサイズ・2分割（Split）・複数行結合（Merge）・新規ボックス追加・削除・個別/一括再認識（Re-OCR）・Undo/Redo
   - **エクスポート** (`data/ExportManager.kt`):
     - テキスト共有、アノテーション付き画像（PNG）、PDF、JSON 出力
7. **履歴画面** (`ui/screens/HistoryScreen.kt`, `data/HistoryManager.kt`)
   - 検索、スワイプ/選択削除、JSON サイドカーによる編集状態・翻訳キャッシュの永続化
8. **設定・AI翻訳設定画面** (`ui/screens/SettingsScreen.kt`, `ui/screens/TranslationSettingsScreen.kt`, `data/SettingsManager.kt`, `data/TranslationService.kt`)
   - 検出モデル切替（DEIMv2 / RTMDet-s）、認識モード切替（Cascade PARSeq 3段階 / 単一 PARSeq）、傾き補正・低信頼度フィルタ等の ON/OFF、表示言語（システム/日本語/英語）、OpenAI 互換 API 設定・接続テスト

---

## 4. Claude Code にお願いしたい作業（動作検証 & デモ動画作成）

### Step 1: 実機（エミュレータ）での一通りの動作検証・バグ修正
エミュレータ上で以下を順番に操作し、レイアウト崩れ・クラッシュ・意図しない挙動があれば修正してください。

1. **初回起動 → オンボーディング**:
   - スプラッシュ表示後、3ページのオンボーディングが表示され、「はじめる」または「サンプルで試す」で遷移できるか。
2. **サンプル画像での OCR 実行**:
   - ホーム画面の「サンプル資料で試す」から「竹取物語」または「源氏物語」を選択。
   - 4点クロップ画面 → 確認画面 → OCR 実行と進み、行検出・文字認識が正常に完了するか。
3. **結果画面の各タブ・編集機能**:
   - **画像タブ**: ボックスのタップ選択、ルビ表示切替、読み順表示切替、ボックス編集モード（分割・結合・追加・削除・再認識）が動くか。
   - **全文タブ**: 全文表示、検索、フォントサイズ調整、コピーが動くか。
   - **AI翻訳タブ**: 未設定時の案内表示、設定画面への遷移が動くか。
   - **共有・エクスポート**: PNG / PDF / JSON / TXT エクスポートがエラーなく動作するか。
4. **履歴・設定タブ**:
   - 履歴一覧に保存され、再表示・編集保存ができるか。
   - 設定タブでテーマ（外観モード）や OCR パイプライン設定を変更できるか。

### Step 2: スクリーンショット & デモ動画の作成
検証完了後、以下のコマンド等で主要画面のスクリーンショットおよび一連の操作デモ動画（MP4）を撮影・保存してください。

```bash
# スクリーンショット撮影例
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /tmp/koten_android_screen.png

# 画面録画（最大180秒、Ctrl+C または kill で停止後に pull）
~/Library/Android/sdk/platform-tools/adb shell screenrecord --bit-rate 8000000 /sdcard/koten_demo.mp4
~/Library/Android/sdk/platform-tools/adb pull /sdcard/koten_demo.mp4 ./koten_android_demo.mp4
```

---

## 5. 2026-11-01 動作検証の進捗（Claude Code）

注意: Debug の applicationId は `com.nakamura196.kotenocr.debug`（`.dev` ではない）。起動は
`adb shell am start -n com.nakamura196.kotenocr.debug/com.nakamura196.kotenocr.MainActivity`

### 直した不具合（コード修正済み）
1. 上部にタイトルバー（"Dev Native KotenOCR"）が出る → `MainActivity` に `installSplashScreen()` を追加
2. 古典籍 OCR が必ず失敗 → `RTMDetector` の入力を 1280 → 1024（モデル実体は 1024、iOS も 1024）
3. 認識結果が全行空 → `PARSeqRecognizer.loadVocab` が存在しない `char_list:` を読んでいた。iOS と同じく `charset_train` を読む形に。あわせて CTC 用の重複除去と ID<4 の読み飛ばしを削除（iOS に合わせる）
4. 近代 OCR の検出 → `DEIMDetector` の入力を 1024 → 800（モデル実体・iOS とも 800）
5. 結果画面 → 翻訳設定 →「戻る」でホームに落ちる → 開いた元の画面に戻す（`AppState.navigateBackFromTranslationSettings`）
6. 読み順が崩れる（竹取物語で右から 6 本目が 1 番）→ `ReadingOrder.kt` を iOS の XY-cut に忠実に移し直し、`ReadingOrderTest.kt` を追加。実機で 1〜16 が右から順になることを確認
7. OCR 失敗時に例外を握りつぶしていた → `Log.e("OCREngine", ...)` を追加

### 確認済み
オンボーディング 3 頁、ダークテーマ、言語切替、サンプル一覧、竹取物語の OCR（16 行・本文正しい）、枠の選択・表示切替、枠編集（選択・削除・元に戻す）、API キー未設定時の案内、翻訳設定画面

### 未解決・未確認
- 未確認: 近代モード（DEIM）で実際に読めるか、4 点クロップ画面（サンプルはクロップを飛ばして直接 OCR する）、分割・結合・追加・再認識、エクスポート、履歴、デモ動画とスクリーンショット
- デザイン（iOS との差）:
  - 日本語の見出しが明朝体にならない（`FontFamily.Serif` は Android では和文に明朝を当てない）。iOS はヒラギノ明朝。和文明朝フォント（Noto Serif JP など）の同梱が必要
  - API キーの案内ダイアログ・翻訳設定のプルダウンが Material 標準の見た目のまま
  - 結果画面の「コピー」ボタンが行の文字に重なる
  - 英語表示でホームの「Choose from Ph…」が切れる

### 2026-11-01 続き（Claude Code）
- **リリース先は既存の Play の掲載（`com.nakamura196.koten_ocr`、旧 Flutter 版と同じ）に決定**（本人の指示）。`app/build.gradle.kts` の applicationId を `com.nakamura196.kotenocr` → `com.nakamura196.koten_ocr` に変更済み（namespace は据え置き。Debug は `com.nakamura196.koten_ocr.debug` になる）。署名は旧版と同じアップロード鍵（koten-ocr-flutter/android/key.properties が指す jks）が必要。versionCode 6 は旧版（3）より大きいので可。旧 Flutter 版の AAB は約 290MB。Play の容量上限に掛からないかはアップロード時に確かめる
- 確認済み（追加）: 近代モード（NDL マニュアル 25 行）、写真から選ぶ → 範囲を選ぶ → 確認 → 古典籍で読み取り（柴田 15 行）、枠の分割・結合・追加・再認識・完了、TXT と PDF の書き出し（PDF は A4 2 頁）
- 新しく見つけた不具合:
  - 範囲を選ぶ画面で、初回の吹き出し「角を動かして範囲を選ぶ」が出ている間、「切り取り」「回転」が反応しなかった（1 回目。2 回目は吹き出しが出ず正常）。設定の「ヒントの再表示」で再現を確かめる
  - 同じ吹き出しが画面の右端で切れる。iOS は和紙色・朱の縁取りで、左上の角の近く
  - 枠の編集から戻ると、結果画面の画像が拡大されたまま右側が切れる
  - 書き出しのメニューが Material 標準の薄紫
- iOS との比較用の画面: 使い方動画の素材 `~/git/blog/kotenocr-ios/work/ja/chNN/raw_cfr.mp4` から、台本の区切りごとに 1 枚ずつ取り出せる（取り出し方: timeline.json の start − record.json の t0 + 3 秒）
- デザインの主な差（iOS に寄せる）: 範囲を選ぶ画面（画像を画面幅いっぱい・辺の中央の取っ手・案内文・下のボタンを 1 本の帯に）、確認画面（種類の選択と「読み取る」を 1 行に）、結果画面（見出し「15 行」を中央・明朝、ボタン類を画像の下へ、OCR 結果／現代語訳をタブで切替、行番号を朱、行の区切り線なし、確信度のスライダーを iOS 型に）
- 明朝体: エミュレータ（Pixel）では端末の Noto Serif CJK で明朝になっていた。機種によっては無いので、同梱は引き続き推奨
- 未確認: 履歴（開く・削除）、設定の各項目、デモ動画とスクリーンショット

### 2026-11-01 続き 2（Claude Code）— 範囲を選ぶ・確認・結果を iOS に寄せた
- 吹き出し（`CoachBubble`）を iOS と同じ形に描き直した: 和紙色・朱 3dp の縁・明朝 18 太字・上下に尖り。範囲を選ぶ画面では左上の角の近くに出し、6 秒か、画像に触れると消える。タッチは取らない
  - 「吹き出しの間ボタンが効かない」は再現しなかった（吹き出しを出したまま「回転」が効くことを確認）
- 範囲を選ぶ画面: 画像を画面幅いっぱい・暗い台（`docBackground` を iOS の色に統一）・初期範囲は画像全体・辺の中央に白い取っ手・朱の点 20dp・案内文「四隅の朱の点を…」・下のボタンを 1 本の帯に
- 範囲を選ぶ画面で見つけて直した不具合:
  - 2 回目以降のドラッグが、画面を開いたときの範囲から始まっていた（前に動かした分が消える）。4 点補正も同じ。最新の値を読むよう修正
  - 画像を端まで広げたので、端の取っ手をつかむと Android の「戻る」（端からのスワイプ）に取られた。取っ手の周りだけ「戻る」を無効にした（取っ手が動くたびに更新）
- 確認画面: 上を「‹ 再切り取り｜確認｜キャンセル」に、画像を大きく、種類の選択と「読み取る」を 1 本の帯に
- 結果画面: 上を「‹ 戻る｜N 行（明朝）｜切り取りアイコン」に。枠・枠を直す・共有を画像の下へ。画像は和紙の台紙。確信度のスライダーを iOS 型（目盛りなし、白い丸い つまみ）。OCR 結果／現代語訳を常に出るタブに。行番号を朱、区切り線なし。現代語訳タブに「まだありません＋現代語訳」ボタン。共有メニューを和紙色に
  - 「戻る」は iOS と同じく確認画面へ（履歴から開いたときは「‹ 履歴」で履歴へ）。切り取りアイコンで範囲の画面へ。端末の戻る操作も同じ
- アプリごとの言語: Android 13 以降は端末の設定で選んだ言語を起動時に消していた。消さないよう修正し、`locales_config.xml` を追加（端末の設定の「アプリの言語」に出る）
- 確認済み（追加）: 履歴を開く・「‹ 履歴」で戻る・1 件削除
- 未解決:
  - ダイアログ（履歴の削除など）が Material 標準の薄紫
  - 起動直後のスプラッシュ（端末が出す画面）のアイコンが旧版の紺の丸。アプリ内の朱の「典」と違う。ランチャーのアイコンも確認が要る
  - 1 回だけ、入れ直し直後にスプラッシュのまま止まった（プロセスが無いのに画面だけ残っていた）。再現せず
  - 枠の編集から戻ると拡大されたまま、は今回再現せず
  - 明朝体の同梱、設定の各項目、デモ動画とスクリーンショット

### 2026-11-02 APK 配布（Claude Code）

- Play の掲載はクローズドテスト段階（本番未公開）。本番には 12 人・14 日のテストが要る
- Play App Signing は Google 管理の鍵（アプリ署名鍵 SHA-256 B9:BE:F3:…）で、手元の jks はアップロード鍵（50:10:DC:…）。APK 版から Play 版へは上書き更新できない
- そのため APK は別アプリ `com.nakamura196.koten_ocr.direct`（build type `direct`、arm64 のみ、アップロード鍵で署名）として配布。`./gradlew assembleDirect` → `scripts/release/publish-apk.zsh <notes.md>`
- v2.1.0 を https://github.com/nakamura196/koten-ocr-android/releases/tag/v2.1.0 に公開（236MB）
- 処理中画面の朱の線を、毎フレーム滑らかに進むよう修正（ProcessingScreen.kt）
- 未確認: direct 版（R8 で縮小）での読み取り。紹介サイトへのリンク追加
- 2026-11-02 テスター募集: Google グループ kotenocr-android-testers@googlegroups.com を作成（Play と同じ Google アカウント、Chrome の /u/2/。誰でも参加・投稿と参加者一覧は管理者のみ）。Play のクローズドテスト Alpha は現在メールのリスト「はじめてのメーリス」(16 人) で、オプトインは 3 人（本番には 12 人・14 日）。リストとグループはどちらか一方しか選べないため、切り替えは本人の判断待ち。その後、参加用リンクを取り、kotenocr.ldas.jp に Android 節（APK + テスター募集）を作る
- 本人の決定（2026-11-02）: Play のテスターは Google グループに切り替え、メールのリストからはオプトイン済みの 3 人だけを移す。ただし Play Console には誰がオプトインしたかが出ないため、3 人のアドレスは本人に聞く。次の作業: 3 人をグループに招待 → Alpha のテスターを Google グループ（kotenocr-android-testers@googlegroups.com）に切り替え → 参加用リンクを控える → サイトに Android 節
- 本人の要望: Play の掲載のロゴ（アイコン）や画像も、iOS の朱の「典」に合わせて新しくしたい（アプリ内の起動アイコンも同じ。未着手）

#### 2026-11-02 テスターを Google グループに切替
- Play のテスター欄は「メーリングリスト」か「Google グループ」の**どちらか一方**しか選べない（併用不可）。
- 本人の判断で、旧リスト（はじめてのメーリス、16 人・参加済み 3 人）を外し、`kotenocr-android-testers@googlegroups.com` に切替。参加済みはいったん 0 人から数え直し。
- 2026-11-02 に審査へ送信済み（公開の概要で「審査中」）。
- 参加用リンク: ウェブ https://play.google.com/apps/testing/com.nakamura196.koten_ocr / Android https://play.google.com/store/apps/details?id=com.nakamura196.koten_ocr
- 参加の手順: グループに参加 → ウェブのリンクで「テスターになる」→ Play からインストール。

#### 2026-11-02 2.1.0 を Alpha へ・サイト公開
- モデル（221MB）を install-time のアセットパック `model_pack/` に移した（Play の本体 200MB 上限対策。コードは `context.assets.open("models/...")` のまま）。debug と direct の APK は同じフォルダを普通の assets として取り込む。release は upload 鍵で署名。commit 1e500b8
- AAB（約 248MB、ダウンロード約 223MB）は Chrome 拡張では 10MB 超を上げられないため、本人がドラッグで上げた。リリースノート日英を入れて 6 (2.1.0) を Alpha に。テスターのグループ切替と一緒に審査中（2026-11-02 送信）。警告はデバッグシンボル未アップロードの 1 件のみ
- 紹介サイトに Android 版ページを公開: https://kotenocr.ldas.jp/android.html（英語 android-en.html）。koten-ocr-ios PR #13
- 残り: 審査が通ったら自分の端末でテスト参加 → Play から入るか確認。テスター募集の告知（身近な人・Bluesky・ブログ、集まらなければ X）。ロゴ・掲載画像を iOS の朱の「典」に揃える

#### 2026-11-02 募集ページを絞る・APK を別ページに
- android.html をテスター募集だけに（何のアプリか → お願い → 必要なもの → 3 手順）。APK は apk.html / apk-en.html に分け、サイトからリンクせず noindex。すぐ直接入れたい人にだけ個別に URL を渡す。koten-ocr-ios PR #14
- ファビコンを `<link rel="icon" href="/favicon.ico?v=2.1">` で明示（古いキャッシュが残っていたため）
- 次: 審査が通ったら、未参加の 2 つ目の Google アカウントで 3 手順を端末で通しで操作してもらい、adb screencap で 3 枚（グループに参加 / テスターになる / インストール）を撮って android.html の各手順の下に入れる。参加〜Play インストールの確認も兼ねる。告知文の下書きは scratchpad の tester-recruit-drafts.md（Bluesky 日英・身近な人向け）
- 2026-11-02 時点で審査中（自動点検は終了）

#### 2026-11-02 Play の掲載アイコン・フィーチャーグラフィックを「典」に
- `store/make_play_graphics.py <iOS の AppIcon.png> <出力先>` で icon-512.png と feature-{ja,en}.png（1024×500）を作った。元は koten-ocr-ios の `KotenOCR/Assets.xcassets/AppIcon.appiconset/AppIcon.png`
- Play Console のデフォルト掲載（共通のビジュアルアセット）にアイコンと feature-ja を入れ、**「未公開として保存」まで**。審査に送ると進行中の 2.1.0・テスター切替の審査がやり直しになるので、審査が通ってから「公開の概要」で送る
- 残り: スマートフォンのスクリーンショットが Flutter 時代の古いもの（橙・青・紫）。ネイティブ版の画面を adb で撮って差し替える。英語の掲載に feature-en を当てるか確認
- 本人の了承（2026-11-02）: 審査が通ったら、聞き直さずにそのまま「公開の概要」からアイコン・フィーチャーグラフィックを審査に送ってよい
- 告知文の下書き: `store/tester-recruit-drafts.md`
- 審査の状況は Play Developer API では取れない（審査状態のエンドポイントが無い）ので、Chrome で公開の概要を見る

#### 2026-11-02 審査通過・掲載画像を審査へ・通知とグループ名
- 2.1.0 とテスター切替（送信 6）は 0:45 送信 → 1:05 公開（20 分）。アイコン・フィーチャーグラフィック（送信 7）を審査に送った。AI アセットの申告は「ラベルを付けない」（本人了承）
- 審査中に新しい変更を送ると、前の送信は「キャンセル」になり、まとめて送り直しになる（送信 5 がそうなった）
- Play Console のメール通知「アプリの公開に関する最新情報」をオンにした（宛先 na.kamura.1263@gmail.com）
- グループ名を「KotenOCR Android Testers」に変更（アドレスは同じ）。サイトの android.md の表記も直した（koten-ocr-ios、未コミット）
- ポリシーの問題 1 件: 内部テストに Flutter 版 1.0.2（versionCode 3）が残っており、Billing Library 8 未満で警告。次の修正版を内部テストにも出して置き換える
- グループのメンバーはまだ本人 1 人。旧リストでオプトイン済みの 3 人は未招待（アドレスを本人に聞く）
- 次: 別セッションの不具合修正ができたら versionCode 7 で AAB を作り、本人がドラッグで上げ、Alpha と内部テストに出す

#### 2026-11-02 2.1.1（versionCode 7）を出した
- 枠の編集の修正（PR #1）を入れて versionCode 7 / 2.1.1 に上げ、AAB を作った（単体テスト 29 件すべて通過。ビルドは `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` が要る）
- 本人がドラッグで上げ、Alpha に 7 (2.1.1) を作って審査に送った。審査中だったアイコン・フィーチャーグラフィック（送信 7）は「やり直し」になり、まとめて送り直した
- 内部テストにも同じ 7 をライブラリから追加して公開（内部テストは審査なしで即公開）。Flutter 版 1.0.2（versionCode 3）は外れたので、Billing のポリシー警告が消えるか後で確かめる
- テスト参加ページ（play.google.com/apps/testing/...）のアイコンはストアの掲載アイコンを使うので、審査が通れば新しい「典」に替わる
- 注意: PR #2「OCR の枠の検出と二重検出の除去を iOS に揃える」は AAB を作った後（15:01Z）にマージされたので、出した 7 (2.1.1) には入っていない。入れるには versionCode 8 で作り直して出し直す（本人の判断待ち）
- サイトのグループ名の修正は koten-ocr-ios の PR #17（未マージ）

#### 2026-11-02 2.1.2（versionCode 8）に差し替えた
- PR #2（OCR の枠の検出と二重検出の除去を iOS に揃える）を入れて versionCode 8 / 2.1.2 で AAB を作り直した（単体テスト通過）
- Alpha に 8 (2.1.2) を作って審査に送った（「審査のやり直し」で 7 は取り下げ、アイコン・フィーチャーグラフィックとまとめて送り直し）。内部テストも 8 に差し替えて公開
- 審査の画面に出る警告「ネイティブコードのデバッグシンボルが未アップロード」は消せない。中のネイティブライブラリ（onnxruntime・CameraX など）は配布元で既にシンボルを削った状態で入ってくるため、`ndk { debugSymbolLevel = "SYMBOL_TABLE" }` を付けても取り出すシンボルが無く、AAB に何も入らなかった（2026-11-02 に実測）。審査や配信には影響しない
