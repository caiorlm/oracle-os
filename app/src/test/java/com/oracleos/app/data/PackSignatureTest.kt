package com.oracleos.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Uses the real development key and the signed London map pack built by pack_builder.py. */
class PackSignatureTest {
    private val pem = """
        -----BEGIN PUBLIC KEY-----
        MCowBQYDK2VwAyEApo4qLnAG8FC8iQfcWrpcMzae0jEJ0M4I23K/xqo+poY=
        -----END PUBLIC KEY-----
    """.trimIndent()
    private val signature = Base64.getDecoder().decode(
        "9ihcY4NYdZm1ii9PEz1ByE+Jc1oVTvd9bnlXCMdRFc7QEVL3jcM5PyapW3SX8MQGFu7bLNLJE43b5yqVXLPiAA==",
    )
    private val manifest = Base64.getDecoder().decode(
        "ewogICJtaW5BbmRyb2lkQXBpIjogMjgsCiAgInBhY2tJZCI6ICJvcmFjbGUtbG9uZG9uLW1hcC1kZXYiLAogICJwYWNrYWdlcyI6IFsKICAgIHsKICAgICAgImJ5dGVzIjogNjgxMjgyNzgsCiAgICAgICJpZCI6ICJncmVhdGVyLWxvbmRvbi1wbXRpbGVzIiwKICAgICAgInBhdGgiOiAibWFwcy9ncmVhdGVyLWxvbmRvbi5wbXRpbGVzIiwKICAgICAgInNoYTI1NiI6ICI4MjgzOTQ2NzQxNzg5YTA5NTQ3YWZkZTEzNGMwNDlhZTM0MTI2NDdmYWEyNjYwN2ZjYTE0OWZlMzdmYjE0OGUyIiwKICAgICAgInR5cGUiOiAibWFwIgogICAgfSwKICAgIHsKICAgICAgImJ5dGVzIjogMjk2LAogICAgICAiaWQiOiAib3NtLWF0dHJpYnV0aW9uIiwKICAgICAgInBhdGgiOiAibGljZW5zZXMvT1NNLU9EYkwtTk9USUNFLnR4dCIsCiAgICAgICJzaGEyNTYiOiAiNWQ3MzM0ZjM0NGNlNDBlZWE0MzgzZmM2N2IyMWQzOTg5NzQ2MmE5MTkzMWMxNmFiMTZiOGMwOGVhOWYxNzIwYSIsCiAgICAgICJ0eXBlIjogImxpY2Vuc2UiCiAgICB9CiAgXSwKICAic2NoZW1hVmVyc2lvbiI6IDEsCiAgInNpZ25hdHVyZSI6IHsKICAgICJhbGdvcml0aG0iOiAiRWQyNTUxOSIsCiAgICAiZmlsZSI6ICJzaWduYXR1cmVzL21hbmlmZXN0LnNpZyIsCiAgICAia2V5SWQiOiAib3JhY2xlLWRldi0yMDI2IgogIH0sCiAgInZlcnNpb24iOiAiMC4xLjAiCn0K",
    )

    @Test
    fun parsesRawKeyFromPem() {
        val key = PackSignature.publicKeyFromPem(pem)
        assertEquals(32, key.size)
        assertArrayEquals(Base64.getDecoder().decode("MCowBQYDK2VwAyEApo4qLnAG8FC8iQfcWrpcMzae0jEJ0M4I23K/xqo+poY=").copyOfRange(12, 44), key)
    }

    @Test
    fun acceptsSignedManifest() {
        assertTrue(PackSignature.verify(PackSignature.publicKeyFromPem(pem), manifest, signature))
    }

    @Test
    fun rejectsModifiedManifestOrSignature() {
        val key = PackSignature.publicKeyFromPem(pem)
        val tampered = manifest.copyOf().also { it[40] = (it[40] + 1).toByte() }
        assertFalse(PackSignature.verify(key, tampered, signature))
        val badSignature = signature.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(PackSignature.verify(key, manifest, badSignature))
        assertFalse(PackSignature.verify(ByteArray(31), manifest, signature))
    }
}
