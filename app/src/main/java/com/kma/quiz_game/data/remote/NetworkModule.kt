package com.kma.quiz_game.data.remote

import com.kma.quiz_game.BuildConfig
import com.kma.quiz_game.data.remote.api.AuthApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit/OkHttp stack. The backend's JSON is snake_case; matching it via a single
 * app-wide [JsonNamingStrategy.SnakeCase] avoids hand-annotating every DTO field. */
object NetworkModule {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** No AuthInterceptor -- used only to build [AuthApi], so a refresh call never recurses. */
    fun buildAuthRetrofit(): Retrofit = retrofit(baseClientBuilder().build())

    /** Carries the AuthInterceptor -- used for every endpoint other than AuthApi. */
    fun buildAuthenticatedRetrofit(tokenStore: TokenStore, authApiProvider: () -> AuthApi): Retrofit {
        val client = baseClientBuilder()
            .addInterceptor(AuthInterceptor(tokenStore, authApiProvider))
            .build()
        return retrofit(client)
    }

    /**
     * A separate client for the duo match socket.
     *
     * The 15-second read timeout above would kill a socket that is merely idle -- waiting in the
     * matchmaking queue can legitimately take up to 300 seconds -- so reads never time out here,
     * and OkHttp's own pings detect a genuinely dead connection instead.
     *
     * It also carries no AuthInterceptor: a WebSocket handshake authenticates through the `token`
     * query parameter, not an `Authorization` header.
     */
    fun buildWebSocketClient(): OkHttpClient = baseClientBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
}
