package kr.hui.takt.engine

import kotlin.math.roundToInt

/** 탭 템포: 최근 탭 간격들의 평균으로 BPM 계산. 2초 넘게 쉬면 새로 시작. */
class TapTempo(
    private val maxIntervals: Int = 4,
    private val resetAfterNanos: Long = 2_000_000_000L,
) {
    private val taps = ArrayDeque<Long>()

    /** 탭을 기록하고, 계산 가능하면 BPM(범위 제한됨)을 반환. */
    fun tap(now: Long): Int? {
        val last = taps.lastOrNull()
        if (last != null && now - last > resetAfterNanos) taps.clear()
        taps.addLast(now)
        while (taps.size > maxIntervals + 1) taps.removeFirst()
        if (taps.size < 2) return null
        val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        return (BeatGrid.NANOS_PER_MINUTE / avg).roundToInt()
            .coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM)
    }

    fun reset() = taps.clear()
}
