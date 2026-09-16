# AI FEATURES ROADMAP — Review Hub

> Bản plan chốt để review trước khi triển khai. Mọi quyết định thiết kế trong tài liệu này
> đã được thống nhất: **Review Hub là trung tâm duy nhất** của việc ôn tập từ câu sai,
> AI (Gemini) gọi **on-demand từ backend**, mọi kết quả AI **cache DB**, app Android
> **không bao giờ phụ thuộc sống còn vào AI** (luôn có fallback tĩnh).

---

## 1. Tóm tắt & phạm vi chốt

Xây **Review Hub**: 1 màn hình ôn tập tập trung toàn bộ câu sai của người dùng, chia 3
phần: (trên) card tổng kết câu sai/điểm yếu — bấm nút gọi AI phân tích, mỗi ngày/tuần
1 lần; (giữa) list câu sai group theo topic; (cuối) hàng nút nhỏ ban đầu để trống, chỉ
hiện 2 nút khi tickbox chọn câu sai — [✨ Giải thích] và [📑 Sinh bài đọc] (passage +
3 câu hỏi đọc hiểu, mỗi câu 4 đáp án). Kèm **Placement test** 2 nhánh ở onboarding
(tách riêng, không nằm trong Hub).

| # | Tính năng | Trạng thái | Ghi chú |
| --- | --- | --- | --- |
| 1 | **Giải thích câu sai bằng AI** | ✅ Làm | Nút bấm mới call; cache DB; fallback explanation tĩnh |
| 5 | **Sinh bài đọc hiểu từ câu đã chọn** | ✅ Làm | Passage chèn đúng từ/điểm ngữ pháp đã sai + 3 câu hỏi, mỗi câu 4 đáp án |
| 6 | **Tổng kết điểm yếu bằng AI** | ✅ Làm | Nút trong Hub, bấm 1 lần/ngày/tuần; phân tích câu sai → điểm yếu + cần cải thiện |
| 4 | **Placement test (2 nhánh onboarding)** | ✅ Làm | "Học từ đầu" / "Kiểm tra trình độ" → unlock theo band CEFR |
| 3 | **Listening / TTS** | ❌ Cắt khỏi phạm vi | Muốn thêm sau chỉ là 1 mode mới trong Hub, không phá thiết kế hiện có |

**Nguyên tắc xuyên suốt (ràng buộc thiết kế):**

1. **Một dòng dữ liệu duy nhất**: lesson sai → `mistake_log` → Hub (tổng kết / giải thích /
   bài đọc đều đọc từ đây). Retire câu sai là **thủ công** (nút "Đã nắm vững").
2. **AI on-demand, không chạy ngầm**: không call nào phát sinh khi user không nhấn nút;
   không pre-generate hàng loạt (trừ pool câu placement test).
3. **Cache mọi kết quả AI**: cùng input → trả từ DB, không tốn tiền gọi lại.
4. **Fallback luôn có**: AI lỗi/quota hết → explanation tĩnh (`challenges.explanation`) +
   nội dung số liệu rule-based; UX không bao giờ hiện màn hình chết.
5. **Server-authoritative**: client chỉ gửi `mistake_ids` + đáp án đã chọn; server tự lookup
   câu hỏi, đáp án đúng và chấm điểm (câu hỏi đọc hiểu chấm qua `reading_token`).

---

## 2. Kiến trúc tổng thể

