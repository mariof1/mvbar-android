package com.mvbar.android.wear

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Watch-face Tile showing the currently playing track and a quick
 * play/pause / skip strip. Tapping the tile opens the full Wear app.
 *
 * Resolution-only: the real now-playing source of truth is the
 * DataClient mirror (NowPlayingRepository); the tile reads the latest
 * snapshot synchronously from a process-local cache that the
 * repository keeps in sync.
 */
class NowPlayingTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        // Make sure the repository is listening so the cached state is fresh.
        NowPlayingRepository.attach(applicationContext)
        val state = preferLocalState()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    buildLayout(applicationContext, state)
                )
            )
            // Re-render every 30s so duration ticks visibly even when
            // we don't have an explicit DataClient push.
            .setFreshnessIntervalMillis(30_000L)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildLayout(context: Context, state: NowPlayingState): LayoutElement {
        val accent = if (state.isPodcast || state.isAudiobook) 0xFFF97316.toInt() else 0xFF06B6D4.toInt()
        val title = state.title.ifEmpty { "mvbar" }
        val subtitle = state.artist.ifEmpty { "Tap to open" }

        val openAppAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(dp(192f))
            .setHeight(dp(192f))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_mvbar")
                            .setOnClick(openAppAction)
                            .build()
                    )
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("mvbar")
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(accent))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(title.take(26))
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(15f))
                                    .setColor(argb(AndroidColor.WHITE))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(subtitle.take(28))
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(0xCCFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(if (state.isPlaying) "▶  Playing" else "⏸  Paused")
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(10f))
                                    .setColor(argb(argbAccent(accent, 0xCC)))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun preferLocalState(): NowPlayingState {
        val local = com.mvbar.android.wear.player.WearPlayerHolder.state.value
        if (local.isActive) {
            val item = local.item!!
            return NowPlayingState(
                title = item.title,
                artist = item.subtitle,
                durationMs = local.durationMs,
                positionMs = local.positionMs,
                isPlaying = local.isPlaying,
                isPodcast = item.isPodcast
            )
        }
        return NowPlayingRepository.state.value
    }

    private fun argbAccent(accent: Int, alpha: Int): Int =
        (alpha shl 24) or (accent and 0x00FFFFFF)

    companion object {
        private const val RESOURCES_VERSION = "1"

        /**
         * Ask the system to repaint our tiles. Called by the data layer
         * receiver whenever a new now-playing snapshot arrives.
         */
        fun requestUpdate(context: Context) {
            try {
                getUpdater(context).requestUpdate(NowPlayingTileService::class.java)
            } catch (_: Throwable) { /* tiles unavailable — ignore */ }
        }

        private fun getUpdater(context: Context) =
            androidx.wear.tiles.TileService.getUpdater(context)
    }
}
