package com.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.KeyValueGrid
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.PermissionRequestCard
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.components.RatioBar
import com.deviceinfo.ui.theme.DevicePalette

@Composable
fun NetworkScreen(
    snapshot: DeviceSnapshot,
    topPadding: Dp,
    onRefresh: () -> Unit = {}
) {
    InfoScreen(topPadding = topPadding) {
        item {
            PermissionRequestCard(
                title = stringResource(R.string.net_title_location_perm),
                description = stringResource(R.string.net_desc_location_perm),
                permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
                onGranted = onRefresh
            )
        }
        item { ConnectionCard(snapshot) }
        item { SpeedTestCard() }
        item { TrafficCard(snapshot) }
        item { CellularCard(snapshot) }
    }
}

@Composable
private fun ConnectionCard(snapshot: DeviceSnapshot) {
    val network = snapshot.network
    DeviceInfoCard(title = stringResource(R.string.net_title_connection)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = network.transportLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = DevicePalette.Accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                PillTag(
                    text = if (network.isConnected) stringResource(R.string.net_online) else stringResource(R.string.net_offline),
                    color = if (network.isConnected) DevicePalette.Accent else DevicePalette.Warn
                )
            }
            Spacer(Modifier.height(14.dp))
            KeyValueGrid(
                items = listOf(
                    "IPv4" to (network.ipv4 ?: "—"),
                    "IPv6" to (network.ipv6 ?: "—"),
                    stringResource(R.string.net_label_down_bw) to if (network.linkDownKbps > 0) {
                        "${network.linkDownKbps / 1000} Mbps"
                    } else {
                        "—"
                    },
                    stringResource(R.string.net_label_up_bw) to if (network.linkUpKbps > 0) {
                        "${network.linkUpKbps / 1000} Mbps"
                    } else {
                        "—"
                    }
                )
            )
        }
    }
}

@Composable
private fun TrafficCard(snapshot: DeviceSnapshot) {
    val network = snapshot.network
    val total = network.rxBytes + network.txBytes
    DeviceInfoCard(title = stringResource(R.string.net_title_traffic)) {
        Column {
            KeyValueRow(label = stringResource(R.string.net_label_wifi_speed), value = network.wifiLinkSpeedMbps?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "—")
            KeyValueRow(
                label = stringResource(R.string.net_label_wifi_band),
                value = network.wifiFrequencyMhz?.takeIf { it > 0 }?.let { bandOf(it) } ?: "—"
            )
            KeyValueRow(
                label = stringResource(R.string.net_label_signal),
                value = network.wifiRssiDbm?.takeIf { it != 0 }?.let { "$it dBm" } ?: "—"
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.net_traffic_since_boot),
                style = MaterialTheme.typography.labelMedium,
                color = DevicePalette.TextTertiary
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Fmt.bytes(total),
                    style = MaterialTheme.typography.titleMedium,
                    color = DevicePalette.Accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "↓ ${Fmt.bytes(network.rxBytes)}   ↑ ${Fmt.bytes(network.txBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            RatioBar(
                ratio = if (total > 0) network.rxBytes.toFloat() / total else 0f,
                height = 5.dp
            )
        }
    }
}

@Composable
private fun CellularCard(snapshot: DeviceSnapshot) {
    val network = snapshot.network
    DeviceInfoCard(title = stringResource(R.string.net_title_cellular)) {
        Column {
            KeyValueRow(label = stringResource(R.string.net_label_carrier), value = network.carrier ?: "—")
            KeyValueRow(label = stringResource(R.string.net_label_mobile_type), value = network.mobileType ?: "—")
            KeyValueRow(label = stringResource(R.string.net_label_wifi_ssid), value = network.wifiSsid ?: stringResource(R.string.net_value_unauthorized_offline))
            KeyValueRow(label = stringResource(R.string.net_label_bssid), value = network.wifiBssid ?: stringResource(R.string.net_value_unauthorized_offline))
            if (network.wifiSsid == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.net_hint_ssid),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun bandOf(frequencyMhz: Int): String = when {
    frequencyMhz in 2400..2500 -> "$frequencyMhz MHz (2.4 GHz)"
    frequencyMhz in 4900..5900 -> "$frequencyMhz MHz (5 GHz)"
    frequencyMhz in 5925..7125 -> "$frequencyMhz MHz (6 GHz)"
    else -> "$frequencyMhz MHz"
}
