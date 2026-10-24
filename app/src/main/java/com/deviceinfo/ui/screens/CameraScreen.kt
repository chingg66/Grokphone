package com.deviceinfo.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.data.internal.CameraLiveProbe
import com.deviceinfo.R
import com.deviceinfo.data.model.CameraSpec
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.KeyValueGrid
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.PermissionRequestCard
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.theme.DevicePalette
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CameraScreen(
    snapshot: DeviceSnapshot,
    topPadding: Dp
) {
    InfoScreen(topPadding = topPadding) {
        item { CameraPermissionCard() }
        if (snapshot.cameras.isEmpty()) {
            item {
                DeviceInfoCard(title = stringResource(R.string.cam_camera)) {
                    Text(
                        text = stringResource(R.string.cam_no_camera_detected),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
            }
        }
        items(snapshot.cameras.size) { index ->
            CameraCard(snapshot.cameras[index])
        }
    }
}

/**
 * 相機權限：用途是「即時幀檢測」—— 真正打開鏡頭測量實際輸出解析度與幀率，
 * 驗證感測器標稱值。靜態參數不需要權限，所以權限只綁定這一個功能。
 */
@Composable
private fun CameraPermissionCard() {
    var probeResult by remember { mutableStateOf<CameraLiveProbe.LiveResult?>(null) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val probeFailedText = stringResource(R.string.cam_probe_failed)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionRequestCard(
            title = stringResource(R.string.cam_permission_title),
            description = stringResource(R.string.cam_permission_desc),
            permission = Manifest.permission.CAMERA
        )

        // 已授權（權限卡片會自行消失）才顯示檢測入口；用探測結果是否存在判斷
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            DeviceInfoCard(title = stringResource(R.string.cam_live_probe_title)) {
                Column {
                    Text(
                        text = stringResource(R.string.cam_live_probe_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        GlassPillButton(
                            text = if (probing) stringResource(R.string.cam_probing) else stringResource(R.string.cam_start_probe),
                            onClick = {
                                if (!probing) {
                                    probing = true
                                    probeError = null
                                    probeResult = null
                                    scope.launch {
                                        val result = CameraLiveProbe.measure(context)
                                        result.fold(
                                            onSuccess = { probeResult = it },
                                            onFailure = { probeError = it.message ?: probeFailedText }
                                        )
                                        probing = false
                                    }
                                }
                            }
                        )
                    }

                    probeResult?.let { live ->
                        Spacer(Modifier.height(12.dp))
                        KeyValueRow(
                            label = stringResource(R.string.cam_actual_output),
                            value = "${live.width} × ${live.height}"
                        )
                        KeyValueRow(
                            label = stringResource(R.string.cam_measured_fps),
                            value = stringResource(
                                R.string.cam_measured_fps_value, live.fps, live.frames
                            )
                        )
                        if (live.nominalPixelCount > 0) {
                            KeyValueRow(
                                label = stringResource(R.string.cam_nominal_pixels),
                                value = stringResource(
                                    R.string.cam_nominal_pixel_value, live.nominalPixelCount
                                )
                            )
                        }
                    }

                    probeError?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.cam_probe_failed_with_error, error),
                            style = MaterialTheme.typography.bodySmall,
                            color = DevicePalette.Warn
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCard(camera: CameraSpec) {
    DeviceInfoCard(
        title = stringResource(R.string.cam_facing_camera, camera.facingLabel),
        trailing = {
            PillTag(text = "ID ${camera.id}")
        }
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // 一位小數：50.0 MP 這種整數感測器也會被壓縮演算法標成 50.2 之類，
                    // 只取整會掩蓋真實感測器規格，所以保留精確值
                    text = if (camera.megapixels > 0f) {
                        String.format(Locale.US, "%.1f MP", camera.megapixels)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = DevicePalette.Accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = camera.resolution,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (camera.pixelCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.cam_exact_pixels,
                        NumberFormat.getIntegerInstance(Locale.US).format(camera.pixelCount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
            Spacer(Modifier.height(12.dp))
            KeyValueGrid(
                items = listOf(
                    stringResource(R.string.cam_aperture) to (camera.aperture?.let { String.format(Locale.US, "f/%.2f", it) } ?: "—"),
                    stringResource(R.string.cam_focal_length) to (camera.focalLengthMm?.let { String.format(Locale.US, "%.2f mm", it) } ?: "—"),
                    stringResource(R.string.cam_sensor_size) to (camera.sensorSizeMm ?: "—"),
                    stringResource(R.string.cam_zoom_range) to (camera.zoomRange ?: "—"),
                    stringResource(R.string.cam_max_fps) to (camera.maxFps?.let { "$it fps" } ?: "—"),
                    stringResource(R.string.cam_flash) to if (camera.hasFlash) stringResource(R.string.cam_flash_supported) else stringResource(R.string.cam_flash_unsupported)
                )
            )
        }
    }
}
