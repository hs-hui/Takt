package kr.hui.takt.engine

/**
 * 반복 진동 파형(VibrationEffect.createWaveform 용) 생성기. 안드로이드 의존성 없음.
 *
 * 구조: timings = [첫 지연, on0, off0, on1, off1, ...], amplitudes = [0, a0, 0, a1, 0, ...]
 * repeatIndex = 1 → 첫 지연 이후 구간을 무한 반복.
 *
 * 한 주기는 lcm(bpm, beatsPerBar) 박으로 잡는다. 그러면 주기 길이가
 * 60000 * K / bpm ms 로 정확히 정수가 되어 반복해도 오차가 누적되지 않고,
 * 마디 경계(강박 위치)도 주기마다 같은 자리에 온다.
 * 각 박의 시작 시각은 주기 시작 기준 round(i * 60000 / bpm) ms 로 계산해
 * 박 하나하나의 오차는 0.5ms 이하로 유지한다.
 */
class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray,
) {
    val repeatIndex: Int get() = 1

    /** 반복 구간(첫 지연 제외) 길이(ms). */
    val cycleMillis: Long get() = timings.drop(1).sum()

    companion object {
        fun build(
            bpm: Int,
            beatsPerBar: Int,
            startBarPosition: Int,
            firstDelayMs: Long,
            style: HapticStyle,
        ): HapticPattern {
            require(firstDelayMs >= 0)
            val beats = cycleBeats(bpm, beatsPerBar)
            val timings = LongArray(1 + beats * 2)
            val amps = IntArray(1 + beats * 2)
            timings[0] = firstDelayMs
            amps[0] = 0
            for (i in 0 until beats) {
                val start = beatStartMs(i.toLong(), bpm)
                val end = beatStartMs(i + 1L, bpm)
                val span = end - start
                val barPos = (startBarPosition + i) % beatsPerBar
                val accent = style.accentEnabled && barPos == 0 && beatsPerBar > 1
                val wantedPulse = if (accent) style.accentPulseMs else style.pulseMs
                // 박 간격의 45% 를 넘지 않게 (빠른 템포에서 펄스가 이어붙지 않도록)
                val pulse = wantedPulse.coerceAtMost(span * 45 / 100).coerceAtLeast(1)
                timings[1 + i * 2] = pulse
                timings[2 + i * 2] = span - pulse
                amps[1 + i * 2] = if (accent) style.accentAmplitude else style.amplitude
                amps[2 + i * 2] = 0
            }
            return HapticPattern(timings, amps)
        }

        fun cycleBeats(bpm: Int, beatsPerBar: Int): Int = lcm(bpm, beatsPerBar)

        /** 주기 시작 기준 i 번째 박의 시작 시각(ms), 반올림. */
        fun beatStartMs(i: Long, bpm: Int): Long = (i * 60_000L * 2 + bpm) / (2L * bpm)

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b
    }
}

/** 진동 느낌 설정. 진폭은 1..255. */
data class HapticStyle(
    val amplitude: Int = 150,
    val accentAmplitude: Int = 255,
    val pulseMs: Long = 30,
    val accentPulseMs: Long = 55,
    val accentEnabled: Boolean = true,
) {
    /** 진폭 제어가 안 되는 기기: 진폭은 기본값, 강박은 길이로만 구분. */
    fun withoutAmplitudeControl(defaultAmplitude: Int) = copy(
        amplitude = defaultAmplitude,
        accentAmplitude = defaultAmplitude,
        accentPulseMs = if (accentEnabled) accentPulseMs + 25 else accentPulseMs,
    )
}
