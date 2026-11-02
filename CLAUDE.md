# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KotenOCR Android is a native Kotlin + Jetpack Compose app (`v2.1.0`, build `6`) for recognizing classical Japanese texts (kuzushiji / くずし字) and modern Japanese texts (printed / handwritten) on-device using ONNX Runtime. It implements feature and design parity with the iOS v2.1 app (`koten-ocr-ios`).

- **Package**: `com.nakamura196.kotenocr` (debug suffix: `.debug`)
- **Min SDK**: 26 (Android 8.0) / **Target & Compile SDK**: 36
- **Language**: Kotlin 2.1.0, JVM 17
- **UI**: Jetpack Compose (BOM 2024.12.01) with the KotenOCR v2 "Washi / Ink / Shu (和紙・墨・朱)" design system (`KotenColor`, `KotenStyle.kt`)

## Build & Run Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Install debug APK on connected device
./gradlew installDebug
```

## Architecture

### State Management & Navigation

Single-Activity (`MainActivity`) with custom state-based navigation driven by `AppState`. There is no Jetpack Navigation graph — screen transitions are managed via `AppState.screen: StateFlow<Screen>` and a `when` block in `MainActivity`.

Screen flow:
`LAUNCH` → `ONBOARDING` (first run) → `HOME` → `CAMERA` (or Photo Picker / `SampleSheet`) → `CROP` → `CONFIRM_CROP` → `PROCESSING` → `RESULT`
Side screens reachable from `HOME`, `CAMERA`, or `RESULT`: `HISTORY`, `SETTINGS` → `TRANSLATION_SETTINGS`

### OCR Pipeline (`ocr/` package)

`OCREngine` orchestrates dual-mode on-device inference on `Dispatchers.Default`:

1. **Global Auto-Deskew (`Deskew.kt`)**:
   - Estimates small-angle page skew (`±10°`, coarse `0.5°` + fine `0.1°`) using an integral-image local-mean threshold and projection-profile variance maximization.
   - Verifies consistency across 3 strips (`stripsAgree`) so pages with 2D perspective fan are left to per-line tilt correction.
   - Controlled by `SettingsManager.autoDeskew` (`"autoDeskew"`, default `true`).

2. **Classical Mode (`OCRMode.KOTEN`, NDL古典籍OCR-Lite)**:
   - **Layout Detection (`RTMDetector.kt`)**: `rtmdet-s-1280x1280.onnx` (`maxDetections = 1000`).
   - **Per-Line Tilt & Cropping (`LineTilt.kt`)**: Estimates column lean (`±6°`, step `0.25°`) for vertical lines (`h >= 3*w`, `h >= 48`), applies bilinear `orientedCrop`, and falls back to plain axis-aligned crop if `>= 3` placeholder glyphs (`□`, `〓`, ``) appear.
   - **Character Recognition (`PARSeqRecognizer.kt`)**: `parseq-ndl-32x384-tiny-10.onnx` + `NDLmoji.yaml`, 4-way parallel batching. Near-square boxes (`aspect < 1.5`) follow page-majority direction (`OCREngine.isVerticalPage` / `readsVertically`).
   - **Post-Filtering**: Drops placeholder-only lines and same-column overlapping duplicates (`LineTilt.dropSameLineDuplicates`).

3. **Modern Mode (`OCRMode.NDL`, NDLOCR-Lite)**:
   - **Layout Detection (`DEIMDetector.kt`)**: `deim-s-1024x1024.onnx` + `ndl-deim.yaml` (`confThreshold = 0.25f`, `maxDetections = 1000`, coordinate truncation toward zero, `Int64`/`Float` `char_counts` decoding, line-only NMS).
   - **Cascade Recognition (`CascadePARSeqRecognizer.kt`)**: Routes lines by `predCharCount` across `parseq-ndl-16x256-30-tiny-192epoch-tegaki3.onnx` (`<=30` chars), `parseq-ndl-16x384-50-tiny-146epoch-tegaki2.onnx` (`<=50` chars), and `parseq-ndl-16x768-100-tiny-165epoch-tegaki2.onnx` (`100` chars).

4. **Reading Order (`ReadingOrder.kt`)**:
   - XY-cut algorithm (`ReadingOrderProcessor`) with aspect-ratio grid cap (`maxAspect = 20`) and local column grouping (`sortLeafLines`) for fragmented vertical columns.

### v2 UI & Utilities

- **`ui/theme/Theme.kt` & `ui/theme/KotenStyle.kt`**: Light/Dark `KotenColor` palette (`washi`, `washiCard`, `ink`, `inkSecondary`, `shu`, `shuFill`, `mount`, `rule`, `docBackground`), Mincho/Serif typography, `WashiBackground`, `kotenMount`, `KotenSeal`, `KotenStamp`, `ScanCorners`, `KotenProminentButton`, `KotenGlassButton`, `CoachBubble`.
- **`util/PerspectiveCrop.kt`**: 4-corner `Quad` homography warp (`rectify`), automatic page quad detection (`detectPage`, `detectForCropScreen`), and `adoptDetected`.
- **`util/ImageIntake.kt`**: EXIF orientation normalization + 4096px long-side downsampling and 90° clockwise rotation.
- **`util/TiltMonitor.kt`**: Gravity/accelerometer camera level monitor (`Flat`, `Upright`, `Tilted`).
- **`ui/screens/BoxEdit.kt`**: Interactive Box Editor (`Select`/Move/Resize, `Add`, `Split`, `Merge`, `Undo`, `Delete`, `Re-read` pending boxes).
- **`ocr/ConfidenceFilter.kt`**: Display-only confidence slider filter (`0.25..0.90`, default `0.30`).
