package kr.hui.takt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kr.hui.takt.Metronome

@Composable
fun TaktApp() {
    val state by Metronome.state.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    MaterialTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        state = state,
                        onToggle = Metronome::toggle,
                        onNudge = Metronome::nudgeBpm,
                        onTap = Metronome::tap,
                        onOpenSettings = { nav.navigate("settings") },
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
                        ),
                    )
                }
            }
        }
    }
}
