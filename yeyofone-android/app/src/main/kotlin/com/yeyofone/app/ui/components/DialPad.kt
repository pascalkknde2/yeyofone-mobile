package com.yeyofone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.KeypadBackground
import com.yeyofone.app.ui.theme.CardWhite

data class DialKey(val number: String, val letters: String? = null)

private val DialKeys = listOf(
    DialKey("1"), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*"), DialKey("0", "+"), DialKey("#"),
)

@Composable
fun DialPad(onNumberClick: (String) -> Unit, modifier: Modifier = Modifier, rowSpacing: Dp = 12.dp) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DialKeys.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowKeys.forEach { key -> DialKeyButton(key) { onNumberClick(key.number) } }
            }
        }
    }
}

@Composable
private fun DialKeyButton(key: DialKey, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "key press")
    Box(
        Modifier.size(72.dp).scale(scale).shadow(if (pressed) 1.dp else 3.dp, CircleShape)
            .clip(CircleShape).background(if (pressed) KeypadBackground else CardWhite)
            .border(1.dp, BorderLight.copy(alpha = 0.4f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                key.number,
                fontSize = if (key.number in listOf("*", "0", "#")) 28.sp else 24.sp,
                fontWeight = FontWeight.Medium,
                color = CallTextPrimary,
                lineHeight = 28.sp,
            )
            key.letters?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    fontSize = if (key.number == "0") 10.sp else 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InactiveGray,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
