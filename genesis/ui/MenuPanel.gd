extends Control
## Menu (B): bag, companion, skills, quests, lineage and settings. The child mode shows fewer
## tabs, bigger buttons and less text; the rules underneath are identical (GDD §13).

var main
var tabs := HBoxContainer.new()
var content := VBoxContainer.new()
var current := "bag"
var _opened_at := 0

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := PanelContainer.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.08, 0.1, 0.09, 0.97)
	sb.set_content_margin_all(6)
	bg.add_theme_stylebox_override("panel", sb)
	add_child(bg)
	var v := VBoxContainer.new()
	bg.add_child(v)
	var head := HBoxContainer.new()
	v.add_child(head)
	var title := Label.new()
	title.text = "GENESIS"
	title.add_theme_font_size_override("font_size", 14)
	title.add_theme_color_override("font_color", Color(0.45, 0.85, 0.55))
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(title)
	var close_b := Button.new()
	close_b.text = Game.t("Fechar ✕", "Close ✕")
	close_b.pressed.connect(close)
	head.add_child(close_b)
	var scroll_tabs := ScrollContainer.new()
	scroll_tabs.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll_tabs.custom_minimum_size = Vector2(0, 30)
	scroll_tabs.add_child(tabs)
	v.add_child(scroll_tabs)
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(content)
	v.add_child(scroll)

func _mode() -> String:
	return Game.state["settings"]["ui_mode"]

func open(tab: String = "") -> void:
	if tab != "":
		current = tab
	visible = true
	_opened_at = Time.get_ticks_msec()
	_build_tabs()
	show_tab(current)

func close() -> void:
	visible = false
	Game.save_game()
	if main:
		main.hud.refresh()
		main.update_world_visuals()

func _process(_d: float) -> void:
	if visible and Time.get_ticks_msec() - _opened_at > 300 and Input.is_action_just_pressed("ui_cancel"):
		close()

func _build_tabs() -> void:
	for c in tabs.get_children():
		c.queue_free()
	var list := [["bag", Game.t("Mochila", "Bag")], ["buddy", Game.t("Bichinho", "Companion")]]
	if _mode() != "child":
		list.append(["skills", Game.t("Habilidades", "Skills")])
	list.append(["quests", Game.t("Missões", "Quests")])
	list.append(["lineage", Game.t("Linhagem", "Lineage")])
	list.append(["settings", Game.t("Ajustes", "Settings")])
	for pair in list:
		var b := Button.new()
		b.text = pair[1]
		b.toggle_mode = true
		b.button_pressed = pair[0] == current
		b.add_theme_font_size_override("font_size", 12 if _mode() == "child" else 10)
		var id: String = pair[0]
		b.pressed.connect(func(): show_tab(id))
		tabs.add_child(b)

func _clear() -> void:
	for c in content.get_children():
		c.queue_free()

func _label(text: String, size: int = 11, color: Color = Color(0.92, 0.94, 0.9)) -> Label:
	var l := Label.new()
	l.text = text
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.add_theme_font_size_override("font_size", size + (2 if _mode() == "child" else 0))
	l.add_theme_color_override("font_color", color)
	content.add_child(l)
	return l

func _button(text: String, action: Callable, parent: Control = null) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0, 34 if _mode() == "child" else 26)
	b.add_theme_font_size_override("font_size", 13 if _mode() == "child" else 10)
	b.pressed.connect(func():
		action.call()
		show_tab(current))
	(parent if parent else content).add_child(b)
	return b

func _meter(name: String, value: float, color: Color) -> void:
	var h := HBoxContainer.new()
	var l := Label.new()
	l.text = name
	l.custom_minimum_size = Vector2(90, 0)
	l.add_theme_font_size_override("font_size", 11 if _mode() == "child" else 9)
	var b := ProgressBar.new()
	b.value = value
	b.show_percentage = _mode() != "child"
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b.custom_minimum_size = Vector2(0, 14 if _mode() == "child" else 10)
	var fill := StyleBoxFlat.new(); fill.bg_color = color
	b.add_theme_stylebox_override("fill", fill)
	h.add_child(l); h.add_child(b)
	content.add_child(h)

