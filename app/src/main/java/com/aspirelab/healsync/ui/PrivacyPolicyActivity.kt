package com.aspirelab.healsync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown by Health Connect when the user asks how this app uses their health data. */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealSyncTheme {
                Surface {
                    Column(
                        modifier = Modifier.safeDrawingPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Chính sách quyền riêng tư", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "HealSync đọc bước chân, nhịp tim và giấc ngủ từ tài khoản Google Health (Fitbit) của bạn " +
                                "rồi ghi vào Health Connect trên máy này để Samsung Health hiển thị.",
                        )
                        Text("Dữ liệu chỉ đi từ Google Health API tới Health Connect. Ứng dụng không gửi dữ liệu tới máy chủ nào khác.")
                        Text("Token đăng nhập được mã hoá bằng Android Keystore. Đăng xuất trong ứng dụng sẽ xoá token.")
                    }
                }
            }
        }
    }
}
