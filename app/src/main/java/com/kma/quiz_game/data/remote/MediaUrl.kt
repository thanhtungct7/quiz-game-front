package com.kma.quiz_game.data.remote

import com.kma.quiz_game.BuildConfig
import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The download URL of a lesson image or recording, from the path a challenge option stores
 * (`vocab/images/01_0001.jpg`).
 *
 * The files live in Firebase Storage, where the whole path is a single URL segment -- its slashes
 * are escaped to `%2F`, and `alt=media` asks for the file rather than its metadata.
 */
fun storageMediaUrl(path: String, baseUrl: String = BuildConfig.MEDIA_BASE_URL): String =
    baseUrl + URLEncoder.encode(path, Charsets.UTF_8) + "?alt=media"

/** Where the vocabulary pictures sit inside the app's assets. */
const val BUNDLED_IMAGE_DIR = "vocab/images"

/**
 * The asset a stored picture path ships as, or null for a path the app does not bundle:
 * `vocab/images/01_0001.jpg` -> `vocab/images/01_0001.webp`.
 *
 * The pictures are re-encoded as WebP when bundled (the backend's scripts/export_vocab_images.py,
 * whose `asset_path` must agree with this).
 */
fun bundledImageAsset(path: String): String? {
    if (!path.startsWith("$BUNDLED_IMAGE_DIR/")) return null
    return path.substringBeforeLast('.') + ".webp"
}

/**
 * Resolves an avatar URL against the API host.
 *
 * The backend hands back two shapes: an absolute `https://lh3.googleusercontent.com/...` for
 * accounts that signed in with Google, and a path on this API for an uploaded avatar. Both are
 * handled by one `resolve` call -- an absolute URL resolves to itself -- which also means the
 * server never has to know the host it is reachable at, and the LAN IP in
 * [BuildConfig.API_BASE_URL] stays the single place that changes.
 */
fun String.toAbsoluteMediaUrl(): String =
    BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.resolve(this)?.toString() ?: this