func show_tab(tab: String) -> void:
	current = tab
	_clear()
	for b in tabs.get_children():
		if b is Button:
			(b as Button).button_pressed = false
	match tab:
		"bag": _bag()
		"buddy": _buddy()
		"skills": _skills()
		"quests": _quests()
		"lineage": _lineage()
		"settings": _settings()

func _bag() -> void:
	var inv: Dictionary = Game.state["inventory"]
	_label(Game.t("Semente escolhida: %s", "Selected seed: %s") % Game.item_name(Game.state.get("selected_seed", "")), 10, Color(0.6, 0.9, 0.6))
	if inv.is_empty():
		_label(Game.t("A mochila está vazia.", "The bag is empty."))
	var keys := inv.keys()
	keys.sort()
	for item in keys:
		var h := HBoxContainer.new()
		var l := Label.new()
		l.text = "%s × %d" % [Game.item_name(item), int(inv[item])]
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		l.add_theme_font_size_override("font_size", 13 if _mode() == "child" else 11)
		h.add_child(l)
		content.add_child(h)
		var it: String = item
		if it.begins_with("seed_"):
			_button(Game.t("Plantar esta", "Plant this"), func(): Game.state["selected_seed"] = it, h)
		elif Game.food_value(it) > 0.0:
			_button(Game.t("Comer", "Eat"), func(): main._toast(Game.eat(it)), h)
		elif it == "water":
			_button(Game.t("Beber", "Drink"), func(): main._toast(Game.drink()), h)
	_label(Game.t("\n10%% de tudo o que recolhe vai para a reserva do ovo da linhagem.", "\n10%% of everything you collect goes to the lineage egg's reserve."), 9, Color(0.7, 0.75, 0.7))

func _buddy() -> void:
	var c: Dictionary = Game.state["creature"]
	var tex := TextureRect.new()
	tex.texture = Sprites.tex(Sprites.creature(c["genome"], 0, float(c["needs"]["health"]) < 30.0))
	tex.custom_minimum_size = Vector2(64, 64)
	tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	tex.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	content.add_child(tex)
	_label("%s · %s" % [c["name"], Game.t("%d dias", "%d days") % (Game.day() - int(c["born_day"]))], 13, Color(1, 0.9, 0.6))
	var n: Dictionary = c["needs"]
	_meter(Game.t("Fome", "Food"), n["hunger"], Color(0.92, 0.6, 0.2))
	_meter(Game.t("Água", "Water"), n["hydration"], Color(0.3, 0.6, 0.95))
	_meter(Game.t("Alegria", "Happiness"), n["happiness"], Color(0.95, 0.45, 0.75))
	_meter(Game.t("Energia", "Energy"), n["energy"], Color(0.95, 0.85, 0.25))
	_meter(Game.t("Limpeza", "Hygiene"), n["hygiene"], Color(0.6, 0.85, 0.9))
	_meter(Game.t("Saúde", "Health"), n["health"], Color(0.86, 0.25, 0.25))
	var grid := GridContainer.new()
	grid.columns = 2
	content.add_child(grid)
	for pair in [["feed", Game.t("DAR COMIDA", "FEED")], ["water", Game.t("DAR ÁGUA", "GIVE WATER")], ["play", Game.t("BRINCAR", "PLAY")],
			["clean", Game.t("DAR BANHO", "BATH")], ["pet", Game.t("CARINHO", "CUDDLE")]]:
		var a: String = pair[0]
		var b := _button(pair[1], func(): main._toast(Game.care(a)), grid)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL

