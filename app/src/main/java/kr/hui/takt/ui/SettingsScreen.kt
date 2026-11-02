package kr.hui.takt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import kr.hui.takt.R
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.engine.BeatGrid

@Composable
fun SettingsScreen(
    settings: MetronomeSettings,
    hasVibrator: Boolean,
    onBeatsPerBar: (Int) -> Unit,
    onAccent: (Boolean) -> Unit,
    onStrength: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(stringResource(R.string.settings)) } }
            item {
                StepperRow(
                    label = stringResource(R.string.beats_per_bar),
                    value = "${settings.beatsPerBar}",
                    onMinus = { onBeatsPerBar(settings.beatsPerBar - 1) },
                    onPlus = { onBeatsPerBar(settings.beatsPerBar + 1) },
                    minusEnabled = settings.beatsPerBar > BeatGrid.MIN_BEATS,
                    plusEnabled = settings.beatsPerBar < BeatGrid.MAX_BEATS,
                )
            }
            item {
                SwitchButton(
                    checked = settings.accentEnabled,
                    onCheckedChange = onAccent,
                    label = { Text(stringResource(R.string.accent_first_beat)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.strength),
                    value = "${settings.strength}",
                    onMinus = { onStrength(settings.strength - 1) },
                    onPlus = { onStrength(settings.strength + 1) },
                    minusEnabled = settings.strength > 1,
                    plusEnabled = settings.strength < MetronomeSettings.STRENGTH_AMPLITUDES.size,
                )
            }
            if (!hasVibrator) {
                item {
                    Text(
                        stringResource(R.string.no_vibrator),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(40.dp)) {
            Text("−", fontSize = 20.sp)
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(40.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
}
