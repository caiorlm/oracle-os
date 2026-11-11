extends Node2D
## Starter Valley: a 64×64 map of 16×16 tiles, generated in code with a fixed layout (house, farm,
## well, river, bridge, forest, rocks, village gate). Only the tiles on screen are drawn.

const W := 64
const H := 64
const TILE := 16
const T := TileArt.T

var tiles := PackedByteArray()
var atlas: ImageTexture
var crop_atlas: ImageTexture
var crop_rows := {}
var water_frame := 0
var _anim := 0.0

func _ready() -> void:
	atlas = TileArt.build()
	crop_atlas = TileArt.crops()
	var keys := Crops.DATA.keys()
	for i in keys.size():
		crop_rows[keys[i]] = i
	generate()

func idx(x: int, y: int) -> int:
	return y * W + x

func get_tile(x: int, y: int) -> int:
	if x < 0 or y < 0 or x >= W or y >= H:
		return T.TREE
	return tiles[idx(x, y)]

func set_tile(x: int, y: int, t: int) -> void:
	if x >= 0 and y >= 0 and x < W and y < H:
		tiles[idx(x, y)] = t

func river_x(y: int) -> int:
	return 44 + int(round(2.0 * sin(y * 0.15)))

func generate() -> void:
	tiles.resize(W * H)
	var rng := RandomNumberGenerator.new()
	rng.seed = 64064
	for y in H:
		for x in W:
			tiles[idx(x, y)] = T.FLOWERS if rng.randf() < 0.05 else T.GRASS
	# forest to the east of the river
	for y in H:
		for x in range(48, W):
			var r := rng.randf()
			tiles[idx(x, y)] = T.PINE if r < 0.32 else (T.TREE if r < 0.42 else (T.BUSH if r < 0.5 else T.FOREST_FLOOR))
	# rocky north
	for y in range(2, 7):
		for x in range(20, 42):
			if rng.randf() < 0.18:
				set_tile(x, y, T.ROCK)
	# river with sand banks
	for y in H:
		var cx := river_x(y)
		set_tile(cx - 2, y, T.SAND); set_tile(cx + 2, y, T.SAND)
		for x in range(cx - 1, cx + 2):
			set_tile(x, y, T.WATER)
	# border of trees
	for y in H:
		for x in W:
			if x < 2 or y < 2 or x >= W - 2 or y >= H - 2:
				set_tile(x, y, T.TREE if (x + y) % 3 != 0 else T.PINE)
	# farmhouse
	for x in range(10, 18):
		set_tile(x, 6, T.ROOF); set_tile(x, 7, T.ROOF)
		for y in range(8, 11):
			set_tile(x, y, T.WALL)
	set_tile(13, 10, T.DOOR)
	set_tile(20, 9, T.WELL)
	# farm field with a fence and an opening
	for x in range(8, 25):
		set_tile(x, 15, T.FENCE); set_tile(x, 29, T.FENCE)
	for y in range(15, 30):
		set_tile(8, y, T.FENCE); set_tile(24, y, T.FENCE)
	for y in range(16, 29):
		for x in range(9, 24):
			set_tile(x, y, T.GRASS)
	set_tile(16, 15, T.PATH)
	# paths: door → farm, along to the river, bridge, forest road, village gate
	for y in range(11, 15):
		set_tile(13, y, T.PATH)
	for x in range(13, 41):
		set_tile(x, 13, T.PATH)
	set_tile(16, 14, T.PATH)
	for y in range(13, 31):
		set_tile(40, y, T.PATH)
	for x in range(40, W - 2):
		var cx := river_x(30)
		set_tile(x, 30, T.BRIDGE if x >= cx - 2 and x <= cx + 2 else T.PATH)
		set_tile(x, 31, T.FOREST_FLOOR if x >= 48 else get_tile(x, 31))
		set_tile(x, 29, T.FOREST_FLOOR if x >= 48 else get_tile(x, 29))
	for x in range(14, 21):
		set_tile(x, 11, T.PATH if get_tile(x, 11) == T.GRASS or get_tile(x, 11) == T.FLOWERS else get_tile(x, 11))
	set_tile(W - 2, 30, T.GATE)

func blocked(x: int, y: int) -> bool:
	return get_tile(x, y) in TileArt.BLOCKING

