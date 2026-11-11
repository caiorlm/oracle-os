extends Node
## GENESIS game state, simulation and persistence. The clock is the wall clock: one virtual hour is
## six real minutes and it keeps running while the app is closed; on return the missed hours are
## simulated hour by hour (deterministic, no animation frames replayed).

signal changed
signal message(text: String)
signal lineage_event(kind: String)

const SAVE_PATH := "user://genesis_save.json"
const BACKUP_PATH := "user://genesis_save.bak"
const VERSION := 1
const NEEDS := ["health", "hunger", "hydration", "energy", "hygiene"]
const PROFESSIONS := ["farming", "hunting", "carpentry", "building", "healing", "exploring", "fishing", "trading"]
const FOOD := {"berries": 8}

var state: Dictionary = {}
var english := false
var last_offline_report := ""
var _autosave := 0.0
var _tick := 0.0

func _ready() -> void:
	english = false  # Portuguese by default; "Português / English" in Settings (or on the first screen)
	load_game()
	if state.has("settings"):
		english = bool(state["settings"].get("english", false))

func has_game() -> bool:
	return state.has("player") and not state.get("lineage", {}).get("ended", false)

func t(pt: String, en: String) -> String:
	return en if english else pt

# ---------------------------------------------------------------- clock
func now() -> float:
	return Time.get_unix_time_from_system()

func elapsed() -> float:
	return now() - float(state["clock"]["epoch_unix"])

func day() -> int:
	return Rules.game_day(elapsed())

func hour_of_day() -> int:
	return Rules.game_hours(elapsed()) % Rules.HOURS_PER_DAY

func minute_of_hour() -> int:
	return int(fmod(maxf(elapsed(), 0.0), Rules.REAL_SECONDS_PER_GAME_HOUR) / 6.0)

func season() -> int:
	return Rules.season_of_day(day())

func season_name(s: int = -1) -> String:
	var i := season() if s < 0 else s
	return [t("Primavera", "Spring"), t("Verão", "Summer"), t("Outono", "Autumn"), t("Inverno", "Winter")][i]

func raining(d: int = -1, h: int = -1) -> bool:
	var dd := day() if d < 0 else d
	var hh := hour_of_day() if h < 0 else h
	var rng := RandomNumberGenerator.new()
	rng.seed = hash("%s-%d" % [state["player"]["id"], dd])
	var chance: float = [0.35, 0.15, 0.3, 0.2][Rules.season_of_day(dd)]
	if rng.randf() >= chance:
		return false
	var start := rng.randi_range(0, 18)
	return hh >= start and hh < start + rng.randi_range(2, 6)

# ---------------------------------------------------------------- new game
func new_game(name: String, gender: String, age_band: String, creature_name: String, r: PackedByteArray, ts: int = 0) -> void:
	if ts <= 0:
		ts = int(now())
	var id := Identity.make_id(name, gender, ts, r)
	var archive: Array = state.get("lineage", {}).get("archive", []) if state.has("lineage") else []
	state = {
		"version": VERSION,
		"clock": {"epoch_unix": now() - 6 * Rules.REAL_SECONDS_PER_GAME_HOUR, "sim_hour": 6},  # day 0 starts at 06:00
		"lineage": {"id": id, "generation": 1, "recoveries_left": Rules.MAX_RECOVERIES, "ended": false, "archive": archive},
		"player": {
			"id": id, "name": name, "gender": gender, "age_band": age_band, "created_unix": ts, "born_day": 0,
			"genome": Identity.visual_genome(id),
			"x": 13, "y": 12, "dir": 0,
			"needs": {"health": 100.0, "hunger": 100.0, "hydration": 100.0, "energy": 100.0, "hygiene": 100.0},
			"attrs": _attrs(gender),
			"xp": {}, "counts": {"day": 0, "by": {}},
			"rest_until_hour": 0,
		},
		"creature": {
			"id": Identity.derive(id, "creature").hex_encode(), "name": creature_name, "born_day": 0,
			"genome": Identity.creature_genome(id),
			"needs": {"health": 100.0, "hunger": 100.0, "hydration": 100.0, "energy": 100.0, "hygiene": 100.0, "happiness": 100.0},
		},
		"egg": {},
		"reserve": {"nutrition": 0.0, "hydration": 0.0},
		"carry": {},
		"inventory": {"water": 5, "seed_lettuce": 3, "berries": 3},
		"selected_seed": "seed_lettuce",
		"plots": {"10,17": _plot(), "11,17": _plot(), "12,17": _plot()},
		"bushes": {}, "trees": {},
		"quests": {"farm_001": {"state": "new", "plant": 0, "water": 0, "harvest": 0},
			"water_002": {"state": "locked", "fill": 0, "water": 0}},
		"discovered": [],
		"settings": {"ui_mode": {"5-10": "child", "11-17": "teen"}.get(age_band, "adult"), "english": english},
		"log": [],
	}
	for p in PROFESSIONS:
		state["player"]["xp"][p] = 0.0
	_log(t("Nasceu %s, com %s.", "%s was born, with %s.") % [name, creature_name])
	save_game()
	changed.emit()

