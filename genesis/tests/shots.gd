extends SceneTree
## Screenshots for visual checks (needs a display: xvfb-run).

func snap(name: String) -> void:
	await process_frame
	await process_frame
	await process_frame
	var img := root.get_texture().get_image()
	img.save_png("res://../logs/genesis/%s.png" % name)
	print("captura ", name, " ", img.get_size())

func _initialize() -> void:
	DisplayServer.window_set_size(Vector2i(720, 1560))
	DirAccess.remove_absolute("user://genesis_save.json")
	DirAccess.remove_absolute("user://genesis_save.bak")
	var game = root.get_node("Game")
	game.state = {}
	var main = load("res://ui/Main.gd").new()
	root.add_child(main)
	await process_frame
	main.creator.name_edit.text = "Lia"
	main.creator._update()
	await snap("1-criacao")
	main.creator.buddy_edit.text = "Fofo"
	main.creator._start()
	await process_frame
	await snap("2-boas-vindas")
	main.dialog.close()
	# a farm with every crop at different stages
	var crops := Crops.DATA.keys()
	for i in crops.size():
		for st in 5:
			var key := "%d,%d" % [10 + i, 18 + st]
			game.state["plots"][key] = game._plot()
			game.state["plots"][key]["crop"] = crops[i]
			game.state["plots"][key]["growth"] = Crops.required(crops[i]) * [0.0, 0.2, 0.5, 0.8, 1.0][st]
			game.state["plots"][key]["moisture"] = 80.0 if st % 2 == 0 else 10.0
	main.cell = Vector2i(14, 17); main.player.position = Vector2(14 * 16, 17 * 16)
	main.buddy_cell = Vector2i(13, 17); main.buddy.position = Vector2(13 * 16, 17 * 16)
	main.world.refresh()
	for i in 20:
		await process_frame
	await snap("3-fazenda")
	main.cell = Vector2i(40, 30); main.player.position = Vector2(40 * 16, 30 * 16)
	for i in 20:
		await process_frame
	await snap("4-rio-floresta")
	game.state["creature"]["needs"]["hydration"] = 10.0
	main.hud.refresh()
	game.state["clock"]["epoch_unix"] = float(game.state["clock"]["epoch_unix"]) - 21 * 360.0
	game.simulate_to_now()
	for i in 10:
		await process_frame
	await snap("5-noite-alerta")
	main.menu.open("buddy")
	await snap("6-menu-bichinho")
	main.menu.open("lineage")
	await snap("7-menu-linhagem")
	quit()
