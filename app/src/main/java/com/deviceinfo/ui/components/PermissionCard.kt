package com.deviceinfo.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.deviceinfo.R
import com.deviceinfo.ui.theme.DevicePalette

/**
 * 權限請求卡片。未授權時顯示說明與「授權」按鈕；授權後整張卡片消失，
 * 並透過 [onGranted] 通知呼叫方（通常是重新採集資料）。
 *
 * 各頁面按需放置：網路頁請定位（解鎖 SSID/BSSID）、相機頁請相機（解鎖即時幀檢測）。
 */
@Composable
fun PermissionRequestCard(
    title: String,
    description: String,
    permission: String,
    onGranted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var granted by remember(permission) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) onGranted()
    }

    if (granted) return

    DeviceInfoCard(modifier = modifier, title = title) {
        Column {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                GlassPillButton(text = stringResource(R.string.pc_grant), onClick = { launcher.launch(permission) })
            }
        }
    }
}
