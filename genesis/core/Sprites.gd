class_name Sprites
extends RefCounted
## Procedural pixel art built from the genome at runtime: no copied sprites. Layers of head,
## hair, eyes, clothing pattern and palette make every character and creature different.

const OUTLINE := Color8(28, 22, 30)
const SKIN := [Color8(255, 224, 196), Color8(241, 194, 155), Color8(224, 172, 120), Color8(198, 134, 90),
	Color8(161, 102, 62), Color8(120, 74, 44), Color8(92, 56, 36), Color8(255, 214, 170),
	Color8(234, 184, 140), Color8(210, 150, 100), Color8(176, 118, 74), Color8(140, 88, 54)]
const HAIR := [Color8(24, 20, 22), Color8(66, 40, 24), Color8(110, 66, 36), Color8(160, 110, 60),
	Color8(232, 200, 110), Color8(190, 70, 30), Color8(130, 50, 34), Color8(150, 150, 156),
	Color8(236, 236, 230), Color8(30, 34, 60), Color8(220, 120, 50), Color8(246, 230, 190)]

static func _eye_color(i: int) -> Color:
	return Color.from_hsv(fmod(i * 0.137, 1.0), 0.55, 0.45 + 0.03 * (i % 6))

static func _cloth(i: int) -> Array:
	var main := Color.from_hsv(fmod(i / 32.0 + 0.03, 1.0), 0.55, 0.78)
	var second := Color.from_hsv(fmod(i / 32.0 + 0.5, 1.0), 0.45, 0.9)
	return [main, second, main.darkened(0.45)]

static func _blank(w: int, h: int) -> Image:
	return Image.create_empty(w, h, false, Image.FORMAT_RGBA8)

static func _rect(img: Image, x: int, y: int, w: int, h: int, c: Color) -> void:
	for yy in range(y, y + h):
		for xx in range(x, x + w):
			if xx >= 0 and yy >= 0 and xx < img.get_width() and yy < img.get_height():
				img.set_pixel(xx, yy, c)

static func _outline(img: Image) -> void:
	var w := img.get_width()
	var h := img.get_height()
	var marks: Array[Vector2i] = []
	for y in h:
		for x in w:
			if img.get_pixel(x, y).a > 0.0:
				continue
			for d in [Vector2i(1, 0), Vector2i(-1, 0), Vector2i(0, 1), Vector2i(0, -1)]:
				var p: Vector2i = Vector2i(x, y) + d
				if p.x >= 0 and p.y >= 0 and p.x < w and p.y < h and img.get_pixel(p.x, p.y).a > 0.0 \
						and img.get_pixel(p.x, p.y) != OUTLINE:
					marks.append(Vector2i(x, y))
					break
	for m in marks:
		img.set_pixelv(m, OUTLINE)

