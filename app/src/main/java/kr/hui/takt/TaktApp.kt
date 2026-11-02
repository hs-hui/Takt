package kr.hui.takt

import android.app.Application

class TaktApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Metronome.init(this)
    }
}
