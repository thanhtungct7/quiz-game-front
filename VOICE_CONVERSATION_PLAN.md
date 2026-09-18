# VOICE CONVERSATION PLAN — Luyện hội thoại AI bằng giọng nói

> Plan ngày 2026-09-17. Chi tiết hoá **Giai đoạn 3** trong `AI_CONVERSATION_PLAN.md` §5.
> Phạm vi: màn chat hội thoại AI (`ui/screens/conversation/`). **Backend không đổi**: vẫn gửi/nhận
> chữ qua `/api/v1/conversations`, giọng nói chỉ nằm trên máy.

## 1. Mục tiêu

> Cập nhật 2026-09-17 theo yêu cầu: AI **vừa viết vừa nói**; người dùng **nói hoặc gõ**, nói thì
> chuyển thành chữ và **gửi thẳng** lên backend như một tin nhắn gõ tay.

- **AI viết + nói (TTS)**: mỗi câu AI hiện ra (cả câu mở đầu của phiên mới) thì máy đọc câu đó
  bằng giọng tiếng Anh cùng lúc; bong bóng AI có nút loa để nghe lại.
- **Người dùng nói (STT)**: bấm mic, nói tiếng Anh → chữ nhận được hiện thành bong bóng của người
  dùng và **gửi luôn** lên backend, không phải bấm Gửi.
- **Người dùng gõ**: giữ nguyên như hiện tại.
- Không làm ở bản này: chấm phát âm, voice realtime (streaming audio), đọc tiếng Việt, lưu file âm thanh.

## 2. Công nghệ

| Việc | API Android | Ghi chú |
|---|---|---|
| Đọc câu AI | `android.speech.tts.TextToSpeech`, `Locale.US` | Có sẵn, miễn phí, chạy offline nếu máy có gói giọng. |
| Nhận giọng | `android.speech.SpeechRecognizer` + `RecognizerIntent` (`en-US`, partial results) | Dùng dịch vụ nhận giọng của máy (Google). |

- `minSdk = 33`, `targetSdk = 36`: do package visibility, **phải khai báo `<queries>`** cho
  `android.intent.action.TTS_SERVICE` và `android.speech.RecognitionService`, không thì
  `isRecognitionAvailable()` trả false dù máy có dịch vụ.
- Emulator hiện tại (API 37) đã có `com.google.android.tts` và dịch vụ nhận giọng
  `com.google.android.as` → test được trên emulator (bật *Extended controls → Microphone → Virtual
  microphone uses host audio input*).

## 3. Trải nghiệm người dùng

1. Mở phiên **mới** → câu mở đầu của AI hiện ra và được đọc luôn. Mở lại phiên cũ → không đọc
   lại lịch sử.
2. AI trả lời → bong bóng hiện chữ **và** máy đọc ngay. Bong bóng đang đọc có biểu tượng loa động.
3. Mỗi bong bóng AI có nút 🔊: bấm để nghe lại, bấm lần nữa khi đang đọc thì dừng.
4. TopBar có nút 🔊/🔇 **tắt tiếng AI** (cho lúc ở nơi công cộng; lưu lại cho lần sau, mặc định
   **bật tiếng**). Tắt tiếng thì AI chỉ viết.
5. Thanh nhập: `[💡] [ô nhập] [🎤 hoặc Gửi]`.
   - Ô nhập trống → nút lớn bên phải là 🎤; gõ chữ vào → nút đổi thành **Gửi** (kiểu Messenger/Zalo).
   - **Bấm** 🎤 (không phải giữ): nút đổi màu + vòng sóng theo âm lượng, một bong bóng tạm phía
     người dùng hiện chữ đang nhận dạng (partial) màu nhạt.
   - Người dùng ngừng nói → tự dừng; bấm 🎤 lần nữa để dừng sớm; nút ✕ cạnh bong bóng tạm để huỷ.
   - Nhận xong câu cuối → **gửi luôn** bằng đúng luồng `send()` hiện có (optimistic, lỗi thì
     "Gửi lại"). Backend nhận chữ như tin gõ tay.
   - Không nhận được gì / lỗi → không gửi, báo lỗi, người dùng nói lại hoặc gõ.
   - Chọn bấm thay vì giữ: SpeechRecognizer tự phát hiện im lặng, và giữ tay khi nói câu dài
     dễ tuột.
   - Muốn sửa câu nói trước khi gửi thì dùng gõ; bản này không có bước duyệt để hội thoại liền mạch.
