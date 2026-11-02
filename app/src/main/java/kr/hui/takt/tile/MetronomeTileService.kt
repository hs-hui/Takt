package kr.hui.takt.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kr.hui.takt.MainActivity
import kr.hui.takt.Metronome
import kr.hui.takt.MetronomeState
import kr.hui.takt.R

/**
 * 타일: BPM 표시, −/+ 로 바로 조절, 재생/정지.
 * −/+ 는 앱을 열지 않고 처리(LoadAction). 재생 시작은 백그라운드에서 포그라운드 서비스를
 * 시작할 수 없으므로 앱을 열면서 토글한다(LaunchAction).
 */
class MetronomeTileService : TileService() {
    private val scope = MainScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                try {
                    Metronome.init(this@MetronomeTileService)
                    Metronome.awaitLoaded()
                    when (requestParams.currentState.lastClickableId) {
                        ID_MINUS -> Metronome.nudgeBpm(-1)
                        ID_PLUS -> Metronome.nudgeBpm(+1)
                        ID_STOP -> Metronome.stop()
                    }
                    completer.set(tile(this@MetronomeTileService, Metronome.state.value, requestParams.deviceConfiguration))
                } catch (t: Throwable) {
                    completer.setException(t)
                }
            }
            "MetronomeTile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { c ->
        c.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "MetronomeTileResources"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val ID_MINUS = "minus"
        private const val ID_PLUS = "plus"
        private const val ID_STOP = "stop"

        private val COLORS = Colors(0xFFD0BCFF.toInt(), 0xFF381E72.toInt(), 0xFF2B2930.toInt(), 0xFFE6E0E9.toInt())

        fun tile(context: Context, state: MetronomeState, device: DeviceParameters): TileBuilders.Tile {
            val layout = layout(context, state, device)
            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(0) // 상태 변화 시 앱이 직접 갱신 요청
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

        private fun layout(context: Context, state: MetronomeState, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
            val s = state.settings
            val sub = context.getString(
                when (s.subdivision) {
                    2 -> R.string.sub_eighth
                    3 -> R.string.sub_triplet
                    4 -> R.string.sub_sixteenth
                    else -> R.string.sub_quarter
                },
            )
            val bpmColumn = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(context, s.bpm.toString())
                        .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                        .setColor(argb(0xFFFFFFFF.toInt()))
                        .build(),
                )
                .addContent(
                    Text.Builder(context, "BPM")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(0xFFCAC4D0.toInt()))
                        .build(),
                )
                .build()

            val row = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stepButton(context, "−", ID_MINUS))
                .addContent(spacer(8f))
                .addContent(bpmColumn)
                .addContent(spacer(8f))
                .addContent(stepButton(context, "+", ID_PLUS))
                .build()

            val chip = if (state.playing) {
                CompactChip.Builder(context, context.getString(R.string.stop), loadClickable(ID_STOP), device)
                    .setChipColors(ChipColors.primaryChipColors(COLORS))
                    .build()
            } else {
                CompactChip.Builder(context, context.getString(R.string.play), launchClickable(context), device)
                    .setChipColors(ChipColors.primaryChipColors(COLORS))
                    .build()
            }

            return PrimaryLayout.Builder(device)
                .setResponsiveContentInsetEnabled(true)
                .setPrimaryLabelTextContent(
                    Text.Builder(context, context.getString(R.string.meter_label, s.beatsPerBar, sub))
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(0xFFCAC4D0.toInt()))
                        .build(),
                )
                .setContent(row)
                .setPrimaryChipContent(chip)
                .build()
        }

        private fun stepButton(context: Context, label: String, id: String) =
            Button.Builder(context, loadClickable(id))
                .setTextContent(label)
                .setSize(ButtonDefaults.DEFAULT_SIZE)
                .setButtonColors(ButtonColors.secondaryButtonColors(COLORS))
                .build()

        private fun spacer(widthDp: Float) = LayoutElementBuilders.Spacer.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.dp(widthDp))
            .build()

        private fun loadClickable(id: String) = ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        private fun launchClickable(context: Context) = ModifiersBuilders.Clickable.Builder()
            .setId("launch")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_START,
                                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        private fun argb(c: Int) = androidx.wear.protolayout.ColorBuilders.argb(c)
    }
}
