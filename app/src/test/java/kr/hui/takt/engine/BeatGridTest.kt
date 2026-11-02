package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatGridTest {
    private val ms = 1_000_000L

    @Test fun beatTimesAreEvenlySpaced() {
        val g = BeatGrid(originNanos = 1_000 * ms, originBeat = 0, bpm = 120, beatsPerBar = 4)
        assertEquals(1_000 * ms, g.timeOfBeat(0))
        assertEquals(1_500 * ms, g.timeOfBeat(1))
        assertEquals(1_000 * ms + 60_000 * ms * 120 / 120, g.timeOfBeat(120))
    }

    @Test fun noDriftAfterManyBeatsAtOddTempo() {
        val g = BeatGrid(0, 0, bpm = 133, beatsPerBar = 4)
        // 133박 = 정확히 60초
        assertEquals(60_000 * ms, g.timeOfBeat(133))
        assertEquals(600_000 * ms, g.timeOfBeat(1330))
    }

    @Test fun beatAtOrBeforeAndNext() {
        val g = BeatGrid(1_000 * ms, 0, 60, 4)
        assertEquals(-1L, g.beatAtOrBefore(999 * ms))
        assertEquals(0L, g.beatAtOrBefore(1_000 * ms))
        assertEquals(0L, g.beatAtOrBefore(1_999 * ms))
        assertEquals(1L, g.beatAtOrBefore(2_000 * ms))
        assertEquals(1L, g.nextBeatAfter(1_000 * ms))
        assertEquals(0L, g.nextBeatAfter(500 * ms))
    }

    @Test fun barPositionHandlesNegative() {
        val g = BeatGrid(0, 0, 100, 3)
        assertEquals(0, g.barPosition(0))
        assertEquals(2, g.barPosition(-1))
        assertEquals(1, g.barPosition(4))
    }

    @Test fun retimeKeepsBarPositionWhenOnlyTempoChanges() {
        val g = BeatGrid(0, 0, 120, 4)
        val now = 1_200 * ms // 2번 박(1000ms) 이후, 3번 박(1500ms) 이전
        val r = g.retimed(now, 90, 4)
        assertEquals(1_500 * ms, r.originNanos)
        assertEquals(3L, r.originBeat)
        assertEquals(3, r.barPosition(r.originBeat))
        assertEquals(2_166_666_667L, r.timeOfBeat(4)) // 1.5s + 60s/90 (반올림)
    }

    @Test fun retimeRestartsBarWhenMeterChanges() {
        val g = BeatGrid(0, 0, 120, 4)
        val r = g.retimed(1_200 * ms, 120, 3)
        assertEquals(1_500 * ms, r.originNanos)
        assertEquals(0, r.barPosition(r.originBeat))
    }

    @Test fun nextBeatIsAlwaysStrictlyAfter() {
        val g = BeatGrid(7 * ms, 3, 173, 7)
        var t = 0L
        while (t < 5_000 * ms) {
            val n = g.nextBeatAfter(t)
            assertTrue(g.timeOfBeat(n) > t)
            assertTrue(g.timeOfBeat(n - 1) <= t || n - 1 < g.beatAtOrBefore(t) + 1)
            t += 977_123L
        }
    }
}
