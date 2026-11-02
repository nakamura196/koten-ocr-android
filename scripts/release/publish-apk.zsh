#!/usr/bin/env zsh
# Publish the direct-distribution APK to GitHub Releases.
#
# Prerequisites: gh CLI logged in; APK built with `./gradlew assembleDirect`.
# Usage: zsh scripts/release/publish-apk.zsh <notes.md>
# What it does:
#   1. Creates the public repo nakamura196/koten-ocr-android if it doesn't exist, and pushes main
#   2. Creates release v<versionName> with the APK attached as KotenOCR-<versionName>.apk
set -euo pipefail

cd "${0:A:h}/../.."
NOTES=${1:?release notes file required}
REPO=nakamura196/koten-ocr-android
VER=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
SRC=app/build/outputs/apk/direct/app-direct.apk
[[ -f $SRC ]] || { echo "APK not found: $SRC (run ./gradlew assembleDirect)"; exit 1; }

if ! gh repo view $REPO >/dev/null 2>&1; then
  gh repo create $REPO --public --source=. --remote=origin --description "KotenOCR for Android — on-device OCR for classical and modern Japanese texts"
fi
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/$REPO.git
git push -u origin main

TMP=$(mktemp -d)
cp $SRC $TMP/KotenOCR-$VER.apk
gh release create v$VER $TMP/KotenOCR-$VER.apk --repo $REPO --title "KotenOCR for Android $VER" --notes-file $NOTES
rm -rf $TMP

echo "Done: https://github.com/$REPO/releases/tag/v$VER"