```
[LessonScreen] ── check sai ──▶ ProgressService.check_answer()
                                     │ (hook: insert mistake_log)
                                     ▼
                              ┌──────────────┐
[LessonResultScreen] ────────▶│  MISTAKE_LOG │
 (màn reward thu gọn,          └──────┬───────┘
  chỉ hiển thị tổng +                 │
  nút dẫn sang Hub)                   ▼
                      ┌──────────────────────────────────────────┐
                      │                REVIEW HUB                │
                      ├──────────────────────────────────────────┤
                      │ 📊 TỔNG KẾT (phần trên, ~1/3 màn hình)   │
                      │ số câu sai + điểm yếu + cần cải thiện    │
                      │ nút [✨ Phân tích AI] — 1 lần/ngày/tuần  │
                      ├──────────────────────────────────────────┤
                      │ LIST CÂU SAI (phần giữa, cuộn được)      │
                      │  Filter chips (topic / CEFR)             │
                      │  ☑ tickbox chọn 1..N câu                 │
                      ├──────────────────────────────────────────┤
                      │ HÀNG CUỐI NHỎ (ban đầu để trống)         │
                      │  Chọn ≥1 → hiện 2 nút:                   │
                      │   [✨ Giải thích]   → /review/explain    │
                      │   [📑 Sinh bài đọc] → /review/reading    │
                      └──────────────────────────────────────────┘
                      (mọi kết quả AI cache DB; không còn màn quiz riêng)

[Onboarding] ── 2 nhánh ──▶ "Học từ đầu" (mặc định)
                         └ "Kiểm tra trình độ" → 10-12 câu theo CEFR
                              → chấm rule-based → unlock unit tới band đạt được
```

**Backend layer mới** (đi đúng pattern hiện có của dự án — routes → services → repository):

```
quiz-game-backend/
  app/services/ai/gemini_client.py        # 1 client Gemini dùng chung (retry/timeout/quota)
  app/services/review/review_service.py   # giải thích + bài đọc + tổng kết + mistake log
  app/repository/review/mistake_repository.py
  app/repository/review/ai_generation_repository.py
  app/api/routes/review/review.py         # mount vào api_router, prefix /review
  app/api/routes/review/_review_errors.py # error codes mới
  app/schemas/review/*.py                 # Pydantic request/response
  app/models/review/{mistake_log,ai_generation,summary}.py
  alembic/versions/20260916_0006_ai_review_hub.py
```

## 3. Backend — schema DB (Migration 0006)

### 3.1 Bảng `mistake_log` — nguồn dữ liệu duy nhất của Hub

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | String(36) PK | uuid4 |
| `user_id` | FK users.id, index | |
| `challenge_id` | FK challenges.id, index | |
| `lesson_id` | String(36), index | Denormalize để group nhanh |
| `topic_id` | nullable | Copy từ challenge — phục vụ filter/group |
| `tag` | nullable String(50) | Copy `challenges.tags[0]` — "grammar point" hiển thị |
| `cefr_level` | nullable String(20) | Copy để filter + band cho prompt AI |
| `submitted_text` | Text | Đáp án user (serialize: option text hoặc chuỗi ORDER) |
| `submitted_option_ids` | nullable JSON | Chỉ ORDER challenge |
| `retired` | Boolean, default false, index | Đã nắm vững (chỉ retire thủ công), ẩn khỏi list mặc định |
| `created_at` | DateTime(UTC), index | |
| `last_explained_at` | nullable | Lần gần nhất user xem giải thích |

- **Unique constraint**: `(user_id, challenge_id, submitted_text)` — tránh trùng lặp khi
  user sai cùng 1 câu cùng kiểu đáp án lặp lại (upsert: chỉ bump `created_at`).
- Index chính: `(user_id, retired, created_at DESC)` — truy vấn chính của Hub.
- **Retire**: chỉ qua nút "Đã nắm vững" — không còn cơ chế tự động (bỏ cùng phần làm bài).

### 3.2 Bảng `ai_generations` — cache kết quả AI

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | String(36) PK | |
| `user_id` | FK, index | Cache theo user (prompt chứa ngữ cảnh riêng) |
| `kind` | Enum `EXPLAIN / READING / SUMMARY` | |
| `input_hash` | String(64) | SHA256 của sorted mistake_ids + kind + model version (SUMMARY: thêm period) |
| `payload_json` | Text | Kết quả AI đã validate |
| `created_at` | | |

- **Unique**: `(user_id, kind, input_hash)` → cache hit chính xác, không tốn tiền gọi lại.

### 3.3 Bảng `ai_summaries` — kết quả tổng kết điểm yếu (bấm nút)

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | String(36) PK | |
| `user_id` | FK, index | |
| `period_start` | Date (thứ 2 của tuần hoặc ngày) | |
| `period_kind` | Enum `DAY / WEEK` | Loại kỳ user chọn khi bấm |
| `summary_json` | Text — `{total_wrong, top_weak_points: [{tag, wrong_count}], focus_suggestion}` | |
| `created_at` | | |

