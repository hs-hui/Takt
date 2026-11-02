package kr.hui.takt.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticStyle

data class MetronomeSettings(
    val bpm: Int = 100,
    val beatsPerBar: Int = 4,
    val accentEnabled: Boolean = true,
    /** 진동 세기 1..5 */
    val strength: Int = 3,
    /** 한 박을 몇 개로 나눌지: 1=4분, 2=8분, 3=셋잇단, 4=16분 */
    val subdivision: Int = 1,
    val trainer: TrainerSettings = TrainerSettings(),
) {
    fun hapticStyle(): HapticStyle {
        val amp = STRENGTH_AMPLITUDES[strength - 1]
        return HapticStyle(
            amplitude = amp,
            subAmplitude = (amp * 45 / 100).coerceAtLeast(30),
            accentEnabled = accentEnabled,
        )
    }

    fun sanitized() = copy(
        bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM),
        beatsPerBar = beatsPerBar.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS),
        strength = strength.coerceIn(1, STRENGTH_AMPLITUDES.size),
        subdivision = subdivision.coerceIn(1, MAX_SUBDIVISION),
        trainer = trainer.sanitized(),
    )

    companion object {
        val STRENGTH_AMPLITUDES = intArrayOf(60, 100, 150, 200, 255)
        const val MAX_SUBDIVISION = 4
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
        val STRENGTH = intPreferencesKey("strength")
        val SUBDIVISION = intPreferencesKey("subdivision")
        val TRAINER = booleanPreferencesKey("trainer_enabled")
        val TRAINER_STEP = intPreferencesKey("trainer_step")
        val TRAINER_EVERY = intPreferencesKey("trainer_every")
        val TRAINER_TARGET = intPreferencesKey("trainer_target")
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
        }
    }
}

/** 현재 BPM 에서 목표 쪽으로 한 단계 이동한 BPM. 이미 목표면 null. */
fun TrainerSettings.nextBpm(current: Int): Int? = when {
    current < targetBpm -> (current + stepBpm).coerceAtMost(targetBpm)
    current > targetBpm -> (current - stepBpm).coerceAtLeast(targetBpm)
    else -> null
}
