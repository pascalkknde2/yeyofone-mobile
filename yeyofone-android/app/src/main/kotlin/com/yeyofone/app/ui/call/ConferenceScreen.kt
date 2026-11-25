package com.yeyofone.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.data.model.toSipIdentity
import com.yeyofone.app.statusLabel
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.AccentRed
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary
import com.yeyofone.app.ui.theme.PrimaryLight
import com.yeyofone.core.model.CallId
import com.yeyofone.core.model.CallSession
import com.yeyofone.core.model.Contact
import kotlinx.coroutines.delay

/** Everything the conference screen and the add-people flow need, so the call host passes one object. */
data class ConferenceUi(
    /** Calls already joined to the conference. Empty while there is no conference. */
    val members: List<CallSession>,
    /** Someone being added who has not answered yet. */
    val pending: CallSession?,
    val contacts: List<Contact>,
    val onAdd: (Contact) -> Unit,
    val onRemove: (CallId) -> Unit,
    val onEnd: () -> Unit,
    val onMute: (Boolean) -> Unit,
) {
    val active: Boolean get() = members.isNotEmpty()

    /** Numbers already on the call, so the picker doesn't offer them again. */
    val numbersInCall: Set<String>
        get() = (members + listOfNotNull(pending)).map { it.remoteUri.toSipIdentity().extension }.toSet()
}

private class Person(
    val id: String,
    val callId: CallId?,
    val name: String,
    val isSelf: Boolean,
    val muted: Boolean,
    val statusRes: Int?,
)

/**
 * The conference call screen: everyone on the call as a frameless avatar (a ring marks who is
 * speaking), and a single dark toolbar for mute, speaker, who is on the call, adding people,
 * recording and ending the call.
 */
@Composable
fun ConferenceScreen(
    conference: ConferenceUi,
    muted: Boolean,
    speakerOn: Boolean,
    onSpeakerChange: (Boolean) -> Unit,
    recording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    speakingIds: Set<String>,
    onAddPerson: () -> Unit,
) {
    fun nameOf(session: CallSession): String {
        val identity = session.remoteUri.toSipIdentity()
        return conference.contacts.firstOrNull { it.number == identity.extension }?.displayName ?: identity.displayName
    }
    val people = buildList {
        add(Person("you", null, stringResource(R.string.conference_you), true, muted, null))
        conference.members.forEach { add(Person(it.id.value, it.id, nameOf(it), false, false, null)) }
        conference.pending?.let { add(Person(it.id.value, it.id, nameOf(it), false, false, it.state.statusLabel())) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.conference_title),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.conference_count, people.size),
            fontSize = 14.sp, color = CallTextSecondary, modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // With more than two people the one talking (or the first other person) is the spotlight.
            val spotlight = if (people.size > 2) {
                people.firstOrNull { it.id in speakingIds && !it.isSelf } ?: people.firstOrNull { !it.isSelf }
            } else null
            if (spotlight != null) {
                PersonTile(spotlight, spotlight.id in speakingIds, large = true, onRemove = conference.onRemove)
                Spacer(Modifier.height(28.dp))
            }
            val rest = if (spotlight != null) people.filter { it !== spotlight } else people
            rest.chunked(3).forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { PersonTile(it, it.id in speakingIds, large = false, onRemove = conference.onRemove) }
                }
            }
        }
        ConferenceToolbar(
            muted = muted,
            onMuteChange = conference.onMute,
            speakerOn = speakerOn,
            onSpeakerChange = onSpeakerChange,
            count = people.size,
            onAddPerson = onAddPerson,
            recording = recording,
            onRecordingChange = onRecordingChange,
            onEnd = conference.onEnd,
        )
    }
}

@Composable
private fun PersonTile(person: Person, speaking: Boolean, large: Boolean, onRemove: (CallId) -> Unit) {
    val size = if (large) 112.dp else 72.dp
    Column(Modifier.width(if (large) 160.dp else 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size + 12.dp)
                    .border(if (speaking) 3.dp else 0.dp, if (speaking) AccentBlue else Color.Transparent, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape).background(PrimaryLight).alpha(if (person.statusRes != null) 0.55f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(initialsOf(person.name), fontSize = if (large) 36.sp else 24.sp, fontWeight = FontWeight.Bold, color = CallTextPrimary)
            }
            if (person.muted) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(AccentRed),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.MicOff, stringResource(R.string.mute), tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
            if (person.callId != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Color(0xFF475569))
                        .clickable { onRemove(person.callId) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, stringResource(R.string.remove_from_call), tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            person.name, fontSize = if (large) 17.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = CallTextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        person.statusRes?.let {
            Text(stringResource(it), fontSize = 12.sp, color = CallTextSecondary, textAlign = TextAlign.Center)
        }
    }
}

private val ToolbarBackground = Color(0xFF1C1D21)
private val ToolbarSegment = Color(0xFF34363C)

@Composable
private fun ConferenceToolbar(
    muted: Boolean,
    onMuteChange: (Boolean) -> Unit,
    speakerOn: Boolean,
    onSpeakerChange: (Boolean) -> Unit,
    count: Int,
    onAddPerson: () -> Unit,
    recording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    onEnd: () -> Unit,
) {
    var seconds by remember(recording) { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        if (recording) while (true) { delay(1_000); seconds++ }
    }
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(36.dp)).background(ToolbarBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ToolbarIcon(
            if (muted) Icons.Default.MicOff else Icons.Default.Mic,
            stringResource(if (muted) R.string.unmute else R.string.mute),
            tint = if (muted) AccentRed else Color.White,
        ) { onMuteChange(!muted) }
        ToolbarIcon(
            if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PhoneInTalk,
            stringResource(R.string.speaker),
            tint = if (speakerOn) AccentBlue else Color.White,
        ) { onSpeakerChange(!speakerOn) }
        Row(
            Modifier.height(44.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Group, stringResource(R.string.conference_count, count), tint = Color.White, modifier = Modifier.size(22.dp))
            Text(count.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        ToolbarIcon(Icons.Default.PersonAdd, stringResource(R.string.add_call), tint = Color.White, onClick = onAddPerson)
        Row(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(if (recording) ToolbarSegment else Color.Transparent)
                .clickable { onRecordingChange(!recording) }
                .padding(horizontal = if (recording) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                stringResource(if (recording) R.string.stop_recording else R.string.recording_indicator),
                tint = if (recording) AccentRed else Color.White, modifier = Modifier.size(22.dp),
            )
            if (recording) {
                Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Box(
            Modifier.width(52.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(AccentRed).clickable(onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.CallEnd, stringResource(R.string.hang_up), tint = Color.White, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

internal fun initialsOf(name: String): String = name.split(' ', '.', '-', '_')
    .filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
