package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseClockTest {
    private val ms = 1_000_000L
    private val style = HapticStyle(amplitude = 100, accentAmplitude = 255, subAmplitude = 40,
        pulseMs = 30, accentPulseMs = 55, subPulseMs = 16)

    @Test fun nextPulseWalksBeatsAndSubdivisions() {
        val g = BeatGrid(0, 0, 120, 4) // 박 500ms
        assertEquals(Pulse(0, 0, 0), PulseClock.nextPulseAfter(g, -1, 2))
        assertEquals(Pulse(0, 1, 250 * ms), PulseClock.nextPulseAfter(g, 0, 2))
        assertEquals(Pulse(1, 0, 500 * ms), PulseClock.nextPulseAfter(g, 250 * ms, 2))
        assertEquals(Pulse(1, 0, 500 * ms), PulseClock.nextPulseAfter(g, 300 * ms, 1))
    }

    @Test fun pulsesAreStrictlyIncreasingAndEven() {
        val g = BeatGrid(3 * ms, 0, 133, 7)
        var t = 0L
        var prev = -1L
        repeat(500) {
            val p = PulseClock.nextPulseAfter(g, t, 3)
            assertTrue(p.timeNanos > t)
            if (prev >= 0) {
                val gap = p.timeNanos - prev
                assertTrue("gap $gap", kotlin.math.abs(gap - g.beatNanos / 3) < 2) // ns 반올림 오차만
            }
            prev = p.timeNanos
            t = p.timeNanos
        }
    }

    @Test fun amplitudeAndDurationByKind() {
        val g = BeatGrid(0, 0, 120, 4)
        val levels = intArrayOf(2, 1, 0, 1)
        assertEquals(255, PulseClock.amplitudeOf(Pulse(0, 0, 0), g, style, levels))
        assertEquals(55L, PulseClock.durationOf(Pulse(0, 0, 0), g, style, levels, 2))
        assertEquals(40, PulseClock.amplitudeOf(Pulse(0, 1, 0), g, style, levels))
        assertEquals(16L, PulseClock.durationOf(Pulse(0, 1, 0), g, style, levels, 2))
        assertEquals(100, PulseClock.amplitudeOf(Pulse(1, 0, 0), g, style, levels))
        assertEquals(0, PulseClock.amplitudeOf(Pulse(2, 0, 0), g, style, levels))
        // 마디가 넘어가면 다시 강박
        assertEquals(255, PulseClock.amplitudeOf(Pulse(4, 0, 0), g, style, levels))
        // 기본(levels=null): 첫 박 강세
        assertEquals(255, PulseClock.amplitudeOf(Pulse(8, 0, 0), g, style, null))
        assertEquals(100, PulseClock.amplitudeOf(Pulse(9, 0, 0), g, style, null))
    }

    @Test fun durationCappedAtFastRates() {
        val g = BeatGrid(0, 0, 300, 4) // 박 200ms, 16분이면 50ms 간격
        assertEquals(22L, PulseClock.durationOf(Pulse(1, 0, 0), g, style, null, 4))
    }
}