func _attrs(gender: String) -> Dictionary:
	# Small, balanced conventions of the game (GDD §4), not claims about real people.
	if gender == "male":
		return {"strength": 52, "speed": 52, "gathering": 50, "recovery": 50, "intelligence": 50, "learning": 50}
	return {"strength": 50, "speed": 50, "gathering": 52, "recovery": 52, "intelligence": 50, "learning": 50}

func _plot() -> Dictionary:
	return {"crop": "", "growth": 0.0, "moisture": 40.0, "dry_hours": 0, "fertility": 0.85, "dead": false}

# ---------------------------------------------------------------- simulation
func simulate_to_now(offline_report: bool = false) -> void:
	if not has_game():
		return
	var target := Rules.game_hours(elapsed())
	var from := int(state["clock"]["sim_hour"])
	if target <= from:
		return  # nothing new, or the device clock went backwards: never simulate negative time
	var count := target - from
	var offline := count > 2
	var skipped := 0
	if count > Rules.MAX_CATCHUP_HOURS:
		skipped = count - Rules.MAX_CATCHUP_HOURS
		from = target - Rules.MAX_CATCHUP_HOURS
	var before := (state["player"]["needs"] as Dictionary).duplicate()
	var events := []
	for h in range(from, target):
		var ev := step_hour(h, offline)
		if ev != "":
			events.append(ev)
		if not has_game():
			break
	state["clock"]["sim_hour"] = target
	if offline and offline_report:
		var hours := count
		last_offline_report = t("Enquanto esteve fora passaram %d horas do jogo (%.1f dias).", "While you were away %d game hours passed (%.1f days).") % [hours, hours / 24.0]
		if skipped > 0:
			last_offline_report += t(" Ausência muito longa: só os últimos 120 dias foram simulados.", " Very long absence: only the last 120 days were simulated.")
		for e in events.slice(0, 4):
			last_offline_report += "\n• " + e
	changed.emit()

