package kr.hui.takt.engine

import kr.hui.takt.ui.RotaryAccumulator
import kr.hui.takt.ui.tempoName
import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryTest {
    @Test fun slowDetentsAreSingleSteps() {
        val r = RotaryAccumulator()
        var t = 0L
        repeat(20) { assertEquals(1, r.consume(30f, t)); t += 300 }
        assertEquals(-1, r.consume(-30f, t))
    }

    @Test fun smallEventsAccumulate() {
        val r = RotaryAccumulator()
        assertEquals(0, r.consume(10f, 0))
        assertEquals(0, r.consume(10f, 500))
        assertEquals(1, r.consume(10f, 1000))
    }

    @Test fun fastSpinAccelerates() {
        val r = RotaryAccumulator()
        val steps = (0 until 20).map { r.consume(30f, it * 40L) }
        assertEquals(1, steps[0])
        assertEquals(2, steps[6])
        assertEquals(5, steps[15])
        // 멈췄다 다시 돌리면 원래 속도
        assertEquals(1, r.consume(30f, 5_000))
    }

    @Test fun tempoNames() {
        assertEquals("Andante", tempoName(96))
        assertEquals("Allegro", tempoName(120))
        assertEquals("Prestissimo", tempoName(240))
    }
}
