package com.kma.quiz_game.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What one listening session reports. Every session that starts ends in [Final] or [Failed]. */
sealed interface ListenEvent {
    data object Ready : ListenEvent

    /** Loudness, 0..1, for the ring around the mic. */
    data class Level(val value: Float) : ListenEvent

    data class Partial(val text: String) : ListenEvent

    data class Final(val text: String) : ListenEvent

    data class Failed(val error: ListenError) : ListenEvent
}

enum class ListenError(val message: String) {
    NO_SPEECH("Chưa nghe rõ, bạn thử nói lại nhé."),
    NETWORK("Không kết nối được dịch vụ nhận giọng nói. Hãy kiểm tra mạng hoặc gõ câu trả lời."),
    PERMISSION("Ứng dụng cần quyền micro để nghe bạn nói."),
    BUSY("Micro đang bận, bạn thử lại sau giây lát."),
    LANGUAGE("Máy chưa hỗ trợ nhận giọng nói tiếng Anh. Bạn hãy gõ câu trả lời."),
    OTHER("Không nhận được giọng nói, bạn thử lại nhé."),
}

fun listenErrorFor(code: Int): ListenError = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.NO_SPEECH
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> ListenError.NETWORK
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.PERMISSION
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> ListenError.BUSY
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> ListenError.LANGUAGE
    else -> ListenError.OTHER
}

/** Turns speech into text. An interface so the chat view model can be tested without a microphone. */
interface SpeechListener {
    val isAvailable: Boolean
    val events: SharedFlow<ListenEvent>

    fun start(languageTag: String = "en-US")

    /** Stops listening and keeps what was heard: a [ListenEvent.Final] still follows. */
    fun stop()

    /** Stops listening and throws away what was heard. Nothing follows. */
    fun cancel()

    fun release()
}

/**
 * [SpeechListener] on the phone's own recognition service. Must be used from the main thread,
 * which is where `SpeechRecognizer` insists on being created and called.
 */
class AndroidSpeechListener(context: Context) : SpeechListener {

    private val appContext = context.applicationContext

    override val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val _events = MutableSharedFlow<ListenEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ListenEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(ListenEvent.Ready)
        }

        // Roughly -2 dB in silence to 10 dB for a raised voice.
        override fun onRmsChanged(rmsdB: Float) {
            _events.tryEmit(ListenEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { _events.tryEmit(ListenEvent.Partial(it)) }
        }

        override fun onResults(results: Bundle?) {
            _events.tryEmit(ListenEvent.Final(firstResult(results).orEmpty()))
        }

        override fun onError(error: Int) {
            _events.tryEmit(ListenEvent.Failed(listenErrorFor(error)))
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun start(languageTag: String) {
        if (!isAvailable) {
            _events.tryEmit(ListenEvent.Failed(ListenError.LANGUAGE))
            return
        }
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(callbacks)
            recognizer = it
        }
        active.cancel()
        active.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
}
