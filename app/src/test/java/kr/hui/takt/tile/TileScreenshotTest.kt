package kr.hui.takt.tile

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import org.robolectric.Robolectric
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import kr.hui.takt.MetronomeState
import kr.hui.takt.data.MetronomeSettings
import kr.hui.takt.engine.BeatGrid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor

/** 타일 레이아웃 스크린샷 (실제 타일 렌더러 사용). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class TileScreenshotTest {

    private fun render(name: String, state: MetronomeState) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val context: Context = activity
        val dm = context.resources.displayMetrics
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp((dm.widthPixels / dm.density).toInt())
            .setScreenHeightDp((dm.heightPixels / dm.density).toInt())
            .setScreenDensity(dm.density)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()
        val tile = MetronomeTileService.tile(context, state, device)
        val layout = tile.tileTimeline!!.timelineEntries[0].layout!!
        val parent = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        activity.setContentView(parent)
        val renderer = TileRenderer(context, Executor { it.run() }) { }
        renderer.inflateAsync(layout, ResourceBuilders.Resources.Builder().setVersion("1").build(), parent)
        shadowOf(Looper.getMainLooper()).idle()
        parent.captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun stopped() = render("tile_stopped", MetronomeState(MetronomeSettings(bpm = 96, subdivision = 2)))

    @Test fun playing() = render(
        "tile_playing",
        MetronomeState(MetronomeSettings(bpm = 140, beatsPerBar = 3), grid = BeatGrid(0, 0, 140, 3)),
    )
}
