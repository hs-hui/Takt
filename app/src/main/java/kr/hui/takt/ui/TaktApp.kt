package kr.hui.takt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kr.hui.takt.Metronome

@Composable
fun TaktApp(ambient: Boolean = false) {
    val state by Metronome.state.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    // 재생 중 화면 켜두기 옵션
    val view = LocalView.current
    val keepOn = state.playing && state.settings.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    MaterialTheme {
        AppScaffold {
            if (ambient) {
                AmbientScreen(state)
                return@AppScaffold
            }
            SwipeDismissableNavHost(navController = nav, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        state = state,
                        onToggle = Metronome::toggle,
                        onNudge = Metronome::nudgeBpm,
                        onTap = Metronome::tap,
                        onOpenSettings = { nav.navigate("settings") },
                        onBeatTap = Metronome::cycleBeatLevel,
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        settings = state.settings,
                        hasVibrator = Metronome.hasVibrator,
                        actions = SettingsActions(
                            onBeatsPerBar = Metronome::setBeatsPerBar,
                            onSubdivision = Metronome::setSubdivision,
                            onAccent = Metronome::setAccent,
                            onStrength = Metronome::setStrength,
                            onTrainer = Metronome::setTrainer,
                            onKeepScreenOn = Metronome::setKeepScreenOn,
                            onSavePreset = Metronome::savePreset,
                            onApplyPreset = Metronome::applyPreset,
                            onDeletePreset = Metronome::deletePreset,
                            onTimingMode = Metronome::setTimingMode,
                            timingStats = { Metronome.timingStats },
                        ),
                    )
                }
            }
        }
    }
}
