package kr.hui.takt.engine

import kotlin.math.abs

/**
 * 반복 진동 파형(VibrationEffect.createWaveform 용) 생성기. 안드로이드 의존성 없음.
 *
 * 구조: timings = [첫 지연, on0, off0, on1, off1, ...], amplitudes = [0, a0, 0, a1, 0, ...]
 * repeatIndex = 1 → 첫 지연 이후 구간을 무한 반복.
 *
 * 한 주기는 마디의 정수배 박([cycleBeats])이다. 가능하면 주기 길이가 정확히 정수 ms 가 되는
 * lcm(bpm, beatsPerBar) 박을 쓰고, 그게 너무 길면(바인더 전송 한도) 상한 안에서 ms 오차가 가장 작은
 * 마디 배수를 고른다. 남는 오차(주기당 0.5ms 이하)는 엔진의 주기적 재정렬이 흡수한다.
 * 각 펄스의 시작 시각은 주기 시작 기준 round(k * 60000 / (bpm * subdivision)) ms.
 */
class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray,
    /** 반복 구간의 박 수 */
    val beats: Int,
) {
    val repeatIndex: Int get() = 1

    /** 반복 구간(첫 지연 제외) 길이(ms). */
    val cycleMillis: Long get() = timings.sum() - timings[0]

    companion object {
        /** 한 주기 최대 펄스 수. 펄스당 구간 2개 → 파형 구간 약 4000개 (parcel 약 100KB 미만) */
        const val MAX_CYCLE_PULSES = 2048

        fun build(
            bpm: Int,
            beatsPerBar: Int,
            startBarPosition: Int,
            firstDelayMs: Long,
            style: HapticStyle,
            subdivision: Int = 1,
            /** 마디 내 박별 세기(0=무음, 1=보통, 2=강박). null 이면 style.accentEnabled 에 따른 기본값 */
            levels: IntArray? = null,
        ): HapticPattern {
            require(firstDelayMs >= 0)
            require(subdivision >= 1)
            val beats = cycleBeats(bpm, beatsPerBar, subdivision)
            val pulses = beats * subdivision
            val rate = bpm.toLong() * subdivision // 분당 펄스 수
            val timings = LongArray(1 + pulses * 2)
            val amps = IntArray(1 + pulses * 2)
            timings[0] = firstDelayMs
            for (k in 0 until pulses) {
                val start = roundDiv(k * 60_000L, rate)
                val span = roundDiv((k + 1) * 60_000L, rate) - start
                val barPos = (startBarPosition + k / subdivision) % beatsPerBar
                val level = levels?.getOrNull(barPos) ?: defaultLevel(barPos, beatsPerBar, style.accentEnabled)
                val kind = when {
                    k % subdivision != 0 -> PulseKind.SUB
                    level >= LEVEL_ACCENT -> PulseKind.ACCENT
                    level <= LEVEL_MUTE -> PulseKind.MUTE
                    else -> PulseKind.BEAT
                }
                val wanted = when (kind) {
                    PulseKind.ACCENT -> style.accentPulseMs
                    PulseKind.BEAT, PulseKind.MUTE -> style.pulseMs
                    PulseKind.SUB -> style.subPulseMs
                }
                // 펄스 간격의 45% 를 넘지 않게 (빠른 템포에서 펄스가 이어붙지 않도록)
                val pulse = wanted.coerceAtMost(span * 45 / 100).coerceAtLeast(1)
                timings[1 + k * 2] = pulse
                timings[2 + k * 2] = span - pulse
                amps[1 + k * 2] = when (kind) {
                    PulseKind.ACCENT -> style.accentAmplitude
                    PulseKind.BEAT -> style.amplitude
                    PulseKind.SUB -> style.subAmplitude
                    PulseKind.MUTE -> 0
                }
            }
            return HapticPattern(timings, amps, beats)
        }

        /**
         * 주기 박 수. 마디의 배수 중 펄스 수 상한 안에서 주기 길이(ms)가 정수에 가장 가까운 것.
         * lcm(bpm, beatsPerBar) 가 상한 안이면 그것(오차 0)을 고른다.
         */
        fun cycleBeats(bpm: Int, beatsPerBar: Int, subdivision: Int = 1): Int {
            val maxBars = (MAX_CYCLE_PULSES / (beatsPerBar * subdivision)).coerceAtLeast(1)
            var best = beatsPerBar
            var bestErr = Double.MAX_VALUE
            for (m in 1..maxBars) {
                val k = m * beatsPerBar
                val exact = 60_000.0 * k / bpm
                val err = abs(exact - Math.round(exact))
                if (err < bestErr - 1e-9) {
                    best = k
                    bestErr = err
                    if (err < 1e-9) break
                }
            }
            return best
        }

        /** 주기 길이의 이상값 대비 오차(ms). */
        fun cycleErrorMs(bpm: Int, beats: Int): Double {
            val exact = 60_000.0 * beats / bpm
            return exact - Math.round(exact)
        }

        const val LEVEL_MUTE = 0
        const val LEVEL_NORMAL = 1
        const val LEVEL_ACCENT = 2

        fun defaultLevel(barPos: Int, beatsPerBar: Int, accentEnabled: Boolean) =
            if (accentEnabled && beatsPerBar > 1 && barPos == 0) LEVEL_ACCENT else LEVEL_NORMAL

        private fun roundDiv(a: Long, b: Long): Long = (2 * a + b) / (2 * b)
    }

    private enum class PulseKind { ACCENT, BEAT, SUB, MUTE }
}

/** 진동 느낌 설정. 진폭은 1..255. */
data class HapticStyle(
    val amplitude: Int = 150,
    val accentAmplitude: Int = 255,
    val subAmplitude: Int = 70,
    val pulseMs: Long = 30,
    val accentPulseMs: Long = 55,
    val subPulseMs: Long = 16,
    val accentEnabled: Boolean = true,
) {
    /** 진폭 제어가 안 되는 기기: 진폭은 기본값, 강박/세분은 길이로만 구분. */
    fun withoutAmplitudeControl(defaultAmplitude: Int) = copy(
        amplitude = defaultAmplitude,
        accentAmplitude = defaultAmplitude,
        subAmplitude = defaultAmplitude,
        accentPulseMs = if (accentEnabled) accentPulseMs + 25 else accentPulseMs,
        subPulseMs = (subPulseMs / 2).coerceAtLeast(6),
    )
}
