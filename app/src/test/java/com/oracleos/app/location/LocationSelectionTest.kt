package com.oracleos.app.location

import com.oracleos.app.location.LocationSelection.Fix
import com.oracleos.app.location.LocationSelection.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSelectionTest {
    @Test
    fun firstFixIsAlwaysAccepted() {
        assertTrue(LocationSelection.isBetter(Fix("network", 1_000, 800f), null))
    }

    @Test
    fun preciseGpsReplacesCoarseNetworkFix() {
        val network = Fix("network", 10_000, 600f)
        assertTrue(LocationSelection.isBetter(Fix("gps", 11_000, 8f), network))
    }

    @Test
    fun coarseNetworkDoesNotReplaceRecentGps() {
        val gps = Fix("gps", 10_000, 6f)
        assertFalse(LocationSelection.isBetter(Fix("network", 12_000, 400f), gps))
    }

    @Test
    fun muchNewerFixWinsEvenIfLessPrecise() {
        val oldGps = Fix("gps", 10_000, 5f)
        assertTrue(LocationSelection.isBetter(Fix("network", 10_000 + LocationSelection.SIGNIFICANTLY_NEWER_MS + 1, 300f), oldGps))
    }

    @Test
    fun olderFixIsIgnored() {
        assertFalse(LocationSelection.isBetter(Fix("gps", 1_000, 3f), Fix("gps", 30_000, 10f)))
    }

    @Test
    fun newerGpsWithSlightlyWorseAccuracyKeepsTracking() {
        assertTrue(LocationSelection.isBetter(Fix("gps", 11_000, 14f), Fix("gps", 10_000, 9f)))
    }

    @Test
    fun qualityBands() {
        assertEquals(Quality.EXCELLENT, LocationSelection.quality(6f, 1_000))
        assertEquals(Quality.GOOD, LocationSelection.quality(20f, 1_000))
        assertEquals(Quality.FAIR, LocationSelection.quality(80f, 1_000))
        assertEquals(Quality.POOR, LocationSelection.quality(500f, 1_000))
        assertEquals(Quality.STALE, LocationSelection.quality(5f, LocationSelection.STALE_AFTER_MS + 1))
    }
}
