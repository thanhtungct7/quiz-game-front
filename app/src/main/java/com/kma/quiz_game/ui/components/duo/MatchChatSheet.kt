package com.kma.quiz_game.ui.components.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.ChatMessageDto
import com.kma.quiz_game.data.repository.DuoRepository
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral700

/**
 * In-match chat.
 *
 * A sheet rather than a permanent panel: a round is only a few seconds long, and the question has
 * to stay the thing on screen. The server echoes a sent message back to the sender too, so the
 * list is drawn purely from what arrived over the socket -- nothing is appended optimistically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchChatSheet(
    messages: List<ChatMessageDto>,
    myUserId: String?,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(text = "Trò chuyện", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (messages.isEmpty()) {
                Text(
                    text = "Chưa có tin nhắn nào.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral500,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages) { message -> ChatBubble(message, message.userId == myUserId) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(DuoRepository.MAX_CHAT_LENGTH) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhắn cho đối thủ…") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onSend(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageDto, isMine: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = message.message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isMine) Green500 else Neutral700,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Neutral050)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
