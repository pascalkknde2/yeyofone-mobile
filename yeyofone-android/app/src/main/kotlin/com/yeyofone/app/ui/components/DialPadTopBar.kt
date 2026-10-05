package com.yeyofone.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.CallTextPrimary

@Composable
fun DialPadTopBar(
    onNavigateBack: () -> Unit,
    onAddContact: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.cd_back), tint = CallTextPrimary, modifier = Modifier.size(22.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onAddContact) {
                Icon(Icons.Outlined.PersonAdd, stringResource(R.string.add_contact), tint = CallTextPrimary, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMoreOptions) {
                Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_options), tint = CallTextPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}
