@file:OptIn(ExperimentalMaterial3Api::class)

package com.yeyofone.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.AccountRepository
import com.yeyofone.core.account.RoomAccountRepository
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallHistoryEntry
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.TransportProtocol
import com.yeyofone.core.voip.CallManager
import com.yeyofone.core.voip.CallHistoryRepository
import com.yeyofone.core.voip.AudioRouteManager
import com.yeyofone.core.voip.MediaManager
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        IncomingCallService.start(this)
        val app = application as YeyoFoneApplication
        setContent {
            RequestBackgroundCallPermissions()
            MaterialTheme {
                AccountsApp(
                    app.accountRepository,
                    app.callHistory,
                    app.callManager,
                    app.callManager,
                    app.audioRouteManager,
                )
            }
        }
    }
}

@Composable
private fun RequestBackgroundCallPermissions() {
    val context = LocalContext.current
    val fullScreenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    fun requestFullScreenAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            fullScreenLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestFullScreenAccessIfNeeded()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            runtimeLauncher.launch(missing.toTypedArray())
        } else {
            requestFullScreenAccessIfNeeded()
        }
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Detail(val account: SipAccount) : Screen
    data class Edit(val account: SipAccount?) : Screen
    data class Dial(val account: SipAccount, val destination: String = "") : Screen
    data object History : Screen
}

@Composable
private fun AccountsApp(
    repository: AccountRepository,
    history: CallHistoryRepository,
    callManager: CallManager,
    mediaManager: MediaManager,
    audioRoutes: AudioRouteManager,
) {
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val sessions by callManager.sessions.collectAsStateWithLifecycle(emptyList())
    var screen: Screen by remember { mutableStateOf(Screen.List) }
    var dismissedIncoming by remember { mutableStateOf(setOf<CallId>()) }
    val incoming = sessions.firstOrNull { it.direction == CallDirection.INCOMING && it.id !in dismissedIncoming }

    if (incoming != null) {
        IncomingCallScreen(incoming, callManager, mediaManager, audioRoutes) {
            dismissedIncoming = dismissedIncoming + incoming.id
        }
        return
    }

    when (val current = screen) {
        Screen.List -> AccountList(
            accounts,
            onAdd = { screen = Screen.Edit(null) },
            onHistory = { screen = Screen.History },
            onOpen = { screen = Screen.Detail(it) },
        )
        is Screen.Detail -> AccountDetail(
            account = accounts.firstOrNull { it.id == current.account.id } ?: current.account,
            repository = repository,
            onBack = { screen = Screen.List },
            onEdit = { screen = Screen.Edit(current.account) },
            onCall = { screen = Screen.Dial(current.account) },
        )
        is Screen.Edit -> AccountEditor(current.account, repository) { screen = Screen.List }
        is Screen.Dial -> DialScreen(
            current.account,
            current.destination,
            callManager,
            mediaManager,
            audioRoutes,
        ) {
            screen = Screen.Detail(current.account)
        }
        Screen.History -> CallHistoryScreen(
            history = history,
            accounts = accounts,
            onBack = { screen = Screen.List },
            onCallBack = { entry ->
                accounts.firstOrNull { it.id == entry.accountId }?.let {
                    screen = Screen.Dial(it, entry.remoteUri)
                }
            },
        )
    }
}

@Composable
private fun AccountList(
    accounts: List<SipAccount>,
    onAdd: () -> Unit,
    onHistory: () -> Unit,
    onOpen: (SipAccount) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                actions = { TextButton(onClick = onHistory) { Text(stringResource(R.string.recent_calls)) } },
            )
        },
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
private fun CallHistoryScreen(
    history: CallHistoryRepository,
    accounts: List<SipAccount>,
    onBack: () -> Unit,
    onCallBack: (CallHistoryEntry) -> Unit,
) {
    val entries by history.observeHistory().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_calls)) },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_all)) }
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Text(stringResource(R.string.no_recent_calls), Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.id.value }) { entry ->
                    val accountExists = accounts.any { it.id == entry.accountId }
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.remoteUri, style = MaterialTheme.typography.titleMedium)
                        Text(entry.summary())
                        Text(HISTORY_TIME_FORMATTER.format(entry.startedAt))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = accountExists, onClick = { onCallBack(entry) }) {
                                Text(stringResource(R.string.call_back))
                            }
                            TextButton(onClick = { scope.launch { history.delete(entry.id) } }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.confirm_clear_history)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { history.clear() }; confirmClear = false }) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CallHistoryEntry.summary(): String {
    val directionLabel = stringResource(
        when {
            missed -> R.string.missed_call
            direction == CallDirection.INCOMING -> R.string.incoming_call
            else -> R.string.outgoing_call
        },
    )
    val totalSeconds = duration.seconds
    return stringResource(R.string.call_history_summary, directionLabel, totalSeconds / 60, totalSeconds % 60)
}

