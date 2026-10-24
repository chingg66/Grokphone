package com.deviceinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.ui.theme.AccentStyle
import com.deviceinfo.ui.theme.Appearance
import com.deviceinfo.ui.theme.DarkColors
import com.deviceinfo.ui.theme.DarkMode
import com.deviceinfo.ui.theme.DevicePalette
import com.deviceinfo.ui.theme.dynamicColorSupported
import com.deviceinfo.ui.theme.hueRotated

/**
 * 外觀設定：深淺模式 + 應用色彩。
 *
 * 舊版頂部按鈕是四擋循環（含動態取色），使用者要按好幾次才知道有哪幾種；
 * 改成一次列完，深淺與色彩互相獨立，組合更直覺。
 */
@Composable
fun AppearanceDialog(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.appearance_title),
        subtitle = stringResource(R.string.appearance_subtitle),
        confirmLabel = stringResource(R.string.cl_close)
    ) {
        AppearanceSection(appearance = appearance, onChange = onChange)
    }
}

/** 外觀設定內容（深淺模式 + 色彩），供外觀對話框與設定頁共用。 */
@Composable
fun AppearanceSection(
    appearance: Appearance,
    onChange: (Appearance) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.appearance_mode_section),
            style = MaterialTheme.typography.labelMedium,
            color = DevicePalette.TextTertiary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        DarkMode.entries.forEach { mode ->
            DialogRadioRow(
                label = stringResource(
                    when (mode) {
                        DarkMode.System -> R.string.ob_dark_system
                        DarkMode.Light -> R.string.ob_dark_light
                        DarkMode.Dark -> R.string.ob_dark_dark
                    }
                ),
                selected = appearance.darkMode == mode,
                onClick = { onChange(appearance.copy(darkMode = mode)) }
            )
        }

        Text(
            text = stringResource(R.string.appearance_accent_section),
            style = MaterialTheme.typography.labelMedium,
            color = DevicePalette.TextTertiary,
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
        )
        AccentStyle.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { style ->
                    AccentChip(
                        style = style,
                        selected = appearance.accent == style,
                        enabled = !style.usesWallpaper || dynamicColorSupported,
                        onClick = { onChange(appearance.copy(accent = style)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 最後一行不滿 3 個時補空位，避免色塊被拉寬
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!dynamicColorSupported) {
            Text(
                text = stringResource(R.string.ob_accent_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )
        }
    }
}

@Composable
private fun DialogRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = DevicePalette.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = DevicePalette.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AccentChip(
    style: AccentStyle,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dot = accentDot(style)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) DevicePalette.Accent.copy(alpha = 0.12f)
                else DevicePalette.SurfaceHigh.copy(alpha = 0.7f)
            )
            .border(
                1.dp,
                if (selected) DevicePalette.Accent.copy(alpha = 0.55f) else DevicePalette.OutlineSoft,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (enabled) dot else dot.copy(alpha = 0.35f))
                .border(1.dp, DevicePalette.Outline, CircleShape)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                when (style) {
                    AccentStyle.Wallpaper -> R.string.ob_accent_wallpaper
                    AccentStyle.Green -> R.string.ob_accent_green
                    AccentStyle.Blue -> R.string.ob_accent_blue
                    AccentStyle.Purple -> R.string.ob_accent_purple
                    AccentStyle.Amber -> R.string.ob_accent_amber
                    AccentStyle.Rose -> R.string.ob_accent_rose
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) DevicePalette.TextSecondary else DevicePalette.TextTertiary,
            maxLines = 1
        )
    }
}

/** 色板預覽點：固定以深色主色為基準做色相旋轉，與實際套用一致。 */
@Composable
fun accentDot(style: AccentStyle): Color = when (style) {
    AccentStyle.Wallpaper -> Color(0xFF7C8CF8)
    else -> DarkColors.Accent.hueRotated(style.hueShift)
}
