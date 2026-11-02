package kr.hui.takt.engine

/** 격자 위의 펄스(박 + 세분) 하나. */
data class Pulse(
    val beat: Long,
    /** 박 안에서 몇 번째 세분 (0 = 박 자체) */
    val sub: Int,
    val timeNanos: Long,
)

/**
 * 격자 + 세분 → 펄스 시각 계산. 안드로이드 의존성 없음.
 * 세분 펄스 j 의 시각은 박 n 시각 + j * (박 길이 / subdivision).
 */
object PulseClock {
    fun pulseTime(grid: BeatGrid, beat: Long, sub: Int, subdivision: Int): Long =
        grid.timeOfBeat(beat) + (sub * grid.beatNanos / subdivision).toLong()

    /** 시각 [t] 보다 엄격히 뒤에 오는 첫 펄스. */
    fun nextPulseAfter(grid: BeatGrid, t: Long, subdivision: Int): Pulse {
        val n = grid.beatAtOrBefore(t)
        for (beat in n..n + 1) {
            for (j in 0 until subdivision) {
                val pt = pulseTime(grid, beat, j, subdivision)
                if (pt > t) return Pulse(beat, j, pt)
            }
        }
        // 도달하지 않음(박 n+1 의 0번 펄스는 항상 t 이후)
        return Pulse(n + 1, 0, grid.timeOfBeat(n + 1))
    }

    /** 펄스 종류: 2=강박, 1=보통 박, 0=무음, -1=세분 */
    fun kindOf(pulse: Pulse, grid: BeatGrid, style: HapticStyle, levels: IntArray?): Int =
        if (pulse.sub != 0) -1 else levelOf(pulse, grid, style, levels)

    private fun levelOf(pulse: Pulse, grid: BeatGrid, style: HapticStyle, levels: IntArray?): Int {
        val pos = grid.barPosition(pulse.beat)
        return levels?.getOrNull(pos) ?: HapticPattern.defaultLevel(pos, grid.beatsPerBar, style.accentEnabled)
    }

    /** 펄스 세기 (0 이면 무음). */
    fun amplitudeOf(pulse: Pulse, grid: BeatGrid, style: HapticStyle, levels: IntArray?): Int {
        if (pulse.sub != 0) return style.subAmplitude
        val level = levelOf(pulse, grid, style, levels)
        return when {
            level >= HapticPattern.LEVEL_ACCENT -> style.accentAmplitude
            level <= HapticPattern.LEVEL_MUTE -> 0
            else -> style.amplitude
        }
    }

    /** 펄스 길이(ms). 펄스 간격의 45% 를 넘지 않게. */
    fun durationOf(pulse: Pulse, grid: BeatGrid, style: HapticStyle, levels: IntArray?, subdivision: Int): Long {
        val wanted = when {
            pulse.sub != 0 -> style.subPulseMs
            levelOf(pulse, grid, style, levels) >= HapticPattern.LEVEL_ACCENT -> style.accentPulseMs
            else -> style.pulseMs
        }
        val spanMs = (grid.beatNanos / subdivision / 1_000_000.0).toLong()
        return wanted.coerceAtMost(spanMs * 45 / 100).coerceAtLeast(1)
    }
}
