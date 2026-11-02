package kr.hui.takt.engine

import java.util.concurrent.locks.LockSupport

/** 직접 진동 모드의 재생 설정 */
class PulseConfig(
    val grid: BeatGrid,
    val style: HapticStyle,
    val subdivision: Int,
    val levels: IntArray?,
)

/**
 * 펄스마다 정확한 시각에 [onPulse] 를 부르는 전용 스레드. 안드로이드 의존성 없음(시계·출력 주입).
 *
 * 목표 시각 직전까지 parkNanos 로 잠들고, 마지막 [spinNanos] 만 바쁜 대기로 맞춘다.
 * 설정이 바뀌면 즉시 깨어나 다음 펄스를 다시 계산하며, 이미 울린 시각 이전 펄스는 다시 울리지 않는다.
 */
class PulseScheduler(
    private val clock: () -> Long,
    private val onPulse: (pulse: Pulse, config: PulseConfig) -> Unit,
    private val spinNanos: Long = 700_000L,
    private val threadSetup: () -> Unit = {},
    /**
     * 펄스 시각보다 얼마나 먼저 [onPulse] 를 부를지. 진동 서비스 처리·모터 기동 시간을 보정해
     * "느껴지는" 시점이 펄스 시각에 오도록 한다. 펄스마다 다시 읽는다.
     */
    private val leadNanos: () -> Long = { 0L },
) {
    @Volatile var config: PulseConfig? = null
        private set

    @Volatile private var thread: Thread? = null

    val running: Boolean get() = thread?.isAlive == true

    fun play(c: PulseConfig) {
        config = c
        val t = thread
        if (t == null || !t.isAlive) {
            // 필드에 먼저 대입한 뒤 시작해야 한다 (루프가 thread === 자신 인지 확인하므로)
            val nt = Thread(::loop, "Takt-Pulser")
            thread = nt
            nt.start()
        } else {
            LockSupport.unpark(t)
        }
    }

    fun stop() {
        config = null
        thread?.let { it.interrupt(); LockSupport.unpark(it) }
        thread = null
    }

    private fun loop() {
        threadSetup()
        val me = Thread.currentThread()
        var lastFired = Long.MIN_VALUE
        outer@ while (!me.isInterrupted && thread === me) {
            val c = config ?: break
            val lead = leadNanos()
            val pulse = PulseClock.nextPulseAfter(c.grid, maxOf(clock() + lead, lastFired), c.subdivision)
            val target = pulse.timeNanos - lead
            // 대부분은 잠들어 기다리고
            while (true) {
                if (config !== c || me.isInterrupted) continue@outer
                val remain = target - spinNanos - clock()
                if (remain <= 0) break
                LockSupport.parkNanos(remain)
            }
            // 마지막 짧은 구간은 바쁜 대기로 정확히
            while (clock() < target) {
                if (config !== c) continue@outer
            }
            lastFired = pulse.timeNanos
            onPulse(pulse, c)
        }
    }
}
