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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kr.hui.takt.engine.HapticPattern
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 화면 테두리를 박 수만큼 나눈 링. 현재 박이 켜지고 서서히 흐려진다.
 * 매 프레임 그리지 않고 다음 박 시각까지 잠들었다 깨는 방식이라 배터리 부담이 적다.
 */
@Composable
fun BeatRing(
    grid: BeatGrid?,
    levels: IntArray,
    activeColor: Color,
    accentColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier,
    /** 링의 박 칸을 탭했을 때 (박 번호). 링 바깥쪽 띠에서만 반응 */
    onBeatTap: ((Int) -> Unit)? = null,
) {
    val beatsPerBar = levels.size
    var beat by remember { mutableLongStateOf(-1L) }
    val flash = remember { Animatable(0f) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(grid, lifecycle) {
        if (grid == null) {
            beat = -1L
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        // 화면이 안 보일 때(화면 꺼짐, 다른 앱)는 깨어나지 않는다
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    val tapModifier = if (onBeatTap == null) Modifier else Modifier.pointerInput(beatsPerBar) {
        detectTapGestures { pos ->
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = pos.x - cx
            val dy = pos.y - cy
            val r = sqrt(dx * dx + dy * dy)
            if (r < minOf(cx, cy) * 0.72f) return@detectTapGestures
            // 12시 방향 0도, 시계 방향
            val deg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90 + 360) % 360
            onBeatTap((deg / (360.0 / beatsPerBar)).toInt().coerceIn(0, beatsPerBar - 1))
        }
    }

    Canvas(modifier.then(tapModifier)) {
        val strokeWidth = 7.dp.toPx()
        val inset = strokeWidth * 0.7f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val gapDeg = if (beatsPerBar == 1) 0f else 6f
        val sweep = 360f / beatsPerBar - gapDeg
        val pos = if (beat >= 0 && grid != null) grid.barPosition(beat) else -1
        for (i in 0 until beatsPerBar) {
            val start = -90f + i * (360f / beatsPerBar) + gapDeg / 2
            val level = levels[i]
            val isCurrent = i == pos
            val (base, target, width) = when (level) {
                HapticPattern.LEVEL_ACCENT -> Triple(accentColor.copy(alpha = 0.45f), accentColor, strokeWidth * 1.3f)
                HapticPattern.LEVEL_MUTE -> Triple(idleColor.copy(alpha = 0.35f), idleColor, strokeWidth * 0.35f)
                else -> Triple(idleColor, activeColor, strokeWidth)
            }
            val color = if (isCurrent && level != HapticPattern.LEVEL_MUTE) {
                lerp(base, target, 0.35f + 0.65f * flash.value)
            } else base
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = if (isCurrent) width * 1.3f else width, cap = StrokeCap.Round),
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
