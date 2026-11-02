package kr.hui.takt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import kr.hui.takt.MetronomeState
import kr.hui.takt.R
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun MainScreen(
    state: MetronomeState,
    onToggle: () -> Unit,
    onNudge: (Int) -> Unit,
    onTap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val rotary = remember { RotaryAccumulator() }
    val s = state.settings

    ScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    rotary.consume(it.verticalScrollPixels).let { steps -> if (steps != 0) onNudge(steps) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            BeatRing(
                grid = state.grid,
                beatsPerBar = s.beatsPerBar,
                activeColor = colors.primary,
                accentColor = colors.tertiary,
                idleColor = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.meter_label, s.beatsPerBar, subdivisionLabel(s.subdivision)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNudge(-1) }, modifier = Modifier.size(40.dp)) {
                        Text("−", fontSize = 24.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(80.dp),
                    ) {
                        Text(
                            text = s.bpm.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (s.trainer.enabled) "BPM ▲${s.trainer.targetBpm}" else "BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (s.trainer.enabled) colors.tertiary else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { onNudge(+1) }, modifier = Modifier.size(40.dp)) {
                        Text("+", fontSize = 24.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onTap, modifier = Modifier.size(44.dp)) {
                        Text(stringResource(R.string.tap), style = MaterialTheme.typography.labelSmall)
                    }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
                        if (state.playing) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(colors.onPrimary, RoundedCornerShape(3.dp)),
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(20.dp).padding(0.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** 크라운/베젤 회전을 BPM 단계로 변환. 베젤 한 칸(큰 이벤트)은 최소 1단계. */
private class RotaryAccumulator(private val pixelsPerStep: Float = 24f) {
    private var acc = 0f
    fun consume(pixels: Float): Int {
        if (abs(pixels) >= pixelsPerStep) {
            acc = 0f
            return (pixels / pixelsPerStep).toInt().coerceIn(-5, 5).let { if (it == 0) sign(pixels).toInt() else it }
        }
        acc += pixels
        val steps = (acc / pixelsPerStep).toInt()
        acc -= steps * pixelsPerStep
        return steps
    }
}
