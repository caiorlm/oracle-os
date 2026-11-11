#!/bin/bash
# Oracle OS — compila, testa e instala no telemóvel ligado por USB, e copia o mapa e o acervo.
# Corra no Terminal:  bash ~/Desktop/ORACLE-OS/scripts/oracle-instalar.command
# Opções: ORACLE_APPS=1 também instala os APK do Kiwix e do Organic Maps descarregados pelo
#         script de dados; ORACLE_NO_ZIM=1 não copia as coleções ZIM.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOG="$ROOT/logs/instalar"
mkdir -p "$LOG"
exec > >(tee -a "$LOG/instalar.log") 2>&1
echo "=== Oracle instalar — $(date '+%F %T') ==="
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Visual V2.1 preparado em companion/ui-v21: aplica-o; se não compilar, repõe a versão anterior
# e marca-o como FAILED para não bloquear a instalação dos dados.
UI=companion/ui-v21; A=app/src/main/java/com/oracleos/app; APPLIED=0
ui_targets() { echo "$A/ui/OracleTheme.kt $A/ui/Components.kt $A/feature/dashboard/Telemetry.kt $A/feature/dashboard/BootScreen.kt $A/feature/dashboard/DashboardScreen.kt $A/MainActivity.kt"; }
ui_source() { echo "$UI/$(basename "$1")"; }
if [ -d "$UI" ] && [ ! -e "$UI/FAILED" ]; then
  for t in $(ui_targets); do cmp -s "$(ui_source "$t")" "$t" || APPLIED=1; done
  if [ $APPLIED = 0 ] && [ ! -e "$UI/OK" ]; then
    # Already copied by oracle-v21.command but never built: revert to the saved originals if it fails.
    APPLIED=1; BK="$UI/original"; echo "Visual V2.1 já copiado; primeira compilação"
  elif [ $APPLIED = 1 ]; then
    BK="$LOG/ui-backup-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$BK"
    for t in $(ui_targets); do [ -f "$t" ] && cp "$t" "$BK/$(basename "$t")"; done
    mkdir -p "$A/feature/dashboard"
    for t in $(ui_targets); do cp "$(ui_source "$t")" "$t"; done
    echo "Visual V2.1 aplicado (cópia anterior em $BK)"
  fi
fi

echo "--- [1/4] Testes e APK (log: logs/instalar/gradle.log)"
./gradlew --console=plain :knowledge:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug > "$LOG/gradle.log" 2>&1
code=$?
if [ $code -ne 0 ] && [ $APPLIED = 1 ]; then
  grep -E "^e: " "$LOG/gradle.log" | head -20 > "$UI/FAILED"
  cp "$LOG/gradle.log" "$LOG/gradle-v21-falhou.log"
  echo "ATENÇÃO: o visual V2.1 não compilou; a repor a versão anterior (erros em $UI/FAILED)"
  for t in $(ui_targets); do
    if [ -f "$BK/$(basename "$t")" ]; then cp "$BK/$(basename "$t")" "$t"; else rm -f "$t"; fi
  done
  ./gradlew --console=plain :knowledge:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug > "$LOG/gradle.log" 2>&1
  code=$?
elif [ $code -eq 0 ] && [ $APPLIED = 1 ]; then
  date > "$UI/OK"; echo "Visual V2.1 compilado com sucesso"
fi
grep -E "BUILD (SUCCESSFUL|FAILED)|^e: |tests completed|FAILED" "$LOG/gradle.log" | tail -25
if [ $code -ne 0 ]; then echo "ERRO: build falhou (exit $code). Detalhes em logs/instalar/gradle.log"; exit $code; fi
APK="app/build/outputs/apk/debug/app-debug.apk"
ls -l "$APK"
shasum -a 256 "$APK" | tee "$LOG/apk.sha256"

echo "--- [2/4] Telemóvel"
adb start-server >/dev/null 2>&1
DEVICES=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
if [ -z "$DEVICES" ]; then
  adb devices -l
  echo "ERRO: nenhum telemóvel autorizado. Desbloqueie-o, ative a depuração USB e aceite 'Permitir depuração USB'."
  exit 3
fi
# ORACLE_ONLY=<serial> limita a um aparelho.
[ -n "${ORACLE_ONLY:-}" ] && DEVICES=$(echo "$DEVICES" | grep -x "$ORACLE_ONLY")
# Instala em todos os telemóveis autorizados ligados ao Mac, um de cada vez.
for SERIAL in $DEVICES; do
  export ANDROID_SERIAL="$SERIAL"
  MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
  echo "===== Aparelho: $MODEL ($SERIAL) ====="
  adb install -r "$APK" || { echo "ERRO: adb install falhou em $MODEL"; continue; }
  adb shell am start -n com.oracleos.app/.MainActivity >/dev/null
  sleep 4
  if [ "${ORACLE_APPS:-0}" = "1" ]; then
    for apk in downloads/library/apps/*.apk; do [ -f "$apk" ] && { echo "a instalar $(basename "$apk")"; adb install -r "$apk"; }; done
  fi
  echo "--- [3/4] Pacotes em $MODEL (log: logs/instalar/pacotes.log)"
  ZIM_FLAG=""; [ "${ORACLE_NO_ZIM:-0}" = "1" ] && ZIM_FLAG="--no-zim"
  python3 scripts/phone_install.py --serial "$SERIAL" $ZIM_FLAG 2>&1 | tee -a "$LOG/pacotes.log"
  echo "--- [4/4] Reiniciar o Oracle em $MODEL e recolher erros"
  adb logcat -c
  adb shell am force-stop com.oracleos.app
  adb shell am start -n com.oracleos.app/.MainActivity >/dev/null
  sleep 20
  adb logcat -d -v time 2>/dev/null | grep -E "com\.oracleos|AndroidRuntime|FATAL|Mbgl|maplibre" | tail -200 > "$LOG/logcat-$SERIAL.txt"
  if grep -q "FATAL EXCEPTION" "$LOG/logcat-$SERIAL.txt"; then echo "ATENÇÃO: o app falhou ao arrancar em $MODEL"; else echo "Oracle arrancou sem erros fatais em $MODEL."; fi
done
unset ANDROID_SERIAL
echo "=== Concluído $(date '+%F %T'). No telemóvel: Mapa → Abrir mapa; Acervo → pesquisar. ==="
