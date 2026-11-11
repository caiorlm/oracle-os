extends Control
## Message box at the bottom of the screen. A (or OK) closes it.

signal closed

var title := Label.new()
var body := Label.new()
var box := PanelContainer.new()
var _opened_at := 0

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	visible = false
	box.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
	box.offset_left = 8; box.offset_right = -8; box.offset_top = -230; box.offset_bottom = -110
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.98, 0.95, 0.86); sb.border_color = Color(0.25, 0.18, 0.12)
	sb.set_border_width_all(2); sb.set_corner_radius_all(4); sb.set_content_margin_all(8)
	box.add_theme_stylebox_override("panel", sb)
	add_child(box)
	var v := VBoxContainer.new()
	box.add_child(v)
	title.add_theme_color_override("font_color", Color(0.45, 0.2, 0.1))
	title.add_theme_font_size_override("font_size", 12)
	body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.add_theme_color_override("font_color", Color(0.12, 0.1, 0.08))
	body.add_theme_font_size_override("font_size", 11)
	var ok := Button.new()
	ok.text = "OK"
	ok.custom_minimum_size = Vector2(70, 26)
	ok.size_flags_horizontal = Control.SIZE_SHRINK_END
	ok.pressed.connect(close)
	v.add_child(title); v.add_child(body); v.add_child(ok)

func show_text(t: String, text: String) -> void:
	title.text = t
	body.text = text
	visible = true
	_opened_at = Time.get_ticks_msec()
	await get_tree().process_frame
	box.offset_top = -110 - box.get_combined_minimum_size().y

func close() -> void:
	if visible:
		visible = false
		closed.emit()

func _process(_delta: float) -> void:
	if visible and Time.get_ticks_msec() - _opened_at > 300 and Input.is_action_just_pressed("ui_accept"):
		close()
