package com.yeyofone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.ui.theme.CallTextPrimary
import com.yeyofone.app.ui.theme.CallTextSecondary

data class DialKey(val number: String, val letters: String? = null)

private val DialKeys = listOf(
    DialKey("1"), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*"), DialKey("0", "+"), DialKey("#"),
)

@Composable
fun DialPad(onNumberClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFE8E8E8)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                key.number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = CallTextPrimary,
                lineHeight = 28.sp,
            )
            key.letters?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = CallTextSecondary,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
