package com.yeyofone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.core.calling.HandoffOffer

// A dark card in both appearances, like the iOS and desktop banners.
private val CardColor = Color(0xFF18191D)
private val IconBackground = Color(0xFF1E3A5F)
private val IconColor = Color(0xFF93C5FD)
private val SecondaryText = Color(0xFFCBD5E1)
private val DismissColor = Color(0xFF26282E)
private val ContinueColor = Color(0xFF10B981)

/**
 * "Continue call on this device?": offers a call that is live on another device of the same
 * account. One tap moves it here; the other party stays connected without noticing.
 */
@Composable
fun HandoffBanner(offer: HandoffOffer, onContinue: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .background(CardColor, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(IconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = IconColor, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.handoff_title),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${offer.remoteName ?: offer.remote} · ${stringResource(R.string.handoff_on_device, offer.deviceName)}",
                    color = SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DismissColor, contentColor = Color(0xFFE2E8F0)),
            ) { Text(stringResource(R.string.handoff_not_now), fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ContinueColor, contentColor = Color.White),
            ) { Text(stringResource(R.string.handoff_continue_here), fontWeight = FontWeight.SemiBold) }
        }
    }
}
