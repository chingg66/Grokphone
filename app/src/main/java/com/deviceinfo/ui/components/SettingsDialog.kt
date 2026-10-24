package com.deviceinfo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.core.AppSettings
import com.deviceinfo.core.TempUnit
import com.deviceinfo.ui.theme.Appearance
import com.deviceinfo.ui.theme.DevicePalette
import java.util.Locale

/**
 * 設定頁：外觀（深淺 + 色彩）、溫度單位、電池校正、更新日誌與歡迎頁入口。
 *
 * 原本分散在頂部列的「色彩」「更新日誌」兩個入口收進這裡，頂部只留一個齒輪。
 */
@Composable
fun SettingsDialog(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenChangelog: () -> Unit,
    onReopenOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_title),
        confirmLabel = stringResource(R.string.cl_close)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppearanceSection(appearance = appearance, onChange = onAppearanceChange)

            SectionLabel(stringResource(R.string.settings_section_general))
            SettingsRow(label = stringResource(R.string.settings_temp_unit)) {
                Row {
                    TempUnitChip(
                        label = stringResource(R.string.settings_temp_c),
                        selected = settings.tempUnit == TempUnit.Celsius,
                        onClick = { onSettingsChange(settings.copy(tempUnit = TempUnit.Celsius)) }
                    )
                    Spacer(Modifier.size(6.dp))
                    TempUnitChip(
                        label = stringResource(R.string.settings_temp_f),
                        selected = settings.tempUnit == TempUnit.Fahrenheit,
                        onClick = { onSettingsChange(settings.copy(tempUnit = TempUnit.Fahrenheit)) }
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_battery_calibration))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSettingsChange(settings.copy(dualCell = !settings.dualCell)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dual_cell),
                        style = MaterialTheme.typography.bodyLarge,
                        color = DevicePalette.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.settings_dual_cell_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
                Switch(
                    checked = settings.dualCell,
                    onCheckedChange = { onSettingsChange(settings.copy(dualCell = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DevicePalette.Accent.copy(alpha = 0.6f),
                        checkedThumbColor = DevicePalette.Accent
                    )
                )
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_power_factor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = DevicePalette.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_power_factor_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = DevicePalette.TextTertiary
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.0f%%", settings.powerFactor * 100f),
                        style = MaterialTheme.typography.titleMedium,
                        color = DevicePalette.Accent
                    )
                }
                Slider(
                    value = settings.powerFactor,
                    onValueChange = {
                        // 以 5% 為步進，避免浮點滑桿出現 1.37x 這種沒意義的值
                        val stepped = (it * 20f).toInt() / 20f
                        onSettingsChange(settings.copy(powerFactor = stepped.coerceIn(0.5f, 2.0f)))
                    },
                    valueRange = 0.5f..2.0f
                )
            }

            SectionLabel(stringResource(R.string.settings_section_about))
            SettingsNavRow(
                label = stringResource(R.string.settings_changelog),
                onClick = onOpenChangelog
            )
            SettingsNavRow(
                label = stringResource(R.string.settings_reopen_onboarding),
                onClick = onReopenOnboarding
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DevicePalette.TextTertiary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp, start = 8.dp)
    )
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = DevicePalette.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
private fun TempUnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) DevicePalette.Accent else DevicePalette.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun SettingsNavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = DevicePalette.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = DevicePalette.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