## dir: 0 down, 1 up, 2 left, 3 right; frame 0/1 (walking step).
static func person(g: Dictionary, gender: String, dir: int, frame: int) -> Image:
	var img := _blank(16, 24)
	var skin: Color = SKIN[int(g["skin_palette"]) % SKIN.size()]
	var hair: Color = HAIR[int(g["hair_color"]) % HAIR.size()]
	var cloth := _cloth(int(g["clothing_palette"]))
	var side := dir >= 2
	var face := int(g["face_shape"])
	var head_w := (8 + face % 3) if not side else 7
	var hx := 8 - head_w / 2
	var body_w := (8 + int(g["body_shape"]) % 3) if not side else 6
	var bx := 8 - body_w / 2
	var step := 1 if frame == 1 else 0
	# legs and shoes
	var pants: Color = cloth[2]
	if side:
		_rect(img, 6, 18, 2, 4 - step, pants)
		_rect(img, 8, 18 + step, 2, 4 - step, pants.darkened(0.15))
		_rect(img, 6 - step, 22 - step, 3, 1, OUTLINE.lightened(0.15))
		_rect(img, 8 + step, 22, 3, 1, OUTLINE.lightened(0.15))
	else:
		_rect(img, bx + 1, 18, 2, 4 - step, pants)
		_rect(img, bx + body_w - 3, 18 + step, 2, 4 - step, pants)
		_rect(img, bx + 1, 22 - step, 2, 1, OUTLINE.lightened(0.2))
		_rect(img, bx + body_w - 3, 22, 2, 1, OUTLINE.lightened(0.2))
	# shirt with procedural pattern
	var pattern := int(g["clothing_pattern"]) % 4
	for y in range(11, 18):
		for x in range(bx, bx + body_w):
			var c: Color = cloth[0]
			if pattern == 1 and y % 2 == 0:
				c = cloth[1]
			elif pattern == 2 and (x - bx) % 3 == 1:
				c = cloth[1]
			elif pattern == 3 and (x + y) % 4 == 0:
				c = cloth[1]
			img.set_pixel(x, y, c)
	# arms
	var swing := 1 if frame == 1 else 0
	if side:
		_rect(img, 7 + (swing if dir == 3 else -swing), 12, 2, 5, cloth[0].darkened(0.15))
		_rect(img, 7 + (swing if dir == 3 else -swing), 17, 2, 1, skin)
	else:
		_rect(img, bx - 1, 12 + swing, 1, 5, cloth[0].darkened(0.15))
		_rect(img, bx + body_w, 12 + (1 - swing), 1, 5, cloth[0].darkened(0.15))
		_rect(img, bx - 1, 17 + swing, 1, 1, skin)
		_rect(img, bx + body_w, 17 + (1 - swing), 1, 1, skin)
	# head
	_rect(img, hx, 3, head_w, 8, skin)
	if (face / 3) % 2 == 1:   # rounder jaw
		img.set_pixel(hx, 10, Color(0, 0, 0, 0))
		img.set_pixel(hx + head_w - 1, 10, Color(0, 0, 0, 0))
	# hair
	var style := int(g["hair_style"])
	var length := style % 4
	var top := (style / 4) % 3
	var fringe := (style / 12) % 3
	if gender == "female" and length == 3:
		length = 1
	var cap_h := 2 if length != 3 else 1
	_rect(img, hx - 1, 2, head_w + 2, cap_h + 1, hair)
	if top == 1:
		for x in range(hx, hx + head_w, 2):
			img.set_pixel(x, 1, hair)
	elif top == 2:
		_rect(img, 7, 0, 3, 2, hair)
	if dir == 1:  # back of the head
		_rect(img, hx - 1, 2, head_w + 2, 7 + length * 2, hair)
	else:
		if length >= 1 and length != 3:
			_rect(img, hx - 1, 4, 1, 3 + length * 2, hair)
			if not side or dir == 3:
				_rect(img, hx + head_w, 4, 1, 3 + length * 2, hair)
		if fringe == 1:
			_rect(img, hx, 4, head_w / 2, 1, hair)
		elif fringe == 2:
			_rect(img, hx, 4, head_w, 1, hair)
		# eyes and mouth
		var eye := _eye_color(int(g["eye_color"]))
		var shape := int(g["eye_shape"]) % 4
		var ey := 6
		var eyes_x: Array = [hx + 2, hx + head_w - 3] if not side else ([hx + 1] if dir == 2 else [hx + head_w - 2])
		for ex in eyes_x:
			img.set_pixel(ex, ey, eye)
			if shape == 1:
				img.set_pixel(ex, ey + 1, eye)
			elif shape == 2:
				img.set_pixel(ex + (1 if ex < 8 else -1), ey, eye.darkened(0.3))
			elif shape == 3:
				img.set_pixel(ex, ey - 1, Color(1, 1, 1))
			if gender == "female":
				img.set_pixel(ex, ey - 1 if shape != 3 else ey - 2, OUTLINE.lightened(0.1))
		if not side:
			img.set_pixel(8, 9, skin.darkened(0.3))
		else:
			img.set_pixel(hx + (1 if dir == 2 else head_w - 2), 9, skin.darkened(0.3))
	# identity marking (permanent): a small freckle/mark from the marking seed
	var mark := int(g["marking_seed"])
	if dir == 0 and mark % 3 == 0:
		img.set_pixel(hx + 1 + (mark >> 3) % maxi(1, head_w - 2), 8, skin.darkened(0.18))
	_outline(img)
	return img

