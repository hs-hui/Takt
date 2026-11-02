package kr.hui.takt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import kr.hui.takt.data.TrainerSettings
import kr.hui.takt.engine.BeatGrid

class SettingsActions(
    val onBeatsPerBar: (Int) -> Unit = {},
    val onSubdivision: (Int) -> Unit = {},
    val onAccent: (Boolean) -> Unit = {},
    val onStrength: (Int) -> Unit = {},
    val onTrainer: ((TrainerSettings) -> TrainerSettings) -> Unit = {},
)

@Composable
fun SettingsScreen(
    settings: MetronomeSettings,
    hasVibrator: Boolean,
    actions: SettingsActions,
) {
    val listState = rememberScalingLazyListState()
    val t = settings.trainer
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(stringResource(R.string.settings)) } }
            item {
                StepperRow(
                    label = stringResource(R.string.beats_per_bar),
                    value = "${settings.beatsPerBar}",
                    onMinus = { actions.onBeatsPerBar(settings.beatsPerBar - 1) },
                    onPlus = { actions.onBeatsPerBar(settings.beatsPerBar + 1) },
                    minusEnabled = settings.beatsPerBar > BeatGrid.MIN_BEATS,
                    plusEnabled = settings.beatsPerBar < BeatGrid.MAX_BEATS,
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.subdivision),
                    value = subdivisionLabel(settings.subdivision),
                    onMinus = { actions.onSubdivision(settings.subdivision - 1) },
                    onPlus = { actions.onSubdivision(settings.subdivision + 1) },
                    minusEnabled = settings.subdivision > 1,
                    plusEnabled = settings.subdivision < MetronomeSettings.MAX_SUBDIVISION,
                )
            }
            item {
                SwitchButton(
                    checked = settings.accentEnabled,
                    onCheckedChange = actions.onAccent,
                    label = { Text(stringResource(R.string.accent_first_beat)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.strength),
                    value = "${settings.strength}",
                    onMinus = { actions.onStrength(settings.strength - 1) },
                    onPlus = { actions.onStrength(settings.strength + 1) },
                    minusEnabled = settings.strength > 1,
                    plusEnabled = settings.strength < MetronomeSettings.STRENGTH_AMPLITUDES.size,
                )
            }

            item { ListHeader { Text(stringResource(R.string.trainer)) } }
            item {
                SwitchButton(
                    checked = t.enabled,
                    onCheckedChange = { on -> actions.onTrainer { it.copy(enabled = on) } },
                    label = { Text(stringResource(R.string.trainer_enabled)) },
                    secondaryLabel = {
                        Text(stringResource(R.string.trainer_summary, t.everyBars, t.stepBpm, t.targetBpm))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.trainer_every),
                    value = stringResource(R.string.bars_value, t.everyBars),
                    onMinus = { actions.onTrainer { it.copy(everyBars = it.everyBars - 1) } },
                    onPlus = { actions.onTrainer { it.copy(everyBars = it.everyBars + 1) } },
                    minusEnabled = t.everyBars > 1,
                    plusEnabled = t.everyBars < 32,
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.trainer_step),
                    value = "+${t.stepBpm}",
                    onMinus = { actions.onTrainer { it.copy(stepBpm = it.stepBpm - 1) } },
                    onPlus = { actions.onTrainer { it.copy(stepBpm = it.stepBpm + 1) } },
                    minusEnabled = t.stepBpm > 1,
                    plusEnabled = t.stepBpm < 20,
                )
            }
            item {
                StepperRow(
                    label = stringResource(R.string.trainer_target),
                    value = "${t.targetBpm}",
                    onMinus = { actions.onTrainer { it.copy(targetBpm = it.targetBpm - 5) } },
                    onPlus = { actions.onTrainer { it.copy(targetBpm = it.targetBpm + 5) } },
                    minusEnabled = t.targetBpm > BeatGrid.MIN_BPM,
                    plusEnabled = t.targetBpm < BeatGrid.MAX_BPM,
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
fun subdivisionLabel(n: Int): String = stringResource(
    when (n) {
        2 -> R.string.sub_eighth
        3 -> R.string.sub_triplet
        4 -> R.string.sub_sixteenth
        else -> R.string.sub_quarter
    },
)

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(88.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(40.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
}
