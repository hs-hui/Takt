package kr.hui.takt.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** 진동을 내보내는 방식 */
enum class TimingMode {
    /**
     * 박(펄스)마다 고우선순위 스레드가 정확한 시각에 짧은 진동을 직접 건다.
     * 재설정으로 인한 튐이 없고, 화면 꺼짐 취소 영향도 없다. (기본)
     */
    DIRECT,

    /**
     * 반복 파형 하나를 시스템에 넘겨 시스템이 타이밍을 잰다.
     * 정상 상태에선 정밀하지만, 다시 걸 때마다 시스템 처리 지연만큼 한 번 튄다.
     */
    WAVEFORM,
}

/** DIRECT 모드에서 앱이 진동을 건 시각의 오차 통계 (진단용). */
data class TimingStats(
    val samples: Int,
    /** 진동 시작 추정 시각(vibrate() 호출 종료)과 목표 시각의 평균/최대 차이 (ms) */
    val meanLateMs: Double,
    val maxLateMs: Double,
    /** vibrate() 호출 자체에 걸린 시간 평균/최대 (ms) — 시스템 진동 서비스 응답 */
    val meanCallMs: Double,
    val maxCallMs: Double,
)

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

    private val direct = DirectPulser()

    /** 최근 DIRECT 모드 타이밍 통계 (없으면 null) */
    val timingStats: TimingStats? get() = direct.stats

    private fun effective(style: HapticStyle) =
        if (hasAmplitudeControl) style else style.withoutAmplitudeControl(VibrationEffect.DEFAULT_AMPLITUDE)

    /**
     * 격자 [grid] 를 따라 재생한다. 이미 재생 중이면 다음 펄스부터 새 설정으로 이어진다.
     */
    /** 사용자 보정: 진동을 이만큼 일찍 건다 (음수면 늦게). */
    @Volatile private var userLeadNanos = 0L

    fun play(
        mode: TimingMode,
        grid: BeatGrid,
        style: HapticStyle,
        subdivision: Int,
        levels: IntArray?,
        leadMs: Int = 0,
    ) {
        userLeadNanos = leadMs * 1_000_000L
        val st = effective(style)
        when (mode) {
            TimingMode.DIRECT -> {
                // 파형 모드에서 넘어온 경우 남은 파형 정지
                if (direct.config == null) vibrator.cancel()
                direct.play(PulseConfig(grid, st, subdivision, levels))
            }
            TimingMode.WAVEFORM -> {
                direct.stop()
                playWaveform(grid, st, subdivision, levels)
            }
        }
    }

    /** 화면 꺼짐 등으로 시스템이 진행 중 진동을 끊었을 수 있을 때. DIRECT 는 영향 없음. */
    fun resync(mode: TimingMode, grid: BeatGrid, style: HapticStyle, subdivision: Int, levels: IntArray?) {
        if (mode == TimingMode.WAVEFORM) playWaveform(grid, effective(style), subdivision, levels)
    }

    fun stop() {
        direct.stop()
        vibrator.cancel()
    }

    /** 정지 상태에서 세기 확인용: 강박 1번 + 보통 박 1번. */
    fun preview(style: HapticStyle) {
        val st = effective(style)
        if (st.crisp) {
            direct.previewCrisp()
            return
        }
        vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, st.accentPulseMs, 300, st.pulseMs),
                intArrayOf(0, st.accentAmplitude, 0, st.amplitude),
                -1,
            ),
        )
    }

    private fun playWaveform(grid: BeatGrid, style: HapticStyle, subdivision: Int, levels: IntArray?) {
        val lead = userLeadNanos
        val now = SystemClock.elapsedRealtimeNanos()
        // 다음 "펄스"부터 시작 (다음 박부터 시작하면 그 사이 세분 펄스가 빠진다)
        val first = PulseClock.nextPulseAfter(grid, now + lead + MIN_LEAD_NANOS, subdivision)
        val delayMs = ((first.timeNanos - lead - now) / 1_000_000L).coerceAtLeast(0)
        val pattern = HapticPattern.build(
            bpm = grid.bpm,
            beatsPerBar = grid.beatsPerBar,
            startBarPosition = grid.barPosition(first.beat),
            firstDelayMs = delayMs,
            style = style,
            subdivision = subdivision,
            levels = levels,
            startSub = first.sub,
        )
        if (pattern.amplitudes.all { it == 0 }) {
            vibrator.cancel() // 모든 박이 무음
            return
        }
        vibrate(VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, pattern.repeatIndex))
    }

    private val attributes: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        else null
    private val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()

    private fun vibrate(effect: VibrationEffect) {
        // ALARM 용도: 방해금지 모드에서도 울리고, 포그라운드 서비스 백그라운드 진동 제한에서 허용되는 용도.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, attributes as VibrationAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        }
    }

    /**
     * DIRECT 모드: 펄스마다 짧은 진동을 직접 건다. 타이밍 오차를 진단용으로 기록한다.
     */
    private inner class DirectPulser {
        private val scheduler = PulseScheduler(
            clock = SystemClock::elapsedRealtimeNanos,
            onPulse = ::fire,
            threadSetup = { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) },
            // 자동 보정(최근 vibrate() 호출 시간 평균) + 사용자 보정
            leadNanos = { autoLeadNanos + userLeadNanos },
        )
        /** 최근 vibrate() 호출 평균 시간. 진동은 대략 호출이 끝날 무렵 시작되므로 그만큼 일찍 건다. */
        @Volatile private var autoLeadNanos = 0L
        val config: PulseConfig? get() = scheduler.config

        @Volatile var stats: TimingStats? = null
            private set
        private val lateNs = LongArray(STATS_WINDOW)
        private val callNs = LongArray(STATS_WINDOW)
        private var statCount = 0

        /** 펄스 종류별 진동 효과 캐시 (매 펄스 할당 방지) */
        private val effects = ConcurrentHashMap<Long, VibrationEffect>()

        fun play(c: PulseConfig) {
            if (!scheduler.running) statCount = 0 // 자동 보정값은 유지 (기기 특성)
            scheduler.play(c)
        }

        fun stop() = scheduler.stop()

        /** 시스템 클릭 미리보기: 강박 → 보통 박 */
        fun previewCrisp() {
            vibrate(crispEffect(HapticPattern.LEVEL_ACCENT))
            Thread {
                Thread.sleep(350)
                vibrate(crispEffect(HapticPattern.LEVEL_NORMAL))
            }.start()
        }

        private fun fire(pulse: Pulse, c: PulseConfig) {
            val amp = PulseClock.amplitudeOf(pulse, c.grid, c.style, c.levels)
            if (amp <= 0) return
            val effect = if (c.style.crisp) {
                crispEffect(PulseClock.kindOf(pulse, c.grid, c.style, c.levels))
            } else {
                val dur = PulseClock.durationOf(pulse, c.grid, c.style, c.levels, c.subdivision)
                effects.getOrPut(dur shl 16 or (amp.toLong() and 0xFFFF)) {
                    VibrationEffect.createOneShot(dur, amp)
                }
            }
            val callStart = SystemClock.elapsedRealtimeNanos()
            try {
                vibrate(effect)
            } catch (_: RuntimeException) {
                // 진동 서비스 일시 오류: 다음 펄스에서 다시 시도
            }
            val callEnd = SystemClock.elapsedRealtimeNanos()
            // 앱 오차: 호출이 끝난(≈진동 시작) 시각과 펄스 시각의 차이
            record(callEnd - pulse.timeNanos + userLeadNanos, callEnd - callStart)
        }

        /**
         * 기기에 맞춰 조율된 시스템 효과. 모터를 과구동해 짧아도 세게 친다.
         * 지원하면 합성 프리미티브(THUD/CLICK/TICK), 아니면 미리 정의된 효과.
         */
        private fun crispEffect(kind: Int): VibrationEffect = crisp.getOrPut(kind) {
            val click = VibrationEffect.Composition.PRIMITIVE_CLICK
            val tick = VibrationEffect.Composition.PRIMITIVE_TICK
            if (vibrator.areAllPrimitivesSupported(click, tick)) {
                val c = VibrationEffect.startComposition()
                when (kind) {
                    HapticPattern.LEVEL_ACCENT ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
                        ) {
                            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                        } else {
                            // THUD 가 없으면 클릭 두 번을 붙여 강박을 구분
                            c.addPrimitive(click, 1f).addPrimitive(click, 1f, 20)
                        }
                    -1 -> c.addPrimitive(tick, 1f)
                    else -> c.addPrimitive(click, 1f)
                }
                c.compose()
            } else {
                VibrationEffect.createPredefined(
                    when (kind) {
                        HapticPattern.LEVEL_ACCENT -> VibrationEffect.EFFECT_HEAVY_CLICK
                        -1 -> VibrationEffect.EFFECT_TICK
                        else -> VibrationEffect.EFFECT_CLICK
                    },
                )
            }
        }
        private val crisp = ConcurrentHashMap<Int, VibrationEffect>()

        private fun record(late: Long, call: Long) {
            val i = statCount % STATS_WINDOW
            lateNs[i] = late
            callNs[i] = call
            statCount++
            if (statCount % 8 == 0) {
                val n = minOf(statCount, STATS_WINDOW)
                var sl = 0.0; var ml = 0L; var sc = 0.0; var mc = 0L
                for (k in 0 until n) {
                    sl += abs(lateNs[k]); ml = maxOf(ml, abs(lateNs[k]))
                    sc += callNs[k]; mc = maxOf(mc, callNs[k])
                }
                stats = TimingStats(n, sl / n / 1e6, ml / 1e6, sc / n / 1e6, mc / 1e6)
                autoLeadNanos = (sc / n).toLong().coerceIn(0L, MAX_AUTO_LEAD_NANOS)
            }
        }
    }

    companion object {
        /** vibrate() 호출 지연을 감안한 최소 여유 시간 (파형 모드). */
        private const val MIN_LEAD_NANOS = 15_000_000L
        private const val STATS_WINDOW = 64
        private const val MAX_AUTO_LEAD_NANOS = 25_000_000L
    }
}
