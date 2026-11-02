package kr.hui.takt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.hui.takt.engine.BeatGrid

/**
 * 화면 테두리를 박 수만큼 나눈 링. 현재 박이 켜지고 서서히 흐려진다.
 * 매 프레임 그리지 않고 다음 박 시각까지 잠들었다 깨는 방식이라 배터리 부담이 적다.
 */
@Composable
fun BeatRing(
    grid: BeatGrid?,
    beatsPerBar: Int,
    activeColor: Color,
    accentColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier,
) {
    var beat by remember { mutableLongStateOf(-1L) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(grid) {
        if (grid == null) {
            beat = -1L
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            val now = SystemClock.elapsedRealtimeNanos()
            val next = grid.nextBeatAfter(now)
            delay(((grid.timeOfBeat(next) - now) / 1_000_000L).coerceAtLeast(0))
            beat = next
            launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(((grid.beatNanos / 1e6) * 0.8).toInt()))
            }
        }
    }

    Canvas(modifier) {
        val strokeWidth = 7.dp.toPx()
        val inset = strokeWidth / 2 + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val gapDeg = if (beatsPerBar == 1) 0f else 6f
        val sweep = 360f / beatsPerBar - gapDeg
        val pos = if (beat >= 0 && grid != null) grid.barPosition(beat) else -1
        for (i in 0 until beatsPerBar) {
            val start = -90f + i * (360f / beatsPerBar) + gapDeg / 2
            val isCurrent = i == pos
            val base = if (i == 0 && beatsPerBar > 1) accentColor.copy(alpha = 0.35f) else idleColor
            val color = if (isCurrent) {
                val target = if (i == 0 && beatsPerBar > 1) accentColor else activeColor
                lerp(base, target, 0.35f + 0.65f * flash.value)
            } else base
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = if (isCurrent) strokeWidth * 1.4f else strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
