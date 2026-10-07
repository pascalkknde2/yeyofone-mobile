package com.yeyofone.app.ui.contacts

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CONTACTS_NAVIGATION
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.account.ContactDraft
import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.ContactId

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCall: (Contact) -> Unit,
    onSave: (ContactDraft) -> Unit,
    onSetFavorite: (ContactId, Boolean) -> Unit,
    onDelete: (ContactId) -> Unit,
    onNavigationItemSelected: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = contacts.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
    }

    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = { BottomNavigationBar(CONTACTS_NAVIGATION, onNavigationItemSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Column(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.contacts_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.5).sp)
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.add_contact), tint = TextPrimary)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 0.dp).padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = InactiveGray) },
                    singleLine = true,
                )
            }

            if (contacts.isEmpty()) {
                EmptyState(stringResource(R.string.contacts_empty_title), stringResource(R.string.contacts_empty_subtitle))
            } else if (filtered.isEmpty()) {
                EmptyState(stringResource(R.string.contacts_search_empty_title), "")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id.value }) { contact ->
                        ContactCard(
                            contact = contact,
                            onCall = { onCall(contact) },
                            onToggleFavorite = { onSetFavorite(contact.id, !contact.favorite) },
                            onEdit = { editing = contact },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        ContactEditDialog(
            contact = null,
            onDismiss = { showAdd = false },
            onSave = { draft -> onSave(draft); showAdd = false },
            onDelete = null,
        )
    }
    editing?.let { contact ->
        ContactEditDialog(
            contact = contact,
            onDismiss = { editing = null },
            onSave = { draft -> onSave(draft); editing = null },
            onDelete = { onDelete(contact.id); editing = null },
        )
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

@Composable
private fun ContactCard(contact: Contact, onCall: () -> Unit, onToggleFavorite: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(Color.White)
            .clickable(onClick = onCall).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(PrimaryLight), contentAlignment = Alignment.Center) {
            Text(contact.displayName.initials(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contact.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(contact.number, fontSize = 13.sp, color = TextSecondary)
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (contact.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                stringResource(R.string.cd_favorite_contact),
                tint = if (contact.favorite) AccentBlue else InactiveGray,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, stringResource(R.string.cd_edit_contact), tint = InactiveGray)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(SuccessLight).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, stringResource(R.string.cd_call_contact), tint = AccentGreen, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ContactEditDialog(
    contact: Contact?,
    onDismiss: () -> Unit,
    onSave: (ContactDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(contact?.displayName.orEmpty()) }
    var number by remember { mutableStateOf(contact?.number.orEmpty()) }
    var favorite by remember { mutableStateOf(contact?.favorite ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldsRequiredMessage = stringResource(R.string.contact_fields_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (contact == null) R.string.add_contact_title else R.string.edit_contact_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.contact_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; error = null },
                    label = { Text(stringResource(R.string.contact_number_label)) },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.contact_favorite_label), Modifier.weight(1f), color = TextPrimary)
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isEmpty() || number.trim().isEmpty()) {
                    error = fieldsRequiredMessage
                    return@TextButton
                }
                onSave(ContactDraft(id = contact?.id, displayName = name.trim(), number = number.trim(), favorite = favorite))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