private val HISTORY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@Composable
private fun AccountDetail(
    account: SipAccount,
    repository: AccountRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: () -> Unit,
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
                Button(onClick = onCall) { Text(stringResource(R.string.call)) }
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
private fun DialScreen(
    account: SipAccount,
    initialDestination: String,
    callManager: CallManager,
    mediaManager: MediaManager,
    audioRoutes: AudioRouteManager,
    onBack: () -> Unit,
) {
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    var activeCallId by remember { mutableStateOf<CallId?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sessions by callManager.sessions.collectAsStateWithLifecycle(emptyList())
    val activeSession = sessions.firstOrNull { it.id == activeCallId }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            scope.launch { activeCallId = callManager.call(account.id, destination) }
        } else {
            permissionDenied = true
        }
    }
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.call)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(account.displayName, style = MaterialTheme.typography.headlineSmall)
            Field(stringResource(R.string.destination_number), destination) { destination = it }
            if (permissionDenied) {
                Text(stringResource(R.string.microphone_permission_required), color = MaterialTheme.colorScheme.error)
            }
            activeSession?.let { session ->
                Text(stringResource(R.string.remote_uri_value, session.remoteUri))
                Text(stringResource(session.state.statusLabel()))
                if (session.state == CallState.Connected || session.state == CallState.Held) {
                    InCallControls(session.id, callManager, mediaManager, audioRoutes)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = destination.isNotBlank() && (activeSession == null || activeSession.state.isTerminal()),
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            permissionDenied = false
                            scope.launch { activeCallId = callManager.call(account.id, destination) }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) { Text(stringResource(R.string.call)) }
                if (activeSession != null && !activeSession.state.isTerminal()) {
                    Button(onClick = { scope.launch { callManager.end(activeSession.id) } }) {
                        Text(stringResource(R.string.hang_up))
                    }
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(
    session: CallSession,
    callManager: CallManager,
    mediaManager: MediaManager,
    audioRoutes: AudioRouteManager,
    onDismiss: () -> Unit,
) {
    var permissionDenied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sessions by callManager.sessions.collectAsStateWithLifecycle(emptyList())
    val current = sessions.firstOrNull { it.id == session.id } ?: session
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            scope.launch { callManager.answer(current.id) }
        } else {
            permissionDenied = true
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.incoming_call_title)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.incoming_from_value, current.remoteUri), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(current.state.statusLabel()))
            if (permissionDenied) {
                Text(stringResource(R.string.microphone_permission_required), color = MaterialTheme.colorScheme.error)
            }
            if (current.state == CallState.Connected || current.state == CallState.Held) {
                InCallControls(current.id, callManager, mediaManager, audioRoutes)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    current.state == CallState.Incoming || current.state == CallState.Ringing -> {
                        Button(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                permissionDenied = false
                                scope.launch { callManager.answer(current.id) }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) { Text(stringResource(R.string.accept)) }
                        TextButton(onClick = { scope.launch { callManager.reject(current.id) } }) {
                            Text(stringResource(R.string.decline))
                        }
                    }
                    current.state.isTerminal() -> {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                    }
                    else -> {
                        Button(onClick = { scope.launch { callManager.end(current.id) } }) {
                            Text(stringResource(R.string.hang_up))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InCallControls(
    callId: CallId,
    callManager: CallManager,
    mediaManager: MediaManager,
    audioRoutes: AudioRouteManager,
) {
    val media by mediaManager.observe(callId).collectAsStateWithLifecycle()
    val routes by audioRoutes.availableRoutes.collectAsStateWithLifecycle()
    val selected by audioRoutes.selectedRoute.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showKeypad by remember(callId) { mutableStateOf(false) }
    var enteredDigits by remember(callId) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { mediaManager.setMuted(callId, !media.muted) } }) {
                Text(stringResource(if (media.muted) R.string.unmute else R.string.mute))
            }
            Button(onClick = { scope.launch { mediaManager.setHeld(callId, !media.held) } }) {
                Text(stringResource(if (media.held) R.string.resume else R.string.hold))
            }
            Button(onClick = { showKeypad = !showKeypad }) {
                Text(stringResource(if (showKeypad) R.string.hide_keypad else R.string.keypad))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            routes.forEach { route ->
                FilterChip(
                    selected = route == selected,
                    onClick = { scope.launch { audioRoutes.select(route) } },
                    label = { Text(route.label()) },
                )
            }
        }
        if (showKeypad) {
            if (enteredDigits.isNotEmpty()) {
                Text(stringResource(R.string.dtmf_digits_value, enteredDigits))
            }
            DTMF_KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { digit ->
                        Button(
                            enabled = !media.held,
                            onClick = {
                                enteredDigits += digit
                                scope.launch { callManager.sendDtmf(callId, digit) }
                            },
                        ) { Text(digit.toString()) }
                    }
                }
            }
            if (media.held) {
                Text(stringResource(R.string.dtmf_unavailable_on_hold))
            }
        }
    }
}

private const val DTMF_KEYS = "123456789*0#"

@Composable
private fun AudioRoute.label(): String = when (this) {
    AudioRoute.Earpiece -> stringResource(R.string.earpiece)
    AudioRoute.Speaker -> stringResource(R.string.speaker)
    is AudioRoute.WiredHeadset -> name ?: stringResource(R.string.headset)
    is AudioRoute.Bluetooth -> name
}

private fun CallState.statusLabel(): Int = when (this) {
    CallState.Preparing, CallState.Calling -> R.string.calling_status
    CallState.EarlyMedia, CallState.Ringing, CallState.Incoming -> R.string.ringing_status
    CallState.Connecting -> R.string.connecting_status
    CallState.Connected, CallState.Held, CallState.Transferring -> R.string.connected_status
    CallState.Disconnecting -> R.string.ending_status
    is CallState.Disconnected -> R.string.call_ended_status
    is CallState.Failed -> R.string.call_failed_status
}

private fun CallState.isTerminal(): Boolean = this is CallState.Disconnected || this is CallState.Failed

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
