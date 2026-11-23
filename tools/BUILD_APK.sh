#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v gradle >/dev/null; then
  echo 'Gradle nem elérhető. Android Studio: Build > Build APK(s).' >&2
  exit 2
fi
gradle --no-daemon :app:assembleDebug
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
