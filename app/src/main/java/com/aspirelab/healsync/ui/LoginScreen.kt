package com.aspirelab.healsync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspirelab.healsync.ui.glass.GlassButton
import com.aspirelab.healsync.ui.glass.GlassCard
import com.aspirelab.healsync.ui.glass.GlassColors

@Composable
fun LoginScreen(
    configured: Boolean,
    signingIn: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        GlassCard(shape = CircleShape, contentPadding = PaddingValues(28.dp)) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = GlassColors.Heart, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("HealSync", color = GlassColors.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Đưa dữ liệu Fitbit Charge 5 vào Samsung Health",
            color = GlassColors.TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        FlowChips()
        Spacer(Modifier.weight(1f))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Bắt đầu", color = GlassColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Đăng nhập bằng tài khoản Google bạn dùng cho app Fitbit. HealSync chỉ đọc bước chân, nhịp tim và giấc ngủ.",
                color = GlassColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            if (!configured) {
                Text("Chưa cấu hình google.clientId trong local.properties", color = GlassColors.Warning, fontSize = 14.sp)
            } else {
                GlassButton(onClick = onSignIn, enabled = !signingIn, modifier = Modifier.fillMaxWidth(), tint = Color(0xFF8FB8FF)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (signingIn) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            GoogleMark()
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (signingIn) "Đang đăng nhập…" else "Tiếp tục với Google",
                            color = GlassColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = GlassColors.Warning, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowChips() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Fitbit", "Health Connect", "Samsung Health").forEachIndexed { i, label ->
            if (i > 0) Text("→", color = GlassColors.TextMuted, fontSize = 14.sp)
            GlassCard(shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, color = GlassColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GoogleMark() {
    Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text("G", color = Color(0xFF4285F4), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
