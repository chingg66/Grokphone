package com.deviceinfo.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.data.internal.AppListAccess
import com.deviceinfo.data.internal.AppListConsent
import com.deviceinfo.data.model.AppDetail
import com.deviceinfo.data.model.AppDetailState
import com.deviceinfo.data.model.AppEntry
import com.deviceinfo.data.model.AppsSummary
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.ui.InfoScreen
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassDialog
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.theme.DevicePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppFilter(@StringRes val labelRes: Int) {
    All(R.string.apps_filter_all),
    User(R.string.apps_filter_user),
    System(R.string.apps_filter_system)
}

/** 2000-01-01 的時間戳；比它更早的值在 Android 上只可能是「沒有記錄」，不是真的安裝時間。 */
private const val MIN_PLAUSIBLE_TIME_MS = 946684800000L

/** 單一 ABI 最多先列出的原生庫數量，其餘以「另有 N 個」帶過。 */
private const val MAX_LIBS_SHOWN = 40

/**
 * 應用程式頁。
 *
 * 已安裝清單屬於隱私資料，所以先做「應用內明示同意」再展示：
 * 同意前只顯示權限卡片，同意後才列出清單。
 * 系統層（Android 11+）若沒放行 `QUERY_ALL_PACKAGES`，再顯示一張受限卡片，
 * 把用戶導到系統設定確認，並提供重新掃描。
 */
@Composable
fun AppsScreen(
    snapshot: DeviceSnapshot,
    topPadding: Dp,
    onReloadApps: () -> Unit = {},
    appDetailState: AppDetailState = AppDetailState.Idle,
    onOpenAppDetail: (String) -> Unit = {},
    onDismissAppDetail: () -> Unit = {}
) {
    val context = LocalContext.current
    var consent by remember { mutableStateOf(AppListConsent.isGranted(context)) }
    var declined by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(AppFilter.All) }
    val summary = snapshot.apps

    val apps = remember(summary, filter) {
        when (filter) {
            AppFilter.All -> summary.apps
            AppFilter.User -> summary.apps.filterNot { it.isSystem }
            AppFilter.System -> summary.apps.filter { it.isSystem }
        }
    }

    // 從系統設定返回後自動重掃一次：用戶可能在設定頁放行了權限，
    // 不必再手動點「重新掃描」。首次進入時跳過，避免重複採集。
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce && consent) onReloadApps()
        resumedOnce = true
    }

    InfoScreen(topPadding = topPadding) {
        if (!consent) {
            // 同意前不展示任何清單內容
            item {
                AppListAccessCard(
                    consentGranted = false,
                    declined = declined,
                    fullAccess = summary.fullAccess,
                    queryAllGranted = summary.queryAllGranted,
                    visibleCount = summary.total,
                    onAllow = {
                        AppListConsent.setGranted(context, true)
                        consent = true
                        onReloadApps()
                    },
                    onDecline = { declined = true },
                    onOpenSettings = { AppListAccess.openSystemSettings(context) },
                    onRescan = onReloadApps
                )
            }
        } else {
            if (!summary.fullAccess) {
                item {
                    AppListAccessCard(
                        consentGranted = true,
                        declined = false,
                        fullAccess = false,
                        queryAllGranted = summary.queryAllGranted,
                        visibleCount = summary.total,
                        onAllow = {},
                        onDecline = {},
                        onOpenSettings = { AppListAccess.openSystemSettings(context) },
                        onRescan = onReloadApps
                    )
                }
            }

            item {
                AppsSummaryCard(
                    summary = summary,
                    filter = filter,
                    fullAccess = summary.fullAccess,
                    onFilterChange = { filter = it },
                    onRescan = onReloadApps
                )
            }
            if (apps.isEmpty()) {
                item {
                    DeviceInfoCard {
                        Text(
                            text = stringResource(R.string.apps_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = DevicePalette.TextTertiary
                        )
                    }
                }
            }
            items(apps, key = { it.packageName }) { app ->
                AppRow(app, onClick = { onOpenAppDetail(app.packageName) })
            }
        }
    }

    // 應用架構詳情（LibChecker 風格）：ABI、原生庫、元件、權限等
    when (val detailState = appDetailState) {
        AppDetailState.Idle -> Unit
        else -> AppDetailDialog(detailState, onDismiss = onDismissAppDetail)
    }
}