static func creature(g: Dictionary, frame: int, sick: bool = false) -> Image:
	var img := _blank(16, 16)
	var hue := int(g["hue"]) / 24.0
	var body := Color.from_hsv(hue, 0.5, 0.85)
	if sick:
		body = body.lerp(Color(0.6, 0.6, 0.55), 0.6)
	var belly := Color.from_hsv(hue, 0.18, 0.98)
	var dark := body.darkened(0.35)
	var bob := frame
	var shape := int(g["body"]) % 4
	var rx: float = [5.5, 4.5, 6.5, 5.0][shape]
	var ry: float = [5.0, 6.0, 4.5, 5.5][shape]
	var cy := 9.5 + bob * 0.5
	for y in 16:
		for x in 16:
			var dx := (x + 0.5 - 8.0) / rx
			var dy := (y + 0.5 - cy) / ry
			if shape == 3 and y + 0.5 > cy:
				dx = (x + 0.5 - 8.0) / (rx + 1.0)
			if dx * dx + dy * dy <= 1.0:
				img.set_pixel(x, y, body)
				var bx := (x + 0.5 - 8.0) / (rx * 0.55)
				var by := (y + 0.5 - cy - 1.5) / (ry * 0.5)
				if int(g["belly"]) > 0 and bx * bx + by * by <= 1.0:
					img.set_pixel(x, y, belly)
	var rng := RandomNumberGenerator.new()
	rng.seed = int(g["marking_seed"])
	for i in int(g["spots"]):
		var px := rng.randi_range(4, 11)
		var py := rng.randi_range(6, 13)
		if img.get_pixel(px, py) == body:
			img.set_pixel(px, py, dark)
	var top := int(cy - ry)
	match int(g["ears"]) % 5:
		1:
			_rect(img, 4, top - 1, 1, 2, body); _rect(img, 11, top - 1, 1, 2, body)
		2:
			_rect(img, 4, top, 2, 2, dark); _rect(img, 10, top, 2, 2, dark)
		3:
			_rect(img, 5, top - 3, 1, 4, body); _rect(img, 10, top - 3, 1, 4, body)
		4:
			img.set_pixel(8, top - 2, dark); img.set_pixel(8, top - 1, body)
	match int(g["tail"]) % 4:
		1:
			_rect(img, 13, int(cy), 2, 1, body); img.set_pixel(14, int(cy) - 1, body)
		2:
			img.set_pixel(13, int(cy), dark); img.set_pixel(14, int(cy) - 1, dark)
		3:
			_rect(img, 13, int(cy) - 1, 2, 2, belly)
	var eye_y := int(cy) - 1
	match int(g["eyes"]) % 4:
		0:
			img.set_pixel(6, eye_y, OUTLINE); img.set_pixel(9, eye_y, OUTLINE)
		1:
			_rect(img, 6, eye_y - 1, 1, 2, OUTLINE); _rect(img, 9, eye_y - 1, 1, 2, OUTLINE)
		2:
			img.set_pixel(6, eye_y, OUTLINE); img.set_pixel(9, eye_y, OUTLINE)
			img.set_pixel(6, eye_y - 1, Color(1, 1, 1)); img.set_pixel(9, eye_y - 1, Color(1, 1, 1))
		3:
			_rect(img, 5, eye_y, 2, 1, OUTLINE); _rect(img, 9, eye_y, 2, 1, OUTLINE)
	if not sick:
		img.set_pixel(7, eye_y + 2, dark); img.set_pixel(8, eye_y + 2, dark)
	_outline(img)
	return img

static func egg(pattern: int, hue: float, crack: float = 0.0) -> Image:
	var img := _blank(16, 16)
	var base := Color.from_hsv(hue, 0.18, 0.96)
	var mark := Color.from_hsv(fmod(hue + 0.08, 1.0), 0.55, 0.7)
	var rng := RandomNumberGenerator.new()
	rng.seed = pattern
	var kind := pattern % 6
	for y in 16:
		for x in 16:
			var dx := (x + 0.5 - 8.0) / 5.0
			var dy := (y + 0.5 - 9.0) / (6.5 if y < 9 else 5.5)
			if dx * dx + dy * dy <= 1.0:
				var c := base
				if kind == 1 and y % 4 == 0:
					c = mark
				elif kind == 2 and (x + y) % 5 == 0:
					c = mark
				elif kind == 3 and absi(((x * 2 + y) % 8) - 4) <= 1 and y % 3 == 0:
					c = mark
				elif kind == 4 and y >= 8 and y <= 9:
					c = mark
				img.set_pixel(x, y, c)
	if kind == 0 or kind == 5:
		for i in (6 if kind == 0 else 14):
			var px := rng.randi_range(4, 11)
			var py := rng.randi_range(4, 13)
			if img.get_pixel(px, py).a > 0:
				img.set_pixel(px, py, mark)
	if crack > 0.5:
		for x in range(5, 11):
			img.set_pixel(x, 7 + (x % 2), OUTLINE)
	_outline(img)
	return img

static func tex(img: Image) -> ImageTexture:
	return ImageTexture.create_from_image(img)
