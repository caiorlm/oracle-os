#!/usr/bin/env python3
"""Opens Oracle OS on the connected phone, walks to the map and the library, and saves screenshots.

Taps elements by their visible text (uiautomator dump), so it does not depend on screen size.
If Android asks for location permission for Oracle OS, it chooses "while using the app".
Screenshots go to logs/instalar/check-*.png. Nothing else on the phone is touched.
"""

from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "logs/instalar"
PKG = "com.oracleos.app"


def adb(*args: str) -> str:
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def shot(name: str) -> None:
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    prefix = __import__("os").environ.get("ORACLE_SHOT_PREFIX", "")
    (OUT / f"check-{prefix}{name}.png").write_bytes(data)
    print("captura", name, flush=True)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/oracle-ui.xml")
    xml = adb("shell", "cat", "/sdcard/oracle-ui.xml")
    try:
        root = ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
    except Exception:
        return []
    return list(root.iter("node"))


def tap_text(pattern: str, swipes: int = 0) -> bool:
    regex = re.compile(pattern, re.I)
    for attempt in range(swipes + 1):
        for node in nodes():
            label = (node.get("text") or "") + " " + (node.get("content-desc") or "")
            if regex.search(label):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                print("toque:", label.strip()[:60], flush=True)
                return True
        if attempt < swipes:
            adb("shell", "input", "swipe", "360", "1200", "360", "500", "300")
            time.sleep(1)
    print("não encontrado:", pattern, flush=True)
    return False


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(1.2)
    shot("0-arranque")
    time.sleep(4)
    shot("0b-inicio")
    tap_text(r"^\s*Mapa\b")
    time.sleep(3)
    if tap_text(r"Permitir localização"):
        for _ in range(4):  # the system dialog can take a moment to appear
            time.sleep(2)
            if tap_text(r"(While using|Durante a utiliza|Enquanto|Ao utilizar|Apenas durante|Only this time)"):
                break
        time.sleep(4)
    time.sleep(20)  # pack verification of 1.5 GB and the first GPS fix
    shot("1-aba-mapa")
    if tap_text(r"Abrir mapa com (a )?minha posição", swipes=2):
        time.sleep(12)
        shot("2-mapa")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
    tap_text(r"^\s*Acervo\b")
    time.sleep(25)  # first open copies and attaches the verified library database
    shot("3-acervo")
    if tap_text(r"Pesquisar"):
        time.sleep(1)
        adb("shell", "input", "text", "water%spurification")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
        tap_text(r"^\s*Buscar\s*$")
        time.sleep(6)
        shot("3b-pesquisa")
        if tap_text(r"página \d+", swipes=1):
            time.sleep(6)
            shot("3c-pdf")
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(2)
    tap_text(r"^\s*Início\b")
    tap_text(r"Emergência|Abrir ferramentas")
    time.sleep(2)
    shot("4-emergencia")
    adb("shell", "input", "swipe", "540", "1700", "540", "900", "400")
    time.sleep(4)
    shot("4b-posicao")
    if tap_text(r"Abrir rádio e frequências", swipes=2):
        time.sleep(2)
        shot("6-radio")
        if tap_text(r"^\s*Abrir rádio FM\s*$"):
            time.sleep(4)
            shot("6b-fm")
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(2)
    tap_text(r"^\s*Sistema\b")
    time.sleep(2)
    adb("shell", "input", "swipe", "540", "1700", "540", "700", "400")
    time.sleep(1)
    shot("5-sistema")
    return 0


if __name__ == "__main__":
    sys.exit(main())
