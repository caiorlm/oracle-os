class_name Identity
extends RefCounted
## Permanent identity: ID = SHA256(name ‖ gender ‖ timestamp ‖ R), R = 256 random bits from the
## platform's cryptographic generator. The visual genome is derived from the ID, so it is stable.

static func random_bytes(count: int) -> PackedByteArray:
	return Crypto.new().generate_random_bytes(count)

static func sha256(data: PackedByteArray) -> PackedByteArray:
	var ctx := HashingContext.new()
	ctx.start(HashingContext.HASH_SHA256)
	ctx.update(data)
	return ctx.finish()

static func make_id(name: String, gender: String, timestamp: int, r: PackedByteArray) -> String:
	var payload := ("%s|%s|%d|" % [name, gender, timestamp]).to_utf8_buffer()
	payload.append_array(r)
	return sha256(payload).hex_encode()

static func derive(id_hex: String, label: String) -> PackedByteArray:
	var payload := id_hex.hex_decode()
	payload.append_array(label.to_utf8_buffer())
	return sha256(payload)

static func _u16(b: PackedByteArray, i: int) -> int:
	return (b[i] << 8) | b[i + 1]

static func visual_genome(id_hex: String) -> Dictionary:
	var b := id_hex.hex_decode()
	return {
		"skin_palette": b[0] % 12, "eye_shape": b[1] % 8, "eye_color": b[2] % 20,
		"hair_style": b[3] % 36, "hair_color": b[4] % 12, "face_shape": b[5] % 16,
		"body_shape": b[6] % 6, "clothing_pattern": _u16(b, 7) % 256, "clothing_palette": b[9] % 32,
		"egg_pattern": _u16(b, 10) % 8192, "marking_seed": (_u16(b, 12) << 16) | _u16(b, 14),
	}

static func creature_genome(id_hex: String) -> Dictionary:
	var b := derive(id_hex, "creature")
	return {
		"body": b[0] % 4, "hue": b[1] % 24, "spots": b[2] % 6, "ears": b[3] % 5, "tail": b[4] % 4,
		"eyes": b[5] % 4, "belly": b[6] % 3, "egg_pattern": _u16(b, 7) % 8192,
		"marking_seed": (_u16(b, 9) << 16) | _u16(b, 11),
	}

## Child genome: each gene from one parent, with small controlled mutations (μ ≈ 6%).
static func mix_genome(a: Dictionary, b: Dictionary, seed_value: int) -> Dictionary:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	var child := {}
	for key in a.keys():
		var v = a[key] if rng.randf() < 0.5 or not b.has(key) else b[key]
		if rng.randf() < 0.06 and typeof(v) == TYPE_INT:
			v = int(v) + (1 if rng.randf() < 0.5 else -1)
			v = maxi(0, v)
		child[key] = v
	return child
