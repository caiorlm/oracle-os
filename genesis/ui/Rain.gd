extends CanvasLayer
## Light rain over the screen (cheap: a few dozen lines, redrawn at ~20 FPS).

var canvas := Control.new()
var drops: Array[Vector2] = []
var _t := 0.0

func _ready() -> void:
	layer = 1
	canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	canvas.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(canvas)
	canvas.draw.connect(_paint)
	for i in 70:
		drops.append(Vector2(randf() * 400.0, randf() * 900.0))

func _process(delta: float) -> void:
	if not visible:
		return
	_t += delta
	if _t < 0.05:
		return
	var size := canvas.size
	for i in drops.size():
		var d := drops[i] + Vector2(-2.0, 14.0) * (_t / 0.05)
		if d.y > size.y:
			d = Vector2(randf() * (size.x + 40.0), -10.0)
		drops[i] = d
	_t = 0.0
	canvas.queue_redraw()

func _paint() -> void:
	for d in drops:
		canvas.draw_line(d, d + Vector2(-1, 5), Color(0.7, 0.8, 1.0, 0.55), 1.0)
