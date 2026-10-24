package com.deviceinfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.data.model.DeviceIdentity
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.AndroidVersionBadge
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassDialog
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.KeyValueGrid
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.theme.DevicePalette

@Composable
fun SystemScreen(
    snapshot: DeviceSnapshot,
    topPadding: Dp
) {
    var showFingerprint by remember { mutableStateOf(false) }

    InfoScreen(topPadding = topPadding) {
        item { DeviceCard(snapshot, onShowFingerprint = { showFingerprint = true }) }
        item { OsCard(snapshot) }
        item { BuildCard(snapshot) }
    }

    if (showFingerprint) {
        GlassDialog(
            onDismissRequest = { showFingerprint = false },
            title = stringResource(R.string.sys_full_fingerprint),
            subtitle = "Build.FINGERPRINT"
        ) {
            Text(
                text = snapshot.identity.fingerprint,
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary
            )
        }
    }
}

@Composable
private fun DeviceCard(snapshot: DeviceSnapshot, onShowFingerprint: () -> Unit) {
    val identity = snapshot.identity
    DeviceInfoCard(
        title = stringResource(R.string.sys_device),
        trailing = {
            GlassPillButton(text = stringResource(R.string.sys_fingerprint), onClick = onShowFingerprint)
        }
    ) {
        Column {
            val displayName = remember(identity) { deviceDisplayName(identity) }
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = DevicePalette.Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 行銷名稱與內部型號不同時，把型號留在下面當查證用的補充資訊
            if (identity.model != displayName && identity.model.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = identity.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            KeyValueGrid(
                items = listOf(
                    stringResource(R.string.sys_model) to identity.model,
                    stringResource(R.string.sys_product) to identity.product,
                    stringResource(R.string.sys_device_name) to identity.device,
                    stringResource(R.string.sys_board) to identity.board,
                    stringResource(R.string.sys_manufacturer) to identity.manufacturer,
                    stringResource(R.string.sys_brand) to identity.brand,
                    "SKU" to identity.sku,
                    stringResource(R.string.sys_hardware) to identity.hardware
                )
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sys_baseband),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.TextTertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = identity.baseband,
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OsCard(snapshot: DeviceSnapshot) {
    val os = snapshot.os
    // 代號只在已知且不與版本號重複時才顯示，避免出現「Android 17 (Android)」這種沒資訊量的括號。
    val codename = os.codename.trim().takeIf {
        it.isNotEmpty() && it != "—" && !it.equals("Android", ignoreCase = true) &&
            !it.equals(os.release, ignoreCase = true)
    }
    DeviceInfoCard(title = stringResource(R.string.sys_os)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AndroidVersionBadge(version = os.release, apiLevel = os.sdkInt, size = 104.dp)
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ANDROID ${os.release}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DevicePalette.TextTertiary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = codename ?: os.release,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DevicePalette.Accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        PillTag(text = "API ${os.sdkInt}")
                        Spacer(Modifier.width(8.dp))
                        PillTag(text = if (os.is64Bit) "64-bit" else "32-bit")
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            KeyValueGrid(
                items = listOf(
                    "ROM" to os.romName,
                    stringResource(R.string.sys_rom_version) to os.romVersion,
                    stringResource(R.string.sys_security_patch) to os.securityPatch,
                    stringResource(R.string.sys_kernel) to os.kernel,
                    "Java VM" to os.javaVm,
                    stringResource(R.string.sys_build_type) to os.buildType
                )
            )
        }
    }
}

@Composable
private fun BuildCard(snapshot: DeviceSnapshot) {
    val os = snapshot.os
    DeviceInfoCard(title = stringResource(R.string.sys_build_info)) {
        KeyValueGrid(
            items = listOf(
                stringResource(R.string.sys_build_id) to snapshot.identity.buildId,
                stringResource(R.string.sys_build_time) to Fmt.dateTime(os.buildTime),
                stringResource(R.string.sys_build_tags) to os.buildTags,
                "Bootloader" to snapshot.identity.bootloader,
                stringResource(R.string.sys_serial) to snapshot.identity.serial.let {
                    if (it == "—") stringResource(R.string.sys_restricted_by_system) else it
                },
                "ABI" to os.abis.joinToString(", ")
            )
        )
    }
}

/** 品牌名稱正規化：samsung → Samsung，OPPO / realme 這類維持原樣。 */
private fun prettyBrand(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty() || value == "—") return ""
    return if (value.none { it.isUpperCase() }) {
        value.replaceFirstChar { it.uppercase() }
    } else {
        value
    }
}

/**
 * 卡片主標。優先使用行銷名稱（Redmi K70），沒有時退回型號；
 * 行銷名稱本身若不含品牌，才補上品牌（Samsung Galaxy S24）。
 */
internal fun deviceDisplayName(identity: DeviceIdentity): String {
    val brand = prettyBrand(identity.brand)
    val market = identity.marketName.trim().takeIf { it.isNotEmpty() && it != "—" }
    val base = market ?: identity.model.trim()
    return when {
        base.isEmpty() || base == "—" -> prettyBrand(identity.manufacturer).ifEmpty { "—" }
        brand.isNotEmpty() && !base.startsWith(brand, ignoreCase = true) -> "$brand $base"
        else -> base
    }
}
