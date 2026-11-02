package kr.hui.takt.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.hui.takt.engine.BeatGrid
import kr.hui.takt.engine.HapticPattern
import kotlin.math.atan2
import kotlin.math.sqrt

/** 링 한 프레임의 상태. 안드로이드 의존성 없는 계산이라 단위 테스트 가능. */
data class RingFrame(
    /** 방금 울린 박의 마디 내 위치 (-1: 아직 없음) */
    val currentPos: Int,
    /** 방금 울린 박의 번쩍임 1 → 0 */
    val flash: Float,
    /** 다음에 울릴 박의 마디 내 위치 */
    val nextPos: Int,
    /** 다음 박까지 차오르는 정도 0 → 1 (1 이 되는 순간 그 박이 울린다) */
    val fill: Float,
)

fun ringFrame(grid: BeatGrid, now: Long): RingFrame {
    val cur = grid.beatAtOrBefore(now)
    val tCur = grid.timeOfBeat(cur)
    val tNext = grid.timeOfBeat(cur + 1)
    val fill = ((now - tCur).toDouble() / (tNext - tCur)).coerceIn(0.0, 1.0).toFloat()
    // 번쩍임은 박 길이의 절반 동안 사라진다. 재생 시작 전(첫 박 이전)에는 없음
    val flash = if (cur >= 0 && now >= tCur) {
        (1.0 - (now - tCur) / (grid.beatNanos * 0.5)).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    return RingFrame(
        currentPos = if (cur >= 0) grid.barPosition(cur) else -1,
        flash = flash,
        nextPos = grid.barPosition(cur + 1),
        fill = fill,
    )
}

/**
 * 화면 테두리를 박 수만큼 나눈 링.
 * - 다음 박 칸이 시계 방향으로 차오르고, 꽉 차는 순간 그 박의 진동이 울린다.
 * - 방금 울린 박 칸은 번쩍였다가 흐려진다.
 * 화면이 보일 때만 프레임마다 갱신한다 (화면 꺼짐·다른 앱일 땐 멈춤).
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
    /** 미리보기·테스트용 고정 시각 (null 이면 실시간) */
    fixedNow: Long? = null,
) {
    val beatsPerBar = levels.size
    var now by remember { mutableLongStateOf(fixedNow ?: 0L) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(grid, lifecycle, fixedNow) {
        if (grid == null || fixedNow != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                withFrameNanos { }
                // 화면에 실제로 보이기까지 한 프레임 정도 걸리므로 그만큼 앞선 시각을 그린다
                now = SystemClock.elapsedRealtimeNanos() + VISUAL_LEAD_NANOS
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
        val frame = grid?.let { ringFrame(it, fixedNow ?: now) }
        val strokeWidth = 7.dp.toPx()
        val inset = strokeWidth * 0.7f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val gapDeg = if (beatsPerBar == 1) 0f else 6f
        val sweep = 360f / beatsPerBar - gapDeg

        fun arc(i: Int, color: Color, width: Float, sweepAngle: Float = sweep) = drawSegment(
            color, -90f + i * (360f / beatsPerBar) + gapDeg / 2, sweepAngle, inset, arcSize, width,
        )

        for (i in 0 until beatsPerBar) {
            val level = levels[i]
            val style = segmentStyle(level, activeColor, accentColor, idleColor, strokeWidth)
            // 바탕
            arc(i, style.base, style.width)
            if (frame == null || level == HapticPattern.LEVEL_MUTE) continue
            // 다음 박: 차오르는 바
            if (i == frame.nextPos && frame.fill > 0f) {
                arc(i, style.lit.copy(alpha = 0.6f + 0.4f * frame.fill), style.width, sweep * frame.fill)
            }
            // 방금 울린 박: 번쩍임
            if (i == frame.currentPos && frame.flash > 0f) {
                arc(i, style.lit.copy(alpha = frame.flash), style.width * (1f + 0.35f * frame.flash))
            }
        }
    }
}

private class SegmentStyle(val base: Color, val lit: Color, val width: Float)

private fun segmentStyle(level: Int, active: Color, accent: Color, idle: Color, w: Float) = when (level) {
    HapticPattern.LEVEL_ACCENT -> SegmentStyle(accent.copy(alpha = 0.3f), accent, w * 1.3f)
    HapticPattern.LEVEL_MUTE -> SegmentStyle(idle.copy(alpha = 0.35f), idle, w * 0.35f)
    else -> SegmentStyle(idle.copy(alpha = 0.7f), active, w)
}

private fun DrawScope.drawSegment(
    color: Color, start: Float, sweep: Float, inset: Float, arcSize: Size, width: Float,
) = drawArc(
    color = color,
    startAngle = start,
    sweepAngle = sweep,
    useCenter = false,
    topLeft = Offset(inset, inset),
    size = arcSize,
    style = Stroke(width = width, cap = StrokeCap.Round),
)

private const val VISUAL_LEAD_NANOS = 12_000_000L
