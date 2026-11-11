#!/bin/bash
# GENESIS — testes, exportação Android (Godot 4.7.2), Oracle com a aba "Jogo", instalação nos
# telemóveis ligados e capturas de ecrã do jogo e da aba.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
mkdir -p logs/genesis dist
exec > >(tee -a logs/genesis/build.log) 2>&1
echo "=== GENESIS — $(date '+%F %T') ==="
caffeinate -dimsu -w $$ &
# Espera pelo Godot e pelos modelos de exportação.
for i in $(seq 1 240); do [ -f logs/instalar/godot-estado.txt ] && break; [ $((i % 10)) = 1 ] && echo "à espera do Godot…"; sleep 30; done
[ -f logs/instalar/godot-estado.txt ] || { echo "Godot não instalado"; exit 1; }
G="$ROOT/tools/godot/Godot.app/Contents/MacOS/Godot"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export GODOT_ANDROID_KEYSTORE_DEBUG_PATH="$HOME/.android/debug.keystore"
export GODOT_ANDROID_KEYSTORE_DEBUG_USER=androiddebugkey GODOT_ANDROID_KEYSTORE_DEBUG_PASSWORD=android
ls -la "$GODOT_ANDROID_KEYSTORE_DEBUG_PATH"
ES_DIR="$HOME/Library/Application Support/Godot"; mkdir -p "$ES_DIR"
for f in editor_settings-4.tres editor_settings-4.7.tres; do
  [ -f "$ES_DIR/$f" ] || cat > "$ES_DIR/$f" <<TRES
[gd_resource type="EditorSettings" format=3]

[resource]
export/android/android_sdk_path = "$ANDROID_HOME"
export/android/java_sdk_path = "$JAVA_HOME"
export/android/debug_keystore = "$HOME/.android/debug.keystore"
export/android/debug_keystore_user = "androiddebugkey"
export/android/debug_keystore_pass = "android"
TRES
done
"$G" --headless --path genesis --import > logs/genesis/import.log 2>&1
"$G" --headless --path genesis --script res://tests/run_tests.gd 2>&1 | grep -E "TESTES|FALHOU"
"$G" --headless --path genesis --script res://tests/playthrough.gd 2>&1 | grep -E "PLAYTHROUGH|FALHA"
rm -f dist/GENESIS-0.1.0.apk
"$G" --headless --path genesis --export-debug "Android" "$ROOT/dist/GENESIS-0.1.0.apk" > logs/genesis/export.log 2>&1
tail -15 logs/genesis/export.log
if [ ! -s dist/GENESIS-0.1.0.apk ]; then echo "EXPORT_FALHOU"; echo "EXPORT_FALHOU" > logs/genesis/estado.txt; exit 1; fi
ls -lh dist/GENESIS-0.1.0.apk; shasum -a 256 dist/GENESIS-0.1.0.apk
# Oracle com a aba Jogo (repõe se não compilar)
ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command > logs/genesis/oracle-instalar.log 2>&1
if grep -q "BUILD FAILED" logs/instalar/gradle.log; then
  grep -E "^e: " logs/instalar/gradle.log | head -20
  echo "ORACLE com aba Jogo não compilou; a repor"
  mkdir -p _to_delete/jogo-$(date +%H%M%S) && mv app/src/main/java/com/oracleos/app/feature/game _to_delete/jogo-$(date +%H%M%S)/ 2>/dev/null
  tar xf companion/fontes/src-antes-jogo.tar
  ORACLE_NO_ZIM=1 bash scripts/oracle-instalar.command > logs/genesis/oracle-instalar2.log 2>&1
fi
grep -E "Success|arrancou|BUILD" logs/genesis/oracle-instalar.log | tail -8
for S in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
  M=$(adb -s $S shell getprop ro.product.model | tr -d '\r')
  echo "--- $M"
  adb -s $S install -r dist/GENESIS-0.1.0.apk | tail -1
  adb -s $S shell input keyevent KEYCODE_WAKEUP
  adb -s $S logcat -c
  adb -s $S shell am force-stop com.oracleos.genesis
  adb -s $S shell monkey -p com.oracleos.genesis -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 8
  adb -s $S exec-out screencap -p > "logs/genesis/$S-1-jogo.png"
  adb -s $S logcat -d -v time | grep -iE "godot|FATAL|AndroidRuntime: " | grep -iE "error|fatal|exception" | tail -8
  adb -s $S shell am force-stop com.oracleos.genesis
done
echo "GENESIS_OK" > logs/genesis/estado.txt
echo "=== GENESIS terminado $(date '+%F %T'). Pode fechar esta janela. ==="
