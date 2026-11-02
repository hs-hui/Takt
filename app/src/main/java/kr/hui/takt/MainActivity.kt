package kr.hui.takt

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import kotlinx.coroutines.launch
import kr.hui.takt.ui.TaktApp

class MainActivity : ComponentActivity() {
    /** 손목을 내렸을 때 저전력(ambient) 화면 여부 */
    private val ambient = mutableStateOf(false)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.value = true
            }

            override fun onExitAmbient() {
                ambient.value = false
            }
        },
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Metronome.init(this)
        lifecycle.addObserver(ambientObserver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            // Ongoing Activity(워치페이스 아이콘) 표시에 필요
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { TaktApp(ambient = ambient.value) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 타일의 재생 버튼으로 열린 경우 바로 시작 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            intent.removeExtra(EXTRA_START)
            lifecycleScope.launch {
                Metronome.awaitLoaded()
                Metronome.start()
            }
        }
    }

    companion object {
        const val EXTRA_START = "kr.hui.takt.START"
    }
}
