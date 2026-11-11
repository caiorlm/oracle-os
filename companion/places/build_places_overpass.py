#!/usr/bin/env python3
"""Builds the signed pack oracle-places-<version>: an offline SQLite index of help points from
OpenStreetMap (hospitals, clinics, pharmacies, drinking water, shelters, police, fire stations,
defibrillators, assembly points) for the countries listed below. Data © OpenStreetMap contributors (ODbL).

Data comes from the Overpass API (run on the Mac while online). Each country/category query is cached
in downloads/places/, so a rerun only fetches what failed."""
import json, sqlite3, subprocess, sys, time, urllib.parse, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "downloads/places"
# Country boxes (south, west, north, east); each is queried in 1° tiles so every request stays small.
REGIONS = {
    "GB": [(49.8, -8.7, 60.9, 1.8)],
    "IE": [(51.4, -10.7, 55.4, -5.9)],
    "PT": [(36.9, -9.6, 42.2, -6.1), (32.6, -17.3, 33.2, -16.2), (36.9, -31.3, 39.8, -24.9)],
    "ES": [(35.9, -9.4, 43.8, 4.4), (27.6, -18.2, 29.5, -13.4)],
    "FR": [(41.3, -5.2, 51.1, 9.6)],
}
COUNTRIES = list(REGIONS)
SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter",
           "https://overpass.kumi.systems/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]
SELECTORS = [
    'nwr["amenity"~"^(hospital|clinic|doctors|pharmacy|drinking_water|water_point|police|fire_station)$"]',
    'nwr["healthcare"="hospital"]', 'nwr["social_facility"="shelter"]',
    'nwr["emergency"~"^(assembly_point|defibrillator)$"]',
    'nwr["man_made"="water_tap"]["drinking_water"="yes"]', 'nwr["natural"="spring"]["drinking_water"="yes"]',
    'nwr["amenity"="shelter"]["shelter_type"~"^(basic_hut|lean_to|rock_shelter|weather_shelter)$"]',
    'nwr["tourism"~"^(alpine_hut|wilderness_hut)$"]',
]
CATEGORIES = ["hospital", "clinic", "pharmacy", "water", "shelter", "police", "fire", "aed"]
UA = "OracleOS-places/1.0 (offline survival app)"


def log(m): print(f"{datetime.now():%H:%M:%S} {m}", flush=True)


def categories(tags: dict) -> list:
    a = tags.get("amenity")
    out = []
    if a == "hospital" or tags.get("healthcare") == "hospital": out.append("hospital")
    if a in ("clinic", "doctors"): out.append("clinic")
    if a == "pharmacy": out.append("pharmacy")
    if a in ("drinking_water", "water_point") or (tags.get("drinking_water") == "yes" and
            (tags.get("man_made") == "water_tap" or tags.get("natural") == "spring")): out.append("water")
    if (tags.get("social_facility") == "shelter" or tags.get("emergency") == "assembly_point" or a == "shelter"
            or tags.get("tourism") in ("alpine_hut", "wilderness_hut")): out.append("shelter")
    if a == "police": out.append("police")
    if a == "fire_station": out.append("fire")
    if tags.get("emergency") == "defibrillator": out.append("aed")
    return out


def tiles(box):
    south, west, north, east = box
    lat = south
    while lat < north:
        lon = west
        while lon < east:
            yield (round(lat, 2), round(lon, 2), round(min(lat + 1, north), 2), round(min(lon + 1, east), 2))
            lon += 1
        lat += 1


def fetch(tile, first: int = 0) -> list:
    cache = CACHE / "tiles" / ("%s_%s_%s_%s.json" % tile)
    if cache.exists():
        return json.loads(cache.read_text())["elements"]
    bbox = "(%s,%s,%s,%s)" % tile
    query = "[out:json][timeout:180];(" + "".join(sel + bbox + ";" for sel in SELECTORS) + ");out center tags;"
    for attempt in range(1, 9):
        server = SERVERS[(first + attempt - 1) % len(SERVERS)]
        try:
            req = urllib.request.Request(server, data=urllib.parse.urlencode({"data": query}).encode(),
                                         headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=240) as r:
                data = json.loads(r.read())
            if "error" in data.get("remark", "").lower() or "timed out" in data.get("remark", "").lower():
                raise IOError(data["remark"][:120])
            cache.parent.mkdir(parents=True, exist_ok=True)
            cache.write_text(json.dumps(data))
            return data["elements"]
        except Exception as e:
            log(f"  {tile} tentativa {attempt} ({urllib.parse.urlparse(server).netloc}): {e}")
            time.sleep(min(60, 5 * attempt))
    raise IOError(f"{tile} falhou")