6. Bắt đầu nghe thì **dừng đọc TTS** ngay, để mic không thu lại giọng của máy.
7. Lần đầu bấm 🎤: hộp thoại giải thích (vì sao cần mic) → hộp xin quyền hệ thống. Từ chối vĩnh
   viễn → thông báo kèm nút "Mở cài đặt".
8. Máy không có dịch vụ nhận giọng → ẩn 🎤 (luôn là nút Gửi). Không có giọng tiếng Anh cho TTS → ẩn 🔊 và công tắc,
   hiện một lần "Máy chưa có giọng đọc tiếng Anh".
9. Rời màn chat / app xuống nền → dừng đọc và dừng nghe.

## 4. Thiết kế code

### 4.1 Tầng speech (mới) — `data/speech/`

Hai interface để ViewModel test được bằng fake:

```kotlin
interface SpeechSpeaker {
    val state: StateFlow<SpeakerState>          // Initializing / Ready / Unavailable
    val speakingId: StateFlow<String?>          // id bong bóng đang đọc
    fun speak(id: String, text: String)         // QUEUE_FLUSH: câu mới cắt câu cũ
    fun stop()
    fun setRate(rate: Float)
    fun release()
}

interface SpeechListener {
    val isAvailable: Boolean
    val events: Flow<ListenEvent>               // Ready, Level(rms), Partial(text), Final(text), Error(kind), End
    fun start(languageTag: String = "en-US")
    fun stop()                                  // stopListening: trả kết quả đang có
    fun cancel()
    fun release()
}
```

- `AndroidSpeechSpeaker(context)`: bọc `TextToSpeech`; `setLanguage(Locale.US)` trả
  `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED` → `Unavailable`. `UtteranceProgressListener` cập nhật
  `speakingId` (callback chạy ở thread khác → chỉ ghi vào `MutableStateFlow`). Gọi `speak()` trước khi
  init xong thì giữ câu cuối, đọc khi `Ready`.
- `AndroidSpeechListener(context)`: bọc `SpeechRecognizer` (tạo và gọi **trên main thread**),
  `callbackFlow`/`MutableSharedFlow` từ `RecognitionListener`. Extras: `LANGUAGE_MODEL_FREE_FORM`,
  `EXTRA_LANGUAGE="en-US"`, `EXTRA_PARTIAL_RESULTS=true`, `EXTRA_MAX_RESULTS=1`.
- Map lỗi (`ListenErrorKind`): `NO_MATCH`/`SPEECH_TIMEOUT` → "Chưa nghe rõ, bạn thử nói lại nhé";
  `NETWORK`/`NETWORK_TIMEOUT`/`SERVER` → "Không kết nối được dịch vụ nhận giọng";
  `INSUFFICIENT_PERMISSIONS` → xin lại quyền; `RECOGNIZER_BUSY` → cancel rồi thử lại;
  `LANGUAGE_UNAVAILABLE` → "Máy chưa hỗ trợ nhận giọng tiếng Anh".

### 4.2 Cài đặt — `SettingsStore`

- Thêm key `conversation_voice_on` (Boolean, mặc định `true`), `Flow<Boolean>` + setter, cùng
  pattern `themeMode`.

### 4.3 ViewModel — `ConversationChatViewModel`

- Constructor thêm `speaker: SpeechSpeaker`, `listener: SpeechListener`, `settings: SettingsStore`
  (factory trong `ConversationChatScreen` tạo bản Android từ `app`).
- State mới gom trong `ConversationChatUiState`:

