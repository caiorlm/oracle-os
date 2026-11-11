package com.oracleos.app.data

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.util.Base64

/** Ed25519 verification of pack manifests, matching companion/desktop-pack-builder/pack_builder.py. */
object PackSignature {
    /** DER prefix of an X.509 SubjectPublicKeyInfo holding a raw 32-byte Ed25519 key. */
    private val ED25519_SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    )

    /** Accepts the PEM written by pack_builder.py init-key and returns the raw public key. */
    fun publicKeyFromPem(pem: String): ByteArray {
        val body = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString("")
        val der = Base64.getDecoder().decode(body)
        require(der.size == ED25519_SPKI_PREFIX.size + 32 && der.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)) {
            "A chave pública não é Ed25519"
        }
        return der.copyOfRange(ED25519_SPKI_PREFIX.size, der.size)
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return try {
            val spec = EdDSANamedCurveTable.getByName("Ed25519")
            val key = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec))
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(key)
            engine.verifyOneShot(message, signature)
        } catch (_: Exception) {
            false
        }
    }
}
