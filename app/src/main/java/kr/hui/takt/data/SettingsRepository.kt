package kr.hui.takt.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticPattern
import kr.hui.takt.engine.HapticStyle
import kr.hui.takt.engine.TimingMode

data class MetronomeSettings(
    val bpm: Int = 100,
    val beatsPerBar: Int = 4,
    val accentEnabled: Boolean = true,
    /** 진동 세기 1..5 */
    val strength: Int = 5,
    /** 한 박을 몇 개로 나눌지: 1=4분, 2=8분, 3=셋잇단, 4=16분 */
    val subdivision: Int = 1,
    val trainer: TrainerSettings = TrainerSettings(),
    /** 재생 중 화면을 켜둘지 (배터리 소모 큼) */
    val keepScreenOn: Boolean = false,
    val presets: List<Preset> = emptyList(),
    /** 박별 세기(0 무음/1 보통/2 강박). 비어 있으면 accentEnabled 기본 패턴 */
    val beatLevels: List<Int> = emptyList(),
    val timingMode: TimingMode = TimingMode.DIRECT,
    /** 진동을 이만큼(ms) 일찍 건다. 모터 기동 지연 보정 (음수면 늦게) */
    val hapticLeadMs: Int = 10,
    /** 박마다 화면 전체 번쩍임 */
    val screenFlash: Boolean = true,
) {
    fun levels(): IntArray = IntArray(beatsPerBar) { i ->
        beatLevels.getOrNull(i) ?: HapticPattern.defaultLevel(i, beatsPerBar, accentEnabled)
    }

    /** 박 하나의 세기를 보통 → 강박 → 무음 → 보통 순으로 바꾼다. */
    fun withBeatLevelCycled(index: Int): MetronomeSettings {
        if (index !in 0 until beatsPerBar) return this
        val l = levels()
        l[index] = when (l[index]) {
            HapticPattern.LEVEL_NORMAL -> HapticPattern.LEVEL_ACCENT
            HapticPattern.LEVEL_ACCENT -> HapticPattern.LEVEL_MUTE
            else -> HapticPattern.LEVEL_NORMAL
        }
        return copy(beatLevels = l.toList())
    }

    fun currentPreset() = Preset(bpm, beatsPerBar, subdivision, beatLevels)

    fun withPresetSaved(): MetronomeSettings {
        val p = currentPreset()
        return copy(presets = (listOf(p) + presets.filter { it != p }).take(MAX_PRESETS))
    }

    fun withPresetApplied(p: Preset) =
        copy(bpm = p.bpm, beatsPerBar = p.beatsPerBar, subdivision = p.subdivision, beatLevels = p.beatLevels).sanitized()

    /**
     * 세기 단계 → 진폭 + 길이. 워치의 진동 모터(LRA)는 짧은 펄스에서 힘을 다 못 내므로
     * 진폭만이 아니라 길이도 같이 늘려야 세게 느껴진다.
     */
    fun hapticStyle(): HapticStyle {
        val amp = STRENGTH_AMPLITUDES[strength - 1]
        val ms = STRENGTH_PULSE_MS[strength - 1]
        return HapticStyle(
            amplitude = amp,
            accentAmplitude = 255,
            subAmplitude = (amp * 70 / 100).coerceAtLeast(120),
            pulseMs = ms,
            accentPulseMs = ms + ms / 2 + 20, // 강박은 1.5배 + 20ms
            subPulseMs = (ms * 60 / 100).coerceAtLeast(25),
            accentEnabled = accentEnabled,
        )
    }

    fun sanitized() = copy(
        bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM),
        beatsPerBar = beatsPerBar.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS),
        strength = strength.coerceIn(1, STRENGTH_AMPLITUDES.size),
        subdivision = subdivision.coerceIn(1, MAX_SUBDIVISION),
        trainer = trainer.sanitized(),
        presets = presets.map { it.sanitized() }.distinct().take(MAX_PRESETS),
        hapticLeadMs = hapticLeadMs.coerceIn(MIN_LEAD_MS, MAX_LEAD_MS),
        beatLevels = if (beatLevels.size == beatsPerBar) beatLevels.map { it.coerceIn(0, 2) } else emptyList(),
    )

    companion object {
        // 진폭은 4단계부터 하드웨어 최대(255). 그 위로는 길이로만 세진다.
        val STRENGTH_AMPLITUDES = intArrayOf(150, 190, 225, 255, 255, 255, 255)
        val STRENGTH_PULSE_MS = longArrayOf(35L, 50L, 65L, 80L, 110L, 150L, 200L)
        const val MAX_SUBDIVISION = 4
        const val MAX_PRESETS = 8
        const val MIN_LEAD_MS = -30
        const val MAX_LEAD_MS = 60
    }
}

