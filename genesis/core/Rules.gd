class_name Rules
extends RefCounted
## Universal maths of GENESIS (GDD sections 2, 4, 5, 6, 8, 9). Pure functions, covered by tests.

const REAL_SECONDS_PER_GAME_HOUR := 360.0        # 1 virtual hour = 6 real minutes
const HOURS_PER_DAY := 24
const REAL_SECONDS_PER_GAME_DAY := 8640.0         # 144 real minutes; 1 real day = 10 virtual days
const DAYS_PER_SEASON := 28
const EGG_DAY := 100
const HATCH_DAY := 5000
const LIFESPAN_DAYS := 10000
const MAX_RECOVERIES := 5
const EGG_SHARE := 0.10
const EGG_P_MIN := 60.0
const MAX_CATCHUP_HOURS := 24 * 120              # longer absences only move the calendar

static func game_hours(real_seconds: float) -> int:
	return int(floor(maxf(real_seconds, 0.0) / REAL_SECONDS_PER_GAME_HOUR))

static func game_day(real_seconds: float) -> int:
	return int(floor(maxf(real_seconds, 0.0) / REAL_SECONDS_PER_GAME_DAY))

static func season_of_day(day: int) -> int:
	return int(day / DAYS_PER_SEASON) % 4

## Accumulated XP to reach a level: 25L² + 75L.
static func xp_total(level: int) -> int:
	return 25 * level * level + 75 * level

static func level_for_xp(xp: float) -> int:
	var level := 0
	while level < 100 and float(xp_total(level + 1)) <= xp:
		level += 1
	return level

## Repetition factor F_r in [0.2, 1]: the same trivial action repeated in one virtual day earns less.
static func repetition_factor(count_today: int) -> float:
	return clampf(1.0 - 0.08 * count_today, 0.2, 1.0)

static func xp_gain(base: float, difficulty: float, quality: float, count_today: int) -> float:
	return base * difficulty * quality * repetition_factor(count_today)

## Time of an action at skill level L: T0·(0.4 + 0.6·e^(−0.025L)) — faster, never instant.
static func action_time(t0: float, level: int) -> float:
	return t0 * (0.4 + 0.6 * exp(-0.025 * level))

## Crop growth per interval: G0·Fs·Fw·Ft·Fl·Fn, each factor in [0, 1.2].
static func growth(g0: float, fs: float, fw: float, ft: float, fl: float, fn: float) -> float:
	return g0 * clampf(fs, 0.0, 1.2) * clampf(fw, 0.0, 1.2) * clampf(ft, 0.0, 1.2) \
		* clampf(fl, 0.0, 1.2) * clampf(fn, 0.0, 1.2)

## Health damage from neglect, with tolerance zones (hydration < 20, food < 15, energy < 10).
static func negligence_damage(hydration: float, food: float, energy: float,
		kh: float = 0.05, kf: float = 0.04, ke: float = 0.03) -> float:
	return kh * maxf(0.0, 20.0 - hydration) + kf * maxf(0.0, 15.0 - food) + ke * maxf(0.0, 10.0 - energy)

static func egg_progress(o_n: float, o_h: float, o_d: float) -> float:
	return 0.4 * o_n + 0.3 * o_h + 0.3 * o_d

## Market price: P0·F_supply·F_demand·F_quality, each factor kept within limits.
static func price(p0: float, supply: float, demand: float, quality: float) -> float:
	return p0 * clampf(supply, 0.5, 1.5) * clampf(demand, 0.5, 1.5) * clampf(quality, 0.5, 1.5)

## P(E_i) = w_i / Σw — returns the index picked by r in [0, 1).
static func pick_weighted(weights: Array, r: float) -> int:
	var total := 0.0
	for w in weights:
		total += float(w)
	if total <= 0.0:
		return -1
	var x := r * total
	for i in weights.size():
		x -= float(weights[i])
		if x < 0.0:
			return i
	return weights.size() - 1

## 10% of what is collected goes to the egg. Whole units are kept by the player while the share is
## counted as a debt; a unit is set aside once the debt reaches 1 — but never the only unit of a
## collection when the player holds fewer than 3 of that item (a first harvest is never empty).
## Returns [given_to_player, new_debt].
static func split_share(amount: int, debt: float, held: int = 99) -> Array:
	var d := debt + amount * EGG_SHARE
	var take := int(floor(d + 0.000001))
	var limit := amount - 1 if (amount > 1 or held < 3) else amount
	take = clampi(take, 0, maxi(0, limit))
	return [amount - take, d - take]
