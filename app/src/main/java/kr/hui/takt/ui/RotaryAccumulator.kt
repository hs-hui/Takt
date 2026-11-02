package kr.hui.takt.ui

import kotlin.math.abs
import kotlin.math.sign

/**
 * 크라운/베젤 회전을 BPM 단계로 변환.
 * - 베젤 한 칸(큰 이벤트)은 최소 1단계
 * - 빠르게 연속으로 돌리면 가속(×2, ×5)
 */
class RotaryAccumulator(private val pixelsPerStep: Float = 24f) {
    private var acc = 0f
    private var lastStepAt = Long.MIN_VALUE / 2
    private var streak = 0

    fun consume(pixels: Float, timeMillis: Long): Int {
        val base = if (abs(pixels) >= pixelsPerStep) {
            acc = 0f
            (pixels / pixelsPerStep).toInt().coerceIn(-5, 5).let { if (it == 0) sign(pixels).toInt() else it }
        } else {
            acc += pixels
            val steps = (acc / pixelsPerStep).toInt()
            acc -= steps * pixelsPerStep
            steps
        }
        if (base == 0) return 0
        streak = if (timeMillis - lastStepAt < FAST_GAP_MS) streak + 1 else 0
        lastStepAt = timeMillis
        val mult = when {
            streak >= 12 -> 5
            streak >= 5 -> 2
            else -> 1
        }
        return base * mult
    }

    companion object {
        private const val FAST_GAP_MS = 90L
    }
}

/** 템포 이름 (대략적인 관례 범위) */
fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}
