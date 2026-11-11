extends Node
## Builds the game screen in code: character creation on first run, then the valley with the
## player, the companion, the NPC, camera, day/night, rain, HUD, touch controls and menus.

const TILE := 16
const DIRS := [Vector2i(0, 1), Vector2i(0, -1), Vector2i(-1, 0), Vector2i(1, 0)]
const NPC_CELL := Vector2i(39, 29)
const EGG_CELL := Vector2i(11, 11)

var world
var player: Sprite2D
var buddy: Sprite2D
var npc: Sprite2D
var egg_sprite: Sprite2D
var cam: Camera2D
var night: CanvasModulate
var rain
var hud
var menu
var dialog
var creator

var cell := Vector2i(13, 12)
var face := 0
var moving := false
var move_from := Vector2.ZERO
var move_to := Vector2i.ZERO
var move_t := 0.0
var busy := 0.0
var trail: Array[Vector2i] = []
var buddy_cell := Vector2i(13, 13)
var anim := 0.0
var frame := 0
var player_tex: Array = []
var buddy_tex: Array = []
var npc_tex: Array = []
var rng := RandomNumberGenerator.new()

func _ready() -> void:
	Engine.max_fps = 30  # GDD: 30 FPS target on basic phones, and it saves battery
	rng.randomize()
	if not InputMap.has_action("genesis_menu"):
		InputMap.add_action("genesis_menu")
		var ev := InputEventKey.new(); ev.keycode = KEY_M
		InputMap.action_add_event("genesis_menu", ev)
	Game.message.connect(_toast)
	Game.lineage_event.connect(_on_lineage)
	if Game.has_game():
		start_world()
	else:
		show_creator()

func show_creator() -> void:
	creator = preload("res://ui/Creator.gd").new()
	add_child(creator)
	creator.done.connect(func():
		creator.queue_free()
		start_world())

func start_world() -> void:
	world = preload("res://world/World.gd").new()
	add_child(world)
	world.y_sort_enabled = false
	var p: Dictionary = Game.state["player"]
	cell = Vector2i(int(p["x"]), int(p["y"]))
	face = int(p.get("dir", 0))
	player = Sprite2D.new(); player.centered = false; player.offset = Vector2(0, -8)
	buddy = Sprite2D.new(); buddy.centered = false
	npc = Sprite2D.new(); npc.centered = false; npc.offset = Vector2(0, -8)
	egg_sprite = Sprite2D.new(); egg_sprite.centered = false
	for s in [egg_sprite, npc, buddy, player]:
		world.add_child(s)
	rebuild_sprites()
	npc.position = Vector2(NPC_CELL * TILE)
	egg_sprite.position = Vector2(EGG_CELL * TILE)
	cam = Camera2D.new()
	cam.limit_left = 0; cam.limit_top = 0; cam.limit_right = 64 * TILE; cam.limit_bottom = 64 * TILE
	cam.position_smoothing_enabled = true
	player.add_child(cam)
	cam.position = Vector2(8, 0)
	night = CanvasModulate.new()
	add_child(night)
	rain = preload("res://ui/Rain.gd").new()
	add_child(rain)
	hud = preload("res://ui/Hud.gd").new()
	add_child(hud)
	hud.main = self
	var touch := preload("res://ui/Touch.gd").new()
	add_child(touch)
	menu = preload("res://ui/MenuPanel.gd").new()
	hud.add_child(menu)
	menu.main = self
	menu.visible = false
	dialog = preload("res://ui/Dialog.gd").new()
	hud.add_child(dialog)
	player.position = Vector2(cell * TILE)
	buddy_cell = cell + Vector2i(0, 1)
	buddy.position = Vector2(buddy_cell * TILE)
	trail = [buddy_cell]
	update_world_visuals()
	if Game.last_offline_report != "":
		dialog.show_text(Game.t("De volta ao vale", "Back in the valley"), Game.last_offline_report)
		Game.last_offline_report = ""
	elif Game.state["quests"]["farm_001"]["state"] == "new":
		dialog.show_text("GENESIS", Game.t("Bem-vindo ao Vale Inicial! Fale com a Vó Rosa, junto à ponte do rio, para começar a sua fazenda.\n\nUse as setas para andar e o botão A para agir.",
			"Welcome to Starter Valley! Talk to Grandma Rosa by the river bridge to start your farm.\n\nUse the arrows to walk and the A button to act."))

