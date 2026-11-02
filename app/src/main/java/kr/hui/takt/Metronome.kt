package kr.hui.takt

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import kotlinx.coroutines.CompletableDeferred
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
import kr.hui.takt.complication.BpmComplicationService
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.data.Preset
import kr.hui.takt.data.SettingsRepository
import kr.hui.takt.data.TrainerSettings
import kr.hui.takt.data.nextBpm
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticEngine
import kr.hui.takt.engine.TapTempo
import kr.hui.takt.engine.TimingMode
import kr.hui.takt.engine.TimingStats
import kr.hui.takt.service.MetronomeService
import kr.hui.takt.tile.MetronomeTileService

data class MetronomeState(
    val settings: MetronomeSettings = MetronomeSettings(),
    /** 재생 시작 시각 (SystemClock.elapsedRealtime, ms). 정지 상태면 0 */
    val startedAtElapsedMs: Long = 0L,
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

    private val loaded = CompletableDeferred<Unit>()
    private val _state = MutableStateFlow(MetronomeState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()

    val hasVibrator: Boolean get() = engine.hasVibrator

    /** DIRECT 모드 타이밍 진단 */
    val timingStats: TimingStats? get() = engine.timingStats

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
            // 타일·컴플리케이션이 현재 상태를 보여주도록 변경 시 갱신 요청
            _state.map { Triple(it.playing, it.settings.bpm, it.settings.beatsPerBar to it.settings.subdivision) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(300)
                .collect {
                    TileService.getUpdater(appContext).requestUpdate(MetronomeTileService::class.java)
                    BpmComplicationService.requestUpdate(appContext)
                }
        }
        scope.launch {
            val saved = repo.load()
            if (!_state.value.playing) _state.value = _state.value.copy(settings = saved)
            loaded.complete(Unit)
            // 설정 변경을 모아서 저장
            _state.map { it.settings }
                .distinctUntilChanged()
                .drop(1)
                .debounce(500)
                .collect { repo.save(it) }
        }
    }

    /** 저장된 설정을 다 읽을 때까지 대기 (타일처럼 프로세스가 막 뜬 경우). */
    suspend fun awaitLoaded() = loaded.await()

    fun toggle() = if (_state.value.playing) stop() else start()

    fun start() {
        if (_state.value.playing) return
        val s = _state.value.settings
        val grid = BeatGrid.start(SystemClock.elapsedRealtimeNanos(), s.bpm, s.beatsPerBar)
        _state.value = _state.value.copy(grid = grid, startedAtElapsedMs = SystemClock.elapsedRealtime())
        play(grid, s)
        resetTrainer(grid, s)
        ContextCompat.startForegroundService(appContext, Intent(appContext, MetronomeService::class.java))
    }

    fun stop() {
        if (!_state.value.playing) return
        handler.removeCallbacksAndMessages(null)
        engine.stop()
        // 트레이너가 켜진 채로 끝나면 시작 BPM 으로 되돌린다
        val restored = trainerStartBpm.takeIf { _state.value.settings.trainer.enabled }
        trainerStartBpm = null
        trainerNextBeat = Long.MAX_VALUE
        _state.value = _state.value.let { st ->
            st.copy(
                grid = null,
                startedAtElapsedMs = 0L,
                settings = if (restored != null) st.settings.copy(bpm = restored) else st.settings,
            )
        }
        appContext.stopService(Intent(appContext, MetronomeService::class.java))
    }

    fun setBpm(bpm: Int) = update { it.copy(bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM)) }
    fun nudgeBpm(delta: Int) = setBpm(_state.value.settings.bpm + delta)
    fun setBeatsPerBar(n: Int) = update {
        // 박 수가 바뀌면 박별 세기는 기본값으로
        it.copy(beatsPerBar = n.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS), beatLevels = emptyList())
    }
    fun cycleBeatLevel(index: Int) = update { it.withBeatLevelCycled(index) }
    fun setSubdivision(n: Int) =
        update { it.copy(subdivision = n.coerceIn(1, MetronomeSettings.MAX_SUBDIVISION)) }
    fun setAccent(enabled: Boolean) {
        update { it.copy(accentEnabled = enabled, beatLevels = emptyList()) }
        previewIfStopped()
    }
    fun setStrength(level: Int) {
        update { it.copy(strength = level.coerceIn(1, MetronomeSettings.STRENGTH_AMPLITUDES.size)) }
        previewIfStopped()
    }

    private fun previewIfStopped() {
        val st = _state.value
        if (!st.playing) engine.preview(st.settings.hapticStyle())
    }
    fun setTrainer(change: (TrainerSettings) -> TrainerSettings) =
        update { it.copy(trainer = change(it.trainer).sanitized()) }

    fun setKeepScreenOn(on: Boolean) = update { it.copy(keepScreenOn = on) }
    fun setTimingMode(mode: TimingMode) = update { it.copy(timingMode = mode) }
    fun setHapticLead(ms: Int) = update { it.copy(hapticLeadMs = ms).sanitized() }
    fun setScreenFlash(on: Boolean) = update { it.copy(screenFlash = on) }
    fun savePreset() = update { it.withPresetSaved() }
    fun applyPreset(p: Preset) = update { it.withPresetApplied(p) }
    fun deletePreset(p: Preset) = update { s -> s.copy(presets = s.presets - p) }

    fun tap() {
        tapTempo.tap(SystemClock.elapsedRealtimeNanos())?.let { setBpm(it) }
    }

    /** 화면 꺼짐 등으로 시스템이 진동을 끊었을 수 있을 때 서비스가 호출. (파형 모드만 영향) */
    fun reanchorNow() {
        val st = _state.value
        val grid = st.grid ?: return
        val s = st.settings
        engine.resync(s.timingMode, grid, s.hapticStyle(), s.subdivision, s.levels())
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
        if (newSettings.engineKey() == old.settings.engineKey()) return
        // 베젤을 빠르게 돌리면 변경이 연달아 들어온다. 매번 진동을 다시 걸면 펄스가 잘리므로
        // 잠깐 모았다가 한 번에 적용한다. 화면 숫자는 즉시 바뀐다.
        handler.removeCallbacks(applyRunnable)
        handler.postDelayed(applyRunnable, APPLY_DEBOUNCE_MS)
    }

    /** 진동 출력에 영향을 주는 설정만 */
    private fun MetronomeSettings.engineKey() =
        listOf(bpm, beatsPerBar, subdivision, strength, levels().toList(), timingMode, hapticLeadMs)

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
        if (grid.beatsPerBar != oldGrid.beatsPerBar) {
            resetTrainer(grid, s)
        } else if (grid !== oldGrid) {
            // 템포가 바뀌면 다음 트레이너 시점(박 번호는 그대로)의 실제 시각도 바뀐다
            handler.removeCallbacks(trainerRunnable)
            scheduleTrainer(grid)
        }
        play(grid, s)
    }

    /**
     * 엔진에 반영. 진동을 다시 걸 때마다 시스템 처리 지연만큼 박이 한 번 튈 수 있으므로
     * (특히 파형 모드) 꼭 필요할 때만 부른다: 시작, 진동 관련 설정 변경, 트레이너 단계.
     */
    private fun play(grid: BeatGrid, s: MetronomeSettings) {
        engine.play(s.timingMode, grid, s.hapticStyle(), s.subdivision, s.levels(), s.hapticLeadMs)
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
        val s = st.settings.copy(bpm = nextBpm)
        val newGrid = grid.retimed(SystemClock.elapsedRealtimeNanos(), nextBpm, grid.beatsPerBar)
        _state.value = st.copy(settings = s, grid = newGrid)
        play(newGrid, s)
        // 박 번호는 retimed 후에도 이어지므로 마디 경계 기준으로 다음 시점을 잡는다
        // (깨어나는 게 늦어 새 템포가 한 박 늦게 시작됐더라도 마디 정렬은 유지)
        val span = t.everyBars.toLong() * newGrid.beatsPerBar
        do trainerNextBeat += span while (trainerNextBeat <= newGrid.originBeat)
        scheduleTrainer(newGrid)
    }

    private const val APPLY_DEBOUNCE_MS = 120L
    private const val TRAINER_LEAD_NANOS = 60_000_000L
}
