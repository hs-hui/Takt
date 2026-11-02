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
) {
    fun hapticStyle() = HapticStyle(
        amplitude = STRENGTH_AMPLITUDES[strength - 1],
        accentEnabled = accentEnabled,
    )

    fun sanitized() = copy(
        bpm = bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM),
        beatsPerBar = beatsPerBar.coerceIn(BeatGrid.MIN_BEATS, BeatGrid.MAX_BEATS),
        strength = strength.coerceIn(1, STRENGTH_AMPLITUDES.size),
    )

    companion object {
        val STRENGTH_AMPLITUDES = intArrayOf(60, 100, 150, 200, 255)
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val BPM = intPreferencesKey("bpm")
        val BEATS = intPreferencesKey("beats_per_bar")
        val ACCENT = booleanPreferencesKey("accent")
        val STRENGTH = intPreferencesKey("strength")
    }

    suspend fun load(): MetronomeSettings {
        val p: Preferences = context.dataStore.data.first()
        val d = MetronomeSettings()
        return MetronomeSettings(
            bpm = p[Keys.BPM] ?: d.bpm,
            beatsPerBar = p[Keys.BEATS] ?: d.beatsPerBar,
            accentEnabled = p[Keys.ACCENT] ?: d.accentEnabled,
            strength = p[Keys.STRENGTH] ?: d.strength,
        ).sanitized()
    }

    suspend fun save(s: MetronomeSettings) {
        context.dataStore.edit { p ->
            p[Keys.BPM] = s.bpm
            p[Keys.BEATS] = s.beatsPerBar
            p[Keys.ACCENT] = s.accentEnabled
            p[Keys.STRENGTH] = s.strength
        }
    }
}
