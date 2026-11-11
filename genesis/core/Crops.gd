class_name Crops
extends RefCounted
## The eight starting crops. days = virtual days to harvest in ideal conditions.

const STAGES := ["SEED", "SPROUT", "GROWING", "MATURE", "HARVESTABLE"]

const DATA := {
	"lettuce": {"pt": "Alface", "en": "Lettuce", "days": 1.0, "seasons": [0, 2], "food": 12, "color": Color(0.5, 0.85, 0.3), "price": 4},
	"carrot": {"pt": "Cenoura", "en": "Carrot", "days": 2.0, "seasons": [0, 2], "food": 16, "color": Color(1.0, 0.55, 0.15), "price": 6},
	"potato": {"pt": "Batata", "en": "Potato", "days": 3.0, "seasons": [0, 2], "food": 24, "color": Color(0.8, 0.65, 0.4), "price": 8},
	"corn": {"pt": "Milho", "en": "Corn", "days": 4.0, "seasons": [1], "food": 22, "color": Color(1.0, 0.85, 0.2), "price": 9},
	"bean": {"pt": "Feijão", "en": "Bean", "days": 2.5, "seasons": [0, 1], "food": 18, "color": Color(0.35, 0.65, 0.25), "price": 7},
	"tomato": {"pt": "Tomate", "en": "Tomato", "days": 3.0, "seasons": [1], "food": 15, "color": Color(0.9, 0.2, 0.15), "price": 8},
	"pumpkin": {"pt": "Abóbora", "en": "Pumpkin", "days": 6.0, "seasons": [2], "food": 40, "color": Color(0.95, 0.5, 0.1), "price": 16},
	"strawberry": {"pt": "Morango", "en": "Strawberry", "days": 3.5, "seasons": [0], "food": 10, "color": Color(0.95, 0.15, 0.3), "price": 10},
}
const G0 := 10.0

static func required(crop: String) -> float:
	# Daylight is ~60% of a day's growth, hence the 0.6.
	return G0 * Rules.HOURS_PER_DAY * float(DATA[crop]["days"]) * 0.6

static func stage(crop: String, growth: float) -> int:
	var f := growth / required(crop)
	if f >= 1.0:
		return 4
	if f >= 0.75:
		return 3
	if f >= 0.4:
		return 2
	if f >= 0.12:
		return 1
	return 0

static func temperature_factor(crop: String, season: int) -> float:
	if season in DATA[crop]["seasons"]:
		return 1.0
	return 0.25 if season == 3 else 0.7

static func water_factor(moisture: float) -> float:
	if moisture >= 60.0:
		return 1.0
	if moisture >= 30.0:
		return 0.8
	if moisture >= 10.0:
		return 0.45
	return 0.0

static func light_factor(hour_of_day: int) -> float:
	return 1.0 if hour_of_day >= 6 and hour_of_day < 20 else 0.15
