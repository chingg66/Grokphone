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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.SensorReading
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.ExpandableInfoRow
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.theme.DevicePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.util.Locale

@Composable
fun SensorsScreen(
    snapshot: DeviceSnapshot,
    topPadding: Dp,
    observeSensor: (Int) -> Flow<SensorReading>,
    sensorUnit: (Int) -> String
) {
    // 同一時刻只訂閱一個感測器：幾十個感測器常駐監聽會顯著增加待機功耗
    var expandedType by remember { mutableStateOf<Int?>(null) }

    InfoScreen(topPadding = topPadding) {
        item {
            DeviceInfoCard(title = stringResource(R.string.sen_sensors)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = snapshot.sensors.size.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = DevicePalette.Accent
                        )
                        Spacer(Modifier.fillMaxWidth(0.04f))
                        Text(
                            text = stringResource(R.string.sen_available_sensors_suffix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DevicePalette.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sen_expand_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
            }
        }

        items(snapshot.sensors.size) { index ->
            val sensor = snapshot.sensors[index]
            val flow = remember(sensor.type) { observeSensor(sensor.type) }
            val expanded = expandedType == sensor.type

            ExpandableInfoRow(
                title = sensor.typeLabel,
                subtitle = sensor.name,
                expanded = expanded,
                onToggle = { expandedType = if (expanded) null else sensor.type }
            ) {
                SensorDetail(
                    type = sensor.type,
                    unit = sensorUnit(sensor.type),
                    flow = flow,
                    vendor = sensor.vendor,
                    maxRange = sensor.maxRange,
                    resolution = sensor.resolution,
                    powerMilliAmp = sensor.powerMilliAmp,
                    isWakeUp = sensor.isWakeUp
                )
            }
        }
    }
}

@Composable
private fun SensorDetail(
    type: Int,
    unit: String,
    flow: Flow<SensorReading>,
    vendor: String,
    maxRange: Float,
    resolution: Float,
    powerMilliAmp: Float,
    isWakeUp: Boolean
) {
    var reading by remember(type) { mutableStateOf<SensorReading?>(null) }

    LaunchedEffect(type, flow) {
        flow.catch { /* 订阅失败时保持“等待資料”状态，不抛给 composition */ }
            .collect { reading = it }
    }

    Column {
        val values = reading?.values.orEmpty()
        if (values.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                values.forEachIndexed { index, value ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = axisLabel(values.size, index),
                            style = MaterialTheme.typography.labelSmall,
                            color = DevicePalette.TextTertiary
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", value),
                            style = MaterialTheme.typography.titleMedium,
                            color = DevicePalette.Accent
                        )
                    }
                }
            }
            if (unit.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sen_unit, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
        } else {
            Text(
                text = stringResource(R.string.sen_waiting_data),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
        }

        Spacer(Modifier.height(12.dp))
        KeyValueRow(label = stringResource(R.string.sen_vendor), value = vendor)
        KeyValueRow(label = stringResource(R.string.sen_max_range), value = String.format(Locale.US, "%.2f", maxRange))
        KeyValueRow(label = stringResource(R.string.sen_resolution), value = String.format(Locale.US, "%.4f", resolution))
        KeyValueRow(
            label = stringResource(R.string.sen_power),
            value = if (powerMilliAmp > 0f) String.format(Locale.US, "%.2f mA", powerMilliAmp) else "—"
        )
        Spacer(Modifier.height(8.dp))
        if (isWakeUp) {
            PillTag(text = stringResource(R.string.sen_wake_up))
        }
    }
}

@Composable
private fun axisLabel(size: Int, index: Int): String = when {
    size >= 3 && index < 3 -> listOf("X", "Y", "Z")[index]
    size == 1 -> stringResource(R.string.sen_value)
    else -> stringResource(R.string.sen_value_n, index + 1)
}