```kotlin
data class VoiceUiState(
    val canSpeak: Boolean = false,       // TTS Ready
    val voiceOn: Boolean = true,         // AI có đọc thành tiếng không
    val speakingLineId: String? = null,
    val canListen: Boolean = false,      // listener.isAvailable
    val isListening: Boolean = false,
    val partialText: String = "",
    val level: Float = 0f,               // 0..1 cho vòng sóng
    val needsMicPermission: Boolean = false,
)
```

- Hàm mới: `toggleSpeak(lineId)`, `toggleVoice()`, `onMicClick(hasPermission)`, `cancelListening()`,
  `onMicPermissionResult(granted)`, `stopVoice()` (gọi khi ON_STOP).
- Đọc câu AI: trong nhánh `onSuccess` của `deliver`/`requestRetry`, sau `withTurn`, nếu `voiceOn` thì
  `speaker.speak(reply.id, reply.content)`.
- Câu mở đầu: lần `load()` **đầu tiên** mà phiên chưa có lượt nào của người dùng (`userTurns == 0`)
  và chỉ có 1 tin AI → đọc tin đó. Các lần `load()` khác (mở lại phiên, reload sau lỗi 409) không đọc.
- `send()` tách phần lõi thành `sendText(content: String)`; nút Gửi gọi với `input`, còn
  `Final(text)` gọi `sendText(text.trim().take(MAX_MESSAGE_LENGTH))` nếu `text` không rỗng và
  `canSend` về mặt trạng thái (không đang gửi, phiên chưa đóng). Ô nhập **không bị xoá** khi gửi
  bằng giọng (người dùng có thể đang gõ dở).
- Bắt đầu nghe → `speaker.stop()`. Đang gửi (`isSending`) hoặc phiên đã đóng → khoá 🎤.
- Tốc độ đọc theo CEFR: A1–A2 `0.85f`, B1 `0.95f`, còn lại `1.0f`.
- `onCleared()`: `listener.release()`, `speaker.release()`.

### 4.4 UI — `ConversationChatScreen`

- Bong bóng tạm `ListeningBubble` (phía người dùng) khi `isListening`: `partialText` màu nhạt +
  nút ✕ huỷ.
- `ChatBubble` (AI): thêm `SpeakerButton` (đã có trong `ui/components/OptionMedia.kt`) ở góc
  bong bóng; icon đổi sang `Stop` khi `speakingLineId == line.id`.
- TopBar: icon `VolumeUp`/`VolumeOff` cho công tắc tiếng AI.
- `InputBar`: nút bên phải là `MicButton` khi `input.isBlank() && canListen`, ngược lại là nút Gửi;
  khi `isListening` nút mic có vòng tròn scale theo `level`.
- Quyền mic: `rememberLauncherForActivityResult(RequestPermission())`, kiểm tra bằng
  `ContextCompat.checkSelfPermission`, `shouldShowRequestPermissionRationale` qua `Activity`;
  hộp thoại giải thích dùng `BaseMascotDialog` như `NotificationPermissionRequest`.
- `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopVoice() }`.

### 4.5 Manifest

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<queries>
    <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    <intent><action android:name="android.speech.RecognitionService" /></intent>
