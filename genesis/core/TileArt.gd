class_name TileArt
extends RefCounted
## Original 16×16 tiles drawn in code into one atlas (no imported tilesets).

enum T { GRASS, FLOWERS, PATH, WATER, WATER2, SAND, TREE, BUSH, BUSH_EMPTY, ROCK, FENCE, SOIL, SOIL_WET,
	WALL, ROOF, DOOR, WELL, BRIDGE, GATE, FOREST_FLOOR, PINE, COUNT }

const BLOCKING := [T.WATER, T.WATER2, T.TREE, T.BUSH, T.BUSH_EMPTY, T.ROCK, T.FENCE, T.WALL, T.ROOF,
	T.WELL, T.GATE, T.PINE]

static func _noise(rng: RandomNumberGenerator, img: Image, ox: int, base: Color, spread: float, amount: int) -> void:
	for i in amount:
		var x := rng.randi_range(0, 15)
		var y := rng.randi_range(0, 15)
		var c := base.lightened(spread) if rng.randf() < 0.5 else base.darkened(spread)
		img.set_pixel(ox + x, y, c)

static func _fill(img: Image, ox: int, c: Color) -> void:
	for y in 16:
		for x in 16:
			img.set_pixel(ox + x, y, c)

static func _r(img: Image, ox: int, x: int, y: int, w: int, h: int, c: Color) -> void:
	for yy in range(y, mini(16, y + h)):
		for xx in range(x, mini(16, x + w)):
			img.set_pixel(ox + xx, yy, c)

static func build() -> ImageTexture:
	var n: int = T.COUNT
	var img := Image.create_empty(16 * n, 16, false, Image.FORMAT_RGBA8)
	var rng := RandomNumberGenerator.new()
	rng.seed = 2026
	var grass := Color8(92, 168, 76)
	var dark_grass := Color8(58, 120, 62)
	for t in n:
		var ox := t * 16
		match t:
			T.GRASS, T.FLOWERS, T.TREE, T.BUSH, T.BUSH_EMPTY, T.ROCK, T.FENCE, T.WELL, T.GATE:
				_fill(img, ox, grass)
				_noise(rng, img, ox, grass, 0.12, 26)
			T.FOREST_FLOOR, T.PINE:
				_fill(img, ox, dark_grass)
				_noise(rng, img, ox, dark_grass, 0.12, 30)
			T.PATH:
				_fill(img, ox, Color8(196, 164, 110))
				_noise(rng, img, ox, Color8(196, 164, 110), 0.1, 30)
			T.WATER, T.WATER2:
				_fill(img, ox, Color8(52, 116, 196))
				for i in 5:
					var y := (i * 3 + (2 if t == T.WATER2 else 0)) % 16
					var x := (i * 5 + (3 if t == T.WATER2 else 0)) % 12
					_r(img, ox, x, y, 4, 1, Color8(120, 176, 230))
			T.SAND:
				_fill(img, ox, Color8(222, 204, 150))
				_noise(rng, img, ox, Color8(222, 204, 150), 0.08, 20)
			T.SOIL, T.SOIL_WET:
				var soil := Color8(132, 92, 58) if t == T.SOIL else Color8(86, 58, 38)
				_fill(img, ox, soil)
				for y in [3, 7, 11, 15]:
					_r(img, ox, 0, y, 16, 1, soil.darkened(0.25))
			T.WALL:
				_fill(img, ox, Color8(206, 176, 132))
				for y in [4, 9, 14]:
					_r(img, ox, 0, y, 16, 1, Color8(150, 120, 86))
				_r(img, ox, 4, 5, 4, 3, Color8(120, 170, 210))
			T.ROOF:
				_fill(img, ox, Color8(176, 64, 52))
				for y in range(0, 16, 3):
					_r(img, ox, 0, y, 16, 1, Color8(130, 44, 38))
			T.DOOR:
				_fill(img, ox, Color8(206, 176, 132))
				_r(img, ox, 4, 2, 8, 14, Color8(110, 70, 40))
				img.set_pixel(ox + 10, 9, Color8(240, 210, 90))
			T.BRIDGE:
				_fill(img, ox, Color8(52, 116, 196))
				_r(img, ox, 0, 1, 16, 14, Color8(150, 104, 60))
				for x in range(0, 16, 4):
					_r(img, ox, x, 1, 1, 14, Color8(110, 74, 42))
		# decorations on top of grass
		match t:
			T.FLOWERS:
				for i in 5:
					var c: Color = [Color8(240, 230, 90), Color8(240, 120, 160), Color8(250, 250, 250)][i % 3]
					img.set_pixel(ox + rng.randi_range(1, 14), rng.randi_range(1, 14), c)
			T.TREE, T.PINE:
				var leaf := Color8(44, 120, 56) if t == T.TREE else Color8(30, 92, 58)
				_r(img, ox, 7, 11, 2, 5, Color8(110, 74, 42))
				for y in 12:
					var half := (6 - absi(y - 6)) if t == T.TREE else (y / 2 + 1)
					_r(img, ox, 8 - half, y, half * 2, 1, leaf if (y + half) % 4 != 0 else leaf.lightened(0.15))
			T.BUSH, T.BUSH_EMPTY:
				for y in range(4, 15):
					var half := 6 - absi(y - 9)
					_r(img, ox, 8 - half, y, half * 2, 1, Color8(52, 136, 64))
				if t == T.BUSH:
					for p in [Vector2i(5, 7), Vector2i(10, 8), Vector2i(7, 11), Vector2i(11, 12), Vector2i(4, 11)]:
						img.set_pixel(ox + p.x, p.y, Color8(150, 60, 190))
			T.ROCK:
				for y in range(5, 14):
					var half := 6 - absi(y - 9) / 2
					_r(img, ox, 8 - half, y, half * 2, 1, Color8(140, 140, 146) if y < 10 else Color8(110, 110, 118))
			T.FENCE:
				_r(img, ox, 0, 6, 16, 2, Color8(160, 116, 70))
				_r(img, ox, 0, 11, 16, 2, Color8(160, 116, 70))
				_r(img, ox, 2, 4, 2, 11, Color8(130, 90, 54))
				_r(img, ox, 12, 4, 2, 11, Color8(130, 90, 54))
			T.WELL:
				_r(img, ox, 3, 6, 10, 8, Color8(150, 150, 156))
				_r(img, ox, 5, 7, 6, 4, Color8(40, 80, 150))
				_r(img, ox, 2, 2, 12, 2, Color8(150, 90, 50))
				_r(img, ox, 3, 4, 1, 3, Color8(110, 70, 40))
				_r(img, ox, 12, 4, 1, 3, Color8(110, 70, 40))
			T.GATE:
				_r(img, ox, 2, 2, 3, 14, Color8(120, 90, 60))
				_r(img, ox, 11, 2, 3, 14, Color8(120, 90, 60))
				_r(img, ox, 2, 2, 12, 3, Color8(170, 130, 80))
	return ImageTexture.create_from_image(img)

