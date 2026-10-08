@file:OptIn(ExperimentalMaterial3Api::class)

package com.yeyofone.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeyofone.app.data.model.toCallSummary
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.data.repository.CallRepository
import com.yeyofone.app.ui.accounts.AccountsScreen
import com.yeyofone.app.ui.call.CallOptionsScreen
import com.yeyofone.app.ui.call.OutgoingCallScreen
import com.yeyofone.app.ui.call.TransferCallScreen
import com.yeyofone.app.ui.callended.CallEndedScreen
import com.yeyofone.app.ui.callhistory.CallHistoryScreen
import com.yeyofone.app.ui.callhistory.CallHistoryViewModel
import com.yeyofone.app.ui.chat.ChatScreen
import com.yeyofone.app.ui.chat.ChatViewModel
import com.yeyofone.app.ui.contacts.ContactsScreen
import com.yeyofone.app.ui.components.BottomNavigationBar
import com.yeyofone.app.ui.components.CALLS_NAVIGATION
import com.yeyofone.app.ui.components.CHAT_NAVIGATION
import com.yeyofone.app.ui.components.CONTACTS_NAVIGATION
import com.yeyofone.app.ui.components.HOME_NAVIGATION
import com.yeyofone.app.ui.components.KEYPAD_NAVIGATION
import com.yeyofone.app.ui.components.SETTINGS_NAVIGATION
import com.yeyofone.app.ui.dialpad.DialPadScreen
import com.yeyofone.app.ui.dialpad.DialPadViewModel
import com.yeyofone.app.ui.incomingcall.IncomingCallScreen as IncomingCallContent
import com.yeyofone.app.ui.main.MainScreen
import com.yeyofone.app.ui.settings.SettingsScreen
import com.yeyofone.app.ui.settings.AudioSettingsScreen
import com.yeyofone.app.ui.settings.LanguageSettingsScreen
import com.yeyofone.app.ui.settings.RecordingsScreen
import com.yeyofone.app.ui.settings.IncomingCallsSettingsScreen
import com.yeyofone.app.ui.settings.VideoSettingsScreen
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.BackgroundGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.app.ui.theme.YeyoFoneTheme
import com.yeyofone.app.ui.settings.LanguagePreferences
import com.yeyofone.core.account.AccountDraft
import com.yeyofone.core.account.ContactDraft
import com.yeyofone.core.account.normalizeRegistrarUri
import com.yeyofone.core.model.AccountPreferences
import com.yeyofone.core.model.AudioRoute
import com.yeyofone.core.model.CallDirection
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.CallState
import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.ForwardingState
import com.yeyofone.core.model.MediaState
import com.yeyofone.core.model.NatConfiguration
import com.yeyofone.core.model.RegistrationState
import com.yeyofone.core.model.SecurityMode
import com.yeyofone.core.model.SipAccount
import com.yeyofone.core.model.TransferState
import com.yeyofone.core.model.TransportProtocol