data class Preset(
    val bpm: Int,
    val beatsPerBar: Int,
    val subdivision: Int,
    /** 박별 세기. 비어 있으면 기본 */
    val beatLevels: List<Int> = emptyList(),
) {
    fun sanitized(): Preset {
        val beats = beatsPerBar.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS)
        return Preset(
            bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM),
            beats,
            subdivision.coerceIn(1, MetronomeSettings.MAX_SUBDIVISION),
            if (beatLevels.size == beats) beatLevels.map { it.coerceIn(0, 2) } else emptyList(),
        )
    }

    companion object {
        /** "120,4,1;90,3,2,2-1-0" 형식 (4번째 칸: 박별 세기, 선택) */
        fun encode(list: List<Preset>) = list.joinToString(";") { p ->
            "${p.bpm},${p.beatsPerBar},${p.subdivision}" +
                if (p.beatLevels.isEmpty()) "" else "," + p.beatLevels.joinToString("-")
        }

        fun decode(s: String?): List<Preset> =
            s.orEmpty().split(';').mapNotNull { item ->
                val f = item.split(',').map { it.trim() }
                if (f.size !in 3..4) return@mapNotNull null
                val n = f.take(3).map { it.toIntOrNull() ?: return@mapNotNull null }
                val levels = f.getOrNull(3)?.split('-')?.mapNotNull { it.toIntOrNull() }.orEmpty()
                Preset(n[0], n[1], n[2], levels).sanitized()
            }
    }
}

/** 스피드 트레이너: [everyBars] 마디마다 [stepBpm] 씩 올려 [targetBpm] 까지. */
data class TrainerSettings(
    val enabled: Boolean = false,
    val stepBpm: Int = 5,
    val everyBars: Int = 4,
    val targetBpm: Int = 160,
) {
    fun sanitized() = copy(
        stepBpm = stepBpm.coerceIn(1, 20),
        everyBars = everyBars.coerceIn(1, 32),
        targetBpm = targetBpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM),
    )
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val BPM = intPreferencesKey("bpm")
        val BEATS = intPreferencesKey("beats_per_bar")
        val ACCENT = booleanPreferencesKey("accent")
        // v2: 세기 체계를 바꿨으므로 예전(약한) 저장값은 무시
        val STRENGTH = intPreferencesKey("strength_v2")
        val SCREEN_FLASH = booleanPreferencesKey("screen_flash")
        val SUBDIVISION = intPreferencesKey("subdivision")
        val TRAINER = booleanPreferencesKey("trainer_enabled")
        val TRAINER_STEP = intPreferencesKey("trainer_step")
        val TRAINER_EVERY = intPreferencesKey("trainer_every")
        val TRAINER_TARGET = intPreferencesKey("trainer_target")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PRESETS = stringPreferencesKey("presets")
        val BEAT_LEVELS = stringPreferencesKey("beat_levels")
        val TIMING_MODE = stringPreferencesKey("timing_mode")
        val HAPTIC_LEAD = intPreferencesKey("haptic_lead_ms")
    }

    suspend fun load(): MetronomeSettings {
        val p: Preferences = context.dataStore.data.first()
        val d = MetronomeSettings()
        return MetronomeSettings(
            bpm = p[Keys.BPM] ?: d.bpm,
            beatsPerBar = p[Keys.BEATS] ?: d.beatsPerBar,
            accentEnabled = p[Keys.ACCENT] ?: d.accentEnabled,
            strength = p[Keys.STRENGTH] ?: d.strength,
            subdivision = p[Keys.SUBDIVISION] ?: d.subdivision,
            trainer = TrainerSettings(
                enabled = p[Keys.TRAINER] ?: d.trainer.enabled,
                stepBpm = p[Keys.TRAINER_STEP] ?: d.trainer.stepBpm,
                everyBars = p[Keys.TRAINER_EVERY] ?: d.trainer.everyBars,
                targetBpm = p[Keys.TRAINER_TARGET] ?: d.trainer.targetBpm,
            ),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn,
            presets = Preset.decode(p[Keys.PRESETS]),
            beatLevels = p[Keys.BEAT_LEVELS].orEmpty().split(',').mapNotNull { it.toIntOrNull() },
            timingMode = TimingMode.entries.firstOrNull { it.name == p[Keys.TIMING_MODE] } ?: d.timingMode,
            hapticLeadMs = p[Keys.HAPTIC_LEAD] ?: d.hapticLeadMs,
            screenFlash = p[Keys.SCREEN_FLASH] ?: d.screenFlash,
        ).sanitized()
    }

    suspend fun save(s: MetronomeSettings) {
        context.dataStore.edit { p ->
            p[Keys.BPM] = s.bpm
            p[Keys.BEATS] = s.beatsPerBar
            p[Keys.ACCENT] = s.accentEnabled
            p[Keys.STRENGTH] = s.strength
            p[Keys.SUBDIVISION] = s.subdivision
            p[Keys.TRAINER] = s.trainer.enabled
            p[Keys.TRAINER_STEP] = s.trainer.stepBpm
            p[Keys.TRAINER_EVERY] = s.trainer.everyBars
            p[Keys.TRAINER_TARGET] = s.trainer.targetBpm
            p[Keys.KEEP_SCREEN_ON] = s.keepScreenOn
            p[Keys.PRESETS] = Preset.encode(s.presets)
            p[Keys.BEAT_LEVELS] = s.beatLevels.joinToString(",")
            p[Keys.TIMING_MODE] = s.timingMode.name
            p[Keys.HAPTIC_LEAD] = s.hapticLeadMs
            p[Keys.SCREEN_FLASH] = s.screenFlash
        }
    }
}

/** 현재 BPM 에서 목표 쪽으로 한 단계 이동한 BPM. 이미 목표면 null. */
fun TrainerSettings.nextBpm(current: Int): Int? = when {
    current < targetBpm -> (current + stepBpm).coerceAtMost(targetBpm)
    current > targetBpm -> (current - stepBpm).coerceAtLeast(targetBpm)
    else -> null
}
