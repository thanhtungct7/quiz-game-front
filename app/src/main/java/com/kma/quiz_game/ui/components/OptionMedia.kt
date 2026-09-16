package com.kma.quiz_game.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageScope
import com.kma.quiz_game.data.remote.BUNDLED_IMAGE_DIR
import com.kma.quiz_game.data.remote.bundledImageAsset
import com.kma.quiz_game.data.remote.storageMediaUrl

/** The vocabulary deck's pictures are 390x260. */
private const val OPTION_IMAGE_ASPECT_RATIO = 3f / 2f

/**
 * The picture files the build ships under [BUNDLED_IMAGE_DIR], listed once.
 *
 * The pictures are bundled because Firebase serves them from us-east1, about a second per picture
 * from Vietnam, and a question on a clock cannot wait for that. One the build lacks still loads
 * from Firebase.
 */
private object BundledImages {
    @Volatile
    private var names: Set<String>? = null

    fun contains(context: Context, asset: String): Boolean {
        val shipped = names
            ?: context.assets.list(BUNDLED_IMAGE_DIR).orEmpty().toHashSet().also { names = it }
        return asset.substringAfterLast('/') in shipped
    }
}

private val remoteLoading: @Composable SubcomposeAsyncImageScope.(AsyncImagePainter.State.Loading) -> Unit = {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        modifier = Modifier.align(Alignment.Center).size(24.dp),
    )
}

/**
 * An answer drawn as a picture, from the storage path its option carries.
 *
 * A bundled picture decodes in a frame, so it gets no spinner to flash. [fallback] replaces a
 * picture that cannot be loaded at all: a round runs on a clock, so the option must stay
 * answerable.
 */
@Composable
fun OptionImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val asset = remember(path) {
        bundledImageAsset(path)?.takeIf { BundledImages.contains(context, it) }
    }
    SubcomposeAsyncImage(
        model = if (asset != null) "file:///android_asset/$asset" else storageMediaUrl(path),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        loading = remoteLoading.takeIf { asset == null },
        error = {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                fallback()
            }
        },
        modifier = modifier.fillMaxWidth().aspectRatio(OPTION_IMAGE_ASPECT_RATIO),
    )
}

/**
 * Plays the recordings options carry. One per screen: starting a recording stops the one before,
 * and [rememberOptionAudioPlayer] releases it when the screen goes.
 */
class OptionAudioPlayer {
    private var player: MediaPlayer? = null

    fun play(path: String) {
        release()
        val next = MediaPlayer()
        next.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        next.setOnPreparedListener { it.start() }
        next.setOnCompletionListener { done -> if (player === done) release() }
        // A recording that will not play is not worth an error in the middle of a timed round.
        next.setOnErrorListener { failed, _, _ ->
            if (player === failed) release()
            true
        }
        try {
            next.setDataSource(storageMediaUrl(path))
            next.prepareAsync()
        } catch (_: Exception) {
            next.release()
            return
        }
        player = next
    }

    fun release() {
        player?.release()
        player = null
    }
}

@Composable
fun rememberOptionAudioPlayer(): OptionAudioPlayer {
    val player = remember { OptionAudioPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Plays a word. A touch target of its own, so listening to an option never selects it.
 *
 * 48dp because that is the smallest target Material considers reachable, and this one sits inside
 * an option the player is trying *not* to hit. The glyph stays at 20dp, so the button grew without
 * the icon getting louder.
 */
@Composable
fun SpeakerButton(onClick: () -> Unit, tint: Color, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Nghe phát âm",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
