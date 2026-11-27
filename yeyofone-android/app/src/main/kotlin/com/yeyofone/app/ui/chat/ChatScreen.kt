package com.yeyofone.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeyofone.app.R
import com.yeyofone.app.data.model.ChatContact
import com.yeyofone.app.ui.components.ChatHeader
import com.yeyofone.app.ui.components.ChatInputBar
import com.yeyofone.app.ui.components.MessageBubble
import com.yeyofone.app.ui.components.TypingIndicator
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.feature_coming_soon)
    val unavailable = { scope.launch { snackbar.showSnackbar(comingSoon) }; Unit }
    BackHandler(onBack = onBack)

    LaunchedEffect(state.messages.size, state.isContactTyping) {
        val target = state.messages.size + if (state.isContactTyping) 1 else 0
        if (target > 0) listState.animateScrollToItem(target)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { _ ->
        // The header owns the status-bar inset and the input bar the navigation-bar inset.
        Column(Modifier.fillMaxSize().imePadding()) {
            val contact = state.contact
                ?: ChatContact("", stringResource(R.string.chat_navigation), statusText = "")
            ChatHeader(contact, onBack, onVoiceCall, onVideoCall)
            HorizontalDivider(thickness = 0.5.dp, color = BorderLight)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (state.messages.isEmpty() && !state.isContactTyping) item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_empty), fontSize = 14.sp, color = TextSecondary)
                    }
                } else item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.today).uppercase(),
                            Modifier.clip(RoundedCornerShape(12.dp)).background(BorderLight)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message, unavailable)
                }
                if (state.isContactTyping) item { TypingIndicator() }
            }
            HorizontalDivider(thickness = 0.5.dp, color = BorderLight)
            ChatInputBar(
                text = state.inputText,
                onTextChange = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onAttach = unavailable,
                onCamera = unavailable,
                onVoice = unavailable,
            )
        }
    }
}
