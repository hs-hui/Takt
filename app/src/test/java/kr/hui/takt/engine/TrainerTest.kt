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

class PresetTest {
    @Test fun encodeDecodeRoundTrip() {
        val list = listOf(kr.hui.takt.data.Preset(120, 4, 1), kr.hui.takt.data.Preset(90, 3, 3))
        assertEquals(list, kr.hui.takt.data.Preset.decode(kr.hui.takt.data.Preset.encode(list)))
    }

    @Test fun decodeIgnoresGarbageAndClamps() {
        val d = kr.hui.takt.data.Preset.decode("120,4,1;bad;999,0,9;")
        assertEquals(listOf(kr.hui.takt.data.Preset(120, 4, 1), kr.hui.takt.data.Preset(BeatGrid.MAX_BPM, 1, 4)), d)
        assertEquals(emptyList<kr.hui.takt.data.Preset>(), kr.hui.takt.data.Preset.decode(null))
    }

    @Test fun presetKeepsBeatLevelsAndOldFormatStillLoads() {
        val p = kr.hui.takt.data.Preset(100, 3, 1, listOf(2, 0, 1))
        val enc = kr.hui.takt.data.Preset.encode(listOf(p))
        assertEquals("100,3,1,2-0-1", enc)
        assertEquals(listOf(p), kr.hui.takt.data.Preset.decode(enc))
        // 이전 형식(3칸)
        assertEquals(listOf(kr.hui.takt.data.Preset(100, 3, 1)), kr.hui.takt.data.Preset.decode("100,3,1"))
        // 박 수와 맞지 않는 세기는 버림
        assertEquals(emptyList<Int>(), kr.hui.takt.data.Preset.decode("100,4,1,2-1")[0].beatLevels)
        // 적용하면 설정에 반영
        val s = kr.hui.takt.data.MetronomeSettings().withPresetApplied(p)
        assertEquals(listOf(2, 0, 1), s.levels().toList())
    }

    @Test fun savingMovesToFrontWithoutDuplicates() {
        val s = kr.hui.takt.data.MetronomeSettings(
            bpm = 90, beatsPerBar = 3, subdivision = 1,
            presets = listOf(kr.hui.takt.data.Preset(120, 4, 1), kr.hui.takt.data.Preset(90, 3, 1)),
        ).withPresetSaved()
        assertEquals(listOf(kr.hui.takt.data.Preset(90, 3, 1), kr.hui.takt.data.Preset(120, 4, 1)), s.presets)
    }
}

class StrengthTest {
    @Test fun strongerLevelsAreLongerAtMaxAmplitude() {
        val s5 = kr.hui.takt.data.MetronomeSettings(strength = 5).hapticStyle()
        val s7 = kr.hui.takt.data.MetronomeSettings(strength = 7).hapticStyle()
        assertEquals(255, s7.amplitude)
        assertEquals(200L, s7.pulseMs)
        assertEquals(320L, s7.accentPulseMs)
        assert(s7.pulseMs > s5.pulseMs)
        // 범위 밖은 최대치로
        assertEquals(7, kr.hui.takt.data.MetronomeSettings(strength = 99).sanitized().strength)
    }

    @Test fun longPulsesStillCappedByTempo() {
        val st = kr.hui.takt.data.MetronomeSettings(strength = 7).hapticStyle()
        // 240bpm 박 250ms → 펄스는 최대 112ms
        val p = HapticPattern.build(240, 4, 0, 0, st)
        for (i in 1 until p.timings.size step 2) assert(p.timings[i] <= 112)
    }
}
