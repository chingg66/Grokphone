package com.deviceinfo.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Android 12+ 才支援從桌布取色；低版本自動退回預設綠色系。 */
val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 應用自己的語義配色。深淺兩套都保持「儀錶盤綠」的視覺語言：
 * 深色走螢光綠，淺色壓深綠以保對比。
 */
data class DeviceColors(
    val Background: Color,
    /** 環境光背景的上下端點：頂部略亮、底部漸暗，玻璃折射依賴這個明暗變化。 */
    val BackgroundTop: Color,
    val BackgroundBottom: Color,
    val BackgroundElevated: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val SurfaceHigher: Color,
    val Outline: Color,
    val OutlineSoft: Color,
    val Accent: Color,
    val AccentDim: Color,
    val AccentDeep: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val Warn: Color,
    val Danger: Color,
    val Info: Color,
    /** 卡片頂部漸變高光，模擬玻璃邊緣受光。 */
    val CardTopSheen: Color,
    /** 玻璃面板的染色：深色用白霧，淺色用白霧。 */
    val GlassTint: Color,
    /**
     * 玻璃上層的提亮蒙版。
     *
     * 庫的亮度自適應會在偵測到亮背景時強制疊 14–22% 黑「壓暗保可讀性」，
     * 淺色主題下會把玻璃壓成一塊灰，標籤也跟著發灰。
     * 這裡在玻璃之上、內容之下補一層白霧把它拉回來；深色主題為全透明。
     */
    val GlassVeil: Color
)

val DarkColors = DeviceColors(
    Background = Color(0xFF121312),
    BackgroundTop = Color(0xFF191A19),
    BackgroundBottom = Color(0xFF0D0E0D),
    BackgroundElevated = Color(0xFF1B1C1B),
    Surface = Color(0xFF1F201F),
    SurfaceHigh = Color(0xFF262826),
    SurfaceHigher = Color(0xFF2E302E),
    Outline = Color(0xFF3D423E),
    OutlineSoft = Color(0x14FFFFFF),
    Accent = Color(0xFF6FE08A),
    AccentDim = Color(0xFF3E8F55),
    AccentDeep = Color(0xFF1B3524),
    TextPrimary = Color(0xFFE9ECEA),
    TextSecondary = Color(0xFFA7ADA9),
    TextTertiary = Color(0xFF777D79),
    Warn = Color(0xFFFFC46B),
    Danger = Color(0xFFFF7B72),
    Info = Color(0xFF7FC4FF),
    CardTopSheen = Color(0x0DFFFFFF),
    GlassTint = Color(0x2AFFFFFF),
    GlassVeil = Color(0x00000000)
)

val LightColors = DeviceColors(
    Background = Color(0xFFF4F4F3),
    BackgroundTop = Color(0xFFEAEBEA),
    BackgroundBottom = Color(0xFFE4E5E4),
    BackgroundElevated = Color(0xFFECEDEC),
    Surface = Color(0xFFFFFFFF),
    SurfaceHigh = Color(0xFFF3F4F3),
    SurfaceHigher = Color(0xFFE9EAE9),
    Outline = Color(0xFFC9CEC9),
    OutlineSoft = Color(0x14000000),
    Accent = Color(0xFF1E7C40),
    AccentDim = Color(0xFF4C9E68),
    AccentDeep = Color(0xFFD9F0DF),
    TextPrimary = Color(0xFF171A18),
    TextSecondary = Color(0xFF535A55),
    TextTertiary = Color(0xFF7E857F),
    Warn = Color(0xFF9A6200),
    Danger = Color(0xFFB3402F),
    Info = Color(0xFF1D6FB5),
    CardTopSheen = Color(0x0A000000),
    GlassTint = Color(0x59FFFFFF),
    GlassVeil = Color(0x9EFFFFFF)
)

val LocalDeviceColors = staticCompositionLocalOf { DarkColors }

/**
 * 動態取色：把 Material You（桌布取色）的 ColorScheme 映射到應用語義配色。
 * Accent 系跟主色、表面系跟容器色；Warn / 玻璃相關保持原語義（深淺各自成套），
 * 保證玻璃質感與可讀性規則在動態模式下依然成立。
 */