func rebuild_sprites() -> void:
	var p: Dictionary = Game.state["player"]
	player_tex.clear()
	for d in 4:
		var frames := []
		for f in 2:
			frames.append(Sprites.tex(Sprites.person(p["genome"], p["gender"], d, f)))
		player_tex.append(frames)
	var c: Dictionary = Game.state["creature"]
	var sick := float(c["needs"]["health"]) < 30.0
	buddy_tex = [Sprites.tex(Sprites.creature(c["genome"], 0, sick)), Sprites.tex(Sprites.creature(c["genome"], 1, sick))]
	var rosa := {"skin_palette": 3, "eye_shape": 0, "eye_color": 4, "hair_style": 9, "hair_color": 8, "face_shape": 4,
		"body_shape": 2, "clothing_pattern": 3, "clothing_palette": 27, "egg_pattern": 1, "marking_seed": 0}
	npc_tex = [Sprites.tex(Sprites.person(rosa, "female", 0, 0)), Sprites.tex(Sprites.person(rosa, "female", 0, 1))]

func update_world_visuals() -> void:
	var egg: Dictionary = Game.state["egg"]
	egg_sprite.visible = not egg.is_empty()
	if egg_sprite.visible:
		var g: Dictionary = egg["genome"]
		egg_sprite.texture = Sprites.tex(Sprites.egg(int(g["egg_pattern"]), int(g["hue"]) / 24.0,
			1.0 if egg["state"] == "hibernating" else 0.0))
	world.refresh()

func _process(delta: float) -> void:
	if world == null or not Game.has_game():
		return
	anim += delta
	if anim > 0.28:
		anim = 0.0
		frame = 1 - frame
		buddy.texture = buddy_tex[frame]
		npc.texture = npc_tex[0 if (Time.get_ticks_msec() / 2000) % 2 == 0 else 1]
	busy = maxf(0.0, busy - delta)
	_update_daylight()
	if menu.visible or dialog.visible:
		player.texture = player_tex[face][0]
		return
	if moving:
		move_t += delta / _move_time()
		var target := Vector2(move_to * TILE)
		player.position = move_from.lerp(target, minf(move_t, 1.0))
		buddy.position = buddy.position.lerp(Vector2(buddy_cell * TILE), minf(1.0, delta * 8.0))
		player.texture = player_tex[face][frame]
		if move_t >= 1.0:
			moving = false
			_arrived()
	else:
		player.texture = player_tex[face][0]
		var want := _input_dir()
		if want >= 0:
			face = want
			var next: Vector2i = cell + DIRS[want]
			if not _blocked(next):
				moving = true
				move_t = 0.0
				move_from = player.position
				move_to = next
				trail.append(cell)
				if trail.size() > 4:
					trail.pop_front()
				buddy_cell = trail[trail.size() - 1]
		buddy.position = buddy.position.lerp(Vector2(buddy_cell * TILE), minf(1.0, delta * 8.0))
	if Input.is_action_just_pressed("ui_accept") and busy <= 0.0 and not moving:
		act()
	if Input.is_action_just_pressed("ui_cancel") or Input.is_action_just_pressed("genesis_menu"):
		menu.open()

func _move_time() -> float:
	var speed: float = float(Game.state["player"]["attrs"]["speed"])
	var tired := 1.35 if float(Game.state["player"]["needs"]["energy"]) < 15.0 else 1.0
	return 0.2 * 52.0 / speed * tired

func _input_dir() -> int:
	if Input.is_action_pressed("ui_down"): return 0
	if Input.is_action_pressed("ui_up"): return 1
	if Input.is_action_pressed("ui_left"): return 2
	if Input.is_action_pressed("ui_right"): return 3
	return -1

func _blocked(c: Vector2i) -> bool:
	if world.blocked(c.x, c.y) or c == NPC_CELL:
		return true
	return egg_sprite.visible and c == EGG_CELL

func _arrived() -> void:
	cell = move_to
	var p: Dictionary = Game.state["player"]
	p["x"] = cell.x; p["y"] = cell.y; p["dir"] = face
	Game.energy_use(0.05)
	var chunk := "%d,%d" % [cell.x / 8, cell.y / 8]
	var seen: Array = Game.state["discovered"]
	if not chunk in seen:
		seen.append(chunk)
		Game.gain_xp("exploring", 15.0, 1.0, "discover")
		hud.refresh_minimap()
	if world.get_tile(cell.x, cell.y) in [TileArt.T.FOREST_FLOOR] and rng.randf() < 0.04:
		_encounter()

