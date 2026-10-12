package com.yeyofone.app.ui.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** A sample participant, as in the desktop room. */
internal data class RoomPerson(val id: String, val name: String, val initials: String, @StringRes val role: Int, val color: Long)

private val Attendees = listOf(
    RoomPerson("you", "Sarah Ndio", "SN", R.string.room_role_host, 0xFF567A9D),
    RoomPerson("alex", "Alex Morgan", "AM", R.string.room_role_product_design, 0xFF6B709D),
    RoomPerson("maya", "Maya Wilson", "MW", R.string.room_role_engineering, 0xFF946E78),
    RoomPerson("daniel", "Daniel Evans", "DE", R.string.room_role_customer_experience, 0xFF5A847E),
    RoomPerson("elena", "Elena Carter", "EC", R.string.room_role_operations, 0xFF8D8064),
    RoomPerson("james", "James Brooks", "JB", R.string.room_role_sales, 0xFF697D9A),
)
private val Topics = listOf(R.string.room_topic_check_in, R.string.room_topic_priorities, R.string.room_topic_decisions)

/** Sample messages are translated ([sample]); your own are shown as typed. */
private data class RoomMessage(val from: String, @StringRes val sample: Int? = null, val text: String = "")

private enum class RoomStage { MEETING, NOTES }
private enum class RoomPanel { CHAT, PARTICIPANTS }

/** The room's state; camera and mic start off so Android only asks when you turn them on. */
private class TeamRoomState {
    val joined = mutableStateListOf("you", "alex", "maya", "daniel")
    var focused by mutableStateOf("alex")
    var stage by mutableStateOf(RoomStage.MEETING)
    var panel by mutableStateOf(RoomPanel.PARTICIPANTS)
    var panelOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var muted by mutableStateOf(true)
    var camera by mutableStateOf(false)
    var talking by mutableStateOf(false)
    var raised by mutableStateOf(false)
    var sharingAgenda by mutableStateOf(false)
    var mediaError by mutableStateOf<Int?>(null)
    var seconds by mutableIntStateOf(0)
    var topic by mutableIntStateOf(0)
    var notes by mutableStateOf("")
    val messages = mutableStateListOf(
        RoomMessage("alex", R.string.room_sample_message_1),
        RoomMessage("maya", R.string.room_sample_message_2),
    )
    var draft by mutableStateOf("")

    val participants get() = Attendees.filter { it.id in joined }
    val invited get() = Attendees.filter { it.id !in joined }
    val speaker get() = participants.firstOrNull { it.id == focused } ?: participants.first()
    val time get() = "%02d:%02d".format(seconds / 60, seconds % 60)

    fun matches(person: RoomPerson) = query.isBlank() || person.name.contains(query.trim(), ignoreCase = true)
    fun speaking(person: RoomPerson) = if (person.id == "you") !muted && talking else person.id == speaker.id && !sharingAgenda
    fun handUp(person: RoomPerson) = person.id == "you" && raised

    fun showOnStage(id: String) {
        focused = id
        stage = RoomStage.MEETING
        sharingAgenda = false
    }

    fun toggleShare() {
        mediaError = null
        sharingAgenda = !sharingAgenda
        if (sharingAgenda) stage = RoomStage.MEETING
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return
        messages += RoomMessage("you", text = text.take(500))
        draft = ""
    }
}

// Fixed room colors from the desktop's conference-room.css. The meeting area is dark in both
// appearances; the panel, notes and shared agenda follow the app's light/dark setting.
private val RoomBackground = Color(0xFF15171C)
private val RoomSurface = Color(0xFF22252C)
private val RoomSpeaker = Color(0xFF1D2026)
private val RoomTile = Color(0xFF262A31)
private val RoomControl = Color(0xFF3A3F4A)
private val RoomOffControl = Color(0xFFF1F5F9)
private val RoomOffInk = Color(0xFF0F172A)
private val RoomBlue = Color(0xFF2563EB)
private val RoomSelectedBorder = Color(0xFF60A5FA)
private val RoomRed = Color(0xFFDC2626)
private val RoomHand = Color(0xFFFBBF24)
private val RoomHandInk = Color(0xFF78350F)
private val RoomSubtle = Color(0xFF94A3B8)
private val RoomNote = Color(0xFF64748B)
private val RoomLabel = Color(0x990F172A)