</queries>
```

### 4.6 (Tuỳ chọn) Màn nhận xét

- Nút 🔊 cạnh câu đã sửa đúng và câu "Nói tự nhiên hơn" trong `ConversationFeedbackScreen`, dùng
  lại `AndroidSpeechSpeaker` để người học nghe cách nói đúng.

## 5. Test

- Unit (`app/src/test`, fake speaker/listener):
  - AI trả lời + `voiceOn=true` → `speak` được gọi đúng id/nội dung; `voiceOn=false` → không gọi.
  - Phiên mới (0 lượt) → đọc câu mở đầu; mở lại phiên đã có lượt → không đọc gì.
  - `Partial` cập nhật `partialText`; `Final("Can I have a latte")` → repository `send` được gọi với
    đúng chữ đó, bong bóng optimistic xuất hiện, ô nhập giữ nguyên.
  - `Final("")` hoặc `Error` → không gửi. `cancelListening()` rồi có `Final` muộn → không gửi.
  - Đang `isSending` thì `onMicClick` không làm gì.
  - Bắt đầu nghe → `speaker.stop()`; `onCleared` → release cả hai.
  - Map lỗi `SpeechRecognizer.ERROR_*` → thông báo đúng.
  - Tốc độ đọc theo CEFR.
- Trên emulator/máy thật:
  1. Mở phiên mới "Gọi đồ ở quán cà phê" → câu mở đầu hiện ra và được đọc; gõ 1 câu → câu trả lời
     hiện ra và được đọc.
  2. Bấm 🔊 trên câu cũ → đọc; bấm lại → dừng.
  3. Tắt tiếng AI → thoát → vào lại vẫn tắt, AI chỉ viết.
  4. Bấm 🎤 lần đầu → giải thích → cấp quyền → nói "Can I have a latte, please?" → bong bóng người
     dùng hiện đúng câu, AI trả lời và đọc câu trả lời.
  5. Từ chối quyền 2 lần → có nút mở cài đặt.
  6. Đang đọc thì bấm 🎤 → máy im ngay.
  7. Về màn hình chính khi đang đọc/nghe → dừng hết.
  8. Tắt mạng → nói → báo lỗi dịch vụ nhận giọng, app không crash.

## 6. Thứ tự triển khai

| Bước | Việc | Xong khi |
|---|---|---|
| 1 | Manifest (`RECORD_AUDIO`, `<queries>`) + `SpeechSpeaker` + `AndroidSpeechSpeaker` | Gọi `speak` từ một nút tạm là nghe được tiếng |
| 2 | `conversation_voice_on` trong `SettingsStore` | Giá trị giữ sau khi khởi động lại app |
| 3 | VM + UI phần AI nói: đọc câu mở đầu + câu trả lời, nút 🔊, công tắc, dừng khi rời màn + unit test | Test 1–3, 7 ở §5 chạy |
| 4 | `SpeechListener` + `AndroidSpeechListener` + map lỗi + unit test map lỗi | Log ra được Partial/Final trên emulator |
| 5 | VM + UI phần người dùng nói: quyền, nút mic/Gửi, bong bóng tạm, gửi thẳng + unit test | Test 4–6, 8 ở §5 chạy |
| 6 | (Tuỳ chọn) 🔊 trên màn nhận xét | Nghe được câu sửa đúng |
| 7 | Chạy đủ 1 phiên 12 lượt chỉ bằng giọng trên máy thật | Không gõ phím lần nào |

Bước 1–3 (chỉ TTS) đã dùng được độc lập, có thể dừng ở đó nếu cần demo sớm.

## 7. Rủi ro

- **Máy không có Google app / dịch vụ nhận giọng** (một số máy Trung Quốc): ẩn mic, vẫn gõ được.
- **Nhận giọng cần mạng** trên đa số máy; `createOnDeviceSpeechRecognizer` (API 31+) cần tải gói ngôn
  ngữ trước, không dùng ở bản này.
- **Giọng TTS phụ thuộc máy**: giọng mặc định có thể là tiếng Việt → luôn `setLanguage(Locale.US)`
  và kiểm tra kết quả.
- **Mic thu tiếng loa**: đã xử lý bằng cách dừng TTS trước khi nghe.
- Nhận dạng sai câu của người phát âm chưa chuẩn → câu sai vẫn được gửi. Chấp nhận: AI vẫn trả lời
  theo ngữ cảnh, màn nhận xét sẽ chỉ ra; người dùng thấy bong bóng sai thì có thể gõ lại câu đúng.
  Nếu thấy phiền khi test thật, thêm bước "xem lại 2 giây rồi tự gửi, bấm để sửa" sau.
- Một lượt nói sai vẫn tốn 1 trong 12 lượt của phiên.