## Random discoveries in the forest (weighted, GDD §9).
func _encounter() -> void:
	var i := Rules.pick_weighted([30, 25, 20, 10, 10, 5], rng.randf())
	match i:
		0:
			Game.collect("berries", 2, "food"); _toast(Game.t("Encontrou frutos silvestres.", "Found wild fruit."))
		1:
			var crops := Crops.DATA.keys()
			var s: String = "seed_" + crops[rng.randi_range(0, crops.size() - 1)]
			Game.collect(s, 2, "food"); _toast(Game.t("Encontrou sementes: %s.", "Found seeds: %s.") % Game.item_name(s))
		2:
			_toast(Game.t("Um veado atravessou o caminho e desapareceu na floresta.", "A deer crossed the path and vanished into the woods."))
			Game.gain_xp("hunting", 6.0, 1.0, "track")
		3:
			Game.collect("wood", 3, "food"); _toast(Game.t("Encontrou um tronco caído: madeira boa!", "Found a fallen log: good wood!"))
		4:
			_toast(Game.t("Ouviu alguém a assobiar ao longe… a vila não deve estar longe.", "Someone whistles far away… the village must be close."))
		5:
			Game.energy_use(6.0)
			_toast(Game.t("Escorregou na lama e cansou-se. Cuidado no caminho!", "Slipped in the mud and got tired. Mind the path!"))

func act() -> void:
	var f: Vector2i = cell + DIRS[face]
	var key := "%d,%d" % [f.x, f.y]
	var tile: int = world.get_tile(f.x, f.y)
	var T := TileArt.T
	var msg := ""
	var lvl := Game.level("farming")
	if f == NPC_CELL:
		talk_npc()
		return
	if egg_sprite.visible and f == EGG_CELL:
		menu.open("lineage")
		return
	if world.is_water_source(f.x, f.y):
		var room := 10 - Game.count("water")
		if room <= 0:
			msg = Game.t("O cantil já está cheio (10).", "The canteen is already full (10).")
		else:
			var got := Game.collect("water", room + 1, "water")
			Game.state["inventory"]["water"] = mini(10, Game.count("water"))
			Game.quest_progress("fill")
			Game.gain_xp("farming", 1.0, 1.0, "fill")
			msg = Game.t("Encheu o cantil (+%d água).", "Filled the canteen (+%d water).") % got
	elif tile == T.DOOR:
		var p: Dictionary = Game.state["player"]
		var now_h := Rules.game_hours(Game.elapsed())
		if now_h < int(p.get("rest_until_hour", 0)):
			msg = Game.t("Ainda não tem sono. Volte daqui a pouco.", "Not sleepy yet. Come back a bit later.")
		else:
			var n: Dictionary = p["needs"]
			n["energy"] = minf(100.0, n["energy"] + 35.0)
			n["hygiene"] = minf(100.0, n["hygiene"] + 25.0)
			p["rest_until_hour"] = now_h + 3
			msg = Game.t("Descansou em casa e tomou banho. Energia +35.", "Rested at home and washed. Energy +35.")
	elif tile == T.BUSH:
		if int(Game.state["bushes"].get(key, -1)) == Game.day():
			msg = Game.t("Este arbusto já não tem bagas hoje.", "No berries left on this bush today.")
		else:
			Game.state["bushes"][key] = Game.day()
			var got := Game.collect("berries", rng.randi_range(2, 4), "food")
			Game.gain_xp("hunting", 4.0, 1.0, "forage")
			Game.energy_use(1.5)
			msg = Game.t("Apanhou %d bagas.", "Picked %d berries.") % got
	elif tile == T.TREE or tile == T.PINE:
		if int(Game.state["trees"].get(key, -1)) == Game.day():
			msg = Game.t("Já recolheu madeira desta árvore hoje.", "You already took wood from this tree today.")
		else:
			Game.state["trees"][key] = Game.day()
			var got := Game.collect("wood", rng.randi_range(1, 2), "food")
			Game.gain_xp("carpentry", 6.0, 1.2, "wood")
			Game.energy_use(4.0)
			busy = Rules.action_time(0.8, Game.level("carpentry"))
			msg = Game.t("Recolheu %d madeira (ramos caídos).", "Collected %d wood (fallen branches).") % got
	elif tile == T.ROCK:
		msg = Game.t("Uma rocha. Vai precisar de uma picareta (em breve).", "A rock. You will need a pickaxe (coming soon).")
	elif tile == T.GATE:
		msg = Game.t("Vila Central — a estrada abre na próxima fase do jogo.", "Central Village — the road opens in a later phase.")
	elif world.in_farm(f.x, f.y):
		msg = _farm(f, key, lvl)
	else:
		msg = Game.t("Nada para fazer aqui.", "Nothing to do here.")
	if msg != "":
		_toast(msg)
	world.refresh()
	hud.refresh()