class MainActivity : ComponentActivity() {
    /** A pending notification Accept request; validated against live call state before answering. */
    private val acceptRequest = MutableStateFlow<CallId?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationAccept.callIdFrom(intent)?.let { acceptRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 35+ enforces edge-to-edge for this target; opt in everywhere so every API level
        // lays out the same way and screens own their insets. The light palette needs dark
        // icons; API 26 cannot draw dark navigation icons, so it keeps a dark scrim.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                SystemBarStyle.light(transparent, transparent)
            } else {
                SystemBarStyle.dark(NAVIGATION_SCRIM_API_26)
            },
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        IncomingCallService.start(this)
        NotificationAccept.callIdFrom(intent)?.let { acceptRequest.value = it }
        val app = application as YeyoFoneApplication
        setContent {
            RequestBackgroundCallPermissions()
            YeyoFoneTheme {
                val viewModel: YeyoFoneViewModel = viewModel(factory = YeyoFoneViewModel.Factory(app))
                val callHistoryViewModel: CallHistoryViewModel = viewModel(
                    factory = CallHistoryViewModel.Factory(CallRepository(app.callHistory)),
                )
                val dialPadViewModel: DialPadViewModel = viewModel()
                val chatViewModel: ChatViewModel = viewModel()
                val pendingAccept by acceptRequest.collectAsStateWithLifecycle()
                AccountsApp(
                    viewModel, callHistoryViewModel, dialPadViewModel, chatViewModel,
                    pendingAccept = pendingAccept,
                    onAcceptHandled = { acceptRequest.value = null },
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

@Composable
private fun AccountsApp(
    viewModel: YeyoFoneViewModel,
    callHistoryViewModel: CallHistoryViewModel,
    dialPadViewModel: DialPadViewModel,
    chatViewModel: ChatViewModel,
    pendingAccept: CallId?,
    onAcceptHandled: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (pendingAccept != null) {
        val context = LocalContext.current
        // Drop requests for calls that ended, were answered or never matched a live session.
        LaunchedEffect(pendingAccept, state.sessions) {
            if (acceptAction(pendingAccept, state.sessions, context.hasMicrophone()) == AcceptAction.DISCARD) {
                onAcceptHandled()
            }
        }
        LaunchedEffect(pendingAccept) {
            delay(ACCEPT_REQUEST_TIMEOUT_MS)
            onAcceptHandled()
        }
    }
    val preferences by viewModel.accountPreferences.collectAsStateWithLifecycle()
    val forwarding by viewModel.forwardingState.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callHistoryState by callHistoryViewModel.uiState.collectAsStateWithLifecycle()

    // Call waiting: at most one call is ever "in the foreground" (full-screen ring, or the
    // in-progress screen once answered/dialed), tracked via state.activeCallId for both
    // directions. A second non-terminal call never hijacks that screen - it surfaces as a small
    // banner instead (see CallWaitingBanner below), the same way attended transfer already keeps
    // the original caller visible while consulting. CallCoordinator caps concurrent calls at two,
    // so at most one "waiting" call can exist at a time.
    val nonTerminal = state.sessions.filter { !it.state.isTerminal() && it.id !in state.dismissedIncomingCalls }
    val activeCall = nonTerminal.firstOrNull { it.id == state.activeCallId }
    // Nothing has been answered/dialed yet: the sole (or first-arriving) ringing call takes the
    // full incoming-call screen, exactly as before this feature existed.
    val primaryRinging = if (activeCall == null) {
        nonTerminal.firstOrNull {
            it.direction == CallDirection.INCOMING && (it.state == CallState.Incoming || it.state == CallState.Ringing)
        }
    } else null
    val waitingCall = nonTerminal.firstOrNull { it.id != activeCall?.id && it.id != primaryRinging?.id }
    // Outgoing calls keep using AppScreen.Dial's own OutgoingCallScreen rendering below; only an
    // incoming call (ringing, or already answered) uses the IncomingCallScreen wrapper.
    val showIncomingWrapper = primaryRinging ?: activeCall?.takeIf { it.direction == CallDirection.INCOMING }

    Box(Modifier.fillMaxSize()) {
    if (showIncomingWrapper != null) {
        IncomingCallScreen(
            showIncomingWrapper, state, viewModel,
            pendingAccept = pendingAccept?.takeIf { it == showIncomingWrapper.id },
            onAcceptHandled = onAcceptHandled,
        )
    } else {

    // Same rule as the ring/waiting split above: a second call's own ended-call summary must
    // never take over the screen while a different call (activeCall) is still in progress - a
    // missed-call notification (posted separately by IncomingCallService) is enough for that case.
    val endedCall = if (activeCall == null) {
        state.sessions.lastOrNull { session ->
            session.state.isTerminal() && session.id !in state.dismissedIncomingCalls &&
                (session.id == state.activeCallId || session.direction == CallDirection.INCOMING)
        }
    } else null
    if (endedCall != null) {
        CallEndedScreen(
            summary = endedCall.toCallSummary(),
            onCallAgain = {
                viewModel.dismissCallSummary(endedCall.id)
                viewModel.dial(endedCall.accountId, endedCall.remoteUri.toSipIdentity().extension)
            },
            onSendMessage = if (BuildConfig.DEBUG) {
                {
                    viewModel.dismissCallSummary(endedCall.id)
                    chatViewModel.openConversation(endedCall.remoteUri)
                    viewModel.showChat()
                }
            } else {
                null
            },
            onClose = {
                viewModel.dismissCallSummary(endedCall.id)
                viewModel.showHistory()
            },
        )
        return
    }

    val navigateFromMenu: (Int) -> Unit = { destination ->
        when (destination) {
            HOME_NAVIGATION -> viewModel.showHome()
            CALLS_NAVIGATION -> viewModel.showHistory()
            CONTACTS_NAVIGATION -> viewModel.showContacts()
            KEYPAD_NAVIGATION -> state.accounts.firstOrNull()?.let { viewModel.dial(it.id) }
            CHAT_NAVIGATION -> viewModel.showChat()
            SETTINGS_NAVIGATION -> viewModel.showSettings()
        }
    }

    when (val current = state.screen) {
        AppScreen.Home -> {
            val primary = state.accounts.firstOrNull()
            val registration = primary?.let { viewModel.observeRegistration(it.id).collectAsStateWithLifecycle().value }
            val primaryForwarding = primary?.let { forwarding[it.id.value] } ?: ForwardingState()
            LaunchedEffect(primary?.id) { primary?.let { viewModel.refreshForwarding(it) } }
            MainScreen(
                primaryAccount = primary,
                isRegistered = registration is RegistrationState.Registered || registration is RegistrationState.Refreshing,
                favoriteContacts = contacts.filter { it.favorite },
                recentCalls = callHistoryState.calls,
                onKeypad = { primary?.let { viewModel.dial(it.id) } },
                onContacts = viewModel::showContacts,
                onHistory = viewModel::showHistory,
                onAccountClick = { viewModel.showAccount(it.id) },
                onAddAccount = { viewModel.editAccount(null) },
                onCallContact = { contact -> primary?.let { viewModel.dial(it.id, contact.number) } },
                onAddContact = viewModel::showContacts,
                onCallBack = { viewModel.dial(it.accountId, it.dialDestination) },
                onNavigationItemSelected = navigateFromMenu,
                forwardingDestination = primaryForwarding.destination.takeIf { primaryForwarding.enabled },
            )
        }
        AppScreen.Accounts -> AccountsScreen(
            accounts = state.accounts,
            onAdd = { viewModel.editAccount(null) },
            onOpen = { viewModel.showAccount(it.id) },
            onEnabledChange = { account, enabled -> viewModel.setAccountEnabled(account.id, enabled) },
            onNavigationItemSelected = navigateFromMenu,
        )
        AppScreen.Contacts -> ContactsScreen(
            contacts = contacts,
            onCall = { contact -> state.accounts.firstOrNull()?.let { viewModel.dial(it.id, contact.number) } },
            onSave = { draft -> viewModel.saveContact(draft) },
            onSetFavorite = { id, favorite -> viewModel.setContactFavorite(id, favorite) },
            onDelete = { id -> viewModel.deleteContact(id) },
            onNavigationItemSelected = navigateFromMenu,
        )
        is AppScreen.Detail -> state.accounts.firstOrNull { it.id == current.accountId }?.let { account ->
            AccountDetail(account, viewModel)
        }
        is AppScreen.Edit -> AccountEditor(
            state.accounts.firstOrNull { it.id == current.accountId },
            viewModel,
            viewModel::showAccounts,
        )
        is AppScreen.Dial -> state.accounts.firstOrNull { it.id == current.accountId }?.let { account ->
            val activeSession = state.sessions.firstOrNull {
                it.id == state.activeCallId && !it.state.isTerminal()
            }
            if (activeSession == null) {
                LaunchedEffect(current.accountId, current.destination) {
                    dialPadViewModel.setInitialNumber(current.destination)
                }
                DialPadScreen(
                    viewModel = dialPadViewModel,
                    onNavigateBack = { viewModel.showAccount(account.id) },
                    onCall = { destination ->
                        viewModel.startCall(account.id, destination)
                        dialPadViewModel.clearNumber()
                    },
                    onAddContact = viewModel::showContacts,
                )
            } else {
                val media by viewModel.observeMedia(activeSession.id).collectAsStateWithLifecycle()
                val consultationSession = state.sessions.firstOrNull { it.id == state.consultationCallId }
                OutgoingCallHost(
                    session = activeSession,
                    media = media,
                    speakerOn = state.selectedRoute == AudioRoute.Speaker,
                    availableRoutes = state.availableRoutes,
                    selectedRoute = state.selectedRoute,
                    consultationSession = consultationSession,
                    onNavigateBack = { viewModel.showAccount(account.id) },
                    onSpeakerChange = { enabled ->
                        val route = if (enabled) AudioRoute.Speaker else
                            state.availableRoutes.firstOrNull { it != AudioRoute.Speaker }
                        route?.let(viewModel::selectAudioRoute)
                    },
                    onMuteChange = { viewModel.setMuted(activeSession.id, it) },
                    onHoldChange = { viewModel.setHeld(activeSession.id, it) },
                    onSelectRoute = viewModel::selectAudioRoute,
                    onDtmf = { viewModel.sendDtmf(activeSession.id, it) },
                    onTransfer = { viewModel.transfer(activeSession.id, it) },
                    onStartConsultation = { viewModel.startConsultation(activeSession.accountId, it) },
                    onCompleteTransfer = {
                        consultationSession?.let { viewModel.completeTransfer(activeSession.id, it.id) }
                    },
                    onReturnToCaller = {
                        consultationSession?.let { viewModel.returnToCaller(activeSession.id, it) }
                    },
                    onEndCall = { viewModel.end(activeSession.id) },
                )
            }
        }
        AppScreen.History -> CallHistoryScreen(
            uiState = callHistoryState,
            accountIds = state.accounts.mapTo(mutableSetOf()) { it.id },
            onTabSelected = callHistoryViewModel::onTabSelected,
            onNavigationItemSelected = navigateFromMenu,
            onCallBack = { entry ->
                viewModel.dial(entry.accountId, entry.dialDestination)
            },
            onClear = callHistoryViewModel::clear,
        )
        AppScreen.Chat -> ChatScreen(
            viewModel = chatViewModel,
            onBack = viewModel::showAccounts,
            onVoiceCall = { state.accounts.firstOrNull()?.let { viewModel.dial(it.id) } },
            onNavigationItemSelected = navigateFromMenu,
        )
        AppScreen.Settings -> SettingsScreen(
            accountCount = state.accounts.count { it.enabled },
            onAccountsClick = viewModel::showAccounts,
            onAudioClick = viewModel::showAudioSettings,
            onVideoClick = viewModel::showVideoSettings,
            onIncomingCallsClick = viewModel::showIncomingCallsSettings,
            onLanguageClick = viewModel::showLanguageSettings,
            onRecordingsClick = viewModel::showRecordings,
            onNavigationItemSelected = navigateFromMenu,
        )
        AppScreen.AudioSettings -> AudioSettingsScreen(
            availableRoutes = state.availableRoutes,
            selectedRoute = state.selectedRoute,
            onSelectRoute = viewModel::selectAudioRoute,
            onBack = viewModel::showSettings,
        )
        AppScreen.VideoSettings -> VideoSettingsScreen(onBack = viewModel::showSettings)
        AppScreen.IncomingCallsSettings -> {
            val account = state.accounts.firstOrNull()
            val accountPreferences = account?.let { preferences[it.id.value] } ?: AccountPreferences()
            val accountForwarding = account?.let { forwarding[it.id.value] } ?: ForwardingState()
            LaunchedEffect(account?.id) { account?.let { viewModel.refreshForwarding(it) } }
            IncomingCallsSettingsScreen(
                preferences = accountPreferences,
                forwarding = accountForwarding,
                onBack = viewModel::showSettings,
                onToggle = { toggle, enabled -> account?.let { viewModel.setPreference(it.id, toggle, enabled) } },
                onSaveForwarding = { newState, onResult ->
                    account?.let { viewModel.setForwarding(it, newState, onResult) } ?: onResult(ForwardingClient.Result.NotConfigured)
                },
                onOpenAudioSettings = viewModel::showAudioSettings,
            )
        }
        AppScreen.LanguageSettings -> LanguageSettingsScreen(onBack = viewModel::showSettings)
        AppScreen.Recordings -> RecordingsScreen(onBack = viewModel::showSettings)
    }
    }
    if (waitingCall != null) {
        CallWaitingBanner(
            session = waitingCall,
            isRinging = waitingCall.state == CallState.Incoming || waitingCall.state == CallState.Ringing,
            modifier = Modifier.align(Alignment.TopCenter),
            onAccept = { viewModel.answerWaitingCall(waitingCall.id, activeCall?.id) },
            onDecline = { viewModel.reject(waitingCall.id) },
            onSwap = { activeCall?.let { viewModel.swapActiveCall(it.id, waitingCall.id) } },
        )
    }
    }
}

@Composable
private fun AccountList(
    accounts: List<SipAccount>,
    onAdd: () -> Unit,
    onHistory: () -> Unit,
    onKeypad: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
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
        bottomBar = {
            BottomNavigationBar(
                selectedIndex = CONTACTS_NAVIGATION,
                onItemSelected = {
                    when (it) {
                        CALLS_NAVIGATION -> onHistory()
                        KEYPAD_NAVIGATION -> onKeypad()
                        CHAT_NAVIGATION -> onChat()
                        SETTINGS_NAVIGATION -> onSettings()
                    }
                },
            )
        },
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
private fun MenuDestinationScreen(
    title: String,
    selectedIndex: Int,
    onNavigationItemSelected: (Int) -> Unit,
) {
    Scaffold(
        bottomBar = {
            BottomNavigationBar(selectedIndex, onNavigationItemSelected)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.feature_coming_soon),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DialScreen(
    account: SipAccount,
    initialDestination: String,
    state: YeyoFoneUiState,
    viewModel: YeyoFoneViewModel,
) {
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    var permissionDenied by remember { mutableStateOf(false) }
    val activeSession = state.sessions.firstOrNull { it.id == state.activeCallId }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            viewModel.startCall(account.id, destination)
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
                val identity = session.remoteUri.toSipIdentity()
                Text(stringResource(R.string.caller_identity_value, identity.displayName, identity.extension))
                Text(stringResource(session.state.statusLabel()))
                if (session.state == CallState.Connected || session.state == CallState.Held) {
                    InCallControls(session.id, state, viewModel)
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
                            viewModel.startCall(account.id, destination)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) { Text(stringResource(R.string.call)) }
                if (activeSession != null && !activeSession.state.isTerminal()) {
                    Button(onClick = { viewModel.end(activeSession.id) }) {
                        Text(stringResource(R.string.hang_up))
                    }
                }
                TextButton(onClick = { viewModel.showAccount(account.id) }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(
    session: CallSession,
    state: YeyoFoneUiState,
    viewModel: YeyoFoneViewModel,
    pendingAccept: CallId? = null,
    onAcceptHandled: () -> Unit = {},
) {
    var permissionDenied by remember { mutableStateOf(false) }
    var actionPending by remember(currentCallKey(session)) { mutableStateOf(false) }
    val current = state.sessions.firstOrNull { it.id == session.id } ?: session
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    if (current.state != CallState.Incoming && current.state != CallState.Ringing && !current.state.isTerminal()) {
        val media by viewModel.observeMedia(current.id).collectAsStateWithLifecycle()
        val consultationSession = state.sessions.firstOrNull { it.id == state.consultationCallId }
        OutgoingCallHost(
            session = current,
            media = media,
            speakerOn = state.selectedRoute == AudioRoute.Speaker,
            availableRoutes = state.availableRoutes,
            selectedRoute = state.selectedRoute,
            consultationSession = consultationSession,
            onNavigateBack = viewModel::showAccounts,
            onSpeakerChange = { enabled ->
                val route = if (enabled) AudioRoute.Speaker else
                    state.availableRoutes.firstOrNull { it != AudioRoute.Speaker }
                route?.let(viewModel::selectAudioRoute)
            },
            onMuteChange = { viewModel.setMuted(current.id, it) },
            onHoldChange = { viewModel.setHeld(current.id, it) },
            onSelectRoute = viewModel::selectAudioRoute,
            onDtmf = { viewModel.sendDtmf(current.id, it) },
            onTransfer = { viewModel.transfer(current.id, it) },
            onStartConsultation = { viewModel.startConsultation(current.accountId, it) },
            onCompleteTransfer = {
                consultationSession?.let { viewModel.completeTransfer(current.id, it.id) }
            },
            onReturnToCaller = {
                consultationSession?.let { viewModel.returnToCaller(current.id, it) }
            },
            onEndCall = { viewModel.end(current.id) },
        )
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            actionPending = true
            viewModel.answer(current.id)
        } else {
            permissionDenied = true
            actionPending = false
        }
    }

    if (current.state == CallState.Incoming || current.state == CallState.Ringing) {
        // Same path for the in-app button and the notification's Accept action: answer only with
        // microphone access, otherwise request it and answer on grant.
        val accept = {
            if (!actionPending) {
                if (context.hasMicrophone()) {
                    permissionDenied = false
                    actionPending = true
                    viewModel.answer(current.id)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        LaunchedEffect(pendingAccept) {
            if (pendingAccept == current.id) {
                onAcceptHandled()
                accept()
            }
        }
        val matchedContactName = contacts.firstOrNull { it.number == current.remoteUri.toSipIdentity().extension }?.displayName
        IncomingCallContent(
            remoteUri = current.remoteUri,
            contactName = matchedContactName,
            permissionDenied = permissionDenied,
            actionsEnabled = !actionPending,
            onAccept = accept,
            onDecline = {
                if (!actionPending) {
                    actionPending = true
                    viewModel.reject(current.id)
                }
            },
        )
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.incoming_call_title)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val identity = current.remoteUri.toSipIdentity()
            Text(stringResource(R.string.caller_identity_value, identity.displayName, identity.extension), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(current.state.statusLabel()))
            if (permissionDenied) {
                Text(stringResource(R.string.microphone_permission_required), color = MaterialTheme.colorScheme.error)
            }
            if (current.state == CallState.Connected || current.state == CallState.Held) {
                InCallControls(current.id, state, viewModel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    current.state == CallState.Incoming || current.state == CallState.Ringing -> {
                        Button(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                permissionDenied = false
                                viewModel.answer(current.id)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) { Text(stringResource(R.string.accept)) }
                        TextButton(onClick = { viewModel.reject(current.id) }) {
                            Text(stringResource(R.string.decline))
                        }
                    }
                    current.state.isTerminal() -> {
                        TextButton(onClick = { viewModel.dismissIncoming(current.id) }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                    else -> {
                        Button(onClick = { viewModel.end(current.id) }) {
                            Text(stringResource(R.string.hang_up))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hosts the active call screen and, when the user taps Transfer or More, swaps it for a
 * dedicated [TransferCallScreen] or [CallOptionsScreen] instead of overlaying a dial pad or
 * option list on top of the call UI in place (the previous inline approach caused the transfer
 * dial pad and the call content to overlap).
 */
@Composable
private fun OutgoingCallHost(
    session: CallSession,
    media: MediaState,
    speakerOn: Boolean,
    availableRoutes: List<AudioRoute>,
    selectedRoute: AudioRoute?,
    consultationSession: CallSession?,
    onNavigateBack: () -> Unit,
    onSpeakerChange: (Boolean) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onHoldChange: (Boolean) -> Unit,
    onSelectRoute: (AudioRoute) -> Unit,
    onDtmf: (Char) -> Unit,
    onTransfer: (String) -> Unit,
    onStartConsultation: (String) -> Unit,
    onCompleteTransfer: () -> Unit,
    onReturnToCaller: () -> Unit,
    onEndCall: () -> Unit,
) {
    var transferOpen by remember(session.id) { mutableStateOf(false) }
    var moreOpen by remember(session.id) { mutableStateOf(false) }
    when {
        transferOpen -> TransferCallScreen(
            currentCallerName = session.remoteUri.toSipIdentity().displayName,
            onCancel = { transferOpen = false },
            onTransfer = {
                onTransfer(it)
                transferOpen = false
            },
        )
        moreOpen -> CallOptionsScreen(
            held = media.held,
            onHoldChange = onHoldChange,
            availableRoutes = availableRoutes,
            selectedRoute = selectedRoute,
            onSelectRoute = onSelectRoute,
            consultationSession = consultationSession,
            onStartConsultation = onStartConsultation,
            onCompleteTransfer = onCompleteTransfer,
            onReturnToCaller = onReturnToCaller,
            onCancel = { moreOpen = false },
        )
        else -> OutgoingCallScreen(
            session = session,
            media = media,
            speakerOn = speakerOn,
            onNavigateBack = onNavigateBack,
            onSpeakerChange = onSpeakerChange,
            onMuteChange = onMuteChange,
            onHoldChange = onHoldChange,
            onDtmf = onDtmf,
            onOpenTransfer = { transferOpen = true },
            onOpenMore = { moreOpen = true },
            onEndCall = onEndCall,
        )
    }
}

private fun currentCallKey(session: CallSession): String = session.id.value

/**
 * The second concurrent call (see [AccountsApp]'s call-waiting computation): a compact overlay,
 * never a full-screen takeover, so the primary call stays visible and in control underneath it.
 * [isRinging] picks the action set: Accept/Decline for a call that hasn't been answered yet, or a
 * single Switch action once it's the one on hold in the background (e.g. after a previous Accept).
 */
@Composable
private fun CallWaitingBanner(
    session: CallSession,
    isRinging: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = session.remoteUri.toSipIdentity()
    Row(
        modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(12.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.call_waiting_banner_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(identity.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                if (isRinging) {
                    stringResource(R.string.extension_value, identity.extension)
                } else {
                    stringResource(R.string.on_hold_label)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isRinging) {
                IconButton(onClick = onDecline) {
                    Icon(Icons.Default.CallEnd, stringResource(R.string.decline), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onAccept) {
                    Icon(Icons.Default.Call, stringResource(R.string.accept), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                IconButton(onClick = onSwap) {
                    Icon(Icons.Default.SwapHoriz, stringResource(R.string.switch_calls))
                }
            }
        }
    }
}

@Composable
private fun InCallControls(
    callId: CallId,
    state: YeyoFoneUiState,
    viewModel: YeyoFoneViewModel,
) {
    val media by viewModel.observeMedia(callId).collectAsStateWithLifecycle()
    val session = state.sessions.firstOrNull { it.id == callId }
    val transfer = session?.transfer ?: TransferState.Idle
    var showKeypad by remember(callId) { mutableStateOf(false) }
    var enteredDigits by remember(callId) { mutableStateOf("") }
    var showTransfer by remember(callId) { mutableStateOf(false) }
    var transferDestination by remember(callId) { mutableStateOf("") }
    var showConsultation by remember(callId) { mutableStateOf(false) }
    var consultationDestination by remember(callId) { mutableStateOf("") }
    val consultation = state.sessions.firstOrNull { it.id == state.consultationCallId }

    LaunchedEffect(consultation?.id, consultation?.state) {
        // A later media update from the peer (e.g. a final re-INVITE) is handled reactively by
        // the native onCallMediaState callback, which reattaches on its own ACTIVE event - no
        // fixed-delay retry needed here.
        if (consultation?.state == CallState.Connected) {
            viewModel.setMuted(consultation.id, false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !media.held,
                onClick = { viewModel.setMuted(callId, !media.muted) },
            ) {
                Text(stringResource(if (media.muted) R.string.unmute else R.string.mute))
            }
            Button(onClick = { viewModel.setHeld(callId, !media.held) }) {
                Text(stringResource(if (media.held) R.string.resume else R.string.hold))
            }
            Button(onClick = { showKeypad = !showKeypad }) {
                Text(stringResource(if (showKeypad) R.string.hide_keypad else R.string.keypad))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !media.held && state.consultationCallId == null &&
                    transfer !is TransferState.Pending && transfer !is TransferState.Succeeded,
                onClick = { showTransfer = !showTransfer },
            ) { Text(stringResource(R.string.transfer)) }
            Button(
                enabled = state.consultationCallId == null && transfer !is TransferState.Pending &&
                    transfer !is TransferState.Succeeded,
                onClick = {
                    showConsultation = true
                    viewModel.setHeld(callId, true)
                },
            ) { Text(stringResource(R.string.consult_transfer)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.availableRoutes.forEach { route ->
                FilterChip(
                    selected = route == state.selectedRoute,
                    onClick = { viewModel.selectAudioRoute(route) },
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
                                viewModel.sendDtmf(callId, digit)
                            },
                        ) { Text(digit.toString()) }
                    }
                }
            }
            if (media.held) {
                Text(stringResource(R.string.dtmf_unavailable_on_hold))
            }
        }
        if (showTransfer) {
            Field(stringResource(R.string.transfer_destination), transferDestination) { transferDestination = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = transferDestination.isNotBlank() && transfer !is TransferState.Pending,
                    onClick = {
                        viewModel.transfer(callId, transferDestination)
                        showTransfer = false
                    },
                ) { Text(stringResource(R.string.transfer_now)) }
                TextButton(onClick = { showTransfer = false }) { Text(stringResource(R.string.cancel)) }
            }
        }
        if (showConsultation) {
            Field(stringResource(R.string.consultation_destination), consultationDestination) {
                consultationDestination = it
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = consultationDestination.isNotBlank() && session != null && media.held,
                    onClick = {
                        val current = session ?: return@Button
                        viewModel.startConsultation(current.accountId, consultationDestination)
                        showConsultation = false
                    },
                ) { Text(stringResource(R.string.start_consultation)) }
                TextButton(onClick = {
                    showConsultation = false
                    viewModel.setHeld(callId, false)
                }) { Text(stringResource(R.string.cancel)) }
            }
            if (!media.held) Text(stringResource(R.string.waiting_for_hold))
        }
        consultation?.let { consult ->
            val identity = consult.remoteUri.toSipIdentity()
            Text(stringResource(R.string.consultation_identity, identity.displayName, identity.extension))
            Text(stringResource(consult.state.statusLabel()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = consult.state == CallState.Connected && media.held,
                    onClick = { viewModel.completeTransfer(callId, consult.id) },
                ) { Text(stringResource(R.string.complete_transfer)) }
                Button(onClick = {
                    viewModel.returnToCaller(callId, consult)
                }) { Text(stringResource(R.string.return_to_caller)) }
            }
        }
        when (transfer) {
            TransferState.Idle -> Unit
            is TransferState.Pending -> Text(stringResource(R.string.transfer_pending, transfer.destination))
            is TransferState.Succeeded -> Text(stringResource(R.string.transfer_succeeded, transfer.destination))
            is TransferState.Failed -> Text(
                stringResource(
                    R.string.transfer_failed,
                    transfer.statusCode?.toString() ?: "—",
                    transfer.reason.orEmpty(),
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val DTMF_KEYS = "123456789*0#"

/** How long a notification Accept waits for its call to appear in the session list. */
private const val ACCEPT_REQUEST_TIMEOUT_MS = 5_000L

private fun android.content.Context.hasMicrophone() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

// Matches the legacy theme navigation bar so white API 26 navigation icons stay legible.
private const val NAVIGATION_SCRIM_API_26 = 0xFF0F172A.toInt()

@Composable
internal fun AudioRoute.label(): String = when (this) {
    AudioRoute.Earpiece -> stringResource(R.string.earpiece)
    AudioRoute.Speaker -> stringResource(R.string.speaker)
    is AudioRoute.WiredHeadset -> name ?: stringResource(R.string.headset)
    is AudioRoute.Bluetooth -> name
}

internal fun CallState.statusLabel(): Int = when (this) {
    CallState.Preparing, CallState.Calling -> R.string.calling_status
    CallState.EarlyMedia, CallState.Ringing, CallState.Incoming -> R.string.ringing_status
    CallState.Answering, CallState.Connecting -> R.string.connecting_status
    CallState.Connected, CallState.Held -> R.string.connected_status
    CallState.Transferring -> R.string.transferring_status
    CallState.Disconnecting -> R.string.ending_status
    is CallState.Disconnected -> R.string.call_ended_status
    is CallState.Failed -> R.string.call_failed_status
}

private fun CallState.isTerminal(): Boolean = this is CallState.Disconnected || this is CallState.Failed

@Composable
private fun AccountEditor(existing: SipAccount?, viewModel: YeyoFoneViewModel, onDone: () -> Unit) {
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
    var ice by remember { mutableStateOf(existing?.nat?.iceEnabled ?: false) }
    var srtp by remember { mutableStateOf(existing?.nat?.srtpEnabled ?: false) }
    var expiry by remember { mutableStateOf(existing?.registrationExpirySeconds?.toString() ?: "300") }
    var voicemail by remember { mutableStateOf(existing?.voicemailNumber.orEmpty()) }
    var callerId by remember { mutableStateOf(existing?.callerId.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val saveFailedMessage = stringResource(R.string.save_failed)

    Scaffold(
        containerColor = BackgroundGray,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existing == null) R.string.add_account else R.string.edit_account),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EditorSectionLabel(stringResource(R.string.account_identity_section))
            }
            item {
                EditorCard {
                    Field(stringResource(R.string.display_name), displayName) { displayName = it }
                    Field(stringResource(R.string.sip_username), username) { username = it; if (authUsername.isBlank()) authUsername = it }
                    Field(stringResource(R.string.authentication_username), authUsername) { authUsername = it }
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(if (existing == null) R.string.password else R.string.new_password_optional)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = editorFieldColors(),
                    )
                }
            }
            item { EditorSectionLabel(stringResource(R.string.section_connection)) }
            item {
                EditorCard {
                    Field(stringResource(R.string.domain), domain) { domain = it }
                    Field(stringResource(R.string.registrar_uri), registrar) { registrar = it }
                    Field(stringResource(R.string.outbound_proxy_optional), proxy) { proxy = it }
                    Field(stringResource(R.string.port), port) { port = it.filter(Char::isDigit) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TransportProtocol.entries.forEach { choice ->
                            FilterChip(
                                selected = transport == choice,
                                onClick = {
                                    // SRTP needs TLS signaling; new TLS accounts default to it.
                                    if (choice != TransportProtocol.TLS) srtp = false
                                    else if (existing == null && transport != choice) srtp = true
                                    transport = choice
                                },
                                label = { Text(choice.name) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Field(stringResource(R.string.registration_expiry), expiry) { expiry = it.filter(Char::isDigit) }
                }
            }
            item { EditorSectionLabel(stringResource(R.string.account_security_section)) }
            item {
                EditorCard {
                    Field(stringResource(R.string.stun_server_optional), stun) { stun = it }
                    Field(stringResource(R.string.turn_server_optional), turn) { turn = it }
                    Field(stringResource(R.string.turn_username_optional), turnUsername) { turnUsername = it }
                    CheckRow(stringResource(R.string.enable_ice), ice) { ice = it }
                    CheckRow(
                        stringResource(R.string.require_srtp),
                        srtp,
                        enabled = transport == TransportProtocol.TLS,
                    ) { srtp = it }
                }
            }
            item { EditorSectionLabel(stringResource(R.string.account_optional_section)) }
            item {
                EditorCard {
                    Field(stringResource(R.string.voicemail_optional), voicemail) { voicemail = it }
                    Field(stringResource(R.string.caller_id_optional), callerId) { callerId = it }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    onClick = {
                        saving = true
                        error = null
                        val secret = password.takeIf(String::isNotEmpty)?.toCharArray()
                        password = ""
                        viewModel.saveAccount(
                            AccountDraft(
                                id = existing?.id, displayName = displayName, username = username,
                                authenticationUsername = authUsername, password = secret, domain = domain,
                                registrarUri = normalizeRegistrarUri(registrar),
                                outboundProxyUri = proxy, port = port.toIntOrNull() ?: 0,
                                transport = transport,
                                securityMode = if (transport == TransportProtocol.TLS) SecurityMode.REQUIRE_SECURE else SecurityMode.ALLOW_INSECURE,
                                nat = NatConfiguration(stun, turn, turnUsername, ice, srtp),
                                registrationExpirySeconds = expiry.toIntOrNull() ?: 0,
                                voicemailNumber = voicemail, callerId = callerId, enabled = existing?.enabled ?: true,
                            ),
                        ) { result ->
                            result.onSuccess { onDone() }.onFailure { error = it.message ?: saveFailedMessage }
                            saving = false
                        }
                    },
                ) { Text(stringResource(R.string.save), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
            item {
                TextButton(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { password = ""; onDone() },
                ) { Text(stringResource(R.string.cancel), color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value, onValueChange, Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = editorFieldColors(),
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange, enabled = enabled)
        Text(label, color = if (enabled) TextPrimary else TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditorCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun EditorSectionLabel(label: String) {
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
    )
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = BorderLight,
    focusedContainerColor = BackgroundGray,
    unfocusedContainerColor = BackgroundGray,
)
