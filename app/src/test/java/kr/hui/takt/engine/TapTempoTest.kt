package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {
    private val ms = 1_000_000L

    @Test fun averagesIntervals() {
        val t = TapTempo()
        assertNull(t.tap(0))
        assertEquals(120, t.tap(500 * ms))
        assertEquals(120, t.tap(1_000 * ms))
        assertEquals(100, TapTempo().run { tap(0); tap(600 * ms) })
    }

    @Test fun resetsAfterLongPause() {
        val t = TapTempo()
        t.tap(0); t.tap(500 * ms)
        assertNull(t.tap(5_000 * ms))
        assertEquals(60, t.tap(6_000 * ms))
    }

    @Test fun clampsToRange() {
        val t = TapTempo()
        t.tap(0)
        assertEquals(BeatGrid.MAX_BPM, t.tap(50 * ms))
    }
}
