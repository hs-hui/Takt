package kr.hui.takt

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kr.hui.takt.data.TrainerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * 컨트롤러 통합 테스트: Robolectric 시계를 앞으로 돌려가며
 * 트레이너가 마디 첫 박에 정확히 템포를 바꾸는지, 정지 시 복원되는지 확인.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetronomeControllerTest {
    private val looper get() = shadowOf(Looper.getMainLooper())

    @Test fun trainerStepsOnDownbeatsAndRestoresOnStop() {
        Metronome.init(ApplicationProvider.getApplicationContext<Application>())
        looper.idle()
        Metronome.setBpm(120)
        Metronome.setBeatsPerBar(4)
        Metronome.setTrainer { TrainerSettings(enabled = true, stepBpm = 10, everyBars = 1, targetBpm = 140) }
        looper.idle()

        val t0 = SystemClock.elapsedRealtimeNanos()
        Metronome.start()
        val g0 = Metronome.state.value.grid!!
        assertEquals(t0 + 50_000_000L, g0.timeOfBeat(0))

        // 첫 마디(120bpm × 4박 = 2초) 직후
        looper.idleFor(Duration.ofMillis(2_100))
        val g1 = Metronome.state.value.grid!!
        assertEquals(130, Metronome.state.value.settings.bpm)
        assertEquals(4L, g1.originBeat) // 다음 마디 첫 박부터 새 템포
        assertEquals(g0.timeOfBeat(4), g1.timeOfBeat(4))
        assertEquals(0, g1.barPosition(g1.originBeat))

        // 두 번째 마디(130bpm × 4박 ≈ 1.846초) 직후 → 목표 140
        looper.idleFor(Duration.ofMillis(1_850))
        val g2 = Metronome.state.value.grid!!
        assertEquals(140, Metronome.state.value.settings.bpm)
        assertEquals(8L, g2.originBeat)

        // 목표 도달 후에는 그대로
        looper.idleFor(Duration.ofSeconds(5))
        assertEquals(140, Metronome.state.value.settings.bpm)

        Metronome.stop()
        assertFalse(Metronome.state.value.playing)
        assertEquals(120, Metronome.state.value.settings.bpm)
    }

    @Test fun userTempoChangeIsDebouncedAndAppliedOnNextBeat() {
        Metronome.init(ApplicationProvider.getApplicationContext<Application>())
        looper.idle()
        Metronome.setTrainer { it.copy(enabled = false) }
        Metronome.setBpm(60)
        Metronome.start()
        val g0 = Metronome.state.value.grid!!
        looper.idleFor(Duration.ofMillis(300))

        // 베젤을 빠르게 5칸
        repeat(5) { Metronome.nudgeBpm(+1) }
        assertEquals(65, Metronome.state.value.settings.bpm) // 화면 숫자는 즉시
        assertEquals(60, Metronome.state.value.grid!!.bpm) // 엔진은 아직
        looper.idleFor(Duration.ofMillis(200))
        val g1 = Metronome.state.value.grid!!
        assertEquals(65, g1.bpm)
        // 다음 박(1번 박, 1.05초) 부터 새 템포
        assertEquals(1L, g1.originBeat)
        assertEquals(g0.timeOfBeat(1), g1.originNanos)
        assertTrue(Metronome.state.value.playing)
        Metronome.stop()
    }
}
