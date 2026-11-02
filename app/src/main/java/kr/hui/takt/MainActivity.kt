package kr.hui.takt

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.hui.takt.ui.TaktApp

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Metronome.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            // Ongoing Activity(워치페이스 아이콘) 표시에 필요
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { TaktApp() }
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