/**
 * 權限卡片，三種狀態共用一個版式：
 *  - 尚未同意：說明用途，主按鈕「允許讀取」，可「暫不允許」；
 *  - 已暫不允許：保留一個重新允許的入口；
 *  - 已同意但系統受限：說明目前只能看到部分應用，主按鈕「前往系統設定」。
 */
@Composable
private fun AppListAccessCard(
    consentGranted: Boolean,
    declined: Boolean,
    fullAccess: Boolean,
    queryAllGranted: Boolean,
    visibleCount: Int,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit
) {
    val restricted = consentGranted && !fullAccess
    val accent = when {
        restricted -> DevicePalette.Warn
        declined -> DevicePalette.TextTertiary
        else -> DevicePalette.Accent
    }
    DeviceInfoCard(
        title = when {
            restricted -> stringResource(R.string.apps_title_restricted)
            declined -> stringResource(R.string.apps_title_permission)
            else -> stringResource(R.string.apps_title_permission)
        },
        titleIcon = if (restricted) Icons.Outlined.Lock else Icons.Outlined.Apps
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillTag(
                    text = when {
                        restricted && queryAllGranted -> stringResource(R.string.apps_tag_rom_blocked)
                        restricted -> stringResource(R.string.apps_tag_perm_not_granted)
                        declined -> stringResource(R.string.apps_tag_declined)
                        else -> stringResource(R.string.apps_tag_waiting)
                    },
                    color = accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (consentGranted) stringResource(R.string.apps_visible_count, visibleCount)
                    else stringResource(R.string.apps_local_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    restricted && queryAllGranted ->
                        stringResource(R.string.apps_desc_rom_blocked, visibleCount)
                    restricted ->
                        stringResource(R.string.apps_desc_restricted, visibleCount)
                    declined ->
                        stringResource(R.string.apps_desc_declined)
                    else ->
                        stringResource(R.string.apps_desc_intro)
                },
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    restricted -> {
                        Text(
                            text = stringResource(R.string.apps_rescan),
                            style = MaterialTheme.typography.labelMedium,
                            color = DevicePalette.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onRescan)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        GlassPillButton(text = stringResource(R.string.apps_open_settings), onClick = onOpenSettings)
                    }

                    declined -> {
                        Text(
                            text = stringResource(R.string.apps_system_settings),
                            style = MaterialTheme.typography.labelMedium,
                            color = DevicePalette.TextTertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenSettings)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        GlassPillButton(text = stringResource(R.string.apps_reallow), onClick = onAllow)
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.apps_decline),
                            style = MaterialTheme.typography.labelMedium,
                            color = DevicePalette.TextTertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onDecline)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        GlassPillButton(text = stringResource(R.string.apps_allow), onClick = onAllow)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsSummaryCard(
    summary: AppsSummary,
    filter: AppFilter,
    fullAccess: Boolean,
    onFilterChange: (AppFilter) -> Unit,
    onRescan: () -> Unit
) {
    DeviceInfoCard(title = stringResource(R.string.apps_title)) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = summary.total.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = DevicePalette.Accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = stringResource(R.string.apps_summary_counts, summary.userCount, summary.systemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.apps_summary_size, Fmt.bytes(summary.installedBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppFilter.entries.forEach { option ->
                    val selected = option == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) DevicePalette.Accent.copy(alpha = 0.16f)
                                else DevicePalette.SurfaceHigher
                            )
                            .clickable { onFilterChange(option) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) DevicePalette.Accent else DevicePalette.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onRescan)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.apps_rescan),
                        tint = DevicePalette.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (fullAccess) stringResource(R.string.apps_full_list) else stringResource(R.string.apps_partial_list),
                        style = MaterialTheme.typography.labelSmall,
                        color = DevicePalette.TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DevicePalette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = DevicePalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    PillTag(text = stringResource(R.string.apps_system), color = DevicePalette.TextSecondary)
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.apps_version_line, app.versionName, app.targetSdk),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = Fmt.bytes(app.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            color = DevicePalette.TextSecondary
        )
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DevicePalette.SurfaceHigher),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 應用架構詳情對話框。
 *
 * ABI 判定來自 APK 內 `lib/<abi>/` 底下的 `.so` 條目（含 split APK），
 * 元件與權限數來自 manifest，與 LibChecker 的核心資訊對齊。
 */