## One virtual hour. Returns a short event text when something notable happened.
func step_hour(h: int, offline: bool) -> String:
	var d := int(h / Rules.HOURS_PER_DAY)
	var hod := h % Rules.HOURS_PER_DAY
	var s := Rules.season_of_day(d)
	var rain := raining(d, hod)
	var event := ""
	var p: Dictionary = state["player"]
	var n: Dictionary = p["needs"]
	var heat := 1.3 if s == 1 else 1.0
	n["hunger"] = maxf(0.0, n["hunger"] - 1.6)
	n["hydration"] = maxf(0.0, n["hydration"] - 2.2 * heat)
	n["hygiene"] = maxf(0.0, n["hygiene"] - 0.7)
	var asleep := offline and (hod >= 23 or hod < 7)
	n["energy"] = clampf(n["energy"] + (5.0 if asleep else -1.1), 0.0, 100.0)
	if offline:
		_auto_care()
	var dmg := Rules.negligence_damage(n["hydration"], n["hunger"], n["energy"])
	if offline:
		dmg *= 0.25
	var regen := 0.6 if n["hunger"] > 50 and n["hydration"] > 50 and n["energy"] > 30 else 0.0
	var floor_hp := 10.0 if offline else 0.0  # nobody dies while the phone is in a drawer
	n["health"] = clampf(n["health"] - dmg + regen, floor_hp, 100.0)
	# creature
	var c: Dictionary = state["creature"]
	var cn: Dictionary = c["needs"]
	cn["hunger"] = maxf(0.0, cn["hunger"] - 2.0)
	cn["hydration"] = maxf(0.0, cn["hydration"] - 2.4 * heat)
	cn["hygiene"] = maxf(0.0, cn["hygiene"] - 0.9)
	cn["happiness"] = maxf(0.0, cn["happiness"] - (0.6 if offline else 1.0))
	cn["energy"] = clampf(cn["energy"] + (4.0 if (hod >= 22 or hod < 7) else -1.0), 0.0, 100.0)
	var cdmg := Rules.negligence_damage(cn["hydration"], cn["hunger"], cn["energy"]) * (0.25 if offline else 1.0)
	var cregen := 0.6 if cn["hunger"] > 50 and cn["hydration"] > 50 else 0.0
	cn["health"] = clampf(cn["health"] - cdmg + cregen, 5.0, 100.0)  # the companion falls ill, never dies of neglect
	# crops
	var light := Crops.light_factor(hod)
	for key in state["plots"].keys():
		var plot: Dictionary = state["plots"][key]
		var dry := 3.2 if s == 1 else (1.2 if s == 3 else 2.4)
		plot["moisture"] = clampf(plot["moisture"] + (12.0 if rain else -dry), 0.0, 100.0)
		if plot["crop"] == "" or plot["dead"]:
			continue
		var crop: String = plot["crop"]
		if plot["growth"] < Crops.required(crop):
			plot["growth"] += Rules.growth(Crops.G0, 1.0, Crops.water_factor(plot["moisture"]),
				Crops.temperature_factor(crop, s), light, plot["fertility"])
		plot["dry_hours"] = plot["dry_hours"] + 1 if plot["moisture"] < 5.0 else 0
		if plot["dry_hours"] >= 48:
			plot["dead"] = true
			event = t("Uma planta de %s secou por falta de água.", "A %s plant dried out.") % crop_name(crop)
	# egg
	var egg: Dictionary = state["egg"]
	if egg.is_empty() and d >= Rules.EGG_DAY:
		_create_egg(d)
		event = t("Apareceu o ovo da linhagem!", "The lineage egg has appeared!")
	elif not egg.is_empty() and egg["state"] == "incubating":
		var r: Dictionary = state["reserve"]
		var take_n := minf(r["nutrition"], 0.4)
		var take_h := minf(r["hydration"], 0.4)
		r["nutrition"] -= take_n
		r["hydration"] -= take_h
		egg["on"] = clampf(egg["on"] + take_n * 2.5 - 0.01, 0.0, 100.0)
		egg["oh"] = clampf(egg["oh"] + take_h * 2.5 - 0.01, 0.0, 100.0)
		var care := 0.3 + 0.7 * minf(egg["on"], egg["oh"]) / 100.0
		egg["od"] = clampf(egg["od"] + 100.0 / ((Rules.HATCH_DAY - Rules.EGG_DAY) * 24.0) * care, 0.0, 100.0)
		if d >= Rules.HATCH_DAY and Rules.egg_progress(egg["on"], egg["oh"], egg["od"]) >= Rules.EGG_P_MIN:
			egg["state"] = "hibernating"
			event = t("O ovo eclodiu: um descendente está a hibernar, pronto para continuar a linhagem.", "The egg hatched: a descendant is hibernating, ready to carry on the lineage.")
	# daily counters reset
	if hod == 0:
		p["counts"] = {"day": d, "by": {}}
	# end of life
	if d - int(p["born_day"]) >= Rules.LIFESPAN_DAYS:
		_death("age")
		return t("%s chegou ao fim natural da vida." , "%s reached the natural end of life.") % p["name"]
	if n["health"] <= 0.0:
		_death("health")
		return t("A saúde chegou a zero.", "Health reached zero.")
	return event

func _auto_care() -> void:
	var inv: Dictionary = state["inventory"]
	var n: Dictionary = state["player"]["needs"]
	var cn: Dictionary = state["creature"]["needs"]
	if n["hydration"] < 45.0 and inv.get("water", 0) > 0:
		_take("water", 1); n["hydration"] = minf(100.0, n["hydration"] + 30.0)
	if cn["hydration"] < 45.0 and inv.get("water", 0) > 0:
		_take("water", 1); cn["hydration"] = minf(100.0, cn["hydration"] + 35.0)
	for who in [n, cn]:
		if who["hunger"] < 45.0:
			var food := best_food()
			if food != "":
				_take(food, 1)
				who["hunger"] = minf(100.0, who["hunger"] + food_value(food))

