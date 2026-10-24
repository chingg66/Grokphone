package com.deviceinfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.core.L10n
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.KeyValueGrid
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.components.RatioBar
import com.deviceinfo.ui.components.SocBrandLogo
import com.deviceinfo.ui.theme.DevicePalette

@Composable
fun HardwareScreen(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    topPadding: Dp
) {
    InfoScreen(topPadding = topPadding) {
        item { SocCard(snapshot, live) }
        item { GpuCard(snapshot) }
        item { DisplayCard(snapshot) }
    }
}

/**
 * 處理器主卡。排版參照系統資訊工具的慣例：
 * 左側圓形廠商徽章，右側「品牌 → 市場名」兩級標示，
 * 下方是規格 pill、CPU 組態子卡與鍵值網格。
 */
@Composable
private fun SocCard(snapshot: DeviceSnapshot, live: LiveSnapshot) {
    val soc = snapshot.soc
    DeviceInfoCard(title = stringResource(R.string.hw_soc)) {
        Column {
            // 市場名優先（如 Snapdragon 8 Elite），查不到時如實顯示硬體代號
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocBadge(vendorLabel = soc.vendorLabel)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = soc.vendorLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = DevicePalette.Accent.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = soc.marketingName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DevicePalette.Accent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = soc.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillTag(text = stringResource(R.string.hw_core_count, soc.coreCount))
                PillTag(text = soc.bitness)
                PillTag(text = soc.architecture)
            }

            Spacer(Modifier.height(14.dp))
            ClusterPanel(snapshot, live)

            Spacer(Modifier.height(14.dp))
            KeyValueGrid(
                items = listOf(
                    stringResource(R.string.hw_vendor) to soc.manufacturer,
                    stringResource(R.string.hw_hardware) to soc.hardware,
                    stringResource(R.string.hw_abi) to snapshot.os.abis.joinToString(", "),
                    stringResource(R.string.hw_board) to snapshot.identity.board,
                    stringResource(R.string.hw_device_code) to snapshot.identity.device
                ),
                columns = 2
            )
        }
    }
}

/** 廠商徽章：官方品牌標誌（高通驍龍、聯發科、三星、Google、華為、紫光展銳）。 */
@Composable
private fun SocBadge(vendorLabel: String) {
    SocBrandLogo(vendorLabel = vendorLabel, size = 72.dp)
}

/** CPU 組態子卡：深色內嵌面板，每集群一行「核心數 + 頻率區間 + 即時頻率」。 */
@Composable
private fun ClusterPanel(snapshot: DeviceSnapshot, live: LiveSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevicePalette.Background.copy(alpha = 0.55f))
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.hw_cpu_config),
            style = MaterialTheme.typography.labelMedium,
            color = DevicePalette.TextTertiary
        )
        Spacer(Modifier.height(10.dp))

        if (snapshot.clusters.isEmpty()) {
            Text(
                text = stringResource(R.string.hw_freq_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
            return@Column
        }

        snapshot.clusters.forEach { cluster ->
            val currentMax = cluster.coreIndexes.mapNotNull {
                live.coreFreqsMhz.getOrNull(it)
            }.maxOrNull() ?: 0
            val ratio = if (cluster.maxFreqMhz > 0) {
                currentMax.toFloat() / cluster.maxFreqMhz
            } else {
                0f
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DevicePalette.Accent)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.hw_cluster_cores, cluster.coreIndexes.size, clusterLabelOf(cluster.index, snapshot)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = clusterFreqRange(cluster) + if (currentMax > 0) {
                        stringResource(R.string.hw_freq_now, Fmt.mhz(currentMax))
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.Accent
                )
            }
            Spacer(Modifier.height(6.dp))
            RatioBar(ratio = ratio, height = 4.dp)
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun clusterLabelOf(index: Int, snapshot: DeviceSnapshot): String {
    val count = snapshot.clusters.size
    return when {
        count <= 1 -> L10n.s(R.string.hw_cluster_unified)
        index == 0 -> L10n.s(R.string.hw_cluster_prime)
        index == count - 1 -> L10n.s(R.string.hw_cluster_efficiency)
        else -> L10n.s(R.string.hw_cluster_performance)
    }
}

private fun clusterFreqRange(cluster: com.deviceinfo.data.model.CpuClusterSpec): String =
    when {
        cluster.minFreqMhz > 0 && cluster.maxFreqMhz > 0 ->
            L10n.s(R.string.hw_freq_range, cluster.minFreqMhz, Fmt.mhz(cluster.maxFreqMhz))
        cluster.maxFreqMhz > 0 -> L10n.s(R.string.hw_freq_max, Fmt.mhz(cluster.maxFreqMhz))
        else -> "—"
    }

@Composable
private fun GpuCard(snapshot: DeviceSnapshot) {
    val gpu = snapshot.gpu
    DeviceInfoCard(title = "GPU") {
        Column {
            Text(
                text = gpu.renderer,
                style = MaterialTheme.typography.headlineSmall,
                color = DevicePalette.Accent
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow(label = stringResource(R.string.hw_gpu_vendor), value = gpu.vendor)
            KeyValueRow(label = stringResource(R.string.hw_gl_version), value = gpu.glVersion)
            KeyValueRow(label = stringResource(R.string.hw_data_source), value = gpu.source)
        }
    }
}

@Composable
private fun DisplayCard(snapshot: DeviceSnapshot) {
    val screen = snapshot.screen
    DeviceInfoCard(title = stringResource(R.string.hw_display_panel)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${screen.widthPx} × ${screen.heightPx}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DevicePalette.Accent
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${screen.refreshRateHz.toInt()} Hz",
                    style = MaterialTheme.typography.titleMedium,
                    color = DevicePalette.TextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            KeyValueGrid(
                items = listOf(
                    stringResource(R.string.hw_pixel_density) to stringResource(R.string.hw_density_value, screen.densityDpi, screen.densityBucket),
                    stringResource(R.string.hw_size) to if (screen.diagonalInches > 0f) {
                        stringResource(R.string.hw_inches, screen.diagonalInches)
                    } else {
                        "—"
                    },
                    stringResource(R.string.hw_supported_refresh) to if (screen.supportedRefreshRates.isEmpty()) {
                        "—"
                    } else {
                        screen.supportedRefreshRates.joinToString(", ") { "${it.toInt()}Hz" }
                    },
                    "HDR" to if (screen.hdrTypes.isEmpty()) stringResource(R.string.hw_unsupported) else screen.hdrTypes.joinToString(", ")
                ),
                columns = 2
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
