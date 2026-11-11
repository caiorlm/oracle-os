package com.oracleos.app.location

/**
 * Chooses which of several position fixes to show. Pure Kotlin so it can be unit tested.
 *
 * A clearly newer fix wins, because an old precise fix is a wrong position once the user moves.
 * Among fixes of similar age, the one with the smaller accuracy radius wins, and a fix that is a
 * little less accurate but newer is still accepted when it comes from the same provider.
 */
object LocationSelection {
    const val SIGNIFICANTLY_NEWER_MS = 20_000L
    const val STALE_AFTER_MS = 120_000L

    data class Fix(
        val provider: String,
        val timeMs: Long,
        val accuracyM: Float?,
    )

    fun isBetter(candidate: Fix, current: Fix?): Boolean {
        if (current == null) return true
        val delta = candidate.timeMs - current.timeMs
        if (delta > SIGNIFICANTLY_NEWER_MS) return true
        if (delta < -SIGNIFICANTLY_NEWER_MS) return false
        val candidateAccuracy = candidate.accuracyM ?: Float.MAX_VALUE
        val currentAccuracy = current.accuracyM ?: Float.MAX_VALUE
        val accuracyDelta = candidateAccuracy - currentAccuracy
        val newer = delta > 0
        return when {
            accuracyDelta < 0f -> true
            newer && accuracyDelta == 0f -> true
            newer && candidate.provider == current.provider && accuracyDelta <= 50f -> true
            newer && accuracyDelta <= 15f -> true
            else -> false
        }
    }

    /** Human description of fix quality for the status line. */
    fun quality(accuracyM: Float?, ageMs: Long): Quality = when {
        ageMs > STALE_AFTER_MS -> Quality.STALE
        accuracyM == null -> Quality.UNKNOWN
        accuracyM <= 10f -> Quality.EXCELLENT
        accuracyM <= 25f -> Quality.GOOD
        accuracyM <= 100f -> Quality.FAIR
        else -> Quality.POOR
    }

    enum class Quality(val label: String) {
        EXCELLENT("Excelente"),
        GOOD("Boa"),
        FAIR("Razoável"),
        POOR("Fraca"),
        STALE("Desatualizada"),
        UNKNOWN("Desconhecida"),
    }
}