private fun pick(light: Long, dark: Long) = Color(if (Palette.isDark) dark else light)
private val Paper get() = pick(0xFFFFFFFF, 0xFF1E2026)
private val Paper2 get() = pick(0xFFF8FAFC, 0xFF1A1C21)
private val PaperMuted get() = pick(0xFFF1F5F9, 0xFF2A2D34)
private val PaperBorder get() = pick(0xFFE2E8F0, 0xFF343842)
private val Ink get() = pick(0xFF0F172A, 0xFFF1F5F9)
private val Ink3 get() = pick(0xFF475569, 0xFFB4BCC8)
private val InkMuted get() = pick(0xFF64748B, 0xFF949CAB)
private val Tint get() = pick(0xFFEFF6FF, 0xFF1C2A44)
private val Accent get() = pick(0xFF2563EB, 0xFF3B82F6)
private val ErrorBackground get() = pick(0xFFFEF2F2, 0xFF3A1D22)
private val ErrorInk get() = pick(0xFFB91C1C, 0xFFFCA5A5)

/**
 * The Team room: a video conference preview ported from the desktop app's ConferenceRoom.tsx.
 * Your camera and microphone are real local captures shown only on this device; the other
 * participants and the chat are sample data, and nothing you share is sent to anyone. On a phone
 * the desktop's side panel becomes a bottom sheet, and screen sharing is left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamRoomScreen(onClose: () -> Unit) {
    val room = remember { TeamRoomState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meter = remember { RoomMicMeter(scope) }

    fun startMic() {
        val error = meter.start { room.talking = it }
        if (error == null) {
            room.muted = false
            room.mediaError = null
        } else {
            room.mediaError = error
        }
    }
    fun stopMic() {
        meter.stop()
        room.talking = false
        room.muted = true
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            room.camera = true
            room.mediaError = null
        } else {
            room.mediaError = R.string.room_error_camera_denied
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMic() else room.mediaError = R.string.room_error_mic_denied
    }
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            room.seconds++
        }
    }
    // Elena joins a few seconds in, as on the desktop.
    LaunchedEffect(Unit) {
        delay(4_500)
        if ("elena" !in room.joined) room.joined += "elena"
    }
    // Release every capture when the room closes.
    DisposableEffect(Unit) { onDispose { meter.stop() } }
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(RoomBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomHeader(room, onClose)
        RoomTabs(room)
        if (room.stage == RoomStage.NOTES) {
            RoomNotes(room, Modifier.weight(1f))
        } else {
            RoomStageView(room, Modifier.weight(1f))
            RoomStrip(room)
        }
        Text(
            stringResource(R.string.room_preview_note),
            color = RoomNote,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            RoomControl(
                if (room.camera) R.string.room_camera_off else R.string.room_camera_on,
                if (room.camera) Icons.Default.Videocam else Icons.Default.VideocamOff,
                if (room.camera) ControlStyle.NORMAL else ControlStyle.OFF,
            ) {
                when {
                    room.camera -> room.camera = false
                    granted(Manifest.permission.CAMERA) -> { room.camera = true; room.mediaError = null }
                    else -> cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            RoomControl(
                if (room.muted) R.string.room_unmute else R.string.room_mute,
                if (room.muted) Icons.Default.MicOff else Icons.Default.Mic,
                if (room.muted) ControlStyle.OFF else ControlStyle.NORMAL,
            ) {
                when {
                    !room.muted -> stopMic()
                    granted(Manifest.permission.RECORD_AUDIO) -> startMic()
                    else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            RoomControl(
                if (room.sharingAgenda) R.string.room_stop_sharing else R.string.room_share_agenda,
                Icons.Default.IosShare,
                if (room.sharingAgenda) ControlStyle.ON else ControlStyle.NORMAL,
            ) { room.toggleShare() }
            RoomControl(
                if (room.raised) R.string.room_lower_hand else R.string.room_raise_hand,
                Icons.Default.PanTool,
                if (room.raised) ControlStyle.ON else ControlStyle.NORMAL,
            ) { room.raised = !room.raised }
            RoomControl(R.string.room_leave, Icons.Default.CallEnd, ControlStyle.LEAVE, onClose)
        }
    }

    if (room.panelOpen) {
        ModalBottomSheet(
            onDismissRequest = { room.panelOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Paper,
        ) {
            RoomPanelSheet(room)
        }
    }
}

@Composable
private fun RoomHeader(room: TeamRoomState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                "${stringResource(R.string.room_meeting_name)} · ${room.time}",
                color = RoomSubtle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.room_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        HeaderButton(
            if (room.panelOpen) R.string.room_hide_panel else R.string.room_show_panel,
            Icons.Default.People,
            selected = room.panelOpen,
        ) { room.panelOpen = !room.panelOpen }
        HeaderButton(R.string.room_close, Icons.Default.Close, selected = false, onClick = onClose)
    }
}

@Composable
private fun HeaderButton(@StringRes label: Int, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) RoomBlue else RoomSurface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RoomTabs(room: TeamRoomState) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(RoomSurface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(RoomStage.NOTES to R.string.room_tab_notes, RoomStage.MEETING to R.string.room_tab_meeting).forEach { (stage, label) ->
            val selected = room.stage == stage
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(CircleShape)
                    .background(if (selected) RoomControl else Color.Transparent)
                    .clickable(role = Role.Tab) { room.stage = stage }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    color = if (selected) Color.White else RoomSubtle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private enum class ControlStyle { NORMAL, OFF, ON, LEAVE }

@Composable
private fun RoomControl(@StringRes label: Int, icon: ImageVector, style: ControlStyle, onClick: () -> Unit) {
    val (fill, ink) = when (style) {
        ControlStyle.NORMAL -> RoomControl to RoomOffControl
        ControlStyle.OFF -> RoomOffControl to RoomOffInk
        ControlStyle.ON -> RoomBlue to Color.White
        ControlStyle.LEAVE -> RoomRed to Color.White
    }
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(fill).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RoomAvatar(person: RoomPerson, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(person.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(person.initials, color = Color.White, fontSize = (size * 0.36f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoomBadge(icon: ImageVector, fill: Color = RoomBlue, ink: Color = Color.White) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun RoomStageView(room: TeamRoomState, modifier: Modifier) {
    val speaker = room.speaker
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RoomSurface)) {
        if (room.sharingAgenda) {
            SharedAgenda(room)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(RoomSpeaker)
                    .background(
                        Brush.radialGradient(
                            0f to Color(speaker.color).copy(alpha = .55f),
                            .62f to Color.Transparent,
                        ),
                    ),
            ) {
                if (speaker.id == "you" && room.camera) {
                    val label = stringResource(R.string.room_your_camera)
                    RoomCameraPreview(Modifier.fillMaxSize().semantics { contentDescription = label }) {
                        room.camera = false
                        room.mediaError = it
                    }
                } else {
                    Box(
                        Modifier.align(Alignment.Center).size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)),
                        contentAlignment = Alignment.Center,
                    ) { RoomAvatar(speaker, 120) }
                }
                Text(
                    if (speaker.id == "you") "${speaker.name} (${stringResource(R.string.room_you)})" else speaker.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RoomLabel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        room.mediaError?.let { error ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ErrorBackground)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(error), color = ErrorInk, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { room.mediaError = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.room_dismiss), tint = ErrorInk, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SharedAgenda(room: TeamRoomState) {
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.IosShare, null, tint = Accent, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.room_shared_label), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.room_shared_line1) + "\n" + stringResource(R.string.room_shared_line2),
            color = Ink,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        Topics.forEachIndexed { index, title ->
            val current = room.topic == index
            Text(
                "${index + 1}. ${stringResource(title)}",
                color = if (current) Accent else Ink3,
                fontSize = 17.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun RoomStrip(room: TeamRoomState) {
    val stripLabel = stringResource(R.string.room_participants)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = stripLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        room.participants.forEach { person -> RoomTileView(room, person) }
    }
}

@Composable
private fun RoomTileView(room: TeamRoomState, person: RoomPerson) {
    val you = person.id == "you"
    val selected = person.id == room.speaker.id
    val label = "${stringResource(R.string.room_show_on_stage)}: ${person.name}"
    Box(
        Modifier
            .width(140.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(2.dp, if (selected) RoomSelectedBorder else Color.Transparent), RoundedCornerShape(12.dp))
            .background(RoomTile)
            .background(Brush.radialGradient(0f to Color(person.color).copy(alpha = .45f), .7f to Color.Transparent))
            .clickable(role = Role.Button) { room.showOnStage(person.id) }
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        // CameraX shows one preview at a time: your camera is on the stage when you're there.
        if (you && room.camera && room.speaker.id != "you") {
            RoomCameraPreview(Modifier.fillMaxSize()) {
                room.camera = false
                room.mediaError = it
            }
        } else {
            Box(Modifier.align(Alignment.Center).padding(bottom = 14.dp)) { RoomAvatar(person, 48) }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (you) stringResource(R.string.room_you) else person.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (room.handUp(person)) RoomBadge(Icons.Default.PanTool, RoomHand, RoomHandInk)
            if (you && room.muted) {
                RoomBadge(Icons.Default.MicOff, RoomRed)
            } else if (room.speaking(person)) {
                RoomBadge(Icons.Default.GraphicEq)
            }
        }
    }
}

@Composable
private fun RoomNotes(room: TeamRoomState, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.room_agenda), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Topics.forEachIndexed { index, title ->
            val current = room.topic == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (current) Tint else Color.Transparent)
                    .clickable(role = Role.Button) { room.topic = index }
                    .semantics { this.selected = current }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(if (current) Accent else PaperMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = if (current) Color.White else InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(title),
                    color = if (current) Accent else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Text(
            stringResource(R.string.room_topic_decisions),
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        val notesLabel = stringResource(R.string.room_topic_decisions)
        BasicTextField(
            value = room.notes,
            onValueChange = { room.notes = it.take(2000) },
            textStyle = TextStyle(color = Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = notesLabel },
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Paper2)
                        .border(1.dp, PaperBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    if (room.notes.isEmpty()) Text(stringResource(R.string.room_notes_hint), color = InkMuted, fontSize = 15.sp)
                    field()
                }
            },
        )
        Text(stringResource(R.string.room_notes_note), color = InkMuted, fontSize = 12.sp)
    }
}

@Composable
private fun RoomPanelSheet(room: TeamRoomState) {
    Column(Modifier.fillMaxWidth().height(520.dp).imePadding()) {
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelTab(room, RoomPanel.CHAT, stringResource(R.string.room_chat), null, Modifier.weight(1f))
            PanelTab(room, RoomPanel.PARTICIPANTS, stringResource(R.string.room_participants), room.participants.size, Modifier.weight(1f))
        }
        if (room.panel == RoomPanel.PARTICIPANTS) RoomRoster(room) else RoomChat(room)
    }
}

@Composable
private fun PanelTab(room: TeamRoomState, panel: RoomPanel, label: String, count: Int?, modifier: Modifier) {
    val selected = room.panel == panel
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Tint else Color.Transparent)
            .clickable(role = Role.Tab) { room.panel = panel }
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (selected) Accent else InkMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Text(
                "$count",
                color = if (selected) Accent else InkMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(PaperMuted).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun RoomRoster(room: TeamRoomState) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val searchLabel = stringResource(R.string.room_search)
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(PaperMuted).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Search, null, tint = InkMuted, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = room.query,
                    onValueChange = { room.query = it.take(64) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.weight(1f).semantics { contentDescription = searchLabel },
                    decorationBox = { field ->
                        if (room.query.isEmpty()) Text(searchLabel, color = InkMuted, fontSize = 15.sp)
                        field()
                    },
                )
            }
        }
        item { SectionTitle(stringResource(R.string.room_on_the_call)) }
        items(room.participants.filter(room::matches), key = { "on-${it.id}" }) { person ->
            RosterRow(room, person) {
                if (room.speaking(person)) {
                    Icon(
                        Icons.Default.GraphicEq,
                        stringResource(R.string.room_speaking),
                        tint = Accent,
                        modifier = Modifier.size(36.dp).padding(8.dp),
                    )
                } else {
                    val pinLabel = "${stringResource(R.string.room_show_on_stage)}: ${person.name}"
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) {
                            room.showOnStage(person.id)
                            room.panelOpen = false
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.PushPin, pinLabel, tint = InkMuted, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        if (room.invited.isNotEmpty()) {
            item { SectionTitle("${stringResource(R.string.room_invited)} ${room.invited.size}") }
            items(room.invited.filter(room::matches), key = { "invited-${it.id}" }) { person ->
                RosterRow(room, person) {
                    Text(
                        stringResource(R.string.room_admit),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RoomBlue)
                            .clickable(role = Role.Button) { room.joined += person.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = InkMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun RosterRow(room: TeamRoomState, person: RoomPerson, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoomAvatar(person, 36)
        Column(Modifier.weight(1f)) {
            Text(
                if (person.id == "you") "${person.name} (${stringResource(R.string.room_you)})" else person.name,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (room.handUp(person)) R.string.room_hand_raised else person.role),
                color = InkMuted,
                fontSize = 13.sp,
            )
        }
        trailing()
    }
}

@Composable
private fun RoomChat(room: TeamRoomState) {
    val listState = rememberLazyListState()
    LaunchedEffect(room.messages.size) { listState.animateScrollToItem(room.messages.lastIndex.coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(room.messages) { message ->
                val person = Attendees.first { it.id == message.from }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoomAvatar(person, 32)
                    Column {
                        Text(
                            if (message.from == "you") stringResource(R.string.room_you) else person.name,
                            color = Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(message.sample?.let { stringResource(it) } ?: message.text, color = Ink3, fontSize = 15.sp)
                    }
                }
            }
        }
        val sendLabel = stringResource(R.string.room_send)
        val messageLabel = stringResource(R.string.room_message_everyone)
        val canSend = room.draft.isNotBlank()
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = room.draft,
                onValueChange = { room.draft = it.take(500) },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { room.sendMessage() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = messageLabel },
                decorationBox = { field ->
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(PaperMuted).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (room.draft.isEmpty()) Text(messageLabel, color = InkMuted, fontSize = 15.sp)
                        field()
                    }
                },
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) RoomBlue else RoomBlue.copy(alpha = .5f))
                    .clickable(enabled = canSend, role = Role.Button) { room.sendMessage() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, sendLabel, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Text(
            stringResource(R.string.room_chat_note),
            color = InkMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
