package com.kma.quiz_game

import android.app.Application
import com.kma.quiz_game.data.local.AppDatabase
import com.kma.quiz_game.data.remote.BattleSocket
import com.kma.quiz_game.data.remote.DuoSocket
import com.kma.quiz_game.data.remote.FreshTokenProvider
import com.kma.quiz_game.data.remote.NetworkModule
import com.kma.quiz_game.data.remote.TokenStore
import com.kma.quiz_game.data.remote.api.AuthApi
import com.kma.quiz_game.data.remote.api.BattleApi
import com.kma.quiz_game.data.remote.api.ContentApi
import com.kma.quiz_game.data.remote.api.DuoApi
import com.kma.quiz_game.data.remote.api.GameApi
import com.kma.quiz_game.data.remote.api.ProgressApi
import com.kma.quiz_game.data.remote.api.UsersApi
import com.kma.quiz_game.data.repository.AuthRepository
import com.kma.quiz_game.data.repository.BattleRepository
import com.kma.quiz_game.data.repository.ChallengeRepository
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.data.repository.GameRepository
import com.kma.quiz_game.data.repository.LearnRepository
import com.kma.quiz_game.data.repository.ProfileRepository
import com.kma.quiz_game.data.repository.UserProgressRepository

class DuoGameApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    private val tokenStore: TokenStore by lazy { TokenStore(this) }

    // Unauthenticated -- carries no AuthInterceptor, so AuthApi.refresh() never recurses.
    private val authRetrofit by lazy { NetworkModule.buildAuthRetrofit() }
    private val authApi: AuthApi by lazy { authRetrofit.create(AuthApi::class.java) }

    private val authenticatedRetrofit by lazy {
        NetworkModule.buildAuthenticatedRetrofit(tokenStore) { authApi }
    }
    private val usersApi: UsersApi by lazy { authenticatedRetrofit.create(UsersApi::class.java) }
    private val contentApi: ContentApi by lazy { authenticatedRetrofit.create(ContentApi::class.java) }
    private val progressApi: ProgressApi by lazy { authenticatedRetrofit.create(ProgressApi::class.java) }

    val authRepository: AuthRepository by lazy { AuthRepository(authApi, usersApi, tokenStore) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(usersApi) }

    val learnRepository: LearnRepository by lazy {
        LearnRepository(contentApi, progressApi, database.courseContentDao())
    }

    val challengeRepository: ChallengeRepository by lazy { ChallengeRepository(contentApi, progressApi) }

    val userProgressRepository: UserProgressRepository by lazy {
        UserProgressRepository(database.userProgressDao())
    }

    private val gameApi: GameApi by lazy { authenticatedRetrofit.create(GameApi::class.java) }

    /**
     * Shared by both game modes, not just duo: a lesson battle and a duo match draw their skill
     * bar and their class stats from the same three endpoints.
     */
    val gameRepository: GameRepository by lazy { GameRepository(gameApi) }

    private val duoApi: DuoApi by lazy { authenticatedRetrofit.create(DuoApi::class.java) }

    /** Its own OkHttp client: a match socket must be allowed to sit idle far longer than the
     * 15-second read timeout the REST stack uses. */
    private val duoSocket: DuoSocket by lazy {
        val freshToken = FreshTokenProvider(usersApi, tokenStore)
        DuoSocket(
            client = NetworkModule.buildWebSocketClient(),
            json = NetworkModule.json,
            tokenProvider = freshToken::invoke,
        )
    }

    val duoRepository: DuoRepository by lazy {
        DuoRepository(duoApi, duoSocket, NetworkModule.json)
    }

    private val battleApi: BattleApi by lazy { authenticatedRetrofit.create(BattleApi::class.java) }

    /** A socket of its own, not duo's: two protocols, two engines on the server, and a battle
     * that must not be torn down by anything happening in a match. */
    private val battleSocket: BattleSocket by lazy {
        val freshToken = FreshTokenProvider(usersApi, tokenStore)
        BattleSocket(
            client = NetworkModule.buildWebSocketClient(),
            json = NetworkModule.json,
            tokenProvider = freshToken::invoke,
        )
    }

    val battleRepository: BattleRepository by lazy { BattleRepository(battleApi, battleSocket) }

    /**
     * Logging out revokes the refresh token, so the socket has to go first -- otherwise it keeps a
     * connection open on credentials the server has just thrown away, and reconnects on them.
     */
    suspend fun logout() {
        duoRepository.disconnect()
        battleRepository.disconnect()
        authRepository.logout()
        profileRepository.clear()
        gameRepository.clear()
    }
}
