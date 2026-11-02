package kr.hui.takt

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    }
}