func _farm(f: Vector2i, key: String, lvl: int) -> String:
	var plots: Dictionary = Game.state["plots"]
	if not plots.has(key):
		if Game.state["player"]["needs"]["energy"] < 3.0:
			return Game.t("Sem energia para cavar. Descanse ou coma.", "Too tired to dig. Rest or eat.")
		plots[key] = Game._plot()
		Game.energy_use(3.0)
		Game.gain_xp("farming", 5.0, 1.0, "till")
		busy = Rules.action_time(0.7, lvl)
		hud.refresh_minimap()
		return Game.t("Preparou um canteiro.", "Prepared a plot.")
	var p: Dictionary = plots[key]
	if p["dead"]:
		p["crop"] = ""; p["dead"] = false; p["growth"] = 0.0
		return Game.t("Limpou a planta seca. O canteiro está pronto de novo.", "Cleared the dry plant. The plot is ready again.")
	if p["crop"] == "":
		var seed: String = Game.state.get("selected_seed", "")
		if Game.count(seed) <= 0:
			seed = ""
			for item in Game.state["inventory"].keys():
				if String(item).begins_with("seed_"):
					seed = item
					break
		if seed == "":
			return Game.t("Não tem sementes. A Vó Rosa, junto à ponte, pode ajudar.", "No seeds. Grandma Rosa, by the bridge, can help.")
		Game._take(seed, 1)
		Game.state["selected_seed"] = seed
		p["crop"] = seed.substr(5)
		p["growth"] = 0.0
		Game.energy_use(1.5)
		Game.gain_xp("farming", 4.0, 1.0, "plant")
		Game.quest_progress("plant")
		busy = Rules.action_time(0.6, lvl)
		return Game.t("Plantou %s. Regue para crescer.", "Planted %s. Water it to grow.") % Game.crop_name(p["crop"])
	var crop: String = p["crop"]
	if Crops.stage(crop, float(p["growth"])) >= 4:
		var qty := 1 + rng.randi_range(0, 1) + (1 if rng.randf() < lvl / 20.0 else 0)
		var got := Game.collect(crop, qty, "food")
		if rng.randf() < 0.6:
			Game._give("seed_" + crop, 1)
		p["crop"] = ""; p["growth"] = 0.0
		p["fertility"] = maxf(0.5, float(p["fertility"]) - 0.04)
		Game.energy_use(2.0)
		Game.gain_xp("farming", 10.0, 1.0 + float(Crops.DATA[crop]["days"]) / 4.0, "harvest")
		Game.quest_progress("harvest")
		Input.vibrate_handheld(40)
		busy = Rules.action_time(0.6, lvl)
		return Game.t("Colheu %d %s!", "Harvested %d %s!") % [got, Game.crop_name(crop)]
	if Game.count("water") <= 0:
		return Game.t("Sem água no cantil. Encha no poço ou no rio.", "No water. Fill up at the well or the river.")
	if float(p["moisture"]) >= 90.0:
		var pct := int(100.0 * float(p["growth"]) / Crops.required(crop))
		return Game.t("%s: %s, %d%% crescida. A terra está húmida.", "%s: %s, %d%% grown. The soil is moist.") % [
			Game.crop_name(crop), Crops.STAGES[Crops.stage(crop, float(p["growth"]))], pct]
	Game._take("water", 1)
	p["moisture"] = 100.0
	Game.gain_xp("farming", 2.0, 1.0, "water")
	Game.quest_progress("water")
	busy = Rules.action_time(0.5, lvl)
	return Game.t("Regou %s.", "Watered the %s.") % Game.crop_name(crop)

