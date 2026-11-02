package kr.hui.takt.ui

import kr.hui.takt.engine.BeatGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingFrameTest {
    private val ms = 1_000_000L
    private val g = BeatGrid(1_000 * ms, 0, 120, 4) // 박 500ms

    @Test fun fillReachesFullExactlyAtNextBeat() {
        val f0 = ringFrame(g, 1_000 * ms)
        assertEquals(0, f0.currentPos)
        assertEquals(1, f0.nextPos)
        assertEquals(0f, f0.fill, 1e-6f)
        assertEquals(1f, f0.flash, 1e-6f)

        val f1 = ringFrame(g, 1_250 * ms)
        assertEquals(0.5f, f1.fill, 1e-6f)
        assertEquals(0f, f1.flash, 1e-6f) // 박 길이 절반이면 번쩍임 끝

        val f2 = ringFrame(g, 1_499 * ms)
        assertTrue(f2.fill > 0.99f)
        assertEquals(1, f2.nextPos)

        // 박이 울린 순간 다음 칸으로 넘어감
        val f3 = ringFrame(g, 1_500 * ms)
        assertEquals(1, f3.currentPos)
        assertEquals(2, f3.nextPos)
        assertEquals(0f, f3.fill, 1e-6f)
    }

    @Test fun wrapsToDownbeat() {
        val f = ringFrame(g, 2_750 * ms) // 3번 박(위치 3) 이후
        assertEquals(3, f.currentPos)
        assertEquals(0, f.nextPos)
    }

    @Test fun beforeStartNoFlashButFillsTowardFirstBeat() {
        val f = ringFrame(g, 900 * ms)
        assertEquals(-1, f.currentPos)
        assertEquals(0, f.nextPos)
        assertEquals(0f, f.flash, 1e-6f)
        assertEquals(0.8f, f.fill, 1e-6f)
    }
}