## Crop overlay tiles: one row per crop, columns = stages 0..4 plus a wilted column (5).
static func crops() -> ImageTexture:
	var keys := Crops.DATA.keys()
	var img := Image.create_empty(16 * 6, 16 * keys.size(), false, Image.FORMAT_RGBA8)
	var stem := Color8(70, 150, 60)
	for row in keys.size():
		var produce: Color = Crops.DATA[keys[row]]["color"]
		var oy := row * 16
		for st in 6:
			var ox := st * 16
			var put := func(x: int, y: int, c: Color) -> void:
				if x >= 0 and x < 16 and y >= 0 and y < 16:
					img.set_pixel(ox + x, oy + y, c)
			match st:
				0:
					for p in [Vector2i(5, 9), Vector2i(10, 8), Vector2i(8, 12)]:
						put.call(p.x, p.y, Color8(230, 210, 150))
				1:
					put.call(8, 11, stem); put.call(8, 10, stem); put.call(7, 9, stem); put.call(9, 9, stem)
				2:
					for y in range(6, 13):
						put.call(8, y, stem)
					for d in [-1, 1]:
						put.call(8 + d, 9, stem); put.call(8 + 2 * d, 8, stem); put.call(8 + d, 11, stem)
				3, 4:
					for y in range(3, 13):
						put.call(8, y, stem)
					for d in [-1, 1]:
						for k in 3:
							put.call(8 + d * (k + 1), 6 + k * 2, stem)
					if st == 4:
						var spots := [Vector2i(5, 7), Vector2i(11, 8), Vector2i(8, 4), Vector2i(6, 10), Vector2i(10, 11)]
						for p in spots:
							put.call(p.x, p.y, produce); put.call(p.x + 1, p.y, produce)
							put.call(p.x, p.y + 1, produce.darkened(0.2))
				5:
					for y in range(8, 13):
						put.call(8, y, Color8(120, 90, 50))
					put.call(7, 9, Color8(120, 90, 50)); put.call(9, 10, Color8(120, 90, 50))
	return ImageTexture.create_from_image(img)
