package com.deviceinfo.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.core.L10n
import com.deviceinfo.data.model.BenchmarkResult
import com.deviceinfo.data.model.BenchmarkState
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.GpuBenchmarkResult
import com.deviceinfo.data.model.GpuBenchmarkState
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.CoreFrequencyGrid
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassDialog
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.MetricValue
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.components.RatioBar
import com.deviceinfo.ui.components.RingGauge
import com.deviceinfo.ui.components.Sparkline
import com.deviceinfo.ui.components.ToolTile
import com.deviceinfo.ui.rememberResponsiveColumns
import com.deviceinfo.ui.theme.DevicePalette

@Composable
fun DashboardScreen(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    topPadding: Dp,
    benchmarkState: BenchmarkState,
    gpuBenchmarkState: GpuBenchmarkState,
    onStartBenchmark: (Long) -> Unit,
    onStartGpuBenchmark: (Long) -> Unit,
    onDismissBenchmark: () -> Unit,
    onDismissGpuBenchmark: () -> Unit,
    onOpenTab: (com.deviceinfo.ui.navigation.DeviceTab) -> Unit
) {
    val columns = rememberResponsiveColumns(compact = 2, medium = 3, expanded = 4)
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeTool by remember { mutableStateOf<DashboardTool?>(null) }
    var showBenchmarkPicker by remember { mutableStateOf(false) }
    var benchmarkKind by remember { mutableStateOf(BenchmarkKind.CPU) }
    var showOverlayPermission by remember { mutableStateOf(false) }

    InfoScreen(topPadding = topPadding) {
        item { CpuStatusCard(snapshot, live) }
        item { MetricCardGrid(snapshot, live, columns) }
        item {
            ToolsCard(
                onToolClick = { tool ->
                    when (tool) {
                        DashboardTool.Benchmark -> {
                            if (benchmarkState is BenchmarkState.Idle &&
                                gpuBenchmarkState is GpuBenchmarkState.Idle
                            ) {
                                benchmarkKind = BenchmarkKind.CPU
                                showBenchmarkPicker = true
                            }
                        }
                        DashboardTool.BatteryMonitor -> {
                            val service = com.deviceinfo.service.BatteryOverlayService
                            if (service.hasOverlayPermission(context)) {
                                // 服務與 UI 同進程，isRunning 就是唯一真相；不再讀持久化旗標，
                                // 否則安裝新包／進程被殺後殘留值會讓開關狀態錯亂
                                val running = service.isActive()
                                if (running) service.stop(context) else service.start(context)
                                android.widget.Toast.makeText(
                                    context,
                                    if (running) L10n.s(R.string.dash_overlay_off) else L10n.s(R.string.dash_overlay_on),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showOverlayPermission = true
                            }
                        }
                        DashboardTool.Gadgets -> activeTool = tool
                    }
                }
            )
        }
    }

    // 懸浮窗權限未授予：說明 + 引導去系統設置
    if (showOverlayPermission) {
        GlassDialog(
            onDismissRequest = { showOverlayPermission = false },
            title = stringResource(R.string.dash_battery_overlay_title),
            subtitle = stringResource(R.string.dash_overlay_permission_needed),
            confirmLabel = stringResource(R.string.dash_cancel)
        ) {
            Text(
                text = stringResource(R.string.dash_overlay_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GlassPillButton(
                    text = stringResource(R.string.dash_go_grant),
                    onClick = {
                        showOverlayPermission = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // 效能評估：CPU / GPU 選擇時長、進度與結果
    if (benchmarkState !is BenchmarkState.Idle) {
        BenchmarkDialog(
            state = benchmarkState,
            onDismiss = onDismissBenchmark
        )
    } else if (gpuBenchmarkState !is GpuBenchmarkState.Idle) {
        GpuBenchmarkDialog(
            state = gpuBenchmarkState,
            onDismiss = onDismissGpuBenchmark
        )
    } else if (showBenchmarkPicker) {
        BenchmarkDurationPickerDialog(
            selectedKind = benchmarkKind,
            onKindChange = { benchmarkKind = it },
            onDismiss = { showBenchmarkPicker = false },
            onSelect = { durationMs ->
                showBenchmarkPicker = false
                if (benchmarkKind == BenchmarkKind.GPU) {
                    onStartGpuBenchmark(durationMs)
                } else {
                    onStartBenchmark(durationMs)
                }
            }
        )
    }

    activeTool?.let { tool ->
        GlassDialog(
            onDismissRequest = { activeTool = null },
            title = stringResource(tool.titleRes),
            subtitle = stringResource(tool.subtitleRes)
        ) {
            Text(
                text = stringResource(tool.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(tool.noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CPU 狀態
// ---------------------------------------------------------------------------

@Composable
private fun CpuStatusCard(snapshot: DeviceSnapshot, live: LiveSnapshot) {
    val history = if (live.loadHistory.size >= 2) live.loadHistory else listOf(0f, 0f)
    val cores = if (live.coreFreqsMhz.isEmpty()) {
        List(snapshot.soc.coreCount) { 0 }
    } else {
        live.coreFreqsMhz
    }

    DeviceInfoCard {
        Column {
            // 設備摘要置於 CPU 狀態頂端，寬度佔滿整卡，型號過長自動截斷
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DevicePalette.Accent)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = snapshot.identity.brand.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF06210F),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dash_manufacturer_model, snapshot.identity.manufacturer, snapshot.identity.model),
                        style = MaterialTheme.typography.titleMedium,
                        color = DevicePalette.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.dash_android_soc, snapshot.os.release, snapshot.soc.marketingName),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                PillTag(text = stringResource(R.string.dash_core_count, snapshot.soc.coreCount))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dash_cpu_status),
                    style = MaterialTheme.typography.titleSmall,
                    color = DevicePalette.Accent,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.dash_load, Fmt.percent(live.loadRatio)),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.Accent
                )
            }
            Spacer(Modifier.height(12.dp))

            CoreFrequencyGrid(freqs = cores, maxFreqs = emptyList())
            Spacer(Modifier.height(12.dp))
            Sparkline(
                values = history,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dash_overall_load),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.TextTertiary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Fmt.percent(live.loadRatio),
                    style = MaterialTheme.typography.titleMedium,
                    color = DevicePalette.Accent
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 指標卡網格
// ---------------------------------------------------------------------------

@Composable
private fun MetricCardGrid(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    columns: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 第一行：電池、網路
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BatteryMetricCard(snapshot, live, Modifier.weight(1f))
            NetworkMetricCard(snapshot, Modifier.weight(1f))
        }

        // 第二行：應用程式、記憶體、儲存（依螢幕寬度自動列數）
        val compactCards: List<@Composable (Modifier) -> Unit> = listOf(
            { modifier -> AppsMetricCard(snapshot, modifier) },
            { modifier -> RamMetricCard(snapshot, live, modifier) },
            { modifier -> StorageMetricCard(snapshot, modifier) }
        )
        compactCards.chunked(columns.coerceAtLeast(2) - 1).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCards.forEach { card ->
                    Box(modifier = Modifier.weight(1f)) {
                        card(Modifier.fillMaxWidth())
                    }
                }
                repeat((columns.coerceAtLeast(2) - 1) - rowCards.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // 第三行：螢幕卡片佔滿整行，寬度最大化以利顯示供應商
        ScreenMetricCard(snapshot, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BatteryMetricCard(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    modifier: Modifier
) {
    val battery = live.battery
    val context = LocalContext.current
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_battery),
        titleIcon = Icons.Outlined.BatteryFull,
        onClick = { context.openBatterySettings() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.BatteryFull,
                contentDescription = null,
                tint = DevicePalette.Accent,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = battery?.let { "${it.levelPercent}%" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = DevicePalette.TextPrimary
                )
                Text(
                    text = battery?.let { Fmt.temperature(it.temperatureC) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = battery?.statusLabel ?: stringResource(R.string.dash_loading),
            style = MaterialTheme.typography.bodySmall,
            color = DevicePalette.TextTertiary
        )
        Text(
            text = battery?.pluggedLabel ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = DevicePalette.TextTertiary
        )
    }
}

@Composable
private fun NetworkMetricCard(snapshot: DeviceSnapshot, modifier: Modifier) {
    val network = snapshot.network
    val context = LocalContext.current
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_network),
        titleIcon = Icons.Outlined.Wifi,
        onClick = { context.openWifiSettings() }
    ) {
        Column {
            Text(
                text = network.transportLabel,
                style = MaterialTheme.typography.titleMedium,
                color = DevicePalette.Accent
            )
            Spacer(Modifier.height(4.dp))
            if (network.wifiLinkSpeedMbps != null && network.wifiLinkSpeedMbps > 0) {
                Text(
                    text = stringResource(R.string.dash_wifi_speed, network.wifiLinkSpeedMbps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.TextPrimary
                )
            }
            if (network.linkDownKbps > 0) {
                Text(
                    text = stringResource(R.string.dash_link_down, network.linkDownKbps / 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
            network.wifiRssiDbm?.takeIf { it != 0 }?.let { rssi ->
                Text(
                    text = stringResource(R.string.dash_rssi, qualityOf(rssi), rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
            network.carrier?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun AppsMetricCard(snapshot: DeviceSnapshot, modifier: Modifier) {
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_apps),
        titleIcon = Icons.Outlined.Monitor
    ) {
        Column {
            Text(
                text = snapshot.apps.total.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = DevicePalette.Accent
            )
            Text(
                text = stringResource(R.string.dash_total),
                style = MaterialTheme.typography.labelSmall,
                color = DevicePalette.TextTertiary
            )
            Spacer(Modifier.height(6.dp))
            KeyValueRow(label = stringResource(R.string.dash_apps_user), value = snapshot.apps.userCount.toString())
            KeyValueRow(label = stringResource(R.string.dash_apps_system), value = snapshot.apps.systemCount.toString())
        }
    }
}

@Composable
private fun ScreenMetricCard(snapshot: DeviceSnapshot, modifier: Modifier) {
    val screen = snapshot.screen
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_screen),
        titleIcon = Icons.Outlined.PhoneAndroid
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dash_resolution, screen.widthPx, screen.heightPx),
                    style = MaterialTheme.typography.headlineSmall,
                    color = DevicePalette.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dash_refresh_dpi, screen.refreshRateHz.toInt(), screen.densityDpi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.Accent
                )
                if (screen.diagonalInches > 0f) {
                    Text(
                        text = stringResource(R.string.dash_screen_inches, screen.diagonalInches),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
                if (!screen.supplier.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.dash_supplier, screen.supplier),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RamMetricCard(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    modifier: Modifier
) {
    val ram = snapshot.ram
    val ratio = if (live.ramUsedRatio > 0f) live.ramUsedRatio else ram.usedRatio
    val usedBytes = ram.totalBytes - (if (live.ramAvailableBytes > 0) live.ramAvailableBytes else ram.availableBytes)
    val context = LocalContext.current
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_memory),
        titleIcon = Icons.Outlined.Memory,
        onClick = { context.openMemorySettings() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(
                ratio = ratio,
                centerText = Fmt.percent(ratio),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.dash_ram_used, Fmt.bytes(usedBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.TextPrimary
                )
                Text(
                    text = stringResource(R.string.dash_ram_total, Fmt.bytes(ram.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
                Spacer(Modifier.height(6.dp))
                RatioBar(ratio = ratio, modifier = Modifier.width(80.dp), height = 4.dp)
            }
        }
    }
}

@Composable
private fun StorageMetricCard(snapshot: DeviceSnapshot, modifier: Modifier) {
    val storage = snapshot.storage
    val context = LocalContext.current
    DeviceInfoCard(
        modifier = modifier,
        title = stringResource(R.string.dash_storage),
        titleIcon = Icons.Outlined.Storage,
        onClick = { context.openStorageSettings() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(
                ratio = storage.usedRatio,
                centerText = Fmt.percent(storage.usedRatio),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.dash_storage_used, Fmt.bytesShort(storage.dataUsedBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DevicePalette.TextPrimary
                )
                Text(
                    text = stringResource(R.string.dash_storage_total, Fmt.bytesShort(storage.dataTotalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
                Spacer(Modifier.height(6.dp))
                RatioBar(
                    ratio = storage.usedRatio,
                    modifier = Modifier.width(80.dp),
                    height = 4.dp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 工具入口
// ---------------------------------------------------------------------------

private enum class DashboardTool(
    val titleRes: Int,
    val subtitleRes: Int,
    val descriptionRes: Int,
    val noteRes: Int,
    val icon: ImageVector
) {
    Benchmark(
        titleRes = R.string.dash_tool_benchmark_title,
        subtitleRes = R.string.dash_tool_benchmark_subtitle,
        descriptionRes = R.string.dash_tool_benchmark_desc,
        noteRes = R.string.dash_tool_benchmark_note,
        icon = Icons.Outlined.Speed
    ),
    BatteryMonitor(
        titleRes = R.string.dash_tool_battery_title,
        subtitleRes = R.string.dash_tool_battery_subtitle,
        descriptionRes = R.string.dash_tool_battery_desc,
        noteRes = R.string.dash_tool_battery_note,
        icon = Icons.Outlined.Bolt
    ),
    Gadgets(
        titleRes = R.string.dash_tool_gadgets_title,
        subtitleRes = R.string.dash_tool_gadgets_subtitle,
        descriptionRes = R.string.dash_tool_gadgets_desc,
        noteRes = R.string.dash_tool_gadgets_note,
        icon = Icons.Outlined.Science
    )
}

@Composable
private fun ToolsCard(onToolClick: (DashboardTool) -> Unit) {
    DeviceInfoCard(title = stringResource(R.string.dash_tools)) {
        val tools = DashboardTool.entries
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tools.forEach { tool ->
                ToolTile(
                    icon = tool.icon,
                    label = stringResource(tool.titleRes),
                    onClick = { onToolClick(tool) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tool:" + tool.name.lowercase())
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 效能評估對話框
// ---------------------------------------------------------------------------

@Composable
private fun BenchmarkDurationPickerDialog(
    selectedKind: BenchmarkKind,
    onKindChange: (BenchmarkKind) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        stringResource(R.string.dash_duration_15) to 15_000L,
        stringResource(R.string.dash_duration_30) to 30_000L,
        stringResource(R.string.dash_duration_45) to 45_000L,
        stringResource(R.string.dash_duration_60) to 60_000L
    )
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.dash_tool_benchmark_title),
        subtitle = stringResource(R.string.dash_pick_test),
        confirmLabel = stringResource(R.string.dash_cancel)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dash_test_type),
                style = MaterialTheme.typography.labelMedium,
                color = DevicePalette.TextTertiary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BenchmarkKindButton(
                    label = stringResource(R.string.dash_test_cpu),
                    selected = selectedKind == BenchmarkKind.CPU,
                    onClick = { onKindChange(BenchmarkKind.CPU) },
                    modifier = Modifier.weight(1f)
                )
                BenchmarkKindButton(
                    label = stringResource(R.string.dash_test_gpu),
                    selected = selectedKind == BenchmarkKind.GPU,
                    onClick = { onKindChange(BenchmarkKind.GPU) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(R.string.dash_pick_duration),
                style = MaterialTheme.typography.labelMedium,
                color = DevicePalette.TextTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (label, ms) ->
                        GlassPillButton(
                            text = label,
                            onClick = { onSelect(ms) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Text(
                text = if (selectedKind == BenchmarkKind.GPU) {
                    stringResource(R.string.dash_gpu_warning)
                } else {
                    stringResource(R.string.dash_benchmark_warning)
                },
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private enum class BenchmarkKind { CPU, GPU }

@Composable
private fun BenchmarkKindButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) DevicePalette.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) DevicePalette.Accent else DevicePalette.Outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) DevicePalette.Accent else DevicePalette.TextSecondary
        )
    }
}

@Composable
private fun BenchmarkDialog(
    state: BenchmarkState,
    onDismiss: () -> Unit
) {
    val isFinished = state is BenchmarkState.Finished
    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (isFinished) stringResource(R.string.dash_test_done) else stringResource(R.string.dash_benchmark_running),
        subtitle = if (isFinished) stringResource(R.string.dash_result_summary) else stringResource(R.string.dash_keep_screen),
        confirmLabel = if (isFinished) stringResource(R.string.dash_close) else stringResource(R.string.dash_stop)
    ) {
        when (state) {
            is BenchmarkState.InProgress -> BenchmarkProgressContent(state)
            is BenchmarkState.Finished -> BenchmarkResultContent(state.result)
            else -> {}
        }
    }
}

@Composable
private fun GpuBenchmarkDialog(
    state: GpuBenchmarkState,
    onDismiss: () -> Unit
) {
    val isFinished = state is GpuBenchmarkState.Finished
    val isFailed = state is GpuBenchmarkState.Failed
    GlassDialog(
        onDismissRequest = onDismiss,
        title = when {
            isFinished -> stringResource(R.string.dash_gpu_done)
            isFailed -> stringResource(R.string.dash_gpu_failed)
            else -> stringResource(R.string.dash_gpu_running)
        },
        subtitle = when {
            isFinished || isFailed -> stringResource(R.string.dash_result_summary)
            else -> stringResource(R.string.dash_keep_screen)
        },
        confirmLabel = if (isFinished || isFailed) {
            stringResource(R.string.dash_close)
        } else {
            stringResource(R.string.dash_stop)
        }
    ) {
        when (state) {
            is GpuBenchmarkState.InProgress -> GpuBenchmarkProgressContent(state)
            is GpuBenchmarkState.Finished -> GpuBenchmarkResultContent(state.result)
            is GpuBenchmarkState.Failed -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary
            )
            else -> {}
        }
    }
}

@Composable
private fun GpuBenchmarkProgressContent(progress: GpuBenchmarkState.InProgress) {
    val ratio = (progress.elapsedMs.toFloat() / progress.targetMs).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BenchmarkScoreCircle(
            score = progress.score,
            ratio = ratio,
            label = stringResource(R.string.dash_gpu_score)
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dash_progress_time, progress.elapsedMs / 1000, progress.targetMs / 1000),
                style = MaterialTheme.typography.titleMedium,
                color = DevicePalette.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Fmt.percent(ratio),
                style = MaterialTheme.typography.titleMedium,
                color = DevicePalette.Accent
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = DevicePalette.Accent,
            trackColor = Color(0xFF232E28)
        )
        Spacer(Modifier.height(16.dp))
        KeyValueRow(label = stringResource(R.string.dash_gpu_live_fps), value = Fmt.fps(progress.currentFps))
        KeyValueRow(label = stringResource(R.string.dash_gpu_avg_fps), value = Fmt.fps(progress.avgFps))
        KeyValueRow(label = stringResource(R.string.dash_gpu_frames), value = progress.frameCount.toString())
        KeyValueRow(label = stringResource(R.string.dash_gpu_renderer), value = progress.gpuName)
    }
}

@Composable
private fun GpuBenchmarkResultContent(result: GpuBenchmarkResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BenchmarkScoreCircle(
            score = result.score,
            ratio = 1f,
            label = stringResource(R.string.dash_gpu_score)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.dash_stress_seconds, result.targetMs / 1000),
            style = MaterialTheme.typography.titleMedium,
            color = DevicePalette.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        KeyValueRow(label = stringResource(R.string.dash_gpu_avg_fps), value = Fmt.fps(result.avgFps))
        KeyValueRow(label = stringResource(R.string.dash_gpu_peak_fps), value = Fmt.fps(result.peakFps))
        KeyValueRow(label = stringResource(R.string.dash_gpu_min_fps), value = Fmt.fps(result.minFps))
        KeyValueRow(label = stringResource(R.string.dash_gpu_frames), value = result.frameCount.toString())
        KeyValueRow(label = stringResource(R.string.dash_gpu_renderer), value = result.gpuName)
        KeyValueRow(
            label = stringResource(R.string.dash_temp_change),
            value = stringResource(
                R.string.dash_temp_change_value,
                Fmt.temperature(result.startTempC),
                Fmt.temperature(result.endTempC)
            )
        )
    }
}

@Composable
private fun BenchmarkProgressContent(progress: BenchmarkState.InProgress) {
    val ratio = (progress.elapsedMs.toFloat() / progress.targetMs).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BenchmarkScoreCircle(score = progress.score, ratio = ratio, label = stringResource(R.string.dash_live_score))
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dash_progress_time, progress.elapsedMs / 1000, progress.targetMs / 1000),
                style = MaterialTheme.typography.titleMedium,
                color = DevicePalette.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Fmt.percent(ratio),
                style = MaterialTheme.typography.titleMedium,
                color = DevicePalette.Accent
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = DevicePalette.Accent,
            trackColor = Color(0xFF232E28)
        )
        Spacer(Modifier.height(16.dp))
        KeyValueRow(label = stringResource(R.string.dash_avg_load), value = Fmt.percent(progress.avgLoad))
        KeyValueRow(label = stringResource(R.string.dash_avg_freq), value = Fmt.mhz(progress.avgFreqMhz.toInt()))
        KeyValueRow(label = stringResource(R.string.dash_peak_temp), value = Fmt.temperature(progress.peakTempC))
        KeyValueRow(
            label = stringResource(R.string.dash_current_battery),
            value = stringResource(R.string.dash_battery_pct, progress.currentBatteryPct)
        )
    }
}

@Composable
private fun BenchmarkResultContent(result: BenchmarkResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BenchmarkScoreCircle(score = result.score, ratio = 1f, label = stringResource(R.string.dash_final_score))
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.dash_stress_seconds, result.targetMs / 1000),
            style = MaterialTheme.typography.titleMedium,
            color = DevicePalette.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        KeyValueRow(label = stringResource(R.string.dash_avg_load), value = Fmt.percent(result.avgLoad))
        KeyValueRow(label = stringResource(R.string.dash_peak_load), value = Fmt.percent(result.peakLoad))
        KeyValueRow(label = stringResource(R.string.dash_avg_freq), value = Fmt.mhz(result.avgFreqMhz.toInt()))
        KeyValueRow(label = stringResource(R.string.dash_peak_freq), value = Fmt.mhz(result.peakFreqMhz))
        KeyValueRow(
            label = stringResource(R.string.dash_battery_change),
            value = stringResource(R.string.dash_battery_change_value, result.startBatteryPct, result.endBatteryPct)
        )
        KeyValueRow(
            label = stringResource(R.string.dash_temp_change),
            value = stringResource(R.string.dash_temp_change_value, Fmt.temperature(result.startTempC), Fmt.temperature(result.endTempC))
        )
        KeyValueRow(
            label = stringResource(R.string.dash_avg_power),
            value = Fmt.power(result.avgPowerMw, result.powerPlugged)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append(stringResource(R.string.dash_sample_count, result.sampleCount))
                when {
                    result.avgPowerMw == null -> append(stringResource(R.string.dash_power_unavailable))
                    result.powerPlugged == true -> append(stringResource(R.string.dash_power_charging))
                    result.powerPlugged == false -> append(stringResource(R.string.dash_power_discharging))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = DevicePalette.TextTertiary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BenchmarkScoreCircle(score: Int, ratio: Float, label: String) {
    Box(
        modifier = Modifier.size(128.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxSize(),
            color = DevicePalette.Accent,
            trackColor = Color(0xFF232E28),
            strokeWidth = 8.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = DevicePalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = DevicePalette.TextTertiary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 跳轉系統設定
// ---------------------------------------------------------------------------

private fun android.content.Context.openBatterySettings() {
    safeStartActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Settings.ACTION_SETTINGS)
}

private fun android.content.Context.openStorageSettings() {
    safeStartActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Settings.ACTION_SETTINGS)
}

private fun android.content.Context.openWifiSettings() {
    safeStartActivity(Intent(Settings.ACTION_WIFI_SETTINGS), Settings.ACTION_WIRELESS_SETTINGS)
}

/** 原生（AOSP）記憶體管理介面；找不到時改開「開發人員選項」作為備援。 */
private fun android.content.Context.openMemorySettings() {
    val explicit = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$AppMemoryUsageActivity"
        )
    }
    if (!safeStartActivity(explicit, null)) {
        safeStartActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Settings.ACTION_SETTINGS
        )
    }
}

private fun android.content.Context.safeStartActivity(
    intent: Intent,
    fallbackAction: String?
): Boolean {
    val flagged = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return try {
        if (packageManager.resolveActivity(flagged, 0) != null) {
            startActivity(flagged)
            true
        } else {
            false
        }
    } catch (_: Throwable) {
        false
    }.also { success ->
        if (!success) {
            if (fallbackAction != null) {
                try {
                    startActivity(
                        Intent(fallbackAction)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } catch (_: Throwable) {
                    Toast.makeText(this, L10n.s(R.string.dash_open_settings_failed), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, L10n.s(R.string.dash_open_settings_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** RSSI 到百分比的經驗映射（-50dBm 以上視為滿格）。 */
private fun qualityOf(rssi: Int): Int {
    val clamped = rssi.coerceIn(-100, -50)
    return ((clamped + 100) * 100 / 50).coerceIn(0, 100)
}
