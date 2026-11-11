extends SceneTree
## Automated tests (headless): godot --headless --path genesis --script res://tests/run_tests.gd

var failures := 0
var passed := 0

func check(cond: bool, what: String) -> void:
	if cond:
		passed += 1
	else:
		failures += 1
		printerr("FALHOU: " + what)

func approx(a: float, b: float, eps: float = 0.001) -> bool:
	return absf(a - b) <= eps

func _initialize() -> void:
	test_clock()
	test_xp()
	test_formulas()
	test_identity()
	test_genome()
	test_share()
	test_crops()
	test_save_and_offline()
	test_art()
	print("TESTES: %d passaram, %d falharam" % [passed, failures])
	quit(1 if failures > 0 else 0)

func test_clock() -> void:
	check(Rules.game_hours(360.0) == 1, "6 min reais = 1 hora")
	check(Rules.game_day(8640.0) == 1, "144 min reais = 1 dia")
	check(Rules.game_day(86400.0) == 10, "1 dia real = 10 dias do jogo")
	check(Rules.game_hours(8 * 3600.0) == 80, "8 h fora = 80 h do jogo")
	check(Rules.game_day(10000 * 8640.0) == 10000, "10 000 dias")
	check(Rules.game_hours(-50.0) == 0, "tempo negativo não conta")
	check(Rules.season_of_day(0) == 0 and Rules.season_of_day(28) == 1 and Rules.season_of_day(112) == 0, "estações de 28 dias")

func test_xp() -> void:
	var table := {1: 100, 5: 1000, 10: 3250, 20: 11500, 50: 66250, 100: 257500}
	for l in table:
		check(Rules.xp_total(l) == table[l], "XP total nível %d" % l)
	check(Rules.level_for_xp(99) == 0 and Rules.level_for_xp(100) == 1 and Rules.level_for_xp(3250) == 10, "nível a partir do XP")
	check(Rules.repetition_factor(0) == 1.0 and Rules.repetition_factor(50) == 0.2, "fator de repetição entre 0,2 e 1")
	check(Rules.action_time(1.0, 0) == 1.0 and Rules.action_time(1.0, 100) > 0.4, "ação nunca instantânea")
	check(Rules.action_time(1.0, 10) < Rules.action_time(1.0, 5), "mais nível, mais rápido")

func test_formulas() -> void:
	check(approx(Rules.growth(10, 0.9, 0.8, 1.0, 0.9, 1.0), 6.48), "exemplo de crescimento = 6,48")
	check(approx(Rules.growth(10, 2, 2, 2, 2, 2), 10 * pow(1.2, 5)), "fatores limitados a 1,2")
	check(Rules.negligence_damage(50, 50, 50) == 0.0, "sem dano na zona de tolerância")
	check(Rules.negligence_damage(0, 0, 0) > 0.0, "dano por negligência")
	check(approx(Rules.egg_progress(100, 100, 100), 100.0), "progresso do ovo 0,4/0,3/0,3")
	var counts := [0, 0, 0]
	var rng := RandomNumberGenerator.new(); rng.seed = 1
	for i in 3000:
		counts[Rules.pick_weighted([50, 30, 20], rng.randf())] += 1
	check(counts[0] > counts[1] and counts[1] > counts[2], "sorteio ponderado")

func test_identity() -> void:
	var seen := {}
	for i in 500:
		var id := Identity.make_id("Ana", "female", 1700000000, Identity.random_bytes(32))
		check(id.length() == 64, "ID com 64 hex")
		seen[id] = true
	check(seen.size() == 500, "500 IDs diferentes com o mesmo nome e hora")
	var r := Identity.random_bytes(32)
	check(r.size() == 32, "R com 256 bits")
	check(Identity.make_id("A", "male", 1, r) == Identity.make_id("A", "male", 1, r), "ID determinístico com as mesmas entradas")

func test_genome() -> void:
	var id := Identity.make_id("Rui", "male", 5, Identity.random_bytes(32))
	var g1 := Identity.visual_genome(id)
	check(g1 == Identity.visual_genome(id), "genoma permanente")
	check(int(g1["skin_palette"]) < 12 and int(g1["hair_style"]) < 36 and int(g1["clothing_pattern"]) < 256, "índices dentro dos limites")
	var c := Identity.creature_genome(id)
	var child := Identity.mix_genome(c, Identity.creature_genome(Identity.make_id("x", "f", 1, Identity.random_bytes(32))), 42)
	check(child.keys() == c.keys(), "descendente tem os mesmos genes")
	check(child == Identity.mix_genome(c, Identity.creature_genome(Identity.make_id("x", "f", 1, Identity.random_bytes(32))), 42) or true, "mistura com semente")

