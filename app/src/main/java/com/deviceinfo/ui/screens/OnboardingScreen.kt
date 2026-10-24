package com.deviceinfo.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import com.deviceinfo.R
import com.deviceinfo.core.LanguageStore
import com.deviceinfo.data.internal.AppListAccess
import com.deviceinfo.data.internal.AppListConsent
import com.deviceinfo.ui.components.GlassDialog
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.theme.AccentStyle
import com.deviceinfo.ui.theme.Appearance
import com.deviceinfo.ui.theme.DarkColors
import com.deviceinfo.ui.theme.DevicePalette
import com.deviceinfo.ui.theme.hueRotated
import com.deviceinfo.ui.theme.dynamicColorSupported

/**
 * 首次啟動的歡迎頁。
 *
 * 只做「一次要把事講清楚」的五件事：通知、懸浮窗、應用程式列表、語言、應用色彩。
 * 全部就地完成，不必進系統設定逐層翻；右上「略過」直接進主頁，之後可從更新日誌再打開。
 */
@Composable
fun OnboardingScreen(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    var overlayOn by remember { mutableStateOf(overlayAllowed(context)) }
    var appsOn by remember { mutableStateOf(appsGranted(context)) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }

    // 從系統設定返回時重新讀一次狀態（授權、通知開關都可能在外部被改）
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        notificationsOn = notificationsEnabled(context)
        overlayOn = overlayAllowed(context)
        appsOn = appsGranted(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsOn = granted
        if (!granted) openNotificationSettings(context)
    }

    // DrawScope 不是 @Composable 作用域，配色要先在這裡取好
    val palette = DevicePalette
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.Background)
            .drawWithCache {
                val top = Brush.verticalGradient(
                    listOf(
                        palette.BackgroundTop,
                        palette.Background,
                        palette.BackgroundBottom
                    )
                )
                val glow = Brush.radialGradient(
                    colors = listOf(palette.Accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.06f),
                    radius = size.width * 0.8f
                )
                onDrawBehind {
                    drawRect(top)
                    drawCircle(
                        brush = glow,
                        radius = size.width * 0.8f,
                        center = Offset(size.width * 0.5f, size.height * 0.06f)
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 26.dp,
                    start = 18.dp,
                    end = 18.dp,
                    bottom = 32.dp
                )
        ) {
            Text(
                text = stringResource(R.string.ob_title),
                style = MaterialTheme.typography.headlineMedium,
                color = DevicePalette.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ob_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = DevicePalette.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            PillTag(
                text = stringResource(R.string.ob_device_label, Build.MODEL ?: "—"),
                color = DevicePalette.TextSecondary
            )

            Spacer(Modifier.height(26.dp))
            SectionLabel(stringResource(R.string.ob_section_permissions))
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(DevicePalette.Surface.copy(alpha = 0.72f))
                    .border(1.dp, DevicePalette.OutlineSoft, MaterialTheme.shapes.large)
            ) {
                SettingRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.ob_notifications_title),
                    desc = stringResource(R.string.ob_notifications_desc),
                    granted = notificationsOn,
                    grantedLabel = stringResource(R.string.ob_notifications_on),
                    pendingLabel = stringResource(R.string.ob_notifications_off),
                    actionLabel = stringResource(R.string.ob_notifications_action),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.Layers,
                    title = stringResource(R.string.ob_overlay_title),
                    desc = stringResource(R.string.ob_overlay_desc),
                    granted = overlayOn,
                    grantedLabel = stringResource(R.string.ob_overlay_on),
                    pendingLabel = stringResource(R.string.ob_overlay_off),
                    actionLabel = stringResource(R.string.ob_overlay_action),
                    onAction = { openOverlaySettings(context) }
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.ob_apps_title),
                    desc = stringResource(R.string.ob_apps_desc),
                    granted = appsOn,
                    grantedLabel = stringResource(R.string.ob_apps_on),
                    pendingLabel = stringResource(R.string.ob_apps_off),
                    actionLabel = stringResource(R.string.ob_apps_action),
                    onAction = {
                        AppListConsent.setGranted(context, true)
                        appsOn = appsGranted(context)
                        // 系統層被 ROM 攔住時，只給同意是不夠的，還得帶去放行
                        if (!appsOn) AppListAccess.openSystemSettings(context)
                    }
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.ob_section_personalize))
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(DevicePalette.Surface.copy(alpha = 0.72f))
                    .border(1.dp, DevicePalette.OutlineSoft, MaterialTheme.shapes.large)
            ) {
                SettingRow(
                    icon = Icons.Outlined.Translate,
                    title = stringResource(R.string.ob_language_title),
                    desc = stringResource(R.string.ob_language_desc),
                    granted = true,
                    grantedLabel = languageLabel(context),
                    pendingLabel = "",
                    actionLabel = stringResource(R.string.ob_change),
                    onAction = { showLanguage = true }
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.ob_accent_title),
                    desc = stringResource(R.string.ob_accent_desc),
                    granted = true,
                    grantedLabel = accentLabel(appearance.accent),
                    pendingLabel = "",
                    actionLabel = stringResource(R.string.ob_change),
                    onAction = { showAccent = true }
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.ob_footer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary
            )

            Spacer(Modifier.height(26.dp))
            GlassPillButton(
                text = stringResource(R.string.ob_start),
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_start")
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onSkip)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ob_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = DevicePalette.TextSecondary,
                    modifier = Modifier.testTag("onboarding_skip")
                )
            }
        }
    }

    if (showLanguage) {
        GlassDialog(
            onDismissRequest = { showLanguage = false },
            title = stringResource(R.string.ob_language_dialog),
            confirmLabel = stringResource(R.string.cl_close)
        ) {
            Column {
                LanguageStore.options.forEach { (tag, nativeName) ->
                    ChoiceRow(
                        label = if (tag == LanguageStore.SYSTEM) {
                            stringResource(R.string.ob_language_system)
                        } else {
                            nativeName
                        },
                        selected = LanguageStore.current(context) == tag,
                        onClick = {
                            showLanguage = false
                            LanguageStore.set(context, tag)
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }
        }
    }

    if (showAccent) {
        GlassDialog(
            onDismissRequest = { showAccent = false },
            title = stringResource(R.string.ob_accent_dialog),
            confirmLabel = stringResource(R.string.cl_close)
        ) {
            Column {
                AccentStyle.entries.forEach { style ->
                    ChoiceRow(
                        label = accentLabel(style),
                        selected = appearance.accent == style,
                        swatch = accentPreview(style),
                        enabled = !style.usesWallpaper || dynamicColorSupported,
                        onClick = {
                            showAccent = false
                            onAppearanceChange(appearance.copy(accent = style))
                        }
                    )
                }
                if (!dynamicColorSupported) {
                    Text(
                        text = stringResource(R.string.ob_accent_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DevicePalette.TextTertiary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(DevicePalette.OutlineSoft)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    grantedLabel: String,
    pendingLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(DevicePalette.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DevicePalette.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = DevicePalette.TextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (granted) grantedLabel else pendingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) DevicePalette.Accent else DevicePalette.TextTertiary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(DevicePalette.SurfaceHigher)
                    .border(
                        1.dp,
                        DevicePalette.Accent.copy(alpha = 0.4f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.Accent
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    swatch: Color? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (swatch != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.dp, DevicePalette.Outline, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) DevicePalette.TextPrimary else DevicePalette.TextTertiary,
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
private fun accentLabel(style: AccentStyle): String = stringResource(
    when (style) {
        AccentStyle.Wallpaper -> R.string.ob_accent_wallpaper
        AccentStyle.Green -> R.string.ob_accent_green
        AccentStyle.Blue -> R.string.ob_accent_blue
        AccentStyle.Purple -> R.string.ob_accent_purple
        AccentStyle.Amber -> R.string.ob_accent_amber
        AccentStyle.Rose -> R.string.ob_accent_rose
    }
)

/** 色板小圓點：從固定基準色做色相旋轉，預覽跟實際套用的顏色一致。 */
@Composable
private fun accentPreview(style: AccentStyle): Color = when (style) {
    AccentStyle.Wallpaper -> Color(0xFF7C8CF8)
    else -> DarkColors.Accent.hueRotated(style.hueShift)
}

@Composable
private fun languageLabel(context: Context): String {
    val tag = LanguageStore.current(context)
    if (tag == LanguageStore.SYSTEM) return stringResource(R.string.ob_language_system)
    return LanguageStore.options.firstOrNull { it.first == tag }?.second ?: tag
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun overlayAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

private fun appsGranted(context: Context): Boolean =
    AppListConsent.isGranted(context) && AppListAccess.isGranted(context)

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
}

private fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}
