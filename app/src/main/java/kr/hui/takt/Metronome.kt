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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.data.SettingsRepository
import kr.hui.takt.data.TrainerSettings
import kr.hui.takt.data.nextBpm
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticEngine
import kr.hui.takt.engine.TapTempo
import kr.hui.takt.service.MetronomeService

data class MetronomeState(
    val settings: MetronomeSettings = MetronomeSettings(),
    /** 재생 중이면 현재 진동이 따르는 박자 격자, 정지 상태면 null */
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

    /** 스피드 트레이너가 다음에 템포를 올릴 박(마디 첫 박) 번호 */
    private var trainerNextBeat = Long.MAX_VALUE
    /** 트레이너 시작 전 BPM. 정지하면 이 값으로 되돌린다. */
    private var trainerStartBpm: Int? = null

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
        play(grid, s)
        resetTrainer(grid, s)
        ContextCompat.startForegroundService(appContext, Intent(appContext, MetronomeService::class.java))
    }

    fun stop() {
        if (!_state.value.playing) return
        handler.removeCallbacksAndMessages(null)
        engine.stop()
        val restored = trainerStartBpm
        trainerStartBpm = null
        trainerNextBeat = Long.MAX_VALUE
        _state.value = _state.value.let { st ->
            st.copy(
                grid = null,
                settings = if (restored != null) st.settings.copy(bpm = restored) else st.settings,
            )
        }
        appContext.stopService(Intent(appContext, MetronomeService::class.java))
    }

    fun setBpm(bpm: Int) = update { it.copy(bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM)) }
    fun nudgeBpm(delta: Int) = setBpm(_state.value.settings.bpm + delta)
    fun setBeatsPerBar(n: Int) =
        update { it.copy(beatsPerBar = n.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS)) }
    fun setSubdivision(n: Int) =
        update { it.copy(subdivision = n.coerceIn(1, MetronomeSettings.MAX_SUBDIVISION)) }
    fun setAccent(enabled: Boolean) = update { it.copy(accentEnabled = enabled) }
    fun setStrength(level: Int) =
        update { it.copy(strength = level.coerceIn(1, MetronomeSettings.STRENGTH_AMPLITUDES.size)) }
    fun setTrainer(change: (TrainerSettings) -> TrainerSettings) =
        update { it.copy(trainer = change(it.trainer).sanitized()) }

    fun tap() {
        tapTempo.tap(SystemClock.elapsedRealtimeNanos())?.let { setBpm(it) }
    }

    /** 화면 꺼짐 등으로 시스템이 진동을 끊었을 수 있을 때 서비스가 호출. */
    fun reanchorNow() {
        val st = _state.value
        val grid = st.grid ?: return
        play(grid, st.settings)
    }

    private fun update(change: (MetronomeSettings) -> MetronomeSettings) {
        val old = _state.value
        val newSettings = change(old.settings)
        if (newSettings == old.settings) return
        _state.value = old.copy(settings = newSettings)
        if (!old.playing) return
        if (newSettings.trainer.enabled != old.settings.trainer.enabled) {
            old.grid?.let { resetTrainer(it, newSettings) }
        }
        // 베젤을 빠르게 돌리면 변경이 연달아 들어온다. 매번 진동을 다시 걸면 펄스가 잘리므로
        // 잠깐 모았다가 한 번에 적용한다. 화면 숫자는 즉시 바뀐다.
        handler.removeCallbacks(applyRunnable)
        handler.postDelayed(applyRunnable, APPLY_DEBOUNCE_MS)
    }

    private val applyRunnable = Runnable { applySettingsToEngine() }

    /** 현재 설정을 재생 중인 격자에 반영(다음 박부터). */
    private fun applySettingsToEngine() {
        val st = _state.value
        val oldGrid = st.grid ?: return
        val s = st.settings
        val grid =
            if (s.bpm != oldGrid.bpm || s.beatsPerBar != oldGrid.beatsPerBar) {
                oldGrid.retimed(SystemClock.elapsedRealtimeNanos(), s.bpm, s.beatsPerBar)
            } else oldGrid
        _state.value = st.copy(grid = grid)
        if (grid.beatsPerBar != oldGrid.beatsPerBar) resetTrainer(grid, s)
        play(grid, s)
    }

    private fun play(grid: BeatGrid, s: MetronomeSettings) {
        engine.playFrom(grid, s.hapticStyle(), s.subdivision)
        scheduleReanchor(grid)
    }

    // ---- 재정렬 ----

    private val reanchorRunnable = Runnable { reanchorNow() }

    /**
     * 안전장치: 주기적으로 파형을 격자에 다시 맞춘다.
     * 진행 중인 펄스를 자르지 않도록, 박 펄스가 끝난 직후 시점에 실행한다.
     */
    private fun scheduleReanchor(grid: BeatGrid) {
        handler.removeCallbacks(reanchorRunnable)
        val now = SystemClock.elapsedRealtimeNanos()
        val beat = grid.nextBeatAfter(now + REANCHOR_INTERVAL_NANOS)
        val at = grid.timeOfBeat(beat) + AFTER_PULSE_NANOS
        handler.postDelayed(reanchorRunnable, (at - now) / 1_000_000L)
    }

    // ---- 스피드 트레이너 ----

    private val trainerRunnable = Runnable { trainerStep() }

    private fun resetTrainer(grid: BeatGrid, s: MetronomeSettings) {
        handler.removeCallbacks(trainerRunnable)
        if (!s.trainer.enabled) {
            trainerNextBeat = Long.MAX_VALUE
            return
        }
        if (trainerStartBpm == null) trainerStartBpm = s.bpm
        // 다가오는 마디 첫 박부터 세어 everyBars 마디 뒤 (재생 시작 직후라면 첫 마디부터 센다)
        val next = grid.nextBeatAfter(SystemClock.elapsedRealtimeNanos())
        val nextDownbeat = next + Math.floorMod(-grid.barPosition(next), grid.beatsPerBar)
        trainerNextBeat = nextDownbeat + s.trainer.everyBars.toLong() * grid.beatsPerBar
        scheduleTrainer(grid)
    }

    private fun scheduleTrainer(grid: BeatGrid) {
        if (trainerNextBeat == Long.MAX_VALUE) return
        // 해당 마디 첫 박 직전에 깨어나 템포를 바꾸면, 새 템포가 정확히 그 첫 박부터 시작된다.
        val lead = minOf(TRAINER_LEAD_NANOS, (grid.beatNanos / 3).toLong())
        val at = grid.timeOfBeat(trainerNextBeat) - lead
        val delayMs = ((at - SystemClock.elapsedRealtimeNanos()) / 1_000_000L).coerceAtLeast(0)
        handler.postDelayed(trainerRunnable, delayMs)
    }

    private fun trainerStep() {
        val st = _state.value
        val grid = st.grid ?: return
        val t = st.settings.trainer
        val nextBpm = if (t.enabled) t.nextBpm(st.settings.bpm) else null
        if (nextBpm == null) {
            trainerNextBeat = Long.MAX_VALUE
            return
        }
        handler.removeCallbacks(applyRunnable)
        val s = st.settings.copy(bpm = nextBpm)
        val newGrid = grid.retimed(SystemClock.elapsedRealtimeNanos(), nextBpm, grid.beatsPerBar)
        _state.value = MetronomeState(s, newGrid)
        play(newGrid, s)
        trainerNextBeat = newGrid.originBeat + t.everyBars.toLong() * newGrid.beatsPerBar
        scheduleTrainer(newGrid)
    }

    private const val APPLY_DEBOUNCE_MS = 120L
    private const val REANCHOR_INTERVAL_NANOS = 30_000_000_000L
    private const val AFTER_PULSE_NANOS = 100_000_000L
    private const val TRAINER_LEAD_NANOS = 60_000_000L
}