@Composable
private fun AppDetailDialog(state: AppDetailState, onDismiss: () -> Unit) {
    when (state) {
        AppDetailState.Idle -> Unit
        AppDetailState.Loading -> GlassDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.apps_detail_loading)
        ) {}
        is AppDetailState.Failed -> GlassDialog(
            onDismissRequest = onDismiss,
            title = state.packageName
        ) {
            Text(
                text = stringResource(R.string.apps_detail_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
        is AppDetailState.Ready -> AppDetailContent(state.detail, onDismiss)
    }
}

@Composable
private fun AppDetailContent(detail: AppDetail, onDismiss: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val unknown = stringResource(R.string.apps_detail_unknown)
    // 系統預裝應用可能回傳 0 或極小的時間戳，直接印出來會是 1970-01-01
    fun formatDate(millis: Long): String =
        if (millis < MIN_PLAUSIBLE_TIME_MS) "—" else dateFmt.format(Date(millis))

    GlassDialog(
        onDismissRequest = onDismiss,
        title = detail.label,
        subtitle = detail.packageName
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp)
        ) {
            // ABI pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (detail.abis.isEmpty()) {
                    PillTag(text = "Kotlin / Java", color = DevicePalette.TextSecondary)
                } else {
                    detail.abis.forEach { abi ->
                        PillTag(text = abi, color = DevicePalette.Accent)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            DetailRow(
                label = stringResource(R.string.apps_detail_version),
                value = stringResource(R.string.apps_detail_version_value, detail.versionName, detail.versionCode)
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_sdk),
                value = detail.minSdk.toString()
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_target_sdk),
                value = detail.targetSdk.toString()
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_installer),
                value = detail.installer ?: unknown
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_installed),
                value = formatDate(detail.installedAtMillis)
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_updated),
                value = formatDate(detail.updatedAtMillis)
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_splits),
                value = detail.splitCount.toString()
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_components),
                value = stringResource(
                    R.string.apps_detail_components_value,
                    detail.activityCount ?: 0,
                    detail.serviceCount ?: 0,
                    detail.receiverCount ?: 0,
                    detail.providerCount ?: 0
                )
            )
            DetailRow(
                label = stringResource(R.string.apps_detail_permissions),
                value = (detail.permissionCount ?: 0).toString()
            )

            // 原生庫
            Spacer(Modifier.height(12.dp))
            if (!detail.hasNativeLibs) {
                Text(
                    text = stringResource(R.string.apps_detail_native_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            } else {
                val total = detail.nativeLibs.sumOf { it.libs.size }
                Text(
                    text = stringResource(R.string.apps_detail_native_title, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.Accent
                )
                detail.nativeLibs.forEach { group ->
                    Spacer(Modifier.height(8.dp))
                    PillTag(text = group.abi, color = DevicePalette.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    // 大型 App 動輒上百個 .so，先列前 40 個，其餘以「另有 N 個」提示
                    val shown = group.libs.take(MAX_LIBS_SHOWN)
                    Text(
                        text = shown.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextSecondary
                    )
                    val rest = group.libs.size - shown.size
                    if (rest > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.apps_detail_libs_more, rest),
                            style = MaterialTheme.typography.bodySmall,
                            color = DevicePalette.TextTertiary
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DevicePalette.TextTertiary,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = DevicePalette.TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}
