package kr.hui.takt.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kr.hui.takt.MetronomeState
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.engine.BeatGrid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 워치 화면 스크린샷. `./gradlew recordRoborazziDebug` 로 갱신,
 * 결과는 app/build/outputs/roborazzi/ 에 저장된다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class ScreenshotTest {

    private fun capture(name: String, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            MaterialTheme { AppScaffold { content() } }
        }

    @Test fun mainStopped() = capture("main_stopped") {
        MainScreen(MetronomeState(MetronomeSettings(bpm = 96)), {}, {}, {}, {})
    }

    @Test fun mainPlaying() = capture("main_playing") {
        MainScreen(
            MetronomeState(
                MetronomeSettings(
                    bpm = 132, beatsPerBar = 7, subdivision = 3, beatLevels = listOf(2, 1, 1, 2, 1, 0, 1),
                    trainer = kr.hui.takt.data.TrainerSettings(enabled = true),
                ),
                grid = BeatGrid(0, 0, 132, 7),
            ),
            {}, {}, {}, {},
        )
    }

    @Test fun settingsWithPresets() = capture("settings_presets") {
        SettingsScreen(
            MetronomeSettings(
                presets = listOf(
                    kr.hui.takt.data.Preset(100, 4, 1),
                    kr.hui.takt.data.Preset(72, 7, 3, listOf(2, 1, 2, 1, 2, 1, 0)),
                ),
            ),
            hasVibrator = true,
            SettingsActions(),
            initialItem = 6,
        )
    }

    /** 2번째 박(마디 위치 1)이 울리고 70% 지난 시점: 1번 칸은 흐려지는 중, 2번 칸이 차오르는 중 */
    @Test fun mainFilling() = capture("main_filling") {
        val grid = BeatGrid(0, 0, 120, 4)
        MainScreen(
            MetronomeState(MetronomeSettings(bpm = 120), grid = grid),
            {}, {}, {}, {},
            ringNow = grid.timeOfBeat(1) + (grid.beatNanos * 0.7).toLong(),
        )
    }

    /** 박 직후: 번쩍임 */
    @Test fun mainFlash() = capture("main_flash") {
        val grid = BeatGrid(0, 0, 120, 4)
        MainScreen(
            MetronomeState(MetronomeSettings(bpm = 120), grid = grid),
            {}, {}, {}, {},
            ringNow = grid.timeOfBeat(4) + 20_000_000L,
        )
    }

    @Test fun ambient() = capture("ambient") {
        AmbientScreen(
            MetronomeState(
                MetronomeSettings(bpm = 148, beatsPerBar = 4, beatLevels = listOf(2, 1, 0, 1)),
                grid = BeatGrid(0, 0, 148, 4),
            ),
        )
    }

    @Test fun settingsTiming() = capture("settings_timing") {
        SettingsScreen(
            MetronomeSettings(),
            hasVibrator = true,
            SettingsActions(
                timingStats = { kr.hui.takt.engine.TimingStats(64, 0.02, 0.3, 2.1, 6.4) },
            ),
            initialItem = 14,
        )
    }

    @Test fun settings() = capture("settings") {
        SettingsScreen(MetronomeSettings(), hasVibrator = true, SettingsActions())
    }
}
