# KotenOCR for Android

English | [日本語](README.ja.md)

An Android app that reads classical Japanese cursive script (kuzushiji) and modern printed or handwritten Japanese text. Everything runs on the device: no internet connection is needed for OCR.

It runs the National Diet Library's [NDL Koten OCR-Lite](https://github.com/ndl-lab/ndlkotenocr-lite) and [NDLOCR-Lite](https://github.com/ndl-lab/ndlocr-lite) models with ONNX Runtime. This is the Android counterpart of the iOS app [KotenOCR](https://github.com/nakamura196/koten-ocr-ios).

## Download

- Google Play: not public yet (closed testing). See [how to join the test](https://kotenocr.ldas.jp/android-en.html).
- APK: each version is published on [GitHub Releases](https://github.com/nakamura196/koten-ocr-android/releases).
- Project page: <https://kotenocr.ldas.jp/>

## Features

- Two OCR modes: Classical (kuzushiji) and Modern (printed and handwritten)
- Camera or photo picker, with perspective correction (auto-correct) and automatic deskew
- Results with bounding boxes, a confidence slider, and an interactive box editor (move, resize, add, split, merge, re-read)
- Export as text, TXT or PDF; scan history saved on the device
- Modern-Japanese translation of classical text: cloud API (OpenAI-compatible) that you configure
- Japanese and English UI

## OCR modes

| Mode | Target | Detection | Recognition |
|---|---|---|---|
| Classical | Kuzushiji | RTMDet-S | PARSeq (1 model) |
| Modern | Printed and handwritten | DEIMv2-S | PARSeq cascade (3 models) |

## Requirements

- Android 8.0 (API 26) or later
- To build: JDK 17, Android SDK 36 (Android Studio recommended)

## Build

The ONNX models (about 221 MB) are not in the repository. Put the model files used by the OCR engine into `model_pack/src/main/assets/models/` (git-ignored). They come from the NDL repositories listed under "Models" below; the iOS repository's `setup.sh` downloads the same files from pinned commits.

```bash
git clone https://github.com/nakamura196/koten-ocr-android.git
cd koten-ocr-android

# Debug build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Install on a connected device
./gradlew installDebug
```

Release builds need `keystore.properties` (never committed). Optional: `OPENROUTER_API_KEY` in `local.properties` is embedded in debug builds only, for development of the translation feature.

## Models

| Mode | Model | Source |
|---|---|---|
| Classical | RTMDet-S text detection, PARSeq recognition | [NDL Koten OCR-Lite](https://github.com/ndl-lab/ndlkotenocr-lite) |
| Modern | DEIMv2-S layout detection, PARSeq recognition (30 / 50 / 100 characters) | [NDLOCR-Lite](https://github.com/ndl-lab/ndlocr-lite) |

## License

The code in this repository is released under the [MIT License](LICENSE).

- OCR models and character list: NDL Koten OCR-Lite and NDLOCR-Lite, [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/), National Diet Library, Japan
- ONNX Runtime: [MIT License](https://github.com/microsoft/onnxruntime/blob/main/LICENSE), Microsoft
- Sample page images (`app/src/main/assets/samples/`): see the credits shown in the app. They come from the National Diet Library Digital Collections and the University of Tokyo General Library (public domain, used under the library's reuse terms).

## Citation

See [CITATION.cff](CITATION.cff). GitHub shows a "Cite this repository" button.

## Author

Satoru Nakamura (The University of Tokyo)
Contact: nakamura.satoru@mail.u-tokyo.ac.jp

If you find the app useful, you can support its development via the Sponsor button on this repository.