- **Unique**: `(user_id, period_kind, period_start)` → giới hạn **1 lần bấm/kỳ** thực thi
  ở DB level; bấm lại trong cùng kỳ → trả kết quả đã có (không tốn tiền, không tốn quota).

### 3.4 (Placement test) `users` thêm 2 cột

- `placement_band: String(8) nullable` — band đạt được khi test ("A1".."C1", NULL = chưa
  test / học từ đầu).
- `placement_at: DateTime nullable` — phục vụ cooldown retest 30 ngày.

## 4. Backend — `GeminiService` (`app/services/ai/gemini_client.py`)

**Mục đích**: 1 điểm duy nhất gọi Gemini, dùng chung cho giải thích / bài đọc / tổng kết /
placement note. Không có route nào gọi SDK trực tiếp.

### 4.1 Cấu hình thêm vào `app/core/config.py` (pattern `SecretStr` có sẵn)

```python
gemini_api_key: SecretStr | None = None
gemini_model: str = "gemini-2.0-flash"      # đổi model chỉ qua .env
gemini_timeout_seconds: float = Field(default=20, gt=0, le=60)
ai_max_items_per_call: int = Field(default=10, ge=1, le=20)
ai_daily_quota_per_user: int = Field(default=30, ge=1, le=200)
```

- Không có `GEMINI_API_KEY` trong `.env` → service report `unavailable`, các route trả
  **fallback tĩnh kèm flag `ai_generated: false`** (không raise 500) — app chạy như cũ.
- Thêm `google-genai` vào `pyproject.toml` dependencies.

### 4.2 Hợp đồng API của service

```python
class GeminiService:
    @property
    def is_available(self) -> bool: ...

    async def generate_structured(
        self, prompt: str, response_schema: dict, *, user_key: str
    ) -> dict | None:
        """Trả dict khớp response_schema, hoặc None khi:
        - chưa cấu hình key / quota user hết ngày / Gemini lỗi sau retry."""
```

### 4.3 Hành vi bên trong

1. **Quota check trước**: đếm call thật (không tính cache-hit) theo `(user_key, hôm nay)`;
   vượt `ai_daily_quota_per_user` → raise `AIQuotaExceededError` (service tầng trên quyết
   định fallback). Cache-hit không tính quota (không tốn tiền).
2. **Structured output**: bắt buộc `response_schema` (JSON schema) — Gemini bị force trả
   đúng shape, không parse tự do.
3. **Retry**: tối đa 2 lần retry exponential (0.5s, 2s) cho 429/5xx/timeout; sau đó None.
4. **Logging**: log model + thời gian, không log nội dung user.

## 5. Backend — `ReviewService`: giải thích + bài đọc + tổng kết + mistake log

### 5.1 Hook ghi nhận câu sai (điểm bắt đầu của dòng dữ liệu)

- Trong `ProgressService.check_answer()` — sau khi grade ra `correct == False`:
  insert/upsert `mistake_log`. ORDER challenge: `submitted_text` = chuỗi text của các
  option theo thứ tự user đặt ("i go to school"); single-choice = text option được chọn.
- `ProgressService` nhận thêm optional dependency `mistakes: MistakeRepository | None =
  None` — không ghi khi chưa wired, mọi test cũ không đổi.

### 5.2 Đọc list + retire

```
GET /api/v1/review/mistakes?retired=false&tag=&limit=50&offset=0
  → { summary: {total_wrong, last_7_days_wrong},     # số liệu cho phần TỔNG KẾT
      groups: [ { topic_id, topic_title, items: [
        { mistake_id, question, options[], correct_text, submitted_text,
          tag, cefr_level, created_at } ] } ], total }

POST /api/v1/review/mistakes/{id}/retire     # nút "Đã nắm vững" thủ công
```

