package com.yeyofone.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.ChatMessage
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CHAT_NAVIGATION
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.GlassCard
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.Contact

/** The Chat tab: pick a contact to open (or carry on) a conversation with them. */
@Composable
fun MessagesScreen(
    contacts: List<Contact>,
    conversations: Map<String, List<ChatMessage>>,
    onOpen: (Contact) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // People you've written to come first, then everyone else by name.
    val sorted = contacts.sortedWith(
        compareBy<Contact> { conversations[it.number.trim()].isNullOrEmpty() }.thenBy { it.displayName.lowercase() },
    )
    val filtered = sorted.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { BottomNavigationBar(CHAT_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Text(
                    stringResource(R.string.chat_navigation),
                    Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 14.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = InactiveGray) },
                    singleLine = true,
                )
            }
            when {
                contacts.isEmpty() -> EmptyState(stringResource(R.string.contacts_empty_title), stringResource(R.string.messages_empty_subtitle))
                filtered.isEmpty() -> EmptyState(stringResource(R.string.contacts_search_empty_title), "")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id.value }) { contact ->
                        ConversationRow(contact, conversations[contact.number.trim()]?.lastOrNull()) { onOpen(contact) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(contact: Contact, lastMessage: ChatMessage?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(GlassCard)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(PrimaryLight), contentAlignment = Alignment.Center) {
            Text(contact.displayName.initials(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contact.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                lastMessage?.content ?: contact.number,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (lastMessage != null) {
            Text(lastMessage.timestamp, fontSize = 12.sp, color = InactiveGray)
        } else {
            Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_message_contact), tint = AccentBlue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 14.sp, color = InactiveGray, textAlign = TextAlign.Center)
        }
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
