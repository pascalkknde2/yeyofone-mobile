package com.yeyofone.app.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.GlassCard
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.SuccessLight
import com.yeyofone.core.model.Contact

/** Choose who to add to the call. Only saved contacts can be added, and people already on the call are left out. */
@Composable
fun ContactPickerScreen(
    contacts: List<Contact>,
    alreadyInCall: Set<String>,
    onPick: (Contact) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val available = contacts.filter { it.number !in alreadyInCall }
    val shown = available.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel), tint = CallTextPrimary)
            }
            Text(stringResource(R.string.add_people_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(Modifier.size(48.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = InactiveGray) },
            singleLine = true,
        )
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(if (available.isEmpty()) R.string.add_people_no_contacts else R.string.contacts_search_empty_title),
                    fontSize = 15.sp, color = CallTextSecondary, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id.value }) { contact ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassCard)
                            .clickable { onPick(contact) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(PrimaryLight), contentAlignment = Alignment.Center) {
                            Text(initialsOf(contact.displayName), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(contact.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary)
                            Text(contact.number, fontSize = 13.sp, color = CallTextSecondary)
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).background(SuccessLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Call, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
