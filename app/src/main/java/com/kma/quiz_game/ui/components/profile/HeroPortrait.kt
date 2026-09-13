package com.kma.quiz_game.ui.components.profile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kma.quiz_game.ui.game.art.BattleArt
import com.kma.quiz_game.ui.game.art.HeroClip
import kotlin.math.roundToInt

/**
 * The player's character, stood on a lit plinth, drawn from the same sprite page the arena fights
 * on.
 *
 * Compose rather than libGDX on purpose. The arena needs a GL surface because it animates two
 * fighters, projectiles and a parallax backdrop at once; a profile card needs one idle loop, and
 * paying for a [com.kma.quiz_game.ui.game.ArenaSurface] -- a fragment, a GL context and a second
 * render thread -- to get it would make opening a tab cost more than opening a fight.
 *
 * Three things keep it sharp and cheap:
 *
 * - The page is decoded with `inScaled = false` and blitted with [FilterQuality.None], so an
 *   81x50 knight scaled to a card stays pixel art instead of turning into a blur.
 * - Only the knight is cropped out of the frame, not the frame. Every frame shares one box wide
 *   enough for a thrown sword, so centring the box would slide him sideways -- the same correction
 *   [BattleArt.HERO_BODY_CENTRE] exists for in the arena.
 * - The frame counter is read *inside* the draw lambda. Advancing it invalidates the draw phase
 *   and nothing above it, so an idling knight does not recompose the card, the tab row or the
 *   scrolling list he sits in.
 */
@Composable
fun HeroPortrait(
    modifier: Modifier = Modifier,
    /** The lit half of "skin & glow" -- see [heroGlowFor]. */
    glow: Color,
    /** Which costume to draw him in. Null wears the default. */
    skinCode: String? = null,
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val page = BattleArt.heroPageFor(skinCode)
    val sheet = remember(context, page) { HeroSheet.load(context, page) }
    val frame = remember { mutableIntStateOf(HeroClip.IDLE.start) }

    if (animated) {
        LaunchedEffect(Unit) {
            val startedAt = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val elapsed = (now - startedAt) / NANOS_PER_SECOND
                    frame.intValue = HeroClip.IDLE.frameAt(elapsed)
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        drawPlinth(glow)
        // Read here rather than in composition: this is what keeps the idle loop in the draw
        // phase. See the class comment.
        val index = frame.intValue
        if (sheet == null) drawSilhouette(glow) else drawHeroFrame(sheet, index)
    }
}

/** The pool of light he stands in, and the shadow he casts into it. */
private fun DrawScope.drawPlinth(glow: Color) {
    val radius = size.minDimension * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.38f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.72f),
            radius = radius,
        ),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.72f),
    )
    drawOval(
        color = Color.Black.copy(alpha = 0.20f),
        topLeft = androidx.compose.ui.geometry.Offset(
            x = size.width * 0.5f - size.width * SHADOW_HALF_WIDTH,
            y = size.height * GROUND_Y - size.height * SHADOW_HALF_HEIGHT,
        ),
        size = androidx.compose.ui.geometry.Size(
            width = size.width * SHADOW_HALF_WIDTH * 2f,
            height = size.height * SHADOW_HALF_HEIGHT * 2f,
        ),
    )
}

/**
 * One frame of the idle loop, cropped to the knight and stood on the ground line.
 *
 * The destination is sized from the crop's own aspect so he is never stretched, and rounded to
 * whole pixels: a sprite landing on a half pixel is exactly where nearest-neighbour scaling starts
 * to shimmer as the list scrolls.
 */
private fun DrawScope.drawHeroFrame(sheet: ImageBitmap, frameIndex: Int) {
    val column = frameIndex % BattleArt.HERO_COLUMNS
    val row = frameIndex / BattleArt.HERO_COLUMNS
    val bodyLeft = (BattleArt.HERO_BODY_CENTRE - BattleArt.HERO_BODY_HALF_WIDTH) *
        BattleArt.HERO_FRAME_WIDTH
    val bodyWidth = 2f * BattleArt.HERO_BODY_HALF_WIDTH * BattleArt.HERO_FRAME_WIDTH

    val srcX = column * BattleArt.HERO_FRAME_WIDTH + bodyLeft.roundToInt()
    val srcY = row * BattleArt.HERO_FRAME_HEIGHT
    val srcWidth = bodyWidth.roundToInt()
    val srcHeight = BattleArt.HERO_FRAME_HEIGHT

    // Fit by height; the crop is taller than it is wide, so height is what runs out first.
    val drawHeight = size.height * FIGURE_HEIGHT
    val drawWidth = drawHeight * srcWidth / srcHeight
    val left = (size.width - drawWidth) / 2f
    val top = size.height * GROUND_Y - drawHeight

    drawImage(
        image = sheet,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

/**
 * What is drawn when the sprite page cannot be read.
 *
 * The arena has the same rule for a monster whose art is missing: a card with a blank stage would
 * read as a broken screen, where a silhouette reads as a character whose portrait has not loaded.
 */
private fun DrawScope.drawSilhouette(glow: Color) {
    val body = glow.copy(alpha = 0.45f)
    val height = size.height * FIGURE_HEIGHT
    val width = height * 0.42f
    val centreX = size.width / 2f
    val feet = size.height * GROUND_Y

    drawCircle(color = body, radius = width * 0.32f, center = androidx.compose.ui.geometry.Offset(centreX, feet - height * 0.82f))
    drawRoundRect(
        color = body,
        topLeft = androidx.compose.ui.geometry.Offset(centreX - width / 2f, feet - height * 0.62f),
        size = androidx.compose.ui.geometry.Size(width, height * 0.62f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.35f),
    )
}

/**
 * The sprite page, decoded once per process.
 *
 * Held for the life of the app rather than the composition: it is 12KB, and re-decoding it every
 * time the profile tab is opened would put a file read on the frame that opens it.
 */
private object HeroSheet {
    // One entry per skin rather than one page: a leaderboard draws several players at once and
    // they need not be wearing the same thing. There are seven pages in total, each a few KB.
    private val cached = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    fun load(context: Context, page: String): ImageBitmap? {
        cached[page]?.let { return it }
        // A missing or unreadable asset is not worth a crash on a profile screen; the caller
        // falls back to a silhouette.
        val decoded = runCatching {
            context.assets.open(page).use { stream ->
                // inScaled = false: the page is a pixel-art atlas, and letting the density
                // machinery resample it would defeat the point of drawing it unfiltered.
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
            }
        }.getOrNull() ?: return null
        return decoded.asImageBitmap().also { cached[page] = it }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Where the knight's feet are, as a fraction of the portrait's height. */
private const val GROUND_Y = 0.86f

/** How much of the portrait he fills. The rest is headroom, so the glow reads as a stage. */
private const val FIGURE_HEIGHT = 0.72f

private const val SHADOW_HALF_WIDTH = 0.17f
private const val SHADOW_HALF_HEIGHT = 0.022f