func _skills() -> void:
	_label(Game.t("Profissão principal: %s (é o que mais faz).", "Main profession: %s (what you do most).") % Game.prof_name(Game.main_profession()), 11, Color(0.6, 0.9, 0.6))
	for p in Game.PROFESSIONS:
		var xp := float(Game.state["player"]["xp"][p])
		var lv := Game.level(p)
		var lo := Rules.xp_total(lv)
		var hi := Rules.xp_total(lv + 1)
		_meter("%s %d" % [Game.prof_name(p), lv], 100.0 * (xp - lo) / maxf(1.0, hi - lo), Color(0.45, 0.8, 0.55))
	if _mode() == "adult":
		var a: Dictionary = Game.state["player"]["attrs"]
		_label(Game.t("Atributos: força %d · velocidade %d · coleta %d · recuperação %d · inteligência %d · aprendizagem %d",
			"Attributes: strength %d · speed %d · gathering %d · recovery %d · intelligence %d · learning %d") % [a["strength"], a["speed"], a["gathering"], a["recovery"], a["intelligence"], a["learning"]], 9)
		_label(Game.t("XP total para o nível L = 25L² + 75L. Repetir a mesma ação no mesmo dia rende menos (até 20%).",
			"Total XP for level L = 25L² + 75L. Repeating the same action in one day earns less (down to 20%)."), 9, Color(0.7, 0.75, 0.7))

func _quests() -> void:
	var q: Dictionary = Game.state["quests"]["farm_001"]
	_label(Game.t("Primeira Colheita", "First Harvest"), 13, Color(1, 0.9, 0.6))
	match q["state"]:
		"new": _label(Game.t("Fale com a Vó Rosa, junto à ponte.", "Talk to Grandma Rosa by the bridge."))
		"done": _label(Game.t("Concluída ✓", "Complete ✓"), 11, Color(0.5, 0.9, 0.5))
		_:
			_meter(Game.t("Plantar 3", "Plant 3"), q["plant"] * 33.4, Color(0.5, 0.8, 0.4))
			_meter(Game.t("Regar 3", "Water 3"), q["water"] * 33.4, Color(0.3, 0.6, 0.95))
			_meter(Game.t("Colher 3", "Harvest 3"), q["harvest"] * 33.4, Color(0.95, 0.7, 0.3))
	var w: Dictionary = Game.state["quests"]["water_002"]
	_label(Game.t("\nÁgua para a Fazenda", "\nWater for the Farm"), 13, Color(1, 0.9, 0.6))
	match w["state"]:
		"locked": _label(Game.t("Abre depois da Primeira Colheita.", "Opens after First Harvest."), 10, Color(0.7, 0.7, 0.7))
		"done": _label(Game.t("Concluída ✓", "Complete ✓"), 11, Color(0.5, 0.9, 0.5))
		_:
			_meter(Game.t("Encher o cantil 5", "Fill canteen 5"), w["fill"] * 20.0, Color(0.3, 0.6, 0.95))
			_meter(Game.t("Regar 5", "Water 5"), w["water"] * 20.0, Color(0.3, 0.8, 0.9))
	_label(Game.t("\nEm breve: A Ponte da Floresta, O Mercado da Vila, O Ovo Ancestral.", "\nComing soon: The Forest Bridge, The Village Market, The Ancestral Egg."), 9, Color(0.7, 0.75, 0.7))

