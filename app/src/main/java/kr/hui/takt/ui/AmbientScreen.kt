package kr.hui.takt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kr.hui.takt.MetronomeState
import kr.hui.takt.R
import kr.hui.takt.engine.HapticPattern

/**
 * 저전력(ambient) 화면: 검은 배경, 얇은 선, 애니메이션 없음 (번인·배터리 보호).
 * 진동은 시스템이 계속 재므로 화면은 숫자만 보여준다.
 */
@Composable
fun AmbientScreen(state: MetronomeState) {
    val s = state.settings
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val inset = 6.dp.toPx()
            val n = s.beatsPerBar
            val levels = s.levels()
            val gap = if (n == 1) 0f else 8f
            for (i in 0 until n) {
                if (levels[i] == HapticPattern.LEVEL_MUTE) continue
                drawArc(
                    color = Color.Gray,
                    startAngle = -90f + i * 360f / n + gap / 2,
                    sweepAngle = 360f / n - gap,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = if (levels[i] == HapticPattern.LEVEL_ACCENT) stroke * 2 else stroke),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.meter_label, s.beatsPerBar, subdivisionLabel(s.subdivision)),
                color = Color.Gray,
                fontSize = 13.sp,
            )
            Text(s.bpm.toString(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(
                if (state.playing) "BPM · ${stringResource(R.string.playing)}" else "BPM",
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
    }
}
