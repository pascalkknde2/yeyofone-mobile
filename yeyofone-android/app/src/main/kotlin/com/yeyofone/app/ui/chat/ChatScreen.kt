package com.yeyofone.app.ui.chat

import androidx.compose.foundation.background
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
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CHAT_NAVIGATION
import com.yeyofone.app.ui.components.ChatHeader
import com.yeyofone.app.ui.components.ChatInputBar
import com.yeyofone.app.ui.components.MessageBubble
import com.yeyofone.app.ui.components.TypingIndicator
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.feature_coming_soon)
    val unavailable = { scope.launch { snackbar.showSnackbar(comingSoon) }; Unit }

    LaunchedEffect(state.messages.size, state.isContactTyping) {
        val target = state.messages.size + if (state.isContactTyping) 1 else 0
        if (target > 0) listState.animateScrollToItem(target)
    }

    Scaffold(
        containerColor = BackgroundGray,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomNavigationBar(CHAT_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            ChatHeader(state.contact, onBack, onVoiceCall, unavailable)
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x0D000000))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().background(BackgroundGray),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.today).uppercase(),
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0F000000))
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
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x0D000000))
            ChatInputBar(state.inputText, viewModel::onInputChanged, viewModel::sendMessage, unavailable)
        }
    }
}
