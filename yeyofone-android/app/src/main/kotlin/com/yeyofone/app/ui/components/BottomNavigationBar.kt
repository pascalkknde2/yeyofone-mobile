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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeyofone.app.R
import com.yeyofone.app.ui.theme.InactiveGray
import com.yeyofone.app.ui.theme.NavBackground
import com.yeyofone.app.ui.theme.TextPrimary

const val CALLS_NAVIGATION = 0
const val CONTACTS_NAVIGATION = 1
const val KEYPAD_NAVIGATION = 2
const val SEARCH_NAVIGATION = 3

@Composable
fun BottomNavigationBar(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    val items = listOf(
        NavItem(stringResource(R.string.calls_navigation), Icons.Filled.Call, Icons.Outlined.Call),
        NavItem(stringResource(R.string.contacts_navigation), Icons.Filled.Person, Icons.Outlined.Person),
        NavItem(stringResource(R.string.keypad), Icons.Filled.Apps, Icons.Outlined.Apps),
        NavItem(stringResource(R.string.search_navigation), Icons.Outlined.Search, Icons.Outlined.Search),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(8.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
            .background(NavBackground).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onItemSelected(index) }.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color.Black.copy(alpha = 0.05f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        item.label,
                        tint = if (selected) TextPrimary else InactiveGray,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) TextPrimary else InactiveGray,
                )
            }
        }
    }
}

data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)
