@file:OptIn(ExperimentalMaterial3Api::class)

package com.yeyofone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.account.RoomAccountRepository
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.TransportProtocol
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as YeyoFoneApplication).accountRepository
        setContent { MaterialTheme { AccountsApp(repository) } }
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Detail(val account: SipAccount) : Screen
    data class Edit(val account: SipAccount?) : Screen
}

@Composable
private fun AccountsApp(repository: AccountRepository) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    var screen: Screen by remember { mutableStateOf(Screen.List) }
    when (val current = screen) {
        Screen.List -> AccountList(accounts, { screen = Screen.Edit(null) }) { screen = Screen.Detail(it) }
        is Screen.Detail -> AccountDetail(
            account = accounts.firstOrNull { it.id == current.account.id } ?: current.account,
            repository = repository,
            onBack = { screen = Screen.List },
            onEdit = { screen = Screen.Edit(current.account) },
        )
        is Screen.Edit -> AccountEditor(current.account, repository) { screen = Screen.List }
    }
}

@Composable
private fun AccountList(accounts: List<SipAccount>, onAdd: () -> Unit, onOpen: (SipAccount) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.accounts_title)) }) },
        floatingActionButton = { Button(onClick = onAdd) { Text(stringResource(R.string.add_account)) } },
    ) { padding ->
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.no_accounts), Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(accounts, key = { it.id.value }) { account ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpen(account) }.padding(16.dp),
                    ) {
                        Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${account.username}@${account.server.domain}")
                        Text(stringResource(if (account.enabled) R.string.enabled else R.string.disabled))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetail(
    account: SipAccount,
    repository: AccountRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.account_details)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(account.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.sip_identity, account.username, account.server.domain))
            Text(stringResource(R.string.registrar_value, account.server.registrarUri))
            Text(stringResource(R.string.transport_value, account.server.transport.name, account.server.port))
            Text(stringResource(R.string.expiry_value, account.registrationExpirySeconds))
            account.voicemailNumber?.let { Text(stringResource(R.string.voicemail_value, it)) }
            account.callerId?.let { Text(stringResource(R.string.caller_id_value, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text(stringResource(R.string.edit_account)) }
                Button(onClick = { scope.launch { repository.setEnabled(account.id, !account.enabled) } }) {
                    Text(stringResource(if (account.enabled) R.string.disabled else R.string.enabled))
                }
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { repository.delete(account.id); onBack() } }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AccountEditor(existing: SipAccount?, repository: AccountRepository, onDone: () -> Unit) {
    var displayName by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var authUsername by remember { mutableStateOf(existing?.authenticationUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(existing?.server?.domain.orEmpty()) }
    var registrar by remember { mutableStateOf(existing?.server?.registrarUri.orEmpty()) }
    var proxy by remember { mutableStateOf(existing?.server?.outboundProxyUri.orEmpty()) }
    var port by remember { mutableStateOf(existing?.server?.port?.toString() ?: "5060") }
    var transport by remember { mutableStateOf(existing?.server?.transport ?: TransportProtocol.UDP) }
    var stun by remember { mutableStateOf(existing?.nat?.stunServer.orEmpty()) }
    var turn by remember { mutableStateOf(existing?.nat?.turnServer.orEmpty()) }
    var turnUsername by remember { mutableStateOf(existing?.nat?.turnUsername.orEmpty()) }
    var ice by remember { mutableStateOf(existing?.nat?.iceEnabled ?: true) }
    var srtp by remember { mutableStateOf(existing?.nat?.srtpEnabled ?: false) }
    var expiry by remember { mutableStateOf(existing?.registrationExpirySeconds?.toString() ?: "300") }
    var voicemail by remember { mutableStateOf(existing?.voicemailNumber.orEmpty()) }
    var callerId by remember { mutableStateOf(existing?.callerId.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFailedMessage = stringResource(R.string.save_failed)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (existing == null) R.string.add_account else R.string.edit_account)) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Field(stringResource(R.string.display_name), displayName) { displayName = it } }
            item { Field(stringResource(R.string.sip_username), username) { username = it; if (authUsername.isBlank()) authUsername = it } }
            item { Field(stringResource(R.string.authentication_username), authUsername) { authUsername = it } }
            item {
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (existing == null) R.string.password else R.string.new_password_optional)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
            }
            item { Field(stringResource(R.string.domain), domain) { domain = it } }
            item { Field(stringResource(R.string.registrar_uri), registrar) { registrar = it } }
            item { Field(stringResource(R.string.outbound_proxy_optional), proxy) { proxy = it } }
            item { Field(stringResource(R.string.port), port) { port = it.filter(Char::isDigit) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransportProtocol.entries.forEach { choice ->
                        FilterChip(selected = transport == choice, onClick = { transport = choice }, label = { Text(choice.name) })
                    }
                }
            }
            item { Field(stringResource(R.string.stun_server_optional), stun) { stun = it } }
            item { Field(stringResource(R.string.turn_server_optional), turn) { turn = it } }
            item { Field(stringResource(R.string.turn_username_optional), turnUsername) { turnUsername = it } }
            item { CheckRow(stringResource(R.string.enable_ice), ice) { ice = it } }
            item { CheckRow(stringResource(R.string.require_srtp), srtp) { srtp = it } }
            item { Field(stringResource(R.string.registration_expiry), expiry) { expiry = it.filter(Char::isDigit) } }
            item { Field(stringResource(R.string.voicemail_optional), voicemail) { voicemail = it } }
            item { Field(stringResource(R.string.caller_id_optional), callerId) { callerId = it } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving, onClick = {
                        saving = true
                        error = null
                        val secret = password.takeIf(String::isNotEmpty)?.toCharArray()
                        password = ""
                        scope.launch {
                            repository.save(
                                AccountDraft(
                                    id = existing?.id, displayName = displayName, username = username,
                                    authenticationUsername = authUsername, password = secret, domain = domain,
                                    registrarUri = registrar, outboundProxyUri = proxy, port = port.toIntOrNull() ?: 0,
                                    transport = transport,
                                    securityMode = if (transport == TransportProtocol.TLS) SecurityMode.REQUIRE_SECURE else SecurityMode.ALLOW_INSECURE,
                                    nat = NatConfiguration(stun, turn, turnUsername, ice, srtp),
                                    registrationExpirySeconds = expiry.toIntOrNull() ?: 0,
                                    voicemailNumber = voicemail, callerId = callerId, enabled = existing?.enabled ?: true,
                                ),
                            ).onSuccess { onDone() }.onFailure { error = it.message ?: saveFailedMessage }
                            saving = false
                        }
                    }) { Text(stringResource(R.string.save)) }
                    TextButton(enabled = !saving, onClick = { password = ""; onDone() }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }) {
        Checkbox(checked, onCheckedChange)
        Text(label, Modifier.padding(top = 12.dp))
    }
}