- `options[]` trả đầy đủ (kể cả đáp án đúng) — UI cần hiển thị "Đáp án đúng" trên mỗi
  item; client KHÔNG tự chấm gì cả (chỉ còn 2 hành động AI, không có màn quiz riêng).

### 5.3 ✨ `POST /api/v1/review/explain`

- Request: `{mistake_ids: [...]}` — cap 10 (server validate → `INVALID_MISTAKE_IDS`).
- Flow: cache check (hash các mistake_ids + kind + model) → cache hit trả luôn
  (`cached: true`, không tính quota) → miss: **1 prompt gộp** các câu còn thiếu →
  output schema: `[{"mistake_id", "explanation"}]` (≤120 từ tiếng Anh đơn giản, kết thúc
  bằng 1 câu tiếng Việt hỗ trợ) → validate từng item (fail → drop item đó) → lưu cache.
- Response: `{items: [{mistake_id, explanation, ai_generated, cached}], ...}`.
- Fallback: AI fail/quota hết → explanation tĩnh từ `challenges.explanation` +
  `ai_generated: false` — **HTTP 200**, không lỗi cứng.

### 5.4 📑 `POST /api/v1/review/reading`

- Request: `{mistake_ids: [...]}` — **cap 5**.
- Prompt: passage 90-110 từ, band = **thấp nhất** trong các câu chọn, chèn tự nhiên các
  từ/điểm ngữ pháp đã sai, kèm `highlighted_terms`; **3 câu hỏi đọc hiểu, mỗi câu 4 đáp
  án** (1 câu từ vựng trong passage + 2 câu hiểu nội dung).
- Schema output: `{"passage", "highlighted_terms": [...], "questions": [{"question",
  "options[4]", "answer_index"}]}` → validate (đủ 4 options, answer_index 0..3; fail →
  drop câu hỏi lỗi, passage vẫn trả nếu ≥2 câu hỏi còn sống).
- Chấm điểm: trả kèm `reading_token` (JWT stateless chứa answer_index + hết hạn 30 phút);
  `POST /api/v1/review/reading/submit` `{reading_token, answers: [{question, chosen}]}` →
  server chấm → `{total, correct}`. Kết quả KHÔNG ghi ngược mistake_log (bài đọc chỉ là
  luyện đọc, không phải vòng lặp sai→ôn).

### 5.5 📊 Tổng kết điểm yếu (nút bấm 1 lần/ngày/tuần)

- `POST /api/v1/review/summary` — body: `{period: "DAY" | "WEEK"}`.
- Flow:
  1. Xác định `period_start` (hôm nay / thứ 2 tuần này) theo UTC.
  2. Đã có row `ai_summaries (user, period_kind, period_start)`? → trả luôn (đếm là lần
     bấm thứ 2 trong kỳ, **không gọi AI**, không trừ quota) kèm flag `already_generated`.
  3. Chưa có → aggregate `mistake_log` trong kỳ: tổng số câu sai, group theo
     tag/topic, top điểm yếu.
  4. 1 call Gemini: phân tích các điểm yếu → schema `{"summary" (≤80 từ, tiếng Việt),
     "weak_points": [{"tag", "wrong_count", "advice" (≤30 từ)}], "focus_suggestion"}`.
  5. Lưu row (unique chặn bấm lần 2) → trả về.
- **Fallback không AI**: trả summary rule-based thuần số liệu (`ai_generated: false`)
  + VẪN lưu row → vẫn giới hạn 1 lần/kỳ, không sinh call lại.
- Không cron, không job nền — mọi thứ xảy ra tại request user bấm (đơn giản, không rủi
  ro single-worker).

### 5.6 Error codes mới (`_review_errors.py`, pattern `_pve_errors.py`)

| Code | HTTP | Khi nào |
| --- | --- | --- |
| `MISTAKE_NOT_FOUND` | 404 | mistake_id không tồn tại / không thuộc user |
| `INVALID_MISTAKE_IDS` | 422 | rỗng, quá cap, trùng lặp |
| `INVALID_PERIOD` | 422 | period không phải DAY/WEEK |
| `AI_UNAVAILABLE` | 200 + fallback | Gemini lỗi — trả nội dung tĩnh + flag |
| `AI_QUOTA_EXCEEDED` | 200 + fallback | Hết quota ngày — trả nội dung tĩnh + flag |

