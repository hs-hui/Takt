package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HapticPatternTest {
    private val style = HapticStyle(amplitude = 100, accentAmplitude = 255, pulseMs = 30, accentPulseMs = 55)

    @Test fun cycleLengthIsExactAndCoversWholeBars() {
        for (bpm in listOf(20, 60, 97, 120, 133, 180, 299, 300)) {
            for (beats in 1..12) {
                val p = HapticPattern.build(bpm, beats, 0, 0, style)
                val k = HapticPattern.cycleBeats(bpm, beats)
                assertEquals(0, k % beats)
                // 주기 길이 = 60000 * k / bpm 이 정확히 정수
                assertEquals(0L, 60_000L * k % bpm)
                assertEquals("bpm=$bpm beats=$beats", 60_000L * k / bpm, p.cycleMillis)
            }
        }
    }

    @Test fun eachBeatStartsWithinHalfMillisecondOfIdeal() {
        val bpm = 133
        val p = HapticPattern.build(bpm, 4, 0, 0, style)
        var t = 0L
        val beats = (p.timings.size - 1) / 2
        for (i in 0 until beats) {
            val ideal = i * 60_000.0 / bpm
            assertTrue("beat $i at $t vs $ideal", abs(t - ideal) <= 0.5)
            t += p.timings[1 + 2 * i] + p.timings[2 + 2 * i]
        }
    }

    @Test fun accentFollowsBarPosition() {
        val p = HapticPattern.build(120, 4, startBarPosition = 2, firstDelayMs = 40, style = style)
        assertEquals(40L, p.timings[0])
        assertEquals(0, p.amplitudes[0])
        // 박 위치: 2,3,0,1,2,...
        assertEquals(100, p.amplitudes[1])
        assertEquals(100, p.amplitudes[3])
        assertEquals(255, p.amplitudes[5])
        assertEquals(55L, p.timings[5])
        assertEquals(100, p.amplitudes[7])
        for (i in 2 until p.amplitudes.size step 2) assertEquals(0, p.amplitudes[i])
    }

    @Test fun noAccentWhenDisabledOrSingleBeat() {
        val off = HapticPattern.build(120, 4, 0, 0, style.copy(accentEnabled = false))
        assertEquals(100, off.amplitudes[1])
        val one = HapticPattern.build(120, 1, 0, 0, style)
        assertEquals(100, one.amplitudes[1])
    }

    @Test fun pulsesNeverOverlapAtFastTempo() {
        val p = HapticPattern.build(300, 4, 0, 0, style.copy(accentPulseMs = 150, pulseMs = 150))
        for (i in 1 until p.timings.size) assertTrue(p.timings[i] > 0)
        assertTrue(p.timings[1] <= 90)
    }
}
