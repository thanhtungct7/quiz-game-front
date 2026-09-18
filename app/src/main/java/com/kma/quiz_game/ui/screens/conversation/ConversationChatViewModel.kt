package com.kma.quiz_game.ui.screens.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.ConversationMessageDto
import com.kma.quiz_game.data.repository.ConversationRepository
import com.kma.quiz_game.data.speech.ListenError
import com.kma.quiz_game.data.speech.ListenEvent
import com.kma.quiz_game.data.speech.SpeakerState
import com.kma.quiz_game.data.speech.SpeechListener
import com.kma.quiz_game.data.speech.SpeechSpeaker
import com.kma.quiz_game.data.speech.speechRateFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * One practice conversation.
 *
 * The server stores a learner's line before it asks the AI for a reply, so a failed send does not
 * say whether the line arrived. Rather than guess, a failure re-reads the conversation: a line the
 * server holds without a reply can only be retried, and one it never got goes back into the input.
 * Only when the server cannot be reached at all is the line kept here as unsent.
 *
 * The AI's lines are read aloud as they arrive, and the learner may answer by voice: what the
 * recogniser hears is sent exactly as a typed line would be. The server only ever sees text.
 */
class ConversationChatViewModel(
    private val sessionId: String,
    private val repository: ConversationRepository,
    private val speaker: SpeechSpeaker,
    private val listener: SpeechListener,
    private val voicePreference: VoicePreference,
) : ViewModel() {

    /** Whether the AI speaks, remembered across conversations. */
    interface VoicePreference {
        val voiceOn: Flow<Boolean>
        suspend fun setVoiceOn(on: Boolean)
    }

    private val _uiState = MutableStateFlow(ConversationChatUiState())
    val uiState: StateFlow<ConversationChatUiState> = _uiState.asStateFlow()

    private var localIds = 0
    private var openingChecked = false

    init {
        _uiState.update { it.withVoice { copy(micAvailable = listener.isAvailable) } }
        viewModelScope.launch {
            voicePreference.voiceOn.collect { on ->
                _uiState.update { it.withVoice { copy(voiceOn = on) } }
                if (!on) speaker.stop()
            }
        }
        viewModelScope.launch {
            speaker.state.collect { state ->
                _uiState.update { it.withVoice { copy(canSpeak = state == SpeakerState.READY) } }
            }
        }
        viewModelScope.launch {
            speaker.speakingId.collect { id -> _uiState.update { it.withVoice { copy(speakingLineId = id) } } }
        }
        viewModelScope.launch { listener.events.collect(::onListenEvent) }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = it.scenario == null, loadErrorMessage = null) }
        viewModelScope.launch {
            repository.get(sessionId)
                .onSuccess { detail ->
                    _uiState.update { it.withDetail(detail) }
                    speaker.setRate(speechRateFor(detail.cefr))
                    // Only the first load of a conversation that has just begun: a reload after an
                    // error, or a conversation reopened from history, is not read out again.
                    if (!openingChecked) {
                        openingChecked = true
                        _uiState.value.openingLine?.let { speakAloud(it.id, it.content) }
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoading = false, loadErrorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value.take(MAX_MESSAGE_LENGTH)) }

    fun toggleBrief() = _uiState.update { it.copy(showBrief = !it.showBrief) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        sendLine(state.input.trim(), clearInput = true)
    }

    private fun sendLine(content: String, clearInput: Boolean) {
        val state = _uiState.value
        val localId = "local-${localIds++}"
        speaker.stop()
        _uiState.update {
            it.withLocalLine(content, localId).copy(
                input = if (clearInput) "" else it.input,
                isSending = true,
                errorMessage = null,
                showBrief = false,
            )
        }
        deliver(content, localId, turnsBefore = state.userTurns)
    }

    // --- voice ------------------------------------------------------------------------------------

    /** Play an AI line, or stop it if it is the one playing. Works while muted: the tap asks for it. */
    fun toggleSpeak(lineId: String) {
        val state = _uiState.value
        if (state.voice.speakingLineId == lineId) {
            speaker.stop()
            return
        }
        val line = state.lines.firstOrNull { it.id == lineId } ?: return
        if (state.voice.isListening) cancelListening()
        speaker.speak(line.id, line.content)
    }

    fun toggleVoice() {
        val on = !_uiState.value.voice.voiceOn
        _uiState.update { it.withVoice { copy(voiceOn = on) } }
        if (!on) speaker.stop()
        viewModelScope.launch { voicePreference.setVoiceOn(on) }
    }

    /** Call once the microphone permission is granted. */
    fun startListening() {
        val state = _uiState.value
        if (state.voice.isListening || !state.canListen) return
        // The mic would otherwise hear the AI's own voice and send that back.
        speaker.stop()
        _uiState.update {
            it.copy(errorMessage = null, hints = null).withVoice { copy(isListening = true, partialText = "", level = 0f) }
        }
        listener.start()
    }

    /** Stop early and send what was heard so far. */
    fun stopListening() {
        if (_uiState.value.voice.isListening) listener.stop()
    }

    /** Stop and send nothing. */
    fun cancelListening() {
        if (!_uiState.value.voice.isListening) return
        listener.cancel()
        _uiState.update { it.withVoice { copy(isListening = false, partialText = "", level = 0f) } }
    }

    fun onMicPermissionDenied() {
        _uiState.update { it.copy(errorMessage = ListenError.PERMISSION.message) }
    }

    /** The screen went to the background: nothing should keep talking or listening. */
    fun stopVoice() {
        cancelListening()
        speaker.stop()
    }

    private fun onListenEvent(event: ListenEvent) {
        // Anything arriving after a cancel belongs to a session nobody is waiting for.
        if (!_uiState.value.voice.isListening) return
        when (event) {
            ListenEvent.Ready -> Unit
            is ListenEvent.Level -> _uiState.update { it.withVoice { copy(level = event.value) } }
            is ListenEvent.Partial -> _uiState.update { it.withVoice { copy(partialText = event.text) } }
            is ListenEvent.Final -> {
                _uiState.update { it.withVoice { copy(isListening = false, partialText = "", level = 0f) } }
                val content = event.text.trim().take(MAX_MESSAGE_LENGTH)
                val state = _uiState.value
                when {
                    content.isEmpty() -> _uiState.update { it.copy(errorMessage = ListenError.NO_SPEECH.message) }
                    state.canType && !state.isSending -> sendLine(content, clearInput = false)
                }
            }
            is ListenEvent.Failed -> _uiState.update {
                it.copy(errorMessage = event.error.message)
                    .withVoice { copy(isListening = false, partialText = "", level = 0f) }
            }
        }
    }

    /** Read an AI line that has just appeared, unless the learner has muted the AI. */
    private fun speakAloud(id: String, text: String) {
        viewModelScope.launch {
            if (voicePreference.voiceOn.first()) speaker.speak(id, text)
        }
    }

    private fun speakReply(reply: ConversationMessageDto) = speakAloud(reply.id, reply.content)

    override fun onCleared() {
        listener.cancel()
        listener.release()
        speaker.release()
    }

    /** Get a reply for the line that is stuck, finding out first where it is stuck if that is unknown. */
    fun retry() {
        val state = _uiState.value
        if (!state.needsRetry) return
        _uiState.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            val unsent = state.unsentContent
            if (unsent == null) {
                requestRetry()
                return@launch
            }
            repository.get(sessionId)
                .onSuccess { detail ->
                    val arrived = detail.userTurns > state.userTurns
                    val localId = state.lines.lastOrNull { it.isLocal }?.id
                    when {
                        arrived && detail.awaitingReply -> {
                            _uiState.update { it.withDetail(detail).copy(isSending = true) }
                            requestRetry()
                        }
                        // It arrived and was answered; only the response was lost.
                        arrived -> _uiState.update { it.withDetail(detail).copy(isSending = false) }
                        else -> {
                            _uiState.update {
                                it.withDetail(detail).withLocalLine(unsent, localId ?: "local-${localIds++}")
                                    .copy(isSending = true)
                            }
                            deliver(unsent, _uiState.value.lines.last().id, turnsBefore = detail.userTurns)
                        }
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isSending = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    /** Show or hide an AI line's Vietnamese, fetching it the first time. */
    fun toggleTranslation(lineId: String) {
        val state = _uiState.value
        val line = state.lines.firstOrNull { it.id == lineId } ?: return
        if (line.isLocal || lineId in state.translatingIds) return
        when {
            lineId in state.shownTranslationIds ->
                _uiState.update { it.copy(shownTranslationIds = it.shownTranslationIds - lineId) }
            line.translationVi != null ->
                _uiState.update { it.copy(shownTranslationIds = it.shownTranslationIds + lineId) }
            else -> {
                _uiState.update { it.copy(translatingIds = it.translatingIds + lineId, errorMessage = null) }
                viewModelScope.launch {
                    repository.translate(sessionId, lineId)
                        .onSuccess { result ->
                            _uiState.update { it.withTranslation(lineId, result.translationVi) }
                        }
                        .onFailure { cause ->
                            _uiState.update {
                                it.copy(
                                    translatingIds = it.translatingIds - lineId,
                                    errorMessage = cause.toConversationMessage(),
                                )
                            }
                        }
                }
            }
        }
    }

    fun requestHints() {
        if (!_uiState.value.canAskHint) return
        _uiState.update { it.copy(isLoadingHints = true, errorMessage = null) }
        viewModelScope.launch {
            repository.hint(sessionId)
                .onSuccess { result -> _uiState.update { it.copy(isLoadingHints = false, hints = result.suggestions) } }
                .onFailure { cause ->
                    _uiState.update { it.copy(isLoadingHints = false, errorMessage = cause.toConversationMessage()) }
                    if (cause.isConflict()) load()
                }
        }
    }

    fun pickHint(text: String) = _uiState.update { it.copy(input = text.take(MAX_MESSAGE_LENGTH), hints = null) }

    fun dismissHints() = _uiState.update { it.copy(hints = null) }

    /** From the top bar: once the conversation is over, straight to feedback; before that, ask first. */
    fun onFinishClick() {
        val state = _uiState.value
        if (!state.canFinish) return
        if (state.isClosed) finish() else _uiState.update { it.copy(showFinishDialog = true) }
    }

    fun dismissFinishDialog() = _uiState.update { it.copy(showFinishDialog = false) }

    fun finish() {
        if (!_uiState.value.canFinish) return
        _uiState.update { it.copy(showFinishDialog = false, isFinishing = true, errorMessage = null) }
        viewModelScope.launch {
            repository.finish(sessionId)
                .onSuccess { detail ->
                    _uiState.update { it.withDetail(detail).copy(isFinishing = false, finishedSessionId = detail.id) }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isFinishing = false, errorMessage = cause.toConversationMessage()) }
                }
        }
    }

    fun consumeFinished() = _uiState.update { it.copy(finishedSessionId = null) }

    private fun deliver(content: String, localId: String, turnsBefore: Int) {
        viewModelScope.launch {
            repository.send(sessionId, content)
                .onSuccess { turn ->
                    _uiState.update { it.withTurn(turn, localId).copy(isSending = false) }
                    speakReply(turn.assistantMessage)
                }
                .onFailure { cause -> recoverFromFailedSend(cause, content, turnsBefore) }
        }
    }

    private suspend fun recoverFromFailedSend(cause: Throwable, content: String, turnsBefore: Int) {
        val message = cause.toConversationMessage()
        repository.get(sessionId)
            .onSuccess { detail ->
                val arrived = detail.userTurns > turnsBefore
                _uiState.update {
                    it.withDetail(detail).copy(
                        isSending = false,
                        errorMessage = message,
                        // Never got there: give the learner their words back instead of losing them.
                        input = if (arrived) it.input else content,
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(isSending = false, unsentContent = content, errorMessage = message) }
            }
    }

    private suspend fun requestRetry() {
        repository.retry(sessionId)
            .onSuccess { turn ->
                _uiState.update { it.withTurn(turn).copy(isSending = false) }
                speakReply(turn.assistantMessage)
            }
            .onFailure { cause ->
                _uiState.update { it.copy(isSending = false, errorMessage = cause.toConversationMessage()) }
                if (cause.isConflict()) load()
            }
    }
}

private fun Throwable.isConflict() = this is HttpException && code() == 409
