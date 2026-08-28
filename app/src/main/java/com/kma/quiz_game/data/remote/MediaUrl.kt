package com.kma.quiz_game.data.remote

import com.kma.quiz_game.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
