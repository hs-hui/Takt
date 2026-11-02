package kr.hui.takt.engine

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 절대 시간축 위의 박자 격자.
 *
 * 박 번호 n 은 시각 [originNanos] + (n - [originBeat]) * beatNanos 에 울린다.
 * 시간 기준은 SystemClock.elapsedRealtimeNanos() 이다.
 * 마디 내 위치는 n mod beatsPerBar 로 계산하므로, 템포를 바꿔도 마디 위치는 이어진다.
 */
data class BeatGrid(
    val originNanos: Long,
    val originBeat: Long,
    val bpm: Int,
    val beatsPerBar: Int,
) {
    init {
        require(bpm in MIN_BPM..MAX_BPM) { "bpm out of range: $bpm" }
        require(beatsPerBar in MIN_BEATS..MAX_BEATS) { "beatsPerBar out of range: $beatsPerBar" }
    }

    val beatNanos: Double get() = NANOS_PER_MINUTE / bpm

    fun timeOfBeat(n: Long): Long = originNanos + ((n - originBeat) * beatNanos).roundToLong()

    /** 시각 [t] 이전(같은 시각 포함)에 울린 마지막 박. 아직 시작 전이면 originBeat - 1 이하. */
    fun beatAtOrBefore(t: Long): Long {
        val n = originBeat + floor((t - originNanos) / beatNanos).toLong()
        // 반올림 경계 보정
        return when {
            timeOfBeat(n + 1) <= t -> n + 1
            timeOfBeat(n) > t -> n - 1
            else -> n
        }
    }

    /** 시각 [t] 보다 엄격히 뒤에 오는 첫 박. */
    fun nextBeatAfter(t: Long): Long = beatAtOrBefore(t) + 1

    fun barPosition(n: Long): Int = Math.floorMod(n, beatsPerBar.toLong()).toInt()

    /**
     * 재생 중 설정 변경: 기존 격자의 다음 박을 새 격자의 원점으로 삼는다.
     * 박자표가 바뀌면 다음 박을 새 마디의 첫 박으로 만든다.
     */
    fun retimed(now: Long, newBpm: Int, newBeatsPerBar: Int): BeatGrid {
        val next = nextBeatAfter(now)
        val at = timeOfBeat(next)
        val beatIndex = if (newBeatsPerBar == beatsPerBar) next else 0L
        return BeatGrid(at, beatIndex, newBpm, newBeatsPerBar)
    }

    companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
        const val MIN_BEATS = 1
        const val MAX_BEATS = 12
        const val NANOS_PER_MINUTE = 60_000_000_000.0

        fun start(now: Long, bpm: Int, beatsPerBar: Int, leadNanos: Long = 50_000_000L) =
            BeatGrid(now + leadNanos, 0L, bpm, beatsPerBar)
    }
}