private fun dynamicDeviceColors(scheme: androidx.compose.material3.ColorScheme, dark: Boolean): DeviceColors {
    // 保留桌布色彩的方向，但把大面積背景壓回中性灰，避免整頁被高飽和色染滿。
    val neutral = if (dark) Color(0xFF141514) else Color(0xFFF4F4F3)
    return DeviceColors(
        Background = lerp(scheme.background, neutral, 0.72f),
        BackgroundTop = lerp(scheme.surfaceContainerLow, neutral, 0.66f),
        BackgroundBottom = lerp(scheme.surfaceContainerLowest, neutral, 0.76f),
        BackgroundElevated = lerp(scheme.surfaceContainer, neutral, 0.62f),
        Surface = lerp(scheme.surfaceContainerLow, neutral, 0.56f),
        SurfaceHigh = lerp(scheme.surfaceContainer, neutral, 0.52f),
        SurfaceHigher = lerp(scheme.surfaceContainerHigh, neutral, 0.48f),
        Outline = lerp(scheme.outlineVariant, neutral, 0.34f),
        OutlineSoft = if (dark) Color(0x14FFFFFF) else Color(0x14000000),
        Accent = scheme.primary.vividAccent(dark),
        AccentDim = scheme.secondary.vividAccent(dark),
        AccentDeep = scheme.primaryContainer,
        TextPrimary = scheme.onBackground,
        TextSecondary = scheme.onSurfaceVariant,
        TextTertiary = scheme.outline,
        Warn = if (dark) Color(0xFFFFC46B) else Color(0xFF9A6200),
        Danger = scheme.error,
        Info = scheme.tertiary,
        CardTopSheen = if (dark) Color(0x0DFFFFFF) else Color(0x0A000000),
        GlassTint = if (dark) Color(0x2AFFFFFF) else Color(0x59FFFFFF),
        GlassVeil = if (dark) Color(0x00000000) else Color(0x9EFFFFFF)
    )
}/**
 * 全域語義取色入口。歷史上是 object，改為跟隨主題的組合期讀取後，
 * `DevicePalette.Accent` 這類既有呼叫點語法完全不變。
 */
val DevicePalette: DeviceColors
    @Composable get() = LocalDeviceColors.current

private val DarkScheme = darkColorScheme(
    primary = DarkColors.Accent,
    onPrimary = Color(0xFF04220F),
    primaryContainer = DarkColors.AccentDeep,
    onPrimaryContainer = Color(0xFFB9F5C6),
    secondary = Color(0xFF9CC7A7),
    onSecondary = Color(0xFF0C2113),
    secondaryContainer = Color(0xFF1C2C22),
    onSecondaryContainer = Color(0xFFCBE8D2),
    tertiary = DarkColors.Info,
    onTertiary = Color(0xFF04202F),
    background = DarkColors.Background,
    onBackground = DarkColors.TextPrimary,
    surface = DarkColors.Surface,
    onSurface = DarkColors.TextPrimary,
    surfaceVariant = DarkColors.SurfaceHigh,
    onSurfaceVariant = DarkColors.TextSecondary,
    surfaceContainerLowest = Color(0xFF0E1210),
    surfaceContainerLow = DarkColors.Surface,
    surfaceContainer = DarkColors.SurfaceHigh,
    surfaceContainerHigh = DarkColors.SurfaceHigher,
    surfaceContainerHighest = Color(0xFF26302A),
    outline = DarkColors.Outline,
    outlineVariant = DarkColors.OutlineSoft,
    error = DarkColors.Danger,
    onError = Color(0xFF3A0B08),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFFD9D6),
    inverseSurface = DarkColors.TextPrimary,
    inverseOnSurface = Color(0xFF141A16),
    scrim = Color(0xCC000000)
)