func _lineage() -> void:
	var p: Dictionary = Game.state["player"]
	var lin: Dictionary = Game.state["lineage"]
	_label("%s · %s %d" % [p["name"], Game.t("geração", "generation"), lin["generation"]], 13, Color(1, 0.9, 0.6))
	_label(Game.t("Idade: %d dias (vida natural até 10 000)", "Age: %d days (natural life up to 10,000)") % (Game.day() - int(p["born_day"])), 10)
	_label(Game.t("Recuperações restantes: %d de 5", "Recoveries left: %d of 5") % lin["recoveries_left"], 10)
	var id: String = p["id"]
	_label("ID: " + (id.left(16) + "…" if _mode() == "child" else id), 8, Color(0.6, 0.7, 0.65))
	var egg: Dictionary = Game.state["egg"]
	var r: Dictionary = Game.state["reserve"]
	if egg.is_empty():
		_label(Game.t("\nO ovo aparece no dia 100 (faltam %d dias de jogo, cerca de %.1f dias reais).", "\nThe egg appears on day 100 (%d game days to go, about %.1f real days).") % [
			maxi(0, Rules.EGG_DAY - Game.day()), maxf(0.0, Rules.EGG_DAY - Game.day()) / 10.0], 10)
		_label(Game.t("Reserva já guardada para o ovo: nutrição %.1f · água %.1f", "Reserve already saved for the egg: nutrition %.1f · water %.1f") % [r["nutrition"], r["hydration"]], 10)
	else:
		var prog := Rules.egg_progress(egg["on"], egg["oh"], egg["od"])
		_label(Game.t("\nOvo da linhagem — %s", "\nLineage egg — %s") % {"incubating": Game.t("a incubar", "incubating"), "hibernating": Game.t("descendente a hibernar", "descendant hibernating")}.get(egg["state"], egg["state"]), 12, Color(1, 0.9, 0.6))
		_meter(Game.t("Nutrição", "Nutrition"), egg["on"], Color(0.92, 0.6, 0.2))
		_meter(Game.t("Hidratação", "Hydration"), egg["oh"], Color(0.3, 0.6, 0.95))
		_meter(Game.t("Desenvolvimento", "Development"), egg["od"], Color(0.6, 0.5, 0.95))
		_label(Game.t("Progresso %.0f%% (precisa de %d%% no dia 5000).", "Progress %.0f%% (needs %d%% on day 5000).") % [prog, int(Rules.EGG_P_MIN)], 10)
		if r["nutrition"] < 1.0:
			_label(Game.t("Falta comida na reserva: colha e apanhe bagas.", "The reserve needs food: harvest and pick berries."), 10, Color(1, 0.6, 0.5))
		if r["hydration"] < 1.0:
			_label(Game.t("Falta água na reserva: encha o cantil.", "The reserve needs water: fill the canteen."), 10, Color(1, 0.6, 0.5))
	if _mode() == "adult":
		_label(Game.t("\nGenoma visual", "\nVisual genome"), 11, Color(1, 0.9, 0.6))
		_label(JSON.stringify(p["genome"]), 8, Color(0.6, 0.7, 0.65))
	var arch: Array = lin["archive"]
	if not arch.is_empty():
		_label(Game.t("\nArquivo genealógico", "\nGenealogy archive"), 11, Color(1, 0.9, 0.6))
		for a in arch:
			_label("• %s (G%d) — %s %d" % [a["name"], a["generation"], Game.t("dia", "day"), a["died_day"]], 9)

func _settings() -> void:
	_label(Game.t("Modo da interface (a regra do jogo é a mesma):", "Interface mode (the game rules are the same):"), 10)
	var h := HBoxContainer.new()
	content.add_child(h)
	for pair in [["child", Game.t("5–10", "5–10")], ["teen", "11–17"], ["adult", "18+"]]:
		var m: String = pair[0]
		var pick := func():
			Game.state["settings"]["ui_mode"] = m
			_build_tabs()
		_button(pair[1], pick, h)
	_button(Game.t("Guardar agora", "Save now"), func(): main._toast(Game.t("Jogo guardado.", "Game saved.") if Game.save_game() else "!"))
	var lang := func():
		Game.english = not Game.english
		Game.state["settings"]["english"] = Game.english
		get_tree().reload_current_scene()
	_button("Português / English", lang)
	_label(Game.t("\nO tempo do jogo corre sempre, mesmo com o celular fechado: 1 hora do jogo = 6 minutos reais; 1 dia real = 10 dias do jogo. Guarde comida e água: enquanto estiver fora, a personagem come e bebe sozinha da mochila.",
		"\nGame time always runs, even with the phone closed: 1 game hour = 6 real minutes; 1 real day = 10 game days. Keep food and water: while you are away your character eats and drinks from the bag."), 9, Color(0.7, 0.75, 0.7))
	_label("GENESIS 0.1 · Oracle OS · offline", 8, Color(0.5, 0.55, 0.5))
