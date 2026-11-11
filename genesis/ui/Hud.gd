extends CanvasLayer
## Top bar (day, hour, season, weather), needs of player and companion, minimap, toasts and the
## simple alert of the child mode ("O seu bichinho está com sede!" + DAR ÁGUA).

var main
var clock := Label.new()
var bars := {}
var buddy_bars := {}
var minimap := TextureRect.new()
var dot := ColorRect.new()
var toast_label := Label.new()
var alert := PanelContainer.new()
var alert_text := Label.new()
var alert_button := Button.new()
var _alert_action := ""
var _toast_left := 0.0
var _t := 0.0
var map_big := false

func _ready() -> void:
	layer = 4
	var root := Control.new()
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(root)
	var top := PanelContainer.new()
	top.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.06, 0.08, 0.07, 0.78)
	sb.set_content_margin_all(4)
	top.add_theme_stylebox_override("panel", sb)
	root.add_child(top)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 1)
	top.add_child(v)
	clock.add_theme_font_size_override("font_size", 11)
	v.add_child(clock)
	var child: bool = Game.state["settings"]["ui_mode"] == "child"
	var row := HBoxContainer.new()
	v.add_child(row)
	var defs := [["health", Game.t("VIDA", "LIFE"), Color(0.86, 0.25, 0.25)], ["hunger", Game.t("FOME", "FOOD"), Color(0.92, 0.6, 0.2)],
		["hydration", Game.t("ÁGUA", "WATER"), Color(0.3, 0.6, 0.95)], ["energy", Game.t("ENERGIA", "ENERGY"), Color(0.95, 0.85, 0.25)]]
	for d in defs:
		bars[d[0]] = _bar(row, d[1], d[2], child)
	var row2 := HBoxContainer.new()
	v.add_child(row2)
	var who := Label.new()
	who.text = String(Game.state["creature"]["name"]).left(8)
	who.add_theme_font_size_override("font_size", 9)
	who.custom_minimum_size = Vector2(46, 0)
	row2.add_child(who)
	for d in [["hunger", Color(0.92, 0.6, 0.2)], ["hydration", Color(0.3, 0.6, 0.95)], ["happiness", Color(0.95, 0.45, 0.75)], ["health", Color(0.86, 0.25, 0.25)]]:
		buddy_bars[d[0]] = _bar(row2, "", d[1], false)
	# minimap (tap to enlarge)
	minimap.position = Vector2(0, 0)
	minimap.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	minimap.mouse_filter = Control.MOUSE_FILTER_STOP
	minimap.gui_input.connect(func(e):
		if (e is InputEventMouseButton and e.pressed) or (e is InputEventScreenTouch and e.pressed):
			map_big = not map_big
			_place_minimap())
	root.add_child(minimap)
	dot.color = Color(1, 0.2, 0.2)
	dot.size = Vector2(2, 2)
	dot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	minimap.add_child(dot)
	refresh_minimap()
	# toast
	toast_label.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM)
	toast_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	toast_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	toast_label.add_theme_font_size_override("font_size", 11)
	toast_label.add_theme_color_override("font_outline_color", Color(0, 0, 0))
	toast_label.add_theme_constant_override("outline_size", 4)
	toast_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(toast_label)
	# alert bubble
	var asb := StyleBoxFlat.new()
	asb.bg_color = Color(1, 0.97, 0.85, 0.95); asb.border_color = Color(0.85, 0.35, 0.2)
	asb.set_border_width_all(2); asb.set_corner_radius_all(6); asb.set_content_margin_all(6)
	alert.add_theme_stylebox_override("panel", asb)
	var h := HBoxContainer.new()
	alert.add_child(h)
	alert_text.add_theme_color_override("font_color", Color(0.2, 0.1, 0.05))
	alert_text.add_theme_font_size_override("font_size", 13 if child else 10)
	alert_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	alert_text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	alert_button.add_theme_font_size_override("font_size", 13 if child else 10)
	alert_button.pressed.connect(func():
		if _alert_action != "" and main:
			main.care_action(_alert_action))
	h.add_child(alert_text); h.add_child(alert_button)
	root.add_child(alert)
	alert.visible = false
	get_viewport().size_changed.connect(_place_minimap)
	_place_minimap.call_deferred()
	refresh()

func _bar(parent: Control, label: String, color: Color, big: bool) -> ProgressBar:
	var box := VBoxContainer.new()
	box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	box.add_theme_constant_override("separation", 0)
	if label != "":
		var l := Label.new()
		l.text = label
		l.add_theme_font_size_override("font_size", 10 if big else 8)
		box.add_child(l)
	var b := ProgressBar.new()
	b.show_percentage = false
	b.max_value = 100
	b.custom_minimum_size = Vector2(30, 9 if big else 5)
	var fill := StyleBoxFlat.new(); fill.bg_color = color
	var bg := StyleBoxFlat.new(); bg.bg_color = Color(0.2, 0.2, 0.2, 0.8)
	b.add_theme_stylebox_override("fill", fill)
	b.add_theme_stylebox_override("background", bg)
	box.add_child(b)
	parent.add_child(box)
	return b

func _place_minimap() -> void:
	var size := get_viewport().get_visible_rect().size
	var scale := 3.0 if map_big else 1.0
	minimap.size = Vector2(64, 64) * scale
	minimap.scale = Vector2.ONE
	minimap.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	minimap.stretch_mode = TextureRect.STRETCH_SCALE
	minimap.position = Vector2(size.x - minimap.size.x - 4, 74) if not map_big else (size - minimap.size) / 2
	toast_label.position = Vector2(12, size.y - 200)
	toast_label.size = Vector2(size.x - 24, 40)
	alert.position = Vector2(8, 150)
	alert.size = Vector2(size.x - 16 - 72, 0)
	_place_dot()

func _place_dot() -> void:
	if main and main.player:
		var s := minimap.size.x / 64.0
		dot.size = Vector2(maxf(2.0, s), maxf(2.0, s))
		dot.position = Vector2(main.cell) * s

func refresh_minimap() -> void:
	if main and main.world:
		minimap.texture = ImageTexture.create_from_image(main.world.minimap_image())

func toast(text: String) -> void:
	toast_label.text = text
	toast_label.modulate.a = 1.0
	_toast_left = 3.0

func refresh() -> void:
	if not Game.has_game():
		return
	var weather := Game.t("chuva", "rain") if Game.raining() else Game.t("céu limpo", "clear")
	clock.text = Game.t("Dia %d · %02d:%02d · %s · %s", "Day %d · %02d:%02d · %s · %s") % [Game.day(), Game.hour_of_day(),
		Game.minute_of_hour(), Game.season_name(), weather]
	var n: Dictionary = Game.state["player"]["needs"]
	for k in bars.keys():
		bars[k].value = float(n[k])
	var cn: Dictionary = Game.state["creature"]["needs"]
	for k in buddy_bars.keys():
		buddy_bars[k].value = float(cn[k])
	var u := Game.urgent()
	alert.visible = not u.is_empty()
	if alert.visible:
		alert_text.text = u["text"]
		alert_button.text = u["button"]
		alert_button.visible = u["button"] != ""
		_alert_action = u["action"]
	_place_dot()

func _process(delta: float) -> void:
	_t += delta
	if _t > 0.5:
		_t = 0.0
		refresh()
	if _toast_left > 0.0:
		_toast_left -= delta
		toast_label.modulate.a = clampf(_toast_left, 0.0, 1.0)
