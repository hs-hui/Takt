package kr.hui.takt.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kr.hui.takt.MainActivity
import kr.hui.takt.Metronome
import kr.hui.takt.R

/**
 * 재생 중에만 떠 있는 포그라운드 서비스.
 * - 화면이 꺼지거나 앱을 벗어나도 프로세스·진동이 유지되게 한다.
 * - 워치페이스에 Ongoing Activity 아이콘을 띄운다.
 * - 화면이 꺼질 때 시스템이 진동을 취소하므로 즉시 다시 건다.
 */
class MetronomeService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var ongoing: OngoingActivity? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Metronome.reanchorNow()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Metronome.init(this)
        createChannel()
        val bpm = Metronome.state.value.settings.bpm
        val builder = notificationBuilder(bpm)
        ongoing = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_metronome)
            .setTouchIntent(openAppIntent())
            .setStatus(status(bpm))
            .build()
            .also { it.apply(applicationContext) }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            builder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Takt::Metronome")
            .apply { acquire(MAX_WAKELOCK_MS) }

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )

        lifecycleScope.launch {
            Metronome.state.map { it.settings.bpm }.distinctUntilChanged().collect { b ->
                val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) ongoing?.update(applicationContext, status(b))
            }
        }
        lifecycleScope.launch {
            // 다른 경로(프로세스 재시작 등)로 재생 상태가 아니면 스스로 종료
            Metronome.state.collect { if (!it.playing) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) Metronome.stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun status(bpm: Int) = Status.Builder()
        .addTemplate("#bpm# BPM")
        .addPart("bpm", Status.TextPart(bpm.toString()))
        .build()

    private fun notificationBuilder(bpm: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_metronome)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$bpm BPM")
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.stop),
                PendingIntent.getService(
                    this, 1,
                    Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "kr.hui.takt.STOP"
        /** 깜빡 잊고 켜둔 경우 배터리 보호: 최대 3시간 */
        private const val MAX_WAKELOCK_MS = 3 * 60 * 60 * 1000L
    }
}