func talk_npc() -> void:
	var q: Dictionary = Game.state["quests"]["farm_001"]
	var name := Game.t("Vó Rosa", "Grandma Rosa")
	if q["state"] == "new":
		q["state"] = "active"
		Game._give("seed_carrot", 4)
		dialog.show_text(name, Game.t("Olá! Bem-vindo ao vale. Tome estas 4 sementes de cenoura.\n\nMissão: Primeira Colheita\n• Plante 3 sementes na horta\n• Regue 3 vezes\n• Colha 3 vezes\n\nA água tira-se do poço, ao lado da casa, ou do rio.",
			"Hello! Welcome to the valley. Take these 4 carrot seeds.\n\nQuest: First Harvest\n• Plant 3 seeds in the field\n• Water 3 times\n• Harvest 3 times\n\nGet water from the well by the house or from the river."))
		return
	var crop := ""
	var most := 0
	for item in Game.state["inventory"].keys():
		if Crops.DATA.has(item) and Game.count(item) > most:
			crop = item; most = Game.count(item)
	if most >= 2:
		Game._take(crop, 2)
		var kinds := Crops.DATA.keys()
		var s: String = "seed_" + kinds[rng.randi_range(0, kinds.size() - 1)]
		Game._give(s, 3)
		Game.gain_xp("trading", 12.0, 1.0, "trade_npc")
		dialog.show_text(name, Game.t("Que bela colheita! Troco as suas 2 %s por 3 %s.", "What a harvest! I'll trade your 2 %s for 3 %s.") % [Game.crop_name(crop), Game.item_name(s)])
		return
	if q["state"] == "active":
		dialog.show_text(name, Game.t("Primeira Colheita: plantou %d/3, regou %d/3, colheu %d/3. A alface cresce num dia; a cenoura em dois.",
			"First Harvest: planted %d/3, watered %d/3, harvested %d/3. Lettuce grows in a day; carrots in two.") % [q["plant"], q["water"], q["harvest"]])
		return
	var w: Dictionary = Game.state["quests"]["water_002"]
	if w["state"] == "active":
		dialog.show_text(name, Game.t("Água para a Fazenda: encha o cantil %d/5 e regue %d/5. Quando chove, a terra fica húmida sozinha.",
			"Water for the Farm: fill the canteen %d/5 and water %d/5. When it rains the soil gets wet by itself.") % [w["fill"], w["water"]])
		return
	dialog.show_text(name, Game.t("Traga-me 2 colheitas iguais e troco-as por sementes novas. E cuide do seu bichinho!",
		"Bring me 2 of the same crop and I'll trade them for new seeds. And look after your companion!"))

func _update_daylight() -> void:
	var h := Game.hour_of_day() + Game.minute_of_hour() / 60.0
	var day_c := Color(1, 1, 1)
	var night_c := Color(0.32, 0.36, 0.58)
	var dusk_c := Color(0.95, 0.72, 0.6)
	var c := day_c
	if h >= 20.0 or h < 5.0:
		c = night_c
	elif h < 6.5:
		c = night_c.lerp(dusk_c, (h - 5.0) / 1.5) if h < 5.75 else dusk_c.lerp(day_c, (h - 5.75) / 0.75)
	elif h >= 18.0:
		c = day_c.lerp(dusk_c, (h - 18.0) / 1.0) if h < 19.0 else dusk_c.lerp(night_c, (h - 19.0) / 1.0)
	if Game.raining():
		c = c.darkened(0.15)
	night.color = c
	rain.visible = Game.raining()

func _toast(text: String) -> void:
	if hud:
		hud.toast(text)

func _on_lineage(kind: String) -> void:
	if world == null:
		return
	if kind == "ended":
		dialog.show_text(Game.t("Fim da linhagem", "End of the lineage"), Game.t("A linhagem terminou e ficou guardada no arquivo. Pode começar uma nova.", "The lineage has ended and is kept in the archive. You can start a new one."))
		dialog.closed.connect(func(): get_tree().reload_current_scene(), CONNECT_ONE_SHOT)
	else:
		rebuild_sprites()
		cell = Vector2i(int(Game.state["player"]["x"]), int(Game.state["player"]["y"]))
		player.position = Vector2(cell * TILE)
		dialog.show_text(Game.t("Linhagem", "Lineage"), String(Game.state["log"].back()))

func care_action(action: String) -> void:
	var msg := ""
	match action:
		"care_feed": msg = Game.care("feed")
		"care_water": msg = Game.care("water")
		"care_play": msg = Game.care("play")
		"drink": msg = Game.drink()
		"eat":
			var f := Game.best_food()
			msg = Game.eat(f) if f != "" else Game.t("Não há comida.", "No food.")
	_toast(msg)
	hud.refresh()