func is_water_source(x: int, y: int) -> bool:
	var t := get_tile(x, y)
	return t == T.WATER or t == T.WATER2 or t == T.WELL

func in_farm(x: int, y: int) -> bool:
	return x >= 9 and x <= 23 and y >= 16 and y <= 28

var _last_center := Vector2(-1, -1)

func _process(delta: float) -> void:
	_anim += delta
	if _anim > 0.6:
		_anim = 0.0
		water_frame = 1 - water_frame
		queue_redraw()
	var cam := get_viewport().get_camera_2d()
	if cam:
		var c := cam.get_screen_center_position()
		if c != _last_center:
			_last_center = c
			queue_redraw()

func refresh() -> void:
	queue_redraw()

func _draw() -> void:
	var cam := get_viewport().get_camera_2d()
	var size := get_viewport_rect().size
	var center := cam.get_screen_center_position() if cam else size / 2
	var x0 := maxi(0, int((center.x - size.x / 2) / TILE) - 1)
	var y0 := maxi(0, int((center.y - size.y / 2) / TILE) - 1)
	var x1 := mini(W, x0 + int(size.x / TILE) + 3)
	var y1 := mini(H, y0 + int(size.y / TILE) + 3)
	var winter := Game.has_game() and Game.season() == 3
	var autumn := Game.has_game() and Game.season() == 2
	var plots: Dictionary = Game.state.get("plots", {})
	for y in range(y0, y1):
		for x in range(x0, x1):
			var t := tiles[idx(x, y)]
			if t == T.WATER and water_frame == 1:
				t = T.WATER2
			var key := "%d,%d" % [x, y]
			if plots.has(key):
				t = T.SOIL_WET if float(plots[key]["moisture"]) >= 30.0 else T.SOIL
			var mod := Color.WHITE
			if winter and t in [T.GRASS, T.FLOWERS, T.FOREST_FLOOR, T.TREE, T.PINE, T.BUSH, T.BUSH_EMPTY]:
				mod = Color(0.86, 0.92, 1.0)
			elif autumn and t in [T.TREE, T.BUSH]:
				mod = Color(1.0, 0.85, 0.65)
			if t == T.BUSH and Game.has_game() and int(Game.state["bushes"].get(key, -1)) == Game.day():
				t = T.BUSH_EMPTY
			draw_texture_rect_region(atlas, Rect2(x * TILE, y * TILE, TILE, TILE), Rect2(t * TILE, 0, TILE, TILE), mod)
			if plots.has(key):
				var p: Dictionary = plots[key]
				if p["crop"] != "":
					var col := 5 if p["dead"] else Crops.stage(p["crop"], float(p["growth"]))
					draw_texture_rect_region(crop_atlas, Rect2(x * TILE, y * TILE, TILE, TILE),
						Rect2(col * TILE, int(crop_rows[p["crop"]]) * TILE, TILE, TILE))

## Small image of the whole valley for the minimap (one pixel per tile).
func minimap_image() -> Image:
	var img := Image.create_empty(W, H, false, Image.FORMAT_RGBA8)
	var colors := {T.GRASS: Color8(92, 168, 76), T.FLOWERS: Color8(110, 176, 90), T.PATH: Color8(196, 164, 110),
		T.WATER: Color8(52, 116, 196), T.SAND: Color8(222, 204, 150), T.TREE: Color8(40, 110, 50), T.PINE: Color8(28, 84, 52),
		T.BUSH: Color8(60, 130, 70), T.ROCK: Color8(140, 140, 146), T.FENCE: Color8(160, 116, 70), T.WALL: Color8(206, 176, 132),
		T.ROOF: Color8(176, 64, 52), T.DOOR: Color8(110, 70, 40), T.WELL: Color8(150, 150, 160), T.BRIDGE: Color8(150, 104, 60),
		T.GATE: Color8(240, 210, 90), T.FOREST_FLOOR: Color8(58, 120, 62)}
	for y in H:
		for x in W:
			img.set_pixel(x, y, colors.get(tiles[idx(x, y)], Color8(92, 168, 76)))
	for key in Game.state.get("plots", {}).keys():
		var parts: PackedStringArray = key.split(",")
		img.set_pixel(int(parts[0]), int(parts[1]), Color8(132, 92, 58))
	return img
