package kr.hui.takt.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 진동 출력부.
 *
 * 박마다 앱에서 vibrate() 를 부르면 스레드 스케줄링 때문에 수 ms 씩 흔들린다.
 * 대신 반복 파형 하나를 시스템 진동 서비스에 넘겨서, 타이밍을 시스템이 직접 재게 한다.
 * 앱은 시작·설정 변경·화면 꺼짐 때, 그리고 안전장치로 주기적으로 파형을 격자에 다시 맞춘다(re-anchor).
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    val hasVibrator: Boolean get() = vibrator.hasVibrator()
    val hasAmplitudeControl: Boolean get() = vibrator.hasAmplitudeControl()

    /**
     * 격자 [grid] 의 다음 박부터 시작하는 파형을 재생한다.
     * @return 파형의 첫 박 번호
     */
    fun playFrom(grid: BeatGrid, style: HapticStyle): Long {
        val now = SystemClock.elapsedRealtimeNanos()
        val first = grid.nextBeatAfter(now + MIN_LEAD_NANOS)
        val delayMs = ((grid.timeOfBeat(first) - now) / 1_000_000L).coerceAtLeast(0)
        val effectiveStyle =
            if (hasAmplitudeControl) style
            else style.withoutAmplitudeControl(VibrationEffect.DEFAULT_AMPLITUDE)
        val pattern = HapticPattern.build(
            bpm = grid.bpm,
            beatsPerBar = grid.beatsPerBar,
            startBarPosition = grid.barPosition(first),
            firstDelayMs = delayMs,
            style = effectiveStyle,
        )
        val effect = VibrationEffect.createWaveform(
            pattern.timings, pattern.amplitudes, pattern.repeatIndex,
        )
        vibrate(effect)
        return first
    }

    fun stop() = vibrator.cancel()

    private fun vibrate(effect: VibrationEffect) {
        // ALARM 용도: 방해금지 모드에서도 울리고, 백그라운드(포그라운드 서비스) 진동 제한에서 허용되는 용도.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    companion object {
        /** vibrate() 호출 지연을 감안한 최소 여유 시간. */
        private const val MIN_LEAD_NANOS = 15_000_000L
    }
}
