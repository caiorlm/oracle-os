#!/bin/bash
# Oracle OS — recompila o APK com os ajustes da introdução e volta a filmar nos telemóveis ligados.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --console=plain :app:assembleDebug > logs/instalar/gradle-intro2.log 2>&1 || { echo "BUILD FAILED" >> logs/instalar/intro-teste.log; grep -E "^e: " logs/instalar/gradle-intro2.log >> logs/instalar/intro-teste.log; exit 1; }
rm -f logs/instalar/intro/*.png
exec bash scripts/oracle-intro-teste.command
