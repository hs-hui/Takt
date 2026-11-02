package kr.hui.takt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * 실제 시계(System.nanoTime)로 스케줄러를 돌려 펄스가 빠지거나 중복되지 않고
 * 제시간에 불리는지 확인. (안드로이드의 진동 호출 지연은 포함되지 않음)
 */
class PulseSchedulerTest {
    private val ms = 1_000_000L
    private val style = HapticStyle()

    private class Fired(val pulse: Pulse, val at: Long, val bpm: Int)

    @Test fun firesEveryPulseOnTimeInOrder() {
        val fired = Collections.synchronizedList(mutableListOf<Fired>())
        val s = PulseScheduler(System::nanoTime, { p, c -> fired += Fired(p, System.nanoTime(), c.grid.bpm) })
        val start = System.nanoTime() + 50 * ms // 앱과 같은 시작 여유
        val grid = BeatGrid(start, 0, 240, 4) // 박 250ms
        s.play(PulseConfig(grid, style, 2, null)) // 125ms 간격
        Thread.sleep(1_100)
        s.stop()

        val list = synchronized(fired) { fired.toList() }
        println("late(ms): " + list.joinToString { "%.2f".format((it.at - it.pulse.timeNanos) / 1e6) })
        assertTrue("fired ${list.size}", list.size >= 8)
        list.forEachIndexed { i, f ->
            assertEquals(i.toLong() / 2, f.pulse.beat)
            assertEquals(i % 2, f.pulse.sub)
            // 단독 실행 시 0.3ms 이하. CI 처럼 다른 테스트가 CPU 를 다 쓰는 환경을 감안해 넉넉히 검사
            val late = f.at - f.pulse.timeNanos
            assertTrue("pulse $i late ${late / 1e6}ms", late in 0..(15 * ms))
        }
    }

    @Test fun tempoChangeContinuesWithoutDoubleOrMissedPulse() {
        val fired = Collections.synchronizedList(mutableListOf<Fired>())
        val s = PulseScheduler(System::nanoTime, { p, c -> fired += Fired(p, System.nanoTime(), c.grid.bpm) })
        val g0 = BeatGrid(System.nanoTime() + 50 * ms, 0, 300, 4) // 200ms
        s.play(PulseConfig(g0, style, 1, null))
        Thread.sleep(560) // 0, 200, 400ms 울린 뒤
        val g1 = g0.retimed(System.nanoTime(), 150, 4) // 다음 박(600ms)부터 400ms 간격
        s.play(PulseConfig(g1, style, 1, null))
        Thread.sleep(1_000)
        s.stop()

        val list = synchronized(fired) { fired.toList() }
        val beats = list.map { it.pulse.beat }
        assertEquals((0L until beats.size).toList(), beats) // 연속, 중복 없음
        val gaps = list.zipWithNext { a, b -> (b.pulse.timeNanos - a.pulse.timeNanos) / ms }
        assertEquals(listOf(200L, 200L, 200L, 400L, 400L), gaps.take(5))
    }

    @Test fun leadFiresEarlyByTheGivenAmount() {
        val fired = Collections.synchronizedList(mutableListOf<Fired>())
        val s = PulseScheduler(
            System::nanoTime,
            { p, c -> fired += Fired(p, System.nanoTime(), c.grid.bpm) },
            leadNanos = { 20 * ms },
        )
        s.play(PulseConfig(BeatGrid(System.nanoTime() + 80 * ms, 0, 300, 4), style, 1, null))
        Thread.sleep(700)
        s.stop()
        val list = synchronized(fired) { fired.toList() }
        assertEquals((0L until list.size).toList(), list.map { it.pulse.beat })
        list.forEach {
            val early = it.pulse.timeNanos - it.at
            assertTrue("early ${early / 1e6}ms", early in (5 * ms)..(20 * ms))
        }
    }

    @Test fun stopHaltsQuickly() {
        val fired = Collections.synchronizedList(mutableListOf<Fired>())
        val s = PulseScheduler(System::nanoTime, { p, c -> fired += Fired(p, System.nanoTime(), c.grid.bpm) })
        s.play(PulseConfig(BeatGrid(System.nanoTime(), 0, 300, 4), style, 4, null))
        Thread.sleep(200)
        s.stop()
        Thread.sleep(50)
        val n = fired.size
        Thread.sleep(300)
        assertEquals(n, fired.size)
        assertTrue(!s.running)
    }
}
