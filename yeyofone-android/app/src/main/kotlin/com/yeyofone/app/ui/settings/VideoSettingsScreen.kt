package com.yeyofone.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.*

private val VideoOrange = Color(0xFFF97316)
private val VideoOrangeLight = Color(0xFFFFF7ED)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var frontCamera by remember { mutableStateOf(true) }
    var resolution by remember { mutableIntStateOf(1) }
    var layout by remember { mutableIntStateOf(0) }
    val resolutions = listOf("480p", "720p HD", "1080p Full HD", "4K Ultra HD")
    val layouts = listOf(
        R.string.speaker_view to R.string.speaker_view_subtitle,
        R.string.grid_view to R.string.grid_view_subtitle,
        R.string.gallery_view to R.string.gallery_view_subtitle,
    )

    Scaffold(containerColor = BackgroundGray) { insets ->
        Column(Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding())) {
            Row(
                Modifier.fillMaxWidth()
                    .background(CardWhite)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary)
                }
                Text(stringResource(R.string.video_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column {
                        VideoLabel(stringResource(R.string.camera_preview_section))
                        Box(
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(CardGradientStart, CardGradientEnd))),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .1f), Color.Transparent), center, size.maxDimension * .5f), size.maxDimension * .5f, center)
                            }
                            Icon(Icons.Default.Videocam, null, tint = Color.White.copy(alpha = .2f), modifier = Modifier.size(48.dp))
                            IconButton(onClick = { frontCamera = !frontCamera }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f))) {
                                Icon(Icons.Default.Cameraswitch, stringResource(R.string.flip_camera), tint = CardWhite)
                            }
                        }
                    }
                }
                item {
                    Column {
                        VideoLabel(stringResource(R.string.camera_section))
                        VideoGroup {
                            Row(Modifier.fillMaxWidth().clickable { frontCamera = !frontCamera }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                VideoIcon(Icons.Default.Videocam, VideoOrange, VideoOrangeLight)
                                Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(R.string.camera_device), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(stringResource(if (frontCamera) R.string.front_camera else R.string.rear_camera), fontSize = 13.sp, color = TextSecondary)
                                }
                                Icon(Icons.Default.Cameraswitch, null, tint = InactiveGray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                item {
                    Column {
                        VideoLabel(stringResource(R.string.resolution_section))
                        VideoGroup {
                            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                resolutions.forEachIndexed { index, text ->
                                    val active = resolution == index
                                    Text(
                                        text,
                                        Modifier.clip(CircleShape).background(if (active) PrimaryLight else BackgroundGray)
                                            .border(1.dp, if (active) AccentBlue else BorderLight, CircleShape)
                                            .clickable { resolution = index }.padding(horizontal = 14.dp, vertical = 9.dp),
                                        color = if (active) AccentBlue else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        VideoLabel(stringResource(R.string.layout_section))
                        VideoGroup {
                            layouts.forEachIndexed { index, (title, subtitle) ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 20.dp), color = BorderLight.copy(alpha = .55f))
                                Row(Modifier.fillMaxWidth().clickable { layout = index }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                            drawCircle(if (layout == index) AccentBlue else BorderLight, style = Stroke(2.dp.toPx()))
                                            if (layout == index) drawCircle(AccentBlue, radius = size.minDimension / 2)
                                        }
                                        if (layout == index) Box(Modifier.size(8.dp).clip(CircleShape).background(CardWhite))
                                    }
                                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                        Text(stringResource(subtitle), fontSize = 12.sp, color = InactiveGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InactiveGray, letterSpacing = 1.sp)
}

@Composable
private fun VideoGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite)) { content() }
}

@Composable
private fun VideoIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}
