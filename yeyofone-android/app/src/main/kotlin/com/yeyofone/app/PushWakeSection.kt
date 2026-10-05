package com.yeyofone.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.SipAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PushWakeState(val credential: PushCredential?, val status: PushRelayClient.Status?)

/** Import, status and removal of the account's operator-issued push-relay device credential. */
@Composable
internal fun PushWakeRows(account: SipAccount) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }
    val relayConfigured = BuildConfig.PUSH_RELAY_URL.startsWith("https://", ignoreCase = true)
    val state by produceState(PushWakeState(null, null), account, revision) {
        value = withContext(Dispatchers.IO) {
            PushWakeState(PushRelayClient.credential(context, account), PushRelayClient.lastStatus(context, account.id))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            stringResource(pushStatusText(relayConfigured, state)),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
        state.credential?.let {
            Text(
                stringResource(R.string.push_credential_device, it.device),
                Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }
        if (relayConfigured) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { importOpen = true }) {
                    Text(stringResource(if (state.credential == null) R.string.push_import else R.string.push_replace))
                }
                if (state.credential != null) {
                    TextButton(onClick = { removeOpen = true }) {
                        Text(stringResource(R.string.push_remove), color = AccentRed)
                    }
                }
            }
        }
    }

    if (importOpen) {
        ImportCredentialDialog(
            onDismiss = { importOpen = false },
            onImport = { json, onError ->
                scope.launch {
                    val failure = withContext(Dispatchers.IO) {
                        runCatching { PushRelayClient.importCredential(context, account, json) }
                            .onSuccess {
                                PushRelayClient.cachedToken(context)?.let { token ->
                                    PushRelayClient.register(context, account, token)
                                }
                            }
                            .exceptionOrNull()
                    }
                    if (failure == null) {
                        importOpen = false
                        revision++
                    } else {
                        onError(failure.message ?: "")
                    }
                }
            },
        )
    }
    if (removeOpen) {
        AlertDialog(
            onDismissRequest = { removeOpen = false },
            text = { Text(stringResource(R.string.push_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    removeOpen = false
                    scope.launch {
                        withContext(Dispatchers.IO) { PushRelayClient.removeCredential(context, account) }
                        revision++
                    }
                }) { Text(stringResource(R.string.push_remove), color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { removeOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ImportCredentialDialog(onDismiss: () -> Unit, onImport: (String, (String) -> Unit) -> Unit) {
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.push_import_help), fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    json,
                    { json = it; error = null },
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 12.dp),
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(json) { error = it } }, enabled = json.isNotBlank()) {
                Text(stringResource(R.string.push_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun pushStatusText(relayConfigured: Boolean, state: PushWakeState): Int = when {
    !relayConfigured -> R.string.push_status_unavailable
    state.credential == null -> R.string.push_status_not_set_up
    else -> when (state.status) {
        PushRelayClient.Status.REGISTERED -> R.string.push_status_active
        PushRelayClient.Status.CREDENTIAL_REJECTED -> R.string.push_status_renew
        PushRelayClient.Status.SCOPE_REJECTED -> R.string.push_status_not_authorized
        PushRelayClient.Status.FAILED -> R.string.push_status_failed
        PushRelayClient.Status.NOT_CONFIGURED, null -> R.string.push_status_pending
    }
}