> Quy ước quan trọng: 2 mã cuối **không bao giờ làm UI chết** — response luôn dùng được.

## 6. Backend — Placement test (#4, tách riêng khỏi Hub)

### 6.1 Luồng & UX

```
Onboarding (lần đầu, sau đăng ký):
  ┌────────────────────────────────────────┐
  │ Chào mừng! Bạn muốn bắt đầu thế nào?   │
  │ [🎓 Học từ đầu]    [📊 Kiểm tra trình độ]│
  │  dành cho người mới   ~5 phút, 15 câu   │
  └────────────────────────────────────────┘
        │                        │
        ▼                        ▼
  placement_band = NULL    15 câu tăng dần A1→B2, 1 câu/màn,
  (mặc định, mọi thứ       KHÔNG hiện đúng/sai realtime, KHÔNG
  mở như hiện tại)         quay lại câu trước (tránh đoán theo feedback)
                                   │
                                   ▼
                    [Kết quả: "Trình độ: A2! Đã mở khóa tới Unit 5"]
                    → Learn tab: unit ≤ band mở, unit trên band khóa
                    → Unit cuối vùng mở hiện "vị trí bạn đang ở"
```

- **Dừng sớm adaptive nhẹ**: sai 3 câu liên tiếp cùng band → dừng (chuyển thẳng kết quả,
  không phải "fail").
- **Test lại**: entry trong Profile ("Trình độ: A2 — Kiểm tra lại"), cooldown 30 ngày.
- Chọn "Học từ đầu" nhầm: vẫn còn entry "Kiểm tra trình độ" trong Profile — không ai
  bị kẹt với lựa chọn đầu tiên.

### 6.2 API

| Endpoint | Mô tả |
| --- | --- |
| `POST /api/v1/assessment/start` | Draw 12 câu từ pool `challenges.cefr_level IS NOT NULL` (mỗi band A1..B2 lấy 2-3 câu, random). Trả câu hỏi + options **không kèm đáp án** + `assessment_token` (JWT ngắn hạn chứa challenge_ids + band mỗi câu — stateless, không thêm bảng) |
| `POST /api/v1/assessment/finish` | Body: đáp án theo token. Server chấm rule-based (đúng ≥70% câu thuộc band N → đạt N). Lưu `users.placement_band` + `placement_at`. Trả `{band, unlocked_unit_count, review_note}` — `review_note` là 1 call Gemini optional (fail → null, không ảnh hưởng) |
| `POST /api/v1/assessment/skip` | Ghi band = NULL ("Học từ đầu") |

### 6.3 Unlock logic

