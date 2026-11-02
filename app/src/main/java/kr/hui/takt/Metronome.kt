package kr.hui.takt

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.data.SettingsRepository
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticEngine
import kr.hui.takt.engine.TapTempo
import kr.hui.takt.service.MetronomeService

data class MetronomeState(
    val settings: MetronomeSettings = MetronomeSettings(),
    /** 재생 중이면 현재 박자 격자, 정지 상태면 null */
    val grid: BeatGrid? = null,
) {
    val playing: Boolean get() = grid != null
}

/**
 * 앱 전역 메트로놈 컨트롤러. 모든 메서드는 메인 스레드에서 호출한다.
 * UI 와 [MetronomeService] 가 같은 상태를 공유한다.
 */
@SuppressLint("StaticFieldLeak") // applicationContext 만 보관하므로 누수 아님
object Metronome {
    private lateinit var appContext: Context
    private lateinit var engine: HapticEngine
    private lateinit var repo: SettingsRepository

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val tapTempo = TapTempo()

    private val _state = MutableStateFlow(MetronomeState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()

    val hasVibrator: Boolean get() = engine.hasVibrator

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        engine = HapticEngine(appContext)
        repo = SettingsRepository(appContext)
        scope.launch {
            val loaded = repo.load()
            if (!_state.value.playing) _state.value = _state.value.copy(settings = loaded)
            // 설정 변경을 모아서 저장
            _state.map { it.settings }
                .distinctUntilChanged()
                .drop(1)
                .debounce(500)
                .collect { repo.save(it) }
        }
    }

    fun toggle() = if (_state.value.playing) stop() else start()

    fun start() {
        if (_state.value.playing) return
        val s = _state.value.settings
        val grid = BeatGrid.start(SystemClock.elapsedRealtimeNanos(), s.bpm, s.beatsPerBar)
        _state.value = _state.value.copy(grid = grid)
        engine.playFrom(grid, s.hapticStyle())
        scheduleReanchor()
        ContextCompat.startForegroundService(appContext, Intent(appContext, MetronomeService::class.java))
    }

    fun stop() {
        if (!_state.value.playing) return
        handler.removeCallbacks(reanchorRunnable)
        engine.stop()
        _state.value = _state.value.copy(grid = null)
        appContext.stopService(Intent(appContext, MetronomeService::class.java))
    }

    fun setBpm(bpm: Int) = update { it.copy(bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM)) }
    fun nudgeBpm(delta: Int) = setBpm(_state.value.settings.bpm + delta)
    fun setBeatsPerBar(n: Int) =
        update { it.copy(beatsPerBar = n.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS)) }
    fun setAccent(enabled: Boolean) = update { it.copy(accentEnabled = enabled) }
    fun setStrength(level: Int) =
        update { it.copy(strength = level.coerceIn(1, MetronomeSettings.STRENGTH_AMPLITUDES.size)) }

    fun tap() {
        tapTempo.tap(SystemClock.elapsedRealtimeNanos())?.let { setBpm(it) }
    }

    /** 화면 꺼짐 등으로 시스템이 진동을 끊었을 수 있을 때 서비스가 호출. */
    fun reanchorNow() {
        val st = _state.value
        val grid = st.grid ?: return
        engine.playFrom(grid, st.settings.hapticStyle())
        scheduleReanchor()
    }

    private fun update(change: (MetronomeSettings) -> MetronomeSettings) {
        val old = _state.value
        val newSettings = change(old.settings)
        if (newSettings == old.settings) return
        val oldGrid = old.grid
        if (oldGrid == null) {
            _state.value = old.copy(settings = newSettings)
            return
        }
        val grid =
            if (newSettings.bpm != oldGrid.bpm || newSettings.beatsPerBar != oldGrid.beatsPerBar) {
                oldGrid.retimed(SystemClock.elapsedRealtimeNanos(), newSettings.bpm, newSettings.beatsPerBar)
            } else oldGrid
        _state.value = MetronomeState(newSettings, grid)
        engine.playFrom(grid, newSettings.hapticStyle())
        scheduleReanchor()
    }

    private val reanchorRunnable = Runnable { reanchorNow() }

    /**
     * 안전장치: 주기적으로 파형을 격자에 다시 맞춘다.
     * 진행 중인 펄스를 자르지 않도록, 펄스가 끝난 직후 시점에 실행한다.
     */
    private fun scheduleReanchor() {
        handler.removeCallbacks(reanchorRunnable)
        val grid = _state.value.grid ?: return
        val now = SystemClock.elapsedRealtimeNanos()
        val beat = grid.nextBeatAfter(now + REANCHOR_INTERVAL_NANOS)
        val at = grid.timeOfBeat(beat) + AFTER_PULSE_NANOS
        handler.postDelayed(reanchorRunnable, (at - now) / 1_000_000L)
    }

    private const val REANCHOR_INTERVAL_NANOS = 30_000_000_000L
    private const val AFTER_PULSE_NANOS = 100_000_000L
}