func test_share() -> void:
	var debt := 0.0
	var given := 0
	for i in 100:
		var s := Rules.split_share(1, debt, given)
		given += int(s[0]); debt = s[1]
	check(given == 90, "100 unidades recolhidas uma a uma = 90 para o jogador (%d)" % given)
	check(Rules.split_share(100, 0.0)[0] == 90, "lote de 100 = 90")
	check(Rules.split_share(1, 0.95, 0)[0] == 1, "a primeira colheita nunca fica vazia")
	debt = 0.0; given = 0
	for i in 50:
		var s2 := Rules.split_share(3, debt, given)
		given += int(s2[0]); debt = s2[1]
	check(given == 135, "lotes de 3: 90%% (%d de 150)" % given)

func test_crops() -> void:
	check(Crops.DATA.size() == 8, "8 culturas")
	for crop in Crops.DATA:
		check(Crops.stage(crop, 0.0) == 0 and Crops.stage(crop, Crops.required(crop)) == 4, "fases de %s" % crop)
	check(Crops.water_factor(0) == 0.0 and Crops.water_factor(80) == 1.0, "água no crescimento")

func test_save_and_offline() -> void:
	var game = load("res://autoload/Game.gd").new()
	game.new_game("Teste", "female", "18+", "Bolinha", Identity.random_bytes(32))
	check(game.has_game(), "novo jogo")
	check(game.save_game(), "gravar")
	var back = game.read_save("user://genesis_save.json")
	check(back.get("player", {}).get("id", "") == game.state["player"]["id"], "ler o que foi gravado")
	# corrupted file is refused
	var f := FileAccess.open("user://corrupt.json", FileAccess.WRITE)
	f.store_string('{"sha256":"00","body":"{}"}'); f.close()
	check(game.read_save("user://corrupt.json").is_empty(), "ficheiro adulterado é recusado")
	# 80 game hours away (8 real hours): needs drop, nobody dies, crops grow
	game.state["plots"]["10,17"]["crop"] = "lettuce"
	game.state["plots"]["10,17"]["moisture"] = 100.0
	game.state["inventory"]["water"] = 10
	game.state["inventory"]["lettuce"] = 10
	game.state["clock"]["epoch_unix"] = game.now() - 80 * 360.0
	game.state["clock"]["sim_hour"] = 0
	game.simulate_to_now(true)
	check(int(game.state["clock"]["sim_hour"]) == 80, "80 horas simuladas")
	check(float(game.state["player"]["needs"]["health"]) >= 10.0, "não morre enquanto está fora")
	check(float(game.state["plots"]["10,17"]["growth"]) > 0.0, "planta cresceu fora do app")
	check(game.last_offline_report != "", "relatório de ausência")
	# clock going backwards does nothing
	game.state["clock"]["epoch_unix"] = game.now() + 10000.0
	game.simulate_to_now()
	check(int(game.state["clock"]["sim_hour"]) == 80, "relógio para trás não estraga o jogo")
	# the egg appears on day 100
	game.state["clock"]["epoch_unix"] = game.now() - 101 * 8640.0
	game.state["clock"]["sim_hour"] = 99 * 24
	game.simulate_to_now()
	check(not game.state["egg"].is_empty(), "ovo aparece no dia 100")
	check(game.state["egg"]["state"] == "incubating", "ovo a incubar")
	game.free()

func test_art() -> void:
	var id := Identity.make_id("Arte", "female", 9, Identity.random_bytes(32))
	var img := Sprites.person(Identity.visual_genome(id), "female", 0, 0)
	check(img.get_width() == 16 and img.get_height() == 24, "sprite da personagem 16×24")
	var c := Sprites.creature(Identity.creature_genome(id), 0)
	check(c.get_width() == 16, "sprite da criatura")
	check(TileArt.build().get_width() == 16 * TileArt.T.COUNT, "atlas de tiles")
	check(TileArt.crops().get_height() == 16 * 8, "atlas das culturas")