- Learn tab load tree: nếu `placement_band != NULL`, các unit có difficulty/CEFR tương
  đương **trên** band bị khóa (lock icon + text "Hoàn thành các unit trước hoặc kiểm tra
  lại trình độ"), unit ≤ band mở tự do.
- **Không đụng logic progress có sẵn** (lesson completed là ratchet — xem
  `_store_lesson_progress`): chỉ thêm điều kiện hiển thị/khóa ở UI + filter khi load
  tree, KHÔNG đổi nghĩa "completed".

### 6.4 Rủi ro riêng

- **Dữ liệu `challenges.cefr_level` có thể null hàng loạt** trong seed → **bước đầu tiên
  của task này là audit**: query count null; nếu >20% thì viết script seed điền theo
  unit/difficulty trước khi code tiếp.
- Chấm rule-based thô hơn AI — chấp nhận cho MVP, AI chỉ thêm "nhận xét".

## 7. Frontend Android — UI/UX flow chi tiết

### 7.0 Điều hướng mới

- Home thêm **tab/destination "Ôn tập"** (theo nav graph hiện có của app — bottom bar hoặc
  entry từ LearnScreen, chốt khi code theo nav hiện trạng).
- `LessonResultScreen` → nút `[Ôn tập ngay]` khi có câu sai → navigate thẳng Review Hub.
- Màn mới: `ReviewHubScreen`, `AiReadingScreen`, `AssessmentScreen` (+#4).

### 7.1 Review Hub (màn chính, chia 3 phần)

```
┌──────────────────────────────────────────────┐
│ 📊 TỔNG KẾT CÂU SAI (phần trên, ~1/3 màn)     │
│ Tuần này: 42 câu đúng · 8 câu sai            │
│ Điểm yếu: Quá khứ hoàn thành (4),            │
│           Collocation (3)                    │
│ Cần cải thiện: ôn lại cách chia động từ      │
│           bất quy tắc...                     │
│ [✨ Phân tích bằng AI]   (1 lần/ngày/tuần)   │
├──────────────────────────────────────────────┤
│ LIST CÂU SAI (phần giữa, cuộn được)          │
│ [Tất cả] [Ngữ pháp] [Từ vựng] [A2] [B1]      │  ← FilterChip
│ ▸ Quá khứ đơn — hôm qua                      │  ← group theo topic/tag
│ ┌──────────────────────────────────────────┐ │
│ │ ☑ She ___ to school yesterday.           │ │  ← tickbox bên trái
│ │   Bạn: "go" ❌ · Đúng: "went" ✅          │ │
│ └──────────────────────────────────────────┘ │
│ ▸ Collocation — 3 ngày trước ...             │
├──────────────────────────────────────────────┤
│ (hàng cuối nhỏ — ban đầu để trống/đồ trống)  │
│ Đã chọn 2 · [✨ Giải thích] [📑 Sinh bài đọc] │  ← chỉ hiện khi tickbox chọn
└──────────────────────────────────────────────┘
```

**Trạng thái bắt buộc phải có:**
- Loading lần đầu: skeleton list (không spinner trơn).
- Empty (chưa sai câu nào): mascot + "Làm một bài học để bắt đầu thu thập câu sai!" +
  nút sang Learn.
- Empty (đã hết — retired sạch): mascot mừng + "Tuyệt vời! Bạn đã nắm vững toàn bộ câu
  sai 🎉".
- Card tổng kết: chưa có trong kỳ → hiện số liệu thô (rule-based) + nút [✨ Phân tích AI]
  nhấp nháy nhẹ mời bấm; đã có → hiện text AI + nút chuyển thành "Đã phân tích hôm nay"
  (disable, tap → hint "Quay lại vào kỳ sau để phân tích tiếp"); đang phân tích → spinner
  trong card; AI fail → số liệu rule-based + banner nhỏ "AI tạm bận".
- Tickbox chọn ≥1 → hàng cuối nhỏ **trượt lên có animation** với 2 nút; bỏ chọn hết →
  ẩn đi. Long-press item = "Đã nắm vững" (retire thủ công, có confirm).
- Giải thích đang load: item shimmer; xong → expand khối nền tím nhạt icon ✨; AI fail →
  đổ explanation tĩnh + banner nhỏ.
- Pull-to-refresh + load more (limit/offset).

### 7.2 ✨ Giải thích (từ hàng cuối)

- Nhấn [✨ Giải thích] với N câu đang chọn → các item chuyển shimmer → fill kết quả lần
  lượt; cached thì hiện gần tức thì.
- Kèm nút copy nhỏ (user hay muốn tra lại).
- ORDER challenge: hiển thị "Bạn: i go to school ❌ → Đúng: I went to school ✅"
  (chuỗi text, không optionId).

### 7.3 📑 Bài đọc (`AiReadingScreen`)

```
Nhấn [📑 Sinh bài đọc] với các câu đang chọn (cap 5)
   → loading "Đang tạo bài đọc từ câu sai của bạn..." (~4-6s)
   → passage scrollable, các từ từng sai highlight vàng
   → nút nổi dưới "Trả lời câu hỏi (3)"
   → 3 câu hỏi đọc hiểu, mỗi câu 4 đáp án (component tái dùng của lesson)
   → submit → điểm đúng/tổng (server chấm qua reading_token)
   → [Về Ôn tập]; bài đọc vào history (horizontal scroll card "Bài đọc của bạn"
     nằm cuối phần list của Hub)
```

### 7.4 Assessment (onboarding) — flow đã mô tả ở mục 6.1; UI note thêm:
- Thanh tiến trình trên cùng (1/12), nút thoát có confirm ("Bài kiểm tra sẽ không được
  lưu").
- Kết quả: mascot + band to + nút [Bắt đầu học] dẫn vào Learn tab đúng vị trí unlock.
- Mất mạng giữa test: giữ state trong ViewModel; app kill → lần sau onboarding hỏi lại
  (không resume — test ngắn, làm lại rẻ hơn lưu state).

### 7.5 Data layer (pattern có sẵn của app)

- `ReviewApi.kt` (Retrofit, kotlinx-serialization — pattern `ProgressApi`/`QuizDto`).
- `AssessmentApi.kt`.
- `ReviewRepository` + DTOs mới (`ReviewDto.kt`).
- DI: đăng ký trong `DuoGameApplication` như các repository khác.
- LessonViewModel: giữ `wrongAttempts` từ `AnswerCheckResult` (data đã có đủ) →
  LessonResultScreen chỉ cần đếm + navigate.

## 8. Chất lượng & kiểm thử

### 8.1 Backend tests (mock Gemini — không gọi API thật trong CI)

| Test | Kiểm tra |
| --- | --- |
| `test_mistake_log.py` | Sai → insert; đúng → không insert; ORDER serialize đúng; upsert không trùng row |
| `test_review_explain.py` | Cache hit không gọi AI; AI fail → fallback tĩnh + `ai_generated: false`; cap 10; sai user → 404 |
| `test_review_reading.py` | Cap 5; validate drop câu hỏi lỗi; submit chấm bằng reading_token; token hết hạn |
| `test_review_summary.py` | Lần 1 gọi AI; lần 2 trong kỳ KHÔNG gọi (unique chặn); fallback rule-based vẫn lưu row; period invalid → 422 |
| `test_assessment.py` | Draw đủ band; token expire; chấm rule-based đúng ngưỡng; unlock count đúng; cooldown retest |
| `test_gemini_client.py` | Retry backoff; timeout → None; quota block; không có key → unavailable |

### 8.2 Frontend tests

- Unit test ViewModel (pattern có sẵn trong `app/src/test`): Hub state (loading/empty/
  filled), select/deselect tickbox → hiện/ẩn hàng nút, summary card states.
- Wire capture test cho `ReviewApi` (pattern `duo_wire_capture.json`).

### 8.3 Chất lượng gate (giữ nguyên chuẩn dự án)

```bash
pytest && ruff check . && mypy app    # backend
./gradlew :app:testDebugUnitTest      # frontend
```

### 8.4 Bảng rủi ro tổng hợp & mitigation

| Rủi ro | Khả năng | Tác động | Mitigation (đã bake vào plan) |
| --- | --- | --- | --- |
| Gemini trả JSON sai shape | Trung bình | Item bị drop | Structured output + validate từng item + drop, không crash cả call |
| AI chậm (3-8s) | Cao | UX ản | Fill dần từng item + skeleton + fallback tức thì |
| Chi phí AI tăng đột biến | Trung bình | Tiền | On-demand + cache + cap 10/5 + tổng kết 1 lần/kỳ + daily quota per user |
| Key lộ | Thấp | Nghiêm trọng | Key chỉ ở backend `.env`, không bao giờ nhúng app |
| `cefr_level` seed null | Cao (với #4) | Chặn #4 | Audit là bước đầu tiên của task #4 |
| User thiệt thòi khi AI chết | Trung bình | UX | Mọi action có fallback tĩnh; core lesson không đụng AI |
| Mistake log phình to | Cao theo thời gian | Query chậm | Index `(user_id, retired, created_at)`, limit/offset, retire thủ công |

---

## 9. Timeline & thứ tự triển khai (~7 ngày)

| Ngày | Việc | Output |
| --- | --- | --- |
| 1 | Migration 0006 + models + repositories + `GeminiService` (mục 3, 4) | Nền data + AI client, test pass |
| 2 | Hook mistake vào `check_answer` + list/retire + `explain` (5.1-5.3) | API list + giải thích chạy được |
| 3 | `reading` + `reading/submit` + `summary` (5.4-5.5) + errors tổng hợp (5.6) | Backend hoàn chỉnh + smoke test |
| 4 | Frontend: data layer + Hub khung 3 phần + card tổng kết (7.0, 7.1) | Hub hiện list + tổng kết |
| 5 | Frontend: giải thích + hàng nút tickbox + bài đọc (7.2, 7.3) | Vòng AI đầy đủ |
| 6 | Assessment backend + onboarding UI + unlock (#4) + audit cefr | Placement test chạy được |
| 7 | Polish trạng thái + test + buffer | Đủ Definition of Done |

**Cắt được an toàn nếu thiếu thời gian (thứ tự cắt):** review_note AI của assessment
(giữ rule-based chấm + null note) → history bài đọc trong Hub → filter chips nâng cao
(giữ "Tất cả" + group theo topic).

**Không được cắt** (xương sống): migration 0006, hook mistake, `/explain` fallback tĩnh,
Hub list + tickbox + retire, summary 1 lần/kỳ, reading_token.

## 10. Các điểm cần chốt trước khi code

| # | Câu hỏi | Đề xuất mặc định |
| --- | --- | --- |
| 1 | Model Gemini mặc định | `gemini-2.0-flash` qua `.env` (đổi được không cần sửa code) |
| 2 | Cap câu cho 1 call | Explain: 10 câu · Reading: 5 câu · Quota: 30 call/user/ngày |
| 3 | Vị trí tab "Ôn tập" | Bottom bar Home (song song Learn/Leaderboard) — chốt theo nav hiện trạng khi code |
| 4 | Ngưỡng chấm placement | Đúng ≥70% câu thuộc band N → đạt band N |
| 5 | Tổng kết điểm yếu | Toggle DAY/WEEK trong card; mỗi kỳ bấm AI 1 lần, bấm lại trả cache |
| 6 | Ngôn ngữ giải thích | Tiếng Anh đơn giản ≤120 từ + 1 câu tiếng Việt hỗ trợ cuối; summary bằng tiếng Việt |
| 7 | Câu hỏi bài đọc | 3 câu, mỗi câu 4 đáp án, server chấm qua reading_token, không ghi ngược mistake_log |

---

## 11. Phạm vi KHÔNG làm (nhớ lại để tránh scope creep)

- ❌ Listening/TTS (đã cắt — thêm sau là 1 mode Hub mới, không phá thiết kế).
- ❌ Màn quiz/làm bài sinh từ AI (đã cắt — Hub chỉ có Giải thích + Bài đọc).
- ❌ Flashcard riêng (list câu sai chính là bài học).
- ❌ Chatbot tự do / Live API speaking / Gemini Nano on-device / pronunciation phoneme.
- ❌ Cron/job nền cho tổng kết (summary chạy tại request user bấm).
- ❌ Pre-generate nội dung AI cho toàn bộ course (chỉ pool placement test là draw sẵn).
- ❌ Push notification (in-app là đủ cho MVP).

---

## 12. Definition of Done (để review kết quả sau khi code)

1. `pytest && ruff check . && mypy app` sạch; app Android build + unit test pass.
2. Flow tay chạy được: làm lesson sai ≥1 câu → kết quả hiện "đã lưu vào Ôn tập" → Hub
   hiện câu sai → tickbox chọn → hàng nút trượt lên → ✨ giải thích (AI thật khi có key,
   fallback khi không) → 📑 bài đọc + 3 câu hỏi 4 đáp án + chấm điểm server.
3. Card tổng kết: bấm lần 1 trong ngày → gọi AI; bấm lần 2 → trả cache không gọi;
   chuyển WEEK → kỳ riêng, cũng 1 lần.
4. Onboarding: "Kiểm tra trình độ" → 12 câu → band + unlock đúng; "Học từ đầu" → hành vi
   y như app hiện tại (không regression).
5. Tắt `GEMINI_API_KEY`: app vẫn dùng được 100% tính năng, chỉ thiếu phần chữ AI.
