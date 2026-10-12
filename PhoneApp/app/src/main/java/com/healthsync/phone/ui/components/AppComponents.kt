package com.healthsync.phone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.ui.theme.*

@Composable
fun ScreenHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextWhite, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Shared empty states stay useful, readable and aligned with the rest of the app. */
@Composable
fun AppStateCard(icon: ImageVector, title: String, description: String,
                 actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(28.dp))
            }
            Text(title, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(description, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (actionLabel != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel, color = AccentCyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