private val LightScheme = lightColorScheme(
    primary = LightColors.Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightColors.AccentDeep,
    onPrimaryContainer = Color(0xFF0A3318),
    secondary = Color(0xFF3E6B4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9F0DF),
    onSecondaryContainer = Color(0xFF0A3318),
    tertiary = LightColors.Info,
    onTertiary = Color(0xFFFFFFFF),
    background = LightColors.Background,
    onBackground = LightColors.TextPrimary,
    surface = LightColors.Surface,
    onSurface = LightColors.TextPrimary,
    surfaceVariant = LightColors.SurfaceHigh,
    onSurfaceVariant = LightColors.TextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightColors.Surface,
    surfaceContainer = LightColors.SurfaceHigh,
    surfaceContainerHigh = LightColors.SurfaceHigher,
    surfaceContainerHighest = Color(0xFFDDE6DF),
    outline = LightColors.Outline,
    outlineVariant = LightColors.OutlineSoft,
    error = LightColors.Danger,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = LightColors.TextPrimary,
    inverseOnSurface = Color(0xFFF0F5F0),
    scrim = Color(0x99000000)
)

/**
 * Google Sans（Latin）+ 系統字體回退（CJK 等不在字體內的字型）。
 * Semibold 映射到 Bold——Google Sans 沒有 600 檔，用 700 讓標題層級更明確。
 */
private val GoogleSansFamily = FontFamily(
    androidx.compose.ui.text.font.Font(com.deviceinfo.R.font.google_sans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.deviceinfo.R.font.google_sans_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.deviceinfo.R.font.google_sans_bold, FontWeight.Bold)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

/** 把自訂 / 取色後的語義配色回填到 Material ColorScheme，讓 M3 元件跟著換色。 */
private fun androidx.compose.material3.ColorScheme.matchingDeviceColors(
    colors: DeviceColors
): androidx.compose.material3.ColorScheme = copy(
    primary = colors.Accent,
    primaryContainer = colors.AccentDeep,
    secondary = colors.AccentDim,
    tertiary = colors.Info,
    background = colors.Background,
    onBackground = colors.TextPrimary,
    surface = colors.Surface,
    onSurface = colors.TextPrimary,
    surfaceVariant = colors.SurfaceHigh,
    onSurfaceVariant = colors.TextSecondary,
    surfaceContainerLowest = colors.BackgroundBottom,
    surfaceContainerLow = colors.Surface,
    surfaceContainer = colors.SurfaceHigh,
    surfaceContainerHigh = colors.SurfaceHigher,
    outline = colors.Outline,
    error = colors.Danger
)

@Composable
fun DeviceiNfoTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance.darkMode) {
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Dark -> true
        DarkMode.Light -> false
    }
    val useWallpaper = appearance.accent.usesWallpaper && dynamicColorSupported
    // 換桌布 / 回到前景時 +1，逼下面的取色重算（見 rememberWallpaperRevision）
    val wallpaperRevision = rememberWallpaperRevision()
    val context = androidx.compose.ui.platform.LocalContext.current

    val colors: DeviceColors
    val colorScheme: androidx.compose.material3.ColorScheme
    if (useWallpaper) {
        val systemScheme = remember(wallpaperRevision, darkTheme) {
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        // 系統調色盤資源換桌布後可能沒刷新，這裡用桌布主色色相把它掰回正確方向。
        // 系統已經更新過的話 delta 近乎 0，等於不做任何事。
        val aligned = remember(systemScheme, context) {
            val wallpaperHue = WallpaperPalette.primaryHue(context)
            val base = dynamicDeviceColors(systemScheme, darkTheme)
            if (wallpaperHue == null) {
                base
            } else {
                val schemeHsv = FloatArray(3)
                android.graphics.Color.colorToHSV(systemScheme.primary.toArgb(), schemeHsv)
                base.shiftedHue(hueDelta(schemeHsv[0], wallpaperHue))
            }
        }
        colors = aligned
        colorScheme = systemScheme.matchingDeviceColors(colors)
    } else {
        val base = if (darkTheme) DarkColors else LightColors
        colors = remember(darkTheme, appearance.accent) {
            base.shiftedHue(appearance.accent.hueShift)
        }
        val baseScheme = if (darkTheme) DarkScheme else LightScheme
        // 色板變了，Material 元件（按鈕 / ripple / 對話框）也要跟著換主色
        colorScheme = if (appearance.accent == AccentStyle.Green) {
            baseScheme
        } else {
            baseScheme.matchingDeviceColors(colors)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDeviceColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
