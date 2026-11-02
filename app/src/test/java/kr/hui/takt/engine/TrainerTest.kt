package kr.hui.takt.engine

import kr.hui.takt.data.TrainerSettings
import kr.hui.takt.data.nextBpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainerTest {
    @Test fun stepsUpToTarget() {
        val t = TrainerSettings(enabled = true, stepBpm = 5, everyBars = 4, targetBpm = 112)
        assertEquals(105, t.nextBpm(100))
        assertEquals(112, t.nextBpm(110))
        assertNull(t.nextBpm(112))
    }

    @Test fun stepsDownWhenTargetIsLower() {
        val t = TrainerSettings(enabled = true, stepBpm = 10, targetBpm = 80)
        assertEquals(90, t.nextBpm(100))
        assertEquals(80, t.nextBpm(85))
    }

    @Test fun sanitizeClamps() {
        val t = TrainerSettings(stepBpm = 0, everyBars = 99, targetBpm = 999).sanitized()
        assertEquals(1, t.stepBpm)
        assertEquals(32, t.everyBars)
        assertEquals(BeatGrid.MAX_BPM, t.targetBpm)
    }
}
