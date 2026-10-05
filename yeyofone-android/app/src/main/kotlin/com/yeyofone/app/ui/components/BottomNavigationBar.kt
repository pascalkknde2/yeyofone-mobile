package com.yeyofone.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.AccentBlue
import com.yeyofone.app.ui.theme.BorderLight
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.NavBackground

const val HOME_NAVIGATION = 0
const val KEYPAD_NAVIGATION = 1
const val CONTACTS_NAVIGATION = 2
const val CALLS_NAVIGATION = 3
const val CHAT_NAVIGATION = 4
const val SETTINGS_NAVIGATION = 5

@Composable
fun BottomNavigationBar(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    val items = listOf(
        NavItem(stringResource(R.string.home_navigation), Icons.Filled.Home, Icons.Outlined.Home),
        NavItem(stringResource(R.string.keypad), Icons.Filled.Apps, Icons.Outlined.Apps),
        NavItem(stringResource(R.string.contacts_navigation), Icons.Filled.Person, Icons.Outlined.Person),
        NavItem(stringResource(R.string.calls_navigation), Icons.Filled.Call, Icons.Outlined.Call),
        NavItem(stringResource(R.string.chat_navigation), Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
        NavItem(stringResource(R.string.settings_navigation), Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)
            .shadow(8.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
            .background(NavBackground).border(1.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
            .selectableGroup().padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            val background by animateColorAsState(if (selected) AccentBlue else Color.Transparent, label = "tab background")
            val foreground by animateColorAsState(if (selected) Color.White else InactiveGray, label = "tab icon")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onItemSelected(index) }),
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        item.label,
                        tint = foreground,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)
