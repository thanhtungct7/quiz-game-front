package com.kma.quiz_game.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeakerState { INITIALIZING, READY, UNAVAILABLE }

/**
 * Reads English lines aloud. An interface so the chat screen's view model can be tested without a
 * speech engine behind it.
 */
interface SpeechSpeaker {
    val state: StateFlow<SpeakerState>

    /** The id passed to [speak] for the line being read right now, or null when silent. */
    val speakingId: StateFlow<String?>

    /** Reads [text], cutting off whatever was being read. */
    fun speak(id: String, text: String)

    fun stop()

    fun setRate(rate: Float)

    fun release()
}

/**
 * [SpeechSpeaker] on the phone's own text-to-speech engine, always in US English: left to itself
 * the engine speaks in the phone's language, and a Vietnamese voice reading an English line is
 * worse than no voice at all.
 */
class AndroidSpeechSpeaker(context: Context) : SpeechSpeaker {

    private val _state = MutableStateFlow(SpeakerState.INITIALIZING)
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private val _speakingId = MutableStateFlow<String?>(null)
    override val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    // Each utterance gets its own id, so a late "stopped" for a line that was cut off cannot clear
    // the line that replaced it -- even when both are the same bubble, played twice.
    @Volatile private var currentUtterance: String? = null
    private var utterances = 0
    private var pending: Pair<String, String>? = null
    private var rate = 1f
    private var released = false

    private lateinit var tts: TextToSpeech

    init {
        // The engine can report failure from inside the constructor, before `tts` is assigned.
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        if (released) return
        if (status != TextToSpeech.SUCCESS || !::tts.isInitialized) {
            _state.value = SpeakerState.UNAVAILABLE
            return
        }
        val language = tts.setLanguage(Locale.US)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.value = SpeakerState.UNAVAILABLE
            return
        }
        tts.setSpeechRate(rate)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
        })
        _state.value = SpeakerState.READY
        pending?.let { (id, text) -> speak(id, text) }
        pending = null
    }

    private fun finished(utteranceId: String) {
        if (utteranceId == currentUtterance) {
            currentUtterance = null
            _speakingId.value = null
        }
    }

    override fun speak(id: String, text: String) {
        if (released || text.isBlank()) return
        when (_state.value) {
            SpeakerState.INITIALIZING -> pending = id to text
            SpeakerState.UNAVAILABLE -> Unit
            SpeakerState.READY -> {
                val utterance = "$id#${utterances++}"
                currentUtterance = utterance
                _speakingId.value = id
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance) != TextToSpeech.SUCCESS) {
                    finished(utterance)
                }
            }
        }
    }

    override fun stop() {
        pending = null
        currentUtterance = null
        _speakingId.value = null
        if (_state.value == SpeakerState.READY) tts.stop()
    }

    override fun setRate(rate: Float) {
        this.rate = rate
        if (_state.value == SpeakerState.READY) tts.setSpeechRate(rate)
    }

    override fun release() {
        if (released) return
        released = true
        stop()
        if (::tts.isInitialized) tts.shutdown()
    }
}

/** Slower for beginners: an A1 learner cannot follow a line read at full speed. */
fun speechRateFor(cefr: String): Float = when (cefr.uppercase()) {
    "A1", "A2" -> 0.85f
    "B1" -> 0.95f
    else -> 1f
}
