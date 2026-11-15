package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen
import com.example.ui.theme.*

@Composable
fun QatAgencyBottomBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PalBlackNormal)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            QatBottomTabItem(
                title = "الرئيسية",
                icon = Icons.Default.Dashboard,
                isSelected = currentScreen is Screen.Dashboard,
                onClick = { onNavigate(Screen.Dashboard) }
            )

            QatBottomTabItem(
                title = "المبيعات",
                icon = Icons.Default.PointOfSale,
                isSelected = currentScreen is Screen.Sales,
                onClick = { onNavigate(Screen.Sales) }
            )

            QatBottomTabItem(
                title = "العملاء",
                icon = Icons.Default.People,
                isSelected = currentScreen is Screen.Customers,
                onClick = { onNavigate(Screen.Customers) }
            )

            QatBottomTabItem(
                title = "الرعيّة",
                icon = Icons.Default.LocalShipping,
                isSelected = currentScreen is Screen.Suppliers,
                onClick = { onNavigate(Screen.Suppliers) }
            )

            QatBottomTabItem(
                title = "الكشف",
                icon = Icons.Default.Description,
                isSelected = currentScreen is Screen.Statement,
                onClick = { onNavigate(Screen.Statement) }
            )

            QatBottomTabItem(
                title = "المزيد",
                icon = Icons.Default.Menu,
                isSelected = currentScreen !in listOf(
                    Screen.Dashboard,
                    Screen.Sales,
                    Screen.Customers,
                    Screen.Suppliers,
                    Screen.Statement
                ),
                onClick = onOpenMenu
            )
        }
    }
}

@Composable
private fun QatBottomTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val unifiedTint = if (isSelected) PalIconUnified else PalWhiteMuted

    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(height = 30.dp, width = 46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (isSelected) PalSoftGreenTint else Color.Transparent)
                .border(
                    width = if (isSelected) 1.dp else 0.dp,
                    color = if (isSelected) PalIconUnified.copy(alpha = 0.35f) else Color.Transparent,
                    shape = RoundedCornerShape(15.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = unifiedTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = title,
            color = if (isSelected) PalIconUnified else PalWhiteMuted,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
