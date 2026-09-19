package com.kma.quiz_game.ui.components.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The picture for one catalog item, looked up by its `code`.
 *
 * Bundled in assets and keyed by code rather than fetched from `game_items.image_src`: the
 * catalog is a fixed set that ships with the app, so a URL would buy nothing but a round trip and
 * a blank square whenever the network is down.
 *
 * Drawn on a Canvas with [FilterQuality.None] for the same reason [HeroPortrait] is -- these are
 * 32x32 pixel-art tiles, and letting the density machinery resample them turns a crisp sword into
 * a smudge. Skins are the exception at 50x50: their icon is frame 0 of the sheet the character
 * will actually be drawn from, so the shop shows you the thing you are buying.
 */
@Composable
fun ItemIcon(code: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    PixelTile(path = "items/$code.png", modifier = modifier, size = size)
}

/**
 * Any bundled pixel-art tile under `assets/`, drawn the way [ItemIcon] draws items. [alpha] and
 * [colorFilter] are for the states a tile has to look unavailable in, like a locked chest.
 */
@Composable
fun PixelTile(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalContext.current
    val tile = remember(context, path) { PixelTiles.load(context, path) }

    Canvas(modifier = modifier.size(size)) {
        if (tile != null) drawTile(tile, alpha, colorFilter)
    }
}

private fun DrawScope.drawTile(tile: ImageBitmap, alpha: Float, colorFilter: ColorFilter?) {
    // Contain rather than stretch: the pack's tiles are square, a skin's is not.
    val scale = minOf(size.width / tile.width, size.height / tile.height)
    val width = (tile.width * scale).roundToInt()
    val height = (tile.height * scale).roundToInt()
    drawImage(
        image = tile,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(tile.width, tile.height),
        dstOffset = IntOffset(
            ((size.width - width) / 2f).roundToInt(),
            ((size.height - height) / 2f).roundToInt(),
        ),
        dstSize = IntSize(width, height),
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = FilterQuality.None,
    )
}

private object PixelTiles {
    private val cached = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun load(context: Context, path: String): ImageBitmap? {
        cached[path]?.let { return it }
        // An item with no artwork draws nothing rather than crashing a list: the catalog can grow
        // server-side without the app shipping a tile for the new row on the same day.
        if (path in missing) return null

        val decoded = runCatching {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inScaled = false },
                )
            }
        }.getOrNull()

        if (decoded == null) {
            missing.add(path)
            return null
        }
        return decoded.asImageBitmap().also { cached[path] = it }
    }
}