def info(tags: dict, category: str) -> str:
    parts = []
    if category == "hospital" and tags.get("emergency") == "yes":
        parts.append("A&E/urgência")
    for key in ("opening_hours", "phone", "contact:phone", "addr:street", "addr:city", "description", "access"):
        if tags.get(key):
            value = tags[key] if key != "addr:street" else (tags.get("addr:housenumber", "") + " " + tags[key]).strip()
            parts.append(value)
    if category == "water" and tags.get("seasonal") == "yes":
        parts.append("sazonal")
    return " · ".join(dict.fromkeys(parts))[:240]


def main() -> int:
    out_dir = ROOT / "downloads/places/build"
    out_dir.mkdir(parents=True, exist_ok=True)
    db_path = out_dir / "oracle-places.db"
    db_path.unlink(missing_ok=True)
    db = sqlite3.connect(db_path)
    db.execute("CREATE TABLE place(category TEXT NOT NULL, name TEXT, lat REAL NOT NULL, lon REAL NOT NULL,"
               " country TEXT, info TEXT, osm TEXT)")
    seen, failures, total = set(), [], 0
    # Fetch every tile first, four at a time, each worker starting on a different server.
    from concurrent.futures import ThreadPoolExecutor
    all_tiles = [t for boxes in REGIONS.values() for box in boxes for t in tiles(box)]
    todo = [t for t in all_tiles if not (CACHE / "tiles" / ("%s_%s_%s_%s.json" % t)).exists()]
    log(f"{len(all_tiles)} quadrículas, {len(todo)} por descarregar")
    done = [0]
    def grab(job):
        index, tile = job
        try:
            fetch(tile, index % len(SERVERS))
        except Exception:
            pass
        done[0] += 1
        if done[0] % 20 == 0:
            log(f"  {done[0]}/{len(todo)} quadrículas")
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(grab, enumerate(todo)))
    for country, boxes in REGIONS.items():
        rows = []
        for box in boxes:
            for tile in tiles(box):
                try:
                    elements = fetch(tile)
                except Exception as e:
                    failures.append(str(e)); continue
                for el in elements:
                    lat = el.get("lat", el.get("center", {}).get("lat"))
                    lon = el.get("lon", el.get("center", {}).get("lon"))
                    if lat is None or lon is None:
                        continue
                    tags = el.get("tags", {})
                    for category in categories(tags):
                        key = (el["type"], el["id"], category)
                        if key in seen:
                            continue
                        seen.add(key)
                        name = tags.get("name") or tags.get("operator") or ""
                        rows.append((category, name[:120], round(lat, 6), round(lon, 6), country, info(tags, category),
                                     f"{el['type'][0]}{el['id']}"))
        db.executemany("INSERT INTO place VALUES(?,?,?,?,?,?,?)", rows)
        db.commit()
        total += len(rows)
        counts = {c: sum(1 for r in rows if r[0] == c) for c in CATEGORIES}
        log(f"{country}: {len(rows)} locais {counts}")
    db.execute("CREATE INDEX place_lat ON place(lat, lon)")
    db.execute("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)")
    db.executemany("INSERT INTO meta VALUES(?,?)", [
        ("countries", ",".join(COUNTRIES)), ("builtAt", datetime.now(timezone.utc).isoformat()),
        ("license", "Data © OpenStreetMap contributors, ODbL 1.0"), ("count", str(total))])
    db.commit(); db.execute("VACUUM"); db.close()
    log(f"Total {total} locais · {db_path.stat().st_size / 1e6:.1f} MB · falhas: {len(failures)}")
    lic = out_dir / "OSM-ODbL.txt"
    lic.write_text("Places data © OpenStreetMap contributors.\nAvailable under the Open Database License (ODbL) 1.0:"
                   " https://opendatacommons.org/licenses/odbl/1-0/\nhttps://www.openstreetmap.org/copyright\n")
    if total == 0:
        return 1
    version = datetime.now(timezone.utc).strftime("%Y.%m.%d.%H%M")
    plan = {"packId": "oracle-places", "version": version, "minAndroidApi": 28, "keyId": "oracle-dev-2026",
            "packages": [{"id": "places-db", "type": "places", "source": str(db_path), "path": "places/oracle-places.db"},
                         {"id": "places-license", "type": "license", "source": str(lic), "path": "licenses/OSM-ODbL.txt"}]}
    plan_path = out_dir / "plan.json"
    plan_path.write_text(json.dumps(plan, indent=2))
    out = ROOT / f"oracle-ssd/oracle-places-{version}"
    subprocess.run([sys.executable, str(ROOT / "companion/desktop-pack-builder/pack_builder.py"), "build", "--plan", str(plan_path),
                    "--source-root", str(ROOT), "--output", str(out), "--private", str(ROOT / ".oracle-keys/dev-private.pem")], check=True)
    log(f"Pacote assinado: {out}")
    if failures:
        log("Falharam " + str(len(failures)) + " quadrículas: " + "; ".join(failures[:5]))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
