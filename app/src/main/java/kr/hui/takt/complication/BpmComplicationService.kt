package kr.hui.takt.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import android.graphics.drawable.Icon
import kr.hui.takt.MainActivity
import kr.hui.takt.Metronome
import kr.hui.takt.R

/** 워치페이스 컴플리케이션: 현재 BPM (재생 중이면 ▶ 표시). 누르면 앱 열기. */
class BpmComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build(this, 120, playing = false, tap = null) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        Metronome.init(this)
        Metronome.awaitLoaded()
        val st = Metronome.state.value
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return build(this, st.settings.bpm, st.playing, tap)
    }

    companion object {
        private fun build(context: Context, bpm: Int, playing: Boolean, tap: PendingIntent?) =
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(if (playing) "▶$bpm" else "$bpm").build(),
                contentDescription = PlainComplicationText.Builder("$bpm BPM").build(),
            )
                .setTitle(PlainComplicationText.Builder("BPM").build())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_metronome)).build(),
                )
                .setTapAction(tap)
                .build()

        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context, ComponentName(context, BpmComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
