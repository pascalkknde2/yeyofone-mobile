package com.yeyofone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.ui.theme.AccentGreen
import com.yeyofone.app.ui.theme.TextPrimary
import com.yeyofone.app.ui.theme.TextSecondary
import com.yeyofone.core.model.SipAccount

@Composable
fun AccountCard(
    account: SipAccount,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (account.enabled) Color(0xFFE0EAFF) else Color(0xFFF0F0F5)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                account.displayName.initials(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (account.enabled) TextPrimary else Color(0xFFA0A0A5),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                account.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (account.enabled) TextPrimary else Color(0xFFA0A0A5),
                textDecoration = if (account.enabled) TextDecoration.None else TextDecoration.LineThrough,
            )
            Text(
                "sip:${account.username}@${account.server.domain}",
                fontSize = 12.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Switch(
            checked = account.enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE5E5EA),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+"))
    .filter(String::isNotEmpty).take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
