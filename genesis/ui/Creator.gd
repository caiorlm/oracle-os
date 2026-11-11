extends Control
## Character creation: name, gender, interface age band and the companion's name. The look is
## generated from 256 cryptographic random bits; "Outro visual" draws a new set before confirming.

signal done

var name_edit := LineEdit.new()
var buddy_edit := LineEdit.new()
var gender := "female"
var band := "11-17"
var r := PackedByteArray()
var ts := 0
var preview := TextureRect.new()
var buddy_preview := TextureRect.new()
var gender_buttons := {}
var band_buttons := {}

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var bg := ColorRect.new()
	bg.color = Color(0.07, 0.12, 0.09)
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var scroll := ScrollContainer.new()
	scroll.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	var v := VBoxContainer.new()
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_theme_constant_override("separation", 6)
	var margin := MarginContainer.new()
	margin.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for side in ["left", "right", "top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 14)
	margin.add_child(v)
	scroll.add_child(margin)
	var lang := Button.new()
	lang.text = "English" if not Game.english else "Português"
	lang.size_flags_horizontal = Control.SIZE_SHRINK_END
	lang.pressed.connect(func():
		Game.english = not Game.english
		get_tree().reload_current_scene())
	v.add_child(lang)
	var title := Label.new()
	title.text = "GENESIS"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 26)
	title.add_theme_color_override("font_color", Color(0.45, 0.85, 0.55))
	v.add_child(title)
	var sub := Label.new()
	sub.text = Game.t("Legado da Vida — uma fazenda, um companheiro e uma linhagem.", "Legacy of Life — a farm, a companion and a lineage.")
	sub.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	sub.add_theme_font_size_override("font_size", 10)
	v.add_child(sub)
	var pics := HBoxContainer.new()
	pics.alignment = BoxContainer.ALIGNMENT_CENTER
	for t in [preview, buddy_preview]:
		t.custom_minimum_size = Vector2(72, 96) if t == preview else Vector2(64, 64)
		t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		t.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		pics.add_child(t)
	v.add_child(pics)
	var reroll := Button.new()
	reroll.text = Game.t("Outro visual", "Another look")
	reroll.pressed.connect(_reroll)
	v.add_child(reroll)
	v.add_child(_caption(Game.t("Nome", "Name")))
	name_edit.placeholder_text = Game.t("O seu nome", "Your name")
	name_edit.max_length = 16
	name_edit.text_changed.connect(func(_t): _update())
	v.add_child(name_edit)
	v.add_child(_caption(Game.t("Personagem", "Character")))
	var gh := HBoxContainer.new()
	for pair in [["female", Game.t("Feminino", "Female")], ["male", Game.t("Masculino", "Male")]]:
		var b := Button.new()
		b.text = pair[1]
		b.toggle_mode = true
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var g: String = pair[0]
		b.pressed.connect(func(): gender = g; _update())
		gender_buttons[g] = b
		gh.add_child(b)
	v.add_child(gh)
	v.add_child(_caption(Game.t("Idade de quem joga (muda só a interface)", "Player's age (changes only the interface)")))
	var bh := HBoxContainer.new()
	for pair in [["5-10", "5–10"], ["11-17", "11–17"], ["18+", "18+"]]:
		var b := Button.new()
		b.text = pair[1]
		b.toggle_mode = true
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var k: String = pair[0]
		b.pressed.connect(func(): band = k; _update())
		band_buttons[k] = b
		bh.add_child(b)
	v.add_child(bh)
	v.add_child(_caption(Game.t("Nome do bichinho", "Companion's name")))
	buddy_edit.placeholder_text = Game.t("Ex.: Pipoca", "E.g. Popcorn")
	buddy_edit.max_length = 12
	v.add_child(buddy_edit)
	var go := Button.new()
	go.text = Game.t("COMEÇAR", "START")
	go.custom_minimum_size = Vector2(0, 40)
	go.add_theme_font_size_override("font_size", 16)
	go.pressed.connect(_start)
	v.add_child(go)
	var note := Label.new()
	note.text = Game.t("O visual e a identidade ficam para sempre. As roupas podem mudar com o jogo.",
		"The look and identity are permanent. Clothes can change during the game.")
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	note.add_theme_font_size_override("font_size", 9)
	note.add_theme_color_override("font_color", Color(0.7, 0.75, 0.7))
	v.add_child(note)
	_reroll()

func _name() -> String:
	var n := name_edit.text.strip_edges()
	return n if n != "" else Game.t("Viajante", "Traveller")

func _caption(text: String) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", 11)
	l.add_theme_color_override("font_color", Color(0.85, 0.9, 0.8))
	return l

func _reroll() -> void:
	r = Identity.random_bytes(32)
	ts = int(Time.get_unix_time_from_system())
	_update()

func _preview_id() -> String:
	return Identity.make_id(_name(), gender, ts, r)

func _update() -> void:
	for k in gender_buttons.keys():
		gender_buttons[k].button_pressed = k == gender
	for k in band_buttons.keys():
		band_buttons[k].button_pressed = k == band
	var id := _preview_id()
	preview.texture = Sprites.tex(Sprites.person(Identity.visual_genome(id), gender, 0, 0))
	buddy_preview.texture = Sprites.tex(Sprites.creature(Identity.creature_genome(id), 0))

func _start() -> void:
	var n := _name()
	var b := buddy_edit.text.strip_edges()
	if b == "":
		b = Game.t("Pipoca", "Popcorn")
	# Same name, gender, timestamp and R as the preview, so the look shown is the look kept.
	Game.new_game(n, gender, band, b, r, ts)
	done.emit()
