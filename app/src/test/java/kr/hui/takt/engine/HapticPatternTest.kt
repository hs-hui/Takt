package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HapticPatternTest {
    private val style = HapticStyle(amplitude = 100, accentAmplitude = 255, pulseMs = 30, accentPulseMs = 55)

    @Test fun cycleCoversWholeBarsAndStaysWithinLimits() {
        for (bpm in listOf(20, 60, 97, 120, 133, 180, 299, 300)) {
            for (beats in 1..12) {
                for (sub in 1..4) {
                    val p = HapticPattern.build(bpm, beats, 0, 0, style, sub)
                    val k = p.beats
                    assertEquals(0, k % beats)
                    assertTrue(k * sub <= HapticPattern.MAX_CYCLE_PULSES || k == beats)
                    assertEquals(Math.round(60_000.0 * k / bpm), p.cycleMillis)
                    assertTrue(abs(HapticPattern.cycleErrorMs(bpm, k)) <= 0.5)
                    // lcm 이 상한 안이면 오차 0
                    val lcm = lcm(bpm, beats)
                    if (lcm * sub <= HapticPattern.MAX_CYCLE_PULSES) {
                        assertEquals("bpm=$bpm beats=$beats sub=$sub", 0.0, HapticPattern.cycleErrorMs(bpm, k), 1e-9)
                    }
                }
            }
        }
    }

    @Test fun cycleWithLargeLcmPicksSmallError() {
        // lcm(299, 7) = 2093 > 2048 → 근사 주기
        val k = HapticPattern.cycleBeats(299, 7, 1)
        assertEquals(0, k % 7)
        assertTrue(abs(HapticPattern.cycleErrorMs(299, k)) < 0.05)
    }

    @Test fun subdivisionsAreEvenAndWeaker() {
        val p = HapticPattern.build(120, 2, 0, 0, style, subdivision = 3)
        // 120bpm 셋잇단: 펄스 간격 166.67ms → 167,167,166 반올림 패턴
        val starts = mutableListOf<Long>()
        var t = 0L
        for (i in 1 until p.timings.size step 2) { starts += t; t += p.timings[i] + p.timings[i + 1] }
        for ((k, st) in starts.withIndex()) assertTrue(abs(st - k * 500.0 / 3) <= 0.5)
        assertEquals(255, p.amplitudes[1]) // 강박
        assertEquals(style.subAmplitude, p.amplitudes[3])
        assertEquals(style.subAmplitude, p.amplitudes[5])
        assertEquals(100, p.amplitudes[7]) // 2번째 박
        assertEquals(style.subPulseMs, p.timings[3])
    }

    @Test fun customLevelsAccentAndMute() {
        val p = HapticPattern.build(120, 4, 0, 0, style, levels = intArrayOf(1, 2, 0, 1))
        assertEquals(100, p.amplitudes[1])
        assertEquals(255, p.amplitudes[3])
        assertEquals(0, p.amplitudes[5])
        assertEquals(100, p.amplitudes[7])
        assertEquals(500L, p.timings[5] + p.timings[6]) // 무음 박도 시간은 차지
    }

    @Test fun cycleLevelOrder() {
        var s = kr.hui.takt.data.MetronomeSettings(beatsPerBar = 3)
        assertEquals(listOf(2, 1, 1), s.levels().toList())
        s = s.withBeatLevelCycled(1)
        assertEquals(listOf(2, 2, 1), s.levels().toList())
        s = s.withBeatLevelCycled(1)
        assertEquals(listOf(2, 0, 1), s.levels().toList())
        s = s.withBeatLevelCycled(1)
        assertEquals(listOf(2, 1, 1), s.levels().toList())
        // 박 수가 바뀌면 sanitize 가 초기화
        assertEquals(listOf(2, 1, 1, 1), s.copy(beatsPerBar = 4).sanitized().levels().toList())
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    private fun lcm(a: Int, b: Int) = a / gcd(a, b) * b

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
