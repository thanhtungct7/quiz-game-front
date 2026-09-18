package com.kma.quiz_game.ui.screens.conversation

import com.kma.quiz_game.data.remote.api.ConversationApi
import com.kma.quiz_game.data.remote.dto.ConversationDetailDto
import com.kma.quiz_game.data.remote.dto.ConversationHintDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageDto
import com.kma.quiz_game.data.remote.dto.ConversationMessageRequest
import com.kma.quiz_game.data.remote.dto.ConversationMessageRole
import com.kma.quiz_game.data.remote.dto.ConversationStartRequest
import com.kma.quiz_game.data.remote.dto.ConversationStatus
import com.kma.quiz_game.data.remote.dto.ConversationSummaryDto
import com.kma.quiz_game.data.remote.dto.ConversationTranslationDto
import com.kma.quiz_game.data.remote.dto.ConversationTurnDto
import com.kma.quiz_game.data.remote.dto.ScenarioDto
import com.kma.quiz_game.data.repository.ConversationRepository
import com.kma.quiz_game.data.speech.ListenError
import com.kma.quiz_game.data.speech.ListenEvent
import com.kma.quiz_game.data.speech.SpeakerState
import com.kma.quiz_game.data.speech.SpeechListener
import com.kma.quiz_game.data.speech.SpeechSpeaker
import com.kma.quiz_game.data.speech.listenErrorFor
import com.kma.quiz_game.data.speech.speechRateFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/** The spoken side of the chat: the AI reading its lines, and a spoken answer sent as text. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationChatVoiceTest {

    private class FakeSpeaker : SpeechSpeaker {
        override val state = MutableStateFlow(SpeakerState.READY)
        override val speakingId = MutableStateFlow<String?>(null)
        val spoken = mutableListOf<Pair<String, String>>()
        var stops = 0
        var released = false
        var lastRate = 1f

        override fun speak(id: String, text: String) {
            spoken += id to text
            speakingId.value = id
        }

        override fun stop() {
            stops++
            speakingId.value = null
        }

        override fun setRate(rate: Float) {
            lastRate = rate
        }

        override fun release() {
            released = true
        }
    }

    private class FakeListener : SpeechListener {
        override val isAvailable = true
        override val events = MutableSharedFlow<ListenEvent>(extraBufferCapacity = 16)
        var starts = 0
        var stops = 0
        var cancels = 0

        override fun start(languageTag: String) {
            starts++
        }

        override fun stop() {
            stops++
        }

        override fun cancel() {
            cancels++
        }

        override fun release() = Unit

        fun emit(event: ListenEvent) = check(events.tryEmit(event))
    }

    private class FakePreference(on: Boolean) : ConversationChatViewModel.VoicePreference {
        override val voiceOn = MutableStateFlow(on)
        override suspend fun setVoiceOn(on: Boolean) {
            voiceOn.value = on
        }
    }

    private val scenario = ScenarioDto(
        code = "cafe_order",
        titleVi = "Gọi đồ ở quán cà phê",
        categoryVi = "Ăn uống",
        setting = "A cafe.",
        aiRole = "a barista",
        userRole = "a customer",
        maxTurns = 12,
    )

    private fun message(id: String, seq: Int, role: ConversationMessageRole, content: String) =
        ConversationMessageDto(id = id, seq = seq, role = role, content = content, createdAt = "2026-09-17T04:00:00Z")

    private val opening = message("m1", 1, ConversationMessageRole.ASSISTANT, "Hi! What can I get you?")

    private inner class FakeApi(var detail: ConversationDetailDto) : ConversationApi {
        val sent = mutableListOf<String>()

        override suspend fun get(sessionId: String) = detail

        override suspend fun send(sessionId: String, body: ConversationMessageRequest): ConversationTurnDto {
            sent += body.content
            val seq = detail.messages.size
            return ConversationTurnDto(
                userMessage = message("u$seq", seq + 1, ConversationMessageRole.USER, body.content),
                assistantMessage = message("a$seq", seq + 2, ConversationMessageRole.ASSISTANT, "Sure, anything else?"),
                userTurns = detail.userTurns + 1,
                maxTurns = 12,
                shouldFinish = false,
            )
        }

        override suspend fun listScenarios(): List<ScenarioDto> = error("unused")
        override suspend fun start(body: ConversationStartRequest): ConversationDetailDto = error("unused")
        override suspend fun list(limit: Int, before: String?): List<ConversationSummaryDto> = error("unused")
        override suspend fun retry(sessionId: String): ConversationTurnDto = error("unused")
        override suspend fun hint(sessionId: String): ConversationHintDto = error("unused")
        override suspend fun translate(sessionId: String, messageId: String): ConversationTranslationDto = error("unused")
        override suspend fun finish(sessionId: String): ConversationDetailDto = error("unused")
        override suspend fun delete(sessionId: String): Response<Unit> = error("unused")
    }

    private fun detail(messages: List<ConversationMessageDto>, userTurns: Int = 0, cefr: String = "A2") =
        ConversationDetailDto(
            id = "s1",
            scenario = scenario,
            cefr = cefr,
            status = ConversationStatus.ACTIVE,
            userTurns = userTurns,
            maxTurns = 12,
            shouldFinish = false,
            awaitingReply = false,
            messages = messages,
            startedAt = "2026-09-17T04:00:00Z",
        )

    private val speaker = FakeSpeaker()
    private val listener = FakeListener()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        detail: ConversationDetailDto = detail(listOf(opening)),
        voiceOn: Boolean = true,
    ): Pair<ConversationChatViewModel, FakeApi> {
        val api = FakeApi(detail)
        val vm = ConversationChatViewModel("s1", ConversationRepository(api), speaker, listener, FakePreference(voiceOn))
        return vm to api
    }

    private val StateFlow<ConversationChatUiState>.now get() = value

    @Test
    fun `a conversation that has just begun reads its opening line, at the learner's pace`() {
        viewModel()
        assertEquals(listOf("m1" to "Hi! What can I get you?"), speaker.spoken)
        assertEquals(0.85f, speaker.lastRate)
    }

    @Test
    fun `a conversation reopened later is not read out again`() {
        viewModel(
            detail(
                listOf(
                    opening,
                    message("m2", 2, ConversationMessageRole.USER, "A latte"),
                    message("m3", 3, ConversationMessageRole.ASSISTANT, "Sure!"),
                ),
                userTurns = 1,
            ),
        )
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `the reply is read aloud as it arrives`() {
        val (vm, _) = viewModel()
        vm.onInputChange("A latte, please.")
        vm.send()
        assertEquals("a1" to "Sure, anything else?", speaker.spoken.last())
    }

    @Test
    fun `a muted AI only writes`() {
        val (vm, _) = viewModel(voiceOn = false)
        vm.onInputChange("A latte, please.")
        vm.send()
        assertTrue(speaker.spoken.isEmpty())
        assertEquals(2, vm.uiState.now.lines.count { it.role == ConversationMessageRole.ASSISTANT })
    }

    @Test
    fun `muting stops the line being read`() {
        val (vm, _) = viewModel()
        val before = speaker.stops
        vm.toggleVoice()
        assertFalse(vm.uiState.now.voice.voiceOn)
        assertTrue(speaker.stops > before)
    }

    @Test
    fun `what is heard is sent as a line, and the draft is left alone`() {
        val (vm, api) = viewModel()
        vm.onInputChange("")
        vm.startListening()
        assertTrue(vm.uiState.now.voice.isListening)
        assertEquals(1, listener.starts)

        listener.emit(ListenEvent.Partial("Can I have"))
        assertEquals("Can I have", vm.uiState.now.voice.partialText)

        listener.emit(ListenEvent.Final("Can I have a latte"))
        assertEquals(listOf("Can I have a latte"), api.sent)
        assertFalse(vm.uiState.now.voice.isListening)
        assertEquals("Can I have a latte", vm.uiState.now.lines.first { it.role == ConversationMessageRole.USER }.content)
        assertEquals("a1" to "Sure, anything else?", speaker.spoken.last())
    }

    @Test
    fun `listening silences the AI first, so the mic does not hear it`() {
        val (vm, _) = viewModel()
        val before = speaker.stops
        vm.startListening()
        assertTrue(speaker.stops > before)
    }

    @Test
    fun `nothing heard sends nothing and says so`() {
        val (vm, api) = viewModel()
        vm.startListening()
        listener.emit(ListenEvent.Final("  "))
        assertTrue(api.sent.isEmpty())
        assertEquals(ListenError.NO_SPEECH.message, vm.uiState.now.errorMessage)
    }

    @Test
    fun `a recogniser error sends nothing`() {
        val (vm, api) = viewModel()
        vm.startListening()
        listener.emit(ListenEvent.Failed(ListenError.NETWORK))
        assertTrue(api.sent.isEmpty())
        assertFalse(vm.uiState.now.voice.isListening)
        assertEquals(ListenError.NETWORK.message, vm.uiState.now.errorMessage)
    }

    @Test
    fun `a result arriving after a cancel is dropped`() {
        val (vm, api) = viewModel()
        vm.startListening()
        vm.cancelListening()
        assertEquals(1, listener.cancels)
        listener.emit(ListenEvent.Final("Can I have a latte"))
        assertTrue(api.sent.isEmpty())
    }

    @Test
    fun `the mic is shut while a line is on its way or the conversation is over`() {
        val (vm, _) = viewModel()
        assertTrue(vm.uiState.now.canListen)
        assertFalse(vm.uiState.now.copy(isSending = true).canListen)
        assertFalse(vm.uiState.now.copy(shouldFinish = true).canListen)
        assertFalse(vm.uiState.now.copy(awaitingReply = true).canListen)
    }

    @Test
    fun `tapping the playing line stops it, tapping another plays that one`() {
        val (vm, _) = viewModel()
        assertEquals("m1", vm.uiState.now.voice.speakingLineId)
        vm.toggleSpeak("m1")
        assertNull(vm.uiState.now.voice.speakingLineId)
        vm.toggleSpeak("m1")
        assertEquals("m1", vm.uiState.now.voice.speakingLineId)
    }

    @Test
    fun `leaving the screen stops talking and listening`() {
        val (vm, _) = viewModel()
        vm.startListening()
        vm.stopVoice()
        assertFalse(vm.uiState.now.voice.isListening)
        assertEquals(1, listener.cancels)
        assertNull(speaker.speakingId.value)
    }

    @Test
    fun `recogniser error codes map to what the learner is told`() {
        assertEquals(ListenError.NO_SPEECH, listenErrorFor(android.speech.SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(ListenError.NO_SPEECH, listenErrorFor(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(ListenError.NETWORK, listenErrorFor(android.speech.SpeechRecognizer.ERROR_NETWORK))
        assertEquals(ListenError.PERMISSION, listenErrorFor(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(ListenError.BUSY, listenErrorFor(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(ListenError.LANGUAGE, listenErrorFor(android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertEquals(ListenError.OTHER, listenErrorFor(-1))
    }

    @Test
    fun `beginners hear it slower`() {
        assertEquals(0.85f, speechRateFor("A1"))
        assertEquals(0.95f, speechRateFor("B1"))
        assertEquals(1f, speechRateFor("C1"))
    }
}
