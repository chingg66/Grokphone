package com.deviceinfo.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.deviceinfo.R
import com.deviceinfo.core.AppSettings
import com.deviceinfo.core.L10n
import com.deviceinfo.core.Fmt
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.KeyValueGrid
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.MetricValue
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.components.RingGauge
import com.deviceinfo.ui.theme.DevicePalette
import java.util.Locale

@Composable
fun BatteryScreen(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    topPadding: Dp,
    settings: AppSettings = AppSettings()
) {
    InfoScreen(topPadding = topPadding) {
        item { BatteryOverviewCard(live, settings) }
        item { BatteryDetailCard(snapshot, live, settings) }
        item { BatteryHealthCard(live, settings) }
        item { BatteryReportCard(snapshot, live, settings) }
    }
}

@Composable
private fun BatteryOverviewCard(live: LiveSnapshot, settings: AppSettings) {
    val battery = live.battery
    DeviceInfoCard(title = stringResource(R.string.bat_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左側：正圓環形電量儀表，加大並保持比例
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                RingGauge(
                    ratio = (battery?.levelPercent ?: 0) / 100f,
                    centerText = "${battery?.levelPercent ?: 0}%",
                    modifier = Modifier.size(128.dp),
                    strokeWidth = 8.dp
                )
            }

            Spacer(Modifier.width(16.dp))

            // 右側：狀態標籤 + 大字溫度 + 電源說明，層次更清晰
            Column(
                modifier = Modifier.weight(1.3f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillTag(
                    text = battery?.statusLabel ?: stringResource(R.string.bat_reading),
                    color = DevicePalette.Accent
                )
                MetricValue(
                    value = battery?.let { settings.temperature(it.temperatureC) } ?: "—",
                    label = stringResource(R.string.bat_label_temp)
                )
                Text(
                    text = battery?.pluggedLabel ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun BatteryDetailCard(snapshot: DeviceSnapshot, live: LiveSnapshot, settings: AppSettings) {
    val battery = live.battery
    DeviceInfoCard(title = stringResource(R.string.bat_title_electrical)) {
        Column {
            KeyValueGrid(
                items = listOf(
                    stringResource(R.string.bat_label_voltage) to (battery?.voltageMv?.takeIf { it > 0 }?.let { "$it mV" } ?: "—"),
                    stringResource(R.string.bat_label_current) to (battery?.currentUa?.takeIf { it != 0 }?.let { formatCurrent(it) } ?: "—"),
                    stringResource(R.string.bat_label_power) to formatPower(battery?.voltageMv, battery?.currentUa, settings.powerFactor),
                    stringResource(R.string.bat_label_technology) to (battery?.technology ?: "—")
                )
            )
            Spacer(Modifier.height(4.dp))
            KeyValueRow(
                label = stringResource(R.string.bat_label_design_capacity),
                value = battery?.designCapacityUah?.takeIf { it > 0 }
                    ?.let { "${settings.capacityUah(it) / 1000} mAh" } ?: "—"
            )
            KeyValueRow(
                label = stringResource(R.string.bat_label_charge_counter),
                value = battery?.chargeCounterUah?.takeIf { it > 0 }
                    ?.let { "${settings.capacityUah(it) / 1000} mAh" } ?: "—"
            )
            KeyValueRow(
                label = stringResource(R.string.bat_label_uptime),
                value = Fmt.duration(snapshot.uptimeMillis)
            )
        }
    }
}

/**
 * 健康度：優先取「滿充容量 ÷ 設計容量」（sysfs 的 charge_full / charge_full_design），
 * 讀不到時退回系統的籠統健康標籤。雙電芯校正對比值沒影響（分子分母同除 2），
 * 但顯示的 mAh 要校正。
 */
@Composable
private fun BatteryHealthCard(live: LiveSnapshot, settings: AppSettings) {
    val battery = live.battery
    val fullUah = battery?.chargeFullUah?.takeIf { it > 0 }
    val designUah = battery?.designCapacityUah?.takeIf { it > 0 }
    val healthRatio = if (fullUah != null && designUah != null) {
        fullUah.toFloat() / designUah.toFloat()
    } else null

    DeviceInfoCard(title = stringResource(R.string.bat_title_health)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillTag(text = battery?.healthLabel ?: stringResource(R.string.bat_unknown))
                PillTag(
                    text = if ((battery?.temperatureC ?: 0f) > 42f) stringResource(R.string.bat_temp_high) else stringResource(R.string.bat_temp_normal),
                    color = if ((battery?.temperatureC ?: 0f) > 42f) DevicePalette.Warn else DevicePalette.Accent
                )
                if (healthRatio != null) {
                    val (label, color) = healthRating(healthRatio)
                    PillTag(text = label, color = color)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (healthRatio != null && fullUah != null && designUah != null) {
                KeyValueRow(
                    label = stringResource(R.string.bat_label_full_charge),
                    value = "${settings.capacityUah(fullUah) / 1000} mAh"
                )
                KeyValueRow(
                    label = stringResource(R.string.bat_capacity_health),
                    value = String.format(Locale.US, "%.1f%%", healthRatio * 100f)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(R.string.bat_health_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
        }
    }
}

/** 容量健康度評級：≥90% 優良、80–90% 良好、70–80% 衰退、<70% 建議更換。 */
@Composable
private fun healthRating(ratio: Float): Pair<String, androidx.compose.ui.graphics.Color> = when {
    ratio >= 0.90f -> stringResource(R.string.bat_health_excellent) to DevicePalette.Accent
    ratio >= 0.80f -> stringResource(R.string.bat_health_good) to DevicePalette.Accent
    ratio >= 0.70f -> stringResource(R.string.bat_health_fair) to DevicePalette.Warn
    else -> stringResource(R.string.bat_health_poor) to DevicePalette.Danger
}

/** 手機報告：裝置 + 電池一份摘要，可分享（上傳到其他應用）或複製。 */
@Composable
private fun BatteryReportCard(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    settings: AppSettings
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    DeviceInfoCard(title = stringResource(R.string.bat_report_title)) {
        Column {
            Text(
                text = stringResource(R.string.bat_report_desc),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPillButton(
                    text = stringResource(R.string.bat_report_share),
                    onClick = {
                        val report = buildDeviceReport(snapshot, live, settings)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, L10n.s(R.string.bat_report_title))
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, L10n.s(R.string.bat_report_title))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                GlassPillButton(
                    text = stringResource(R.string.bat_report_copy),
                    onClick = {
                        clipboard.setText(AnnotatedString(buildDeviceReport(snapshot, live, settings)))
                    }
                )
            }
        }
    }
}

/** 報告正文：純文字，方便貼到任何地方。 */
private fun buildDeviceReport(
    snapshot: DeviceSnapshot,
    live: LiveSnapshot,
    settings: AppSettings
): String {
    val b = live.battery
    val identity = snapshot.identity
    val os = snapshot.os
    return buildString {
        appendLine("── ${L10n.s(R.string.bat_report_title)} ──")
        appendLine("${L10n.s(R.string.bat_report_time)}: ${Fmt.dateTime(System.currentTimeMillis())}")
        appendLine()
        appendLine("【${L10n.s(R.string.bat_report_device)}】")
        appendLine("${L10n.s(R.string.bat_report_model)}: ${deviceDisplayName(identity)} (${identity.model})")
        appendLine("Android: ${os.release} (API ${os.sdkInt}, ${os.codename})")
        appendLine("SoC: ${snapshot.soc.marketingName}")
        appendLine("RAM: ${Fmt.bytesShort(snapshot.ram.totalBytes)}")
        appendLine()
        appendLine("【${L10n.s(R.string.bat_report_battery)}】")
        if (b != null) {
            appendLine("${L10n.s(R.string.bat_label_level)}: ${b.levelPercent}%")
            appendLine("${L10n.s(R.string.bat_label_status)}: ${b.statusLabel} / ${b.pluggedLabel}")
            appendLine("${L10n.s(R.string.bat_label_temp)}: ${settings.temperature(b.temperatureC)}")
            appendLine("${L10n.s(R.string.bat_label_voltage)}: ${b.voltageMv} mV")
            if (b.currentUa != 0) {
                appendLine("${L10n.s(R.string.bat_label_current)}: ${b.currentUa / 1000} mA")
            }
            b.designCapacityUah?.takeIf { it > 0 }?.let {
                appendLine("${L10n.s(R.string.bat_label_design_capacity)}: ${settings.capacityUah(it) / 1000} mAh")
            }
            b.chargeFullUah?.takeIf { it > 0 }?.let { full ->
                appendLine("${L10n.s(R.string.bat_label_full_charge)}: ${settings.capacityUah(full) / 1000} mAh")
                b.designCapacityUah?.takeIf { it > 0 }?.let { design ->
                    appendLine(
                        "${L10n.s(R.string.bat_capacity_health)}: " +
                            String.format(Locale.US, "%.1f%%", full.toFloat() / design.toFloat() * 100f)
                    )
                }
            }
            appendLine("${L10n.s(R.string.bat_report_health_sys)}: ${b.healthLabel}")
            appendLine("${L10n.s(R.string.bat_label_technology)}: ${b.technology}")
        }
        if (settings.dualCell || settings.powerFactor != 1.0f) {
            appendLine()
            appendLine("【${L10n.s(R.string.bat_report_calibration)}】")
            if (settings.dualCell) appendLine(L10n.s(R.string.settings_dual_cell))
            if (settings.powerFactor != 1.0f) {
                appendLine("${L10n.s(R.string.settings_power_factor)}: " +
                    String.format(Locale.US, "%.0f%%", settings.powerFactor * 100f))
            }
        }
    }.trim()
}

private fun formatCurrent(microAmp: Int): String {
    val milliAmp = microAmp / 1000f
    val direction = if (microAmp >= 0) L10n.s(R.string.bat_charging) else L10n.s(R.string.bat_discharging)
    return String.format(Locale.US, "%.0f mA (%s)", kotlin.math.abs(milliAmp), direction)
}

private fun formatPower(voltageMv: Int?, currentUa: Int?, powerFactor: Float): String {
    if (voltageMv == null || currentUa == null) return "—"
    if (voltageMv <= 0 || currentUa == 0) return "—"
    val watts = (voltageMv / 1000f) * (kotlin.math.abs(currentUa) / 1_000_000f) * powerFactor
    return String.format(Locale.US, "%.2f W", watts)
}
