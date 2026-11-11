extends CanvasLayer
## Multi-touch controls: D-pad (hold to walk), A (act / OK) and B (menu). TouchScreenButtons feed
## the same input actions as the keyboard, so the game logic has one input path.

const PAD := 34

func _ready() -> void:
	layer = 5
	get_viewport().size_changed.connect(_layout)
	_layout()

func _arrow(dir: int) -> ImageTexture:
	var img := Image.create_empty(PAD, PAD, false, Image.FORMAT_RGBA8)
	for y in PAD:
		for x in PAD:
			var edge := x < 2 or y < 2 or x >= PAD - 2 or y >= PAD - 2
			img.set_pixel(x, y, Color(0.1, 0.12, 0.1, 0.55) if not edge else Color(0.9, 0.95, 0.85, 0.5))
	for i in 9:
		for j in range(-i, i + 1):
			var p := Vector2i(PAD / 2 + j, 11 + i)
			match dir:
				1: pass
				0: p = Vector2i(PAD / 2 + j, PAD - 12 - i)
				2: p = Vector2i(11 + i, PAD / 2 + j)
				3: p = Vector2i(PAD - 12 - i, PAD / 2 + j)
			img.set_pixelv(p, Color(0.95, 0.98, 0.9, 0.9))
	return ImageTexture.create_from_image(img)

func _round(letter_color: Color, size: int) -> ImageTexture:
	var img := Image.create_empty(size, size, false, Image.FORMAT_RGBA8)
	var r := size / 2.0
	for y in size:
		for x in size:
			var d := Vector2(x + 0.5 - r, y + 0.5 - r).length()
			if d <= r:
				img.set_pixel(x, y, letter_color if d < r - 2 else Color(0.95, 0.95, 0.9, 0.7))
	return ImageTexture.create_from_image(img)

func _layout() -> void:
	for c in get_children():
		c.queue_free()
	var size := get_viewport().get_visible_rect().size
	var base := Vector2(10, size.y - PAD * 3 - 14)
	var spots := {"ui_up": Vector2(PAD, 0), "ui_down": Vector2(PAD, PAD * 2), "ui_left": Vector2(0, PAD), "ui_right": Vector2(PAD * 2, PAD)}
	var dirs := {"ui_down": 0, "ui_up": 1, "ui_left": 2, "ui_right": 3}
	for action in spots.keys():
		var b := TouchScreenButton.new()
		b.texture_normal = _arrow(dirs[action])
		b.action = action
		b.position = base + spots[action]
		b.passby_press = true
		b.visibility_mode = TouchScreenButton.VISIBILITY_ALWAYS
		add_child(b)
	var a := TouchScreenButton.new()
	a.texture_normal = _round(Color(0.22, 0.62, 0.36, 0.75), 50)
	a.action = "ui_accept"
	a.position = Vector2(size.x - 62, size.y - PAD * 2 - 30)
	add_child(a)
	var bb := TouchScreenButton.new()
	bb.texture_normal = _round(Color(0.65, 0.3, 0.25, 0.75), 36)
	bb.action = "ui_cancel"
	bb.position = Vector2(size.x - 104, size.y - PAD - 20)
	add_child(bb)
	for pair in [[a, "A", Vector2(19, 15)], [bb, "B", Vector2(12, 8)]]:
		var l := Label.new()
		l.text = pair[1]
		l.position = pair[2]
		l.add_theme_font_size_override("font_size", 14)
		(pair[0] as Node).add_child(l)
