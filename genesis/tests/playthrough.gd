extends SceneTree
## Automated playthrough: creates a character, loads the valley, walks, talks to the NPC, farms a
## full cycle (till, plant, water, wait, harvest), cares for the companion and opens every menu tab.

var fails := 0

func ok(c: bool, what: String) -> void:
	print(("OK   " if c else "FALHA ") + what)
	if not c:
		fails += 1

func _initialize() -> void:
	DirAccess.remove_absolute("user://genesis_save.json")
	DirAccess.remove_absolute("user://genesis_save.bak")
	var game = root.get_node("Game")
	game.state = {}
	var main = load("res://ui/Main.gd").new()
	root.add_child(main)
	await process_frame
	ok(main.creator != null, "ecrã de criação aparece sem jogo gravado")
	main.creator.name_edit.text = "Lia"
	main.creator.buddy_edit.text = "Fofo"
	main.creator._start()
	await process_frame
	await process_frame
	ok(game.has_game(), "personagem criada")
	ok(main.world != null and main.player != null, "vale carregado")
	main.dialog.close()
	# talk to Grandma Rosa
	main.cell = Vector2i(39, 30); main.face = 1
	main.act()
	ok(game.state["quests"]["farm_001"]["state"] == "active", "missão iniciada pela Vó Rosa")
	ok(game.count("seed_carrot") == 4, "recebeu 4 sementes de cenoura")
	main.dialog.close()
	# fill the canteen at the well (20,9) from (20,10)
	game.state["inventory"]["water"] = 0
	main.cell = Vector2i(20, 10); main.face = 1
	main.act()
	ok(game.count("water") >= 9, "encheu o cantil no poço (90%%)")
	# farm: till a new plot, plant, water
	main.cell = Vector2i(14, 21); main.face = 0
	main.busy = 0.0; main.act()
	ok(game.state["plots"].has("14,22"), "cavou um canteiro")
	game.state["selected_seed"] = "seed_carrot"
	main.busy = 0.0; main.act()
	ok(game.state["plots"]["14,22"]["crop"] == "carrot", "plantou cenoura")
	game.state["plots"]["14,22"]["moisture"] = 0.0
	main.busy = 0.0; main.act()
	ok(float(game.state["plots"]["14,22"]["moisture"]) == 100.0, "regou")
	# two virtual days pass
	game.state["clock"]["epoch_unix"] = float(game.state["clock"]["epoch_unix"]) - 2.6 * 8640.0
	for p in game.state["plots"].values():
		p["moisture"] = 100.0
	game.simulate_to_now()
	game.state["plots"]["14,22"]["moisture"] = 100.0
	game.simulate_to_now()
	var st := Crops.stage("carrot", float(game.state["plots"]["14,22"]["growth"]))
	print("   fase da cenoura após ~2,6 dias: %d (crescimento %.0f de %.0f)" % [st, game.state["plots"]["14,22"]["growth"], Crops.required("carrot")])
	game.state["plots"]["14,22"]["growth"] = Crops.required("carrot")
	main.busy = 0.0; main.act()
	ok(game.count("carrot") >= 1, "colheu cenoura")
	ok(game.state["quests"]["farm_001"]["plant"] == 1 and game.state["quests"]["farm_001"]["harvest"] == 1, "progresso da missão")
	# berries and wood
	main.cell = Vector2i(50, 30)
	var found := false
	for y in range(26, 36):
		for x in range(49, 62):
			if main.world.get_tile(x, y) == TileArt.T.BUSH and not main.world.blocked(x - 1, y):
				main.cell = Vector2i(x - 1, y); main.face = 3; found = true
				break
		if found:
			break
	var before: int = game.count("berries")
	main.busy = 0.0; main.act()
	ok(game.count("berries") > before, "apanhou bagas")
	# companion care
	game.state["creature"]["needs"]["hydration"] = 10.0
	ok(game.urgent().get("action", "") == "care_water", "alerta: bichinho com sede")
	main.care_action("care_water")
	ok(float(game.state["creature"]["needs"]["hydration"]) > 40.0, "deu água ao bichinho")
	main.care_action("care_feed")
	# menus
	for tab in ["bag", "buddy", "skills", "quests", "lineage", "settings"]:
		main.menu.open(tab)
		await process_frame
		ok(main.menu.content.get_child_count() > 0, "menu: " + tab)
	main.menu.close()
	ok(game.state["reserve"]["nutrition"] > 0.0 and game.state["reserve"]["hydration"] > 0.0, "reserva do ovo recebe 10%")
	ok(game.level("farming") >= 0 and float(game.state["player"]["xp"]["farming"]) > 0.0, "XP de agricultura")
	ok(game.save_game(), "gravou no fim")
	# walking
	main.cell = Vector2i(13, 12); main.player.position = Vector2(13 * 16, 12 * 16)
	Input.action_press("ui_down")
	for i in 30:
		await process_frame
	Input.action_release("ui_down")
	for i in 10:
		await process_frame
	ok(main.cell != Vector2i(13, 12), "andou com as setas (%s)" % str(main.cell))
	print("PLAYTHROUGH: %d falhas" % fails)
	quit(1 if fails > 0 else 0)