func _create_egg(d: int) -> void:
	var g: Dictionary = state["creature"]["genome"]
	var other := Identity.creature_genome(Identity.random_bytes(32).hex_encode())
	var egg_id := Identity.derive(state["player"]["id"], "egg-%d" % d).hex_encode()
	state["egg"] = {"id": egg_id, "created_day": d, "state": "incubating", "on": 0.0, "oh": 0.0, "od": 0.0,
		"genome": Identity.mix_genome(g, other, hash(egg_id)),
		"heir_genome": Identity.mix_genome(state["player"]["genome"], Identity.visual_genome(Identity.random_bytes(32).hex_encode()), hash(egg_id + "heir"))}

func _death(cause: String) -> void:
	var p: Dictionary = state["player"]
	var lin: Dictionary = state["lineage"]
	lin["archive"].append({"name": p["name"], "id": p["id"], "generation": lin["generation"], "died_day": day(), "cause": cause})
	var egg: Dictionary = state["egg"]
	if not egg.is_empty() and egg["state"] == "hibernating":
		# A viable descendant continues the lineage with a new, independent identity.
		lin["generation"] += 1
		var heir_id := Identity.derive(egg["id"], "heir").hex_encode()
		p["id"] = heir_id
		p["name"] = "%s %s" % [String(p["name"]).split(" ")[0], ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"][mini(int(lin["generation"]), 10)]]
		p["genome"] = egg["heir_genome"]
		p["born_day"] = day()
		p["needs"] = {"health": 100.0, "hunger": 80.0, "hydration": 80.0, "energy": 100.0, "hygiene": 100.0}
		state["egg"] = {}
		_log(t("A linhagem continua: %s acorda da hibernação.", "The lineage carries on: %s wakes from hibernation.") % p["name"])
		lineage_event.emit("succession")
	elif int(lin["recoveries_left"]) > 0 and cause == "health":
		lin["recoveries_left"] -= 1
		p["needs"] = {"health": 40.0, "hunger": 50.0, "hydration": 50.0, "energy": 50.0, "hygiene": 60.0}
		p["x"] = 13; p["y"] = 12
		_log(t("Recuperação usada. Restam %d.", "Recovery used. %d left.") % lin["recoveries_left"])
		lineage_event.emit("recovery")
	else:
		lin["ended"] = true
		_log(t("A linhagem terminou e ficou no arquivo.", "The lineage ended and was archived."))
		lineage_event.emit("ended")
	save_game()

# ---------------------------------------------------------------- inventory and items
func count(item: String) -> int:
	return int(state["inventory"].get(item, 0))

func _give(item: String, amount: int) -> void:
	if amount <= 0:
		return
	state["inventory"][item] = count(item) + amount

func _take(item: String, amount: int) -> bool:
	if count(item) < amount:
		return false
	state["inventory"][item] = count(item) - amount
	if state["inventory"][item] <= 0:
		state["inventory"].erase(item)
	return true

## Collect with the 10% egg share (GDD §6): 90% to the player, 10% as contribution points.
func collect(item: String, amount: int, kind: String) -> int:
	var carry: float = state["carry"].get(item, 0.0)
	var split := Rules.split_share(amount, carry, count(item))
	state["carry"][item] = split[1]
	_give(item, int(split[0]))
	var points := amount * Rules.EGG_SHARE * 10.0
	if kind == "water":
		state["reserve"]["hydration"] += points
	else:
		state["reserve"]["nutrition"] += points
	return int(split[0])

func food_value(item: String) -> float:
	if FOOD.has(item):
		return float(FOOD[item])
	if Crops.DATA.has(item):
		return float(Crops.DATA[item]["food"])
	return 0.0

func best_food() -> String:
	var best := ""
	for item in state["inventory"].keys():
		if food_value(item) > food_value(best) if best != "" else food_value(item) > 0.0:
			best = item
	return best

func crop_name(crop: String) -> String:
	return Crops.DATA[crop]["en" if english else "pt"] if Crops.DATA.has(crop) else crop

func item_name(item: String) -> String:
	if item.begins_with("seed_"):
		return t("Semente de %s", "%s seed") % crop_name(item.substr(5))
	match item:
		"water": return t("Água", "Water")
		"berries": return t("Bagas", "Berries")
		"wood": return t("Madeira", "Wood")
		"coins": return t("Moedas", "Coins")
	return crop_name(item)

# ---------------------------------------------------------------- skills
func level(prof: String) -> int:
	return Rules.level_for_xp(float(state["player"]["xp"].get(prof, 0.0)))

func gain_xp(prof: String, base: float, difficulty: float, action_key: String) -> void:
	var counts: Dictionary = state["player"]["counts"]["by"]
	var n := int(counts.get(action_key, 0))
	counts[action_key] = n + 1
	var learn: float = 1.0 + (float(state["player"]["attrs"]["learning"]) - 50.0) / 100.0
	var before := level(prof)
	state["player"]["xp"][prof] = float(state["player"]["xp"].get(prof, 0.0)) + Rules.xp_gain(base, difficulty, learn, n)
	if level(prof) > before:
		message.emit(t("Subiu de nível em %s: %d!", "Level up in %s: %d!") % [prof_name(prof), level(prof)])

func prof_name(prof: String) -> String:
	var pt := {"farming": "Agricultura", "hunting": "Caça e rastreio", "carpentry": "Carpintaria", "building": "Construção",
		"healing": "Cura", "exploring": "Exploração", "fishing": "Pesca", "trading": "Comércio"}
	var en := {"farming": "Farming", "hunting": "Hunting & tracking", "carpentry": "Carpentry", "building": "Building",
		"healing": "Healing", "exploring": "Exploring", "fishing": "Fishing", "trading": "Trading"}
	return (en if english else pt)[prof]

## The main profession is simply what the player does most.
func main_profession() -> String:
	var best := "farming"
	for p in PROFESSIONS:
		if float(state["player"]["xp"][p]) > float(state["player"]["xp"][best]):
			best = p
	return best

func energy_use(base: float) -> void:
	var n: Dictionary = state["player"]["needs"]
	n["energy"] = maxf(0.0, n["energy"] - base)
	n["hygiene"] = maxf(0.0, n["hygiene"] - base * 0.3)

# ---------------------------------------------------------------- quests
func quest_progress(kind: String) -> void:
	var q: Dictionary = state["quests"]["farm_001"]
	if q["state"] == "active" and q.has(kind):
		q[kind] = mini(3, int(q[kind]) + 1)
		if q["plant"] >= 3 and q["water"] >= 3 and q["harvest"] >= 3:
			q["state"] = "done"
			gain_xp("farming", 100.0, 1.0, "quest_farm_001")
			_give("seed_carrot", 5); _give("seed_potato", 3); _give("seed_bean", 3)
			state["quests"]["water_002"]["state"] = "active"
			message.emit(t("Missão concluída: Primeira Colheita! Ganhou sementes novas.", "Quest complete: First Harvest! New seeds earned."))
	var w: Dictionary = state["quests"]["water_002"]
	if w["state"] == "active" and w.has(kind):
		w[kind] = mini(5, int(w[kind]) + 1)
		if w["fill"] >= 5 and w["water"] >= 5:
			w["state"] = "done"
			gain_xp("farming", 80.0, 1.0, "quest_water_002")
			_give("seed_corn", 3); _give("seed_tomato", 3); _give("seed_strawberry", 3); _give("seed_pumpkin", 2)
			message.emit(t("Missão concluída: Água para a Fazenda!", "Quest complete: Water for the Farm!"))

# ---------------------------------------------------------------- care of the companion
func care(action: String) -> String:
	var cn: Dictionary = state["creature"]["needs"]
	var cname: String = state["creature"]["name"]
	match action:
		"feed":
			var food := best_food()
			if food == "":
				return t("Não há comida. Colha algo ou apanhe bagas.", "No food. Harvest something or pick berries.")
			_take(food, 1)
			cn["hunger"] = minf(100.0, cn["hunger"] + food_value(food) * 1.5)
			cn["happiness"] = minf(100.0, cn["happiness"] + 4.0)
			gain_xp("healing", 3.0, 1.0, "care_feed")
			return t("%s comeu %s." , "%s ate %s.") % [cname, item_name(food)]
		"water":
			if not _take("water", 1):
				return t("Sem água. Encha o cantil no rio ou no poço.", "No water. Fill up at the river or the well.")
			cn["hydration"] = minf(100.0, cn["hydration"] + 40.0)
			gain_xp("healing", 3.0, 1.0, "care_water")
			return t("%s bebeu água." , "%s drank water.") % cname
		"play":
			if cn["energy"] < 10.0:
				return t("%s está cansado. Deixe descansar." , "%s is tired. Let it rest.") % cname
			cn["happiness"] = minf(100.0, cn["happiness"] + 18.0)
			cn["energy"] = maxf(0.0, cn["energy"] - 8.0)
			energy_use(2.0)
			return t("Brincaram juntos!" , "You played together!")
		"clean":
			if not _take("water", 1):
				return t("Precisa de 1 água para dar banho." , "Needs 1 water for a bath.")
			cn["hygiene"] = 100.0
			cn["happiness"] = minf(100.0, cn["happiness"] + 5.0)
			gain_xp("healing", 3.0, 1.0, "care_clean")
			return t("%s está limpinho." , "%s is clean.") % cname
		"pet":
			cn["happiness"] = minf(100.0, cn["happiness"] + 6.0)
			return t("%s ficou feliz." , "%s is happy.") % cname
	return ""

func eat(item: String) -> String:
	if not _take(item, 1):
		return ""
	var n: Dictionary = state["player"]["needs"]
	n["hunger"] = minf(100.0, n["hunger"] + food_value(item))
	return t("Comeu %s." , "Ate %s.") % item_name(item)

func drink() -> String:
	if not _take("water", 1):
		return t("O cantil está vazio." , "The canteen is empty.")
	var n: Dictionary = state["player"]["needs"]
	n["hydration"] = minf(100.0, n["hydration"] + 30.0)
	return t("Bebeu água." , "Drank water.")

## The most urgent need, for the simple alerts of the child mode.
func urgent() -> Dictionary:
	var cn: Dictionary = state["creature"]["needs"]
	var n: Dictionary = state["player"]["needs"]
	var cname: String = state["creature"]["name"]
	if cn["hydration"] < 30.0:
		return {"text": t("%s está com sede!", "%s is thirsty!") % cname, "action": "care_water", "button": t("DAR ÁGUA", "GIVE WATER")}
	if cn["hunger"] < 30.0:
		return {"text": t("%s está com fome!", "%s is hungry!") % cname, "action": "care_feed", "button": t("DAR COMIDA", "FEED")}
	if n["hydration"] < 30.0:
		return {"text": t("Está com sede!", "You are thirsty!"), "action": "drink", "button": t("BEBER", "DRINK")}
	if n["hunger"] < 30.0:
		return {"text": t("Está com fome!", "You are hungry!"), "action": "eat", "button": t("COMER", "EAT")}
	if cn["happiness"] < 30.0:
		return {"text": t("%s quer brincar!", "%s wants to play!") % cname, "action": "care_play", "button": t("BRINCAR", "PLAY")}
	if n["energy"] < 20.0:
		return {"text": t("Está muito cansado. Descanse em casa.", "You are exhausted. Rest at home."), "action": "", "button": ""}
	return {}

# ---------------------------------------------------------------- persistence
func _log(text: String) -> void:
	var l: Array = state.get("log", [])
	l.append("D%d %02d:%02d %s" % [day(), hour_of_day(), minute_of_hour(), text])
	while l.size() > 30:
		l.pop_front()
	state["log"] = l

func save_game() -> bool:
	if state.is_empty():
		return false
	var body := JSON.stringify(state)
	var doc := JSON.stringify({"sha256": Identity.sha256(body.to_utf8_buffer()).hex_encode(), "body": body})
	var tmp := SAVE_PATH + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		return false
	f.store_string(doc)
	f.close()
	if FileAccess.file_exists(SAVE_PATH):
		DirAccess.copy_absolute(SAVE_PATH, BACKUP_PATH)
	return DirAccess.rename_absolute(tmp, SAVE_PATH) == OK

static func read_save(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var text := FileAccess.get_file_as_string(path)
	var doc = JSON.parse_string(text)
	if typeof(doc) != TYPE_DICTIONARY or not doc.has("body") or not doc.has("sha256"):
		return {}
	var body: String = doc["body"]
	if Identity.sha256(body.to_utf8_buffer()).hex_encode() != doc["sha256"]:
		return {}  # corrupted or edited: ignored
	var data = JSON.parse_string(body)
	return data if typeof(data) == TYPE_DICTIONARY else {}

func load_game() -> void:
	var data := read_save(SAVE_PATH)
	if data.is_empty():
		data = read_save(BACKUP_PATH)
	state = data
	if has_game():
		simulate_to_now(true)

func _process(delta: float) -> void:
	if not has_game():
		return
	_tick += delta
	if _tick >= 2.0:
		_tick = 0.0
		simulate_to_now()
	_autosave += delta
	if _autosave >= 30.0:
		_autosave = 0.0
		save_game()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_WM_CLOSE_REQUEST \
			or what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		if has_game():
			save_game()
	elif what == NOTIFICATION_APPLICATION_RESUMED:
		if has_game():
			simulate_to_now(true)
