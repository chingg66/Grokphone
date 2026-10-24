package com.deviceinfo.ui.theme

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/** 深淺模式。動態取色不再是「模式」，而是 [AccentStyle] 裡的一個選項。 */
enum class DarkMode { System, Light, Dark;
    fun next(): DarkMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }
}

/**
 * 應用色彩。
 *
 * 除 [Wallpaper] 外的色板都是把預設綠色系整體做色相旋轉生成的：
 * 明度與飽和度完全不變，等於整套配色（含背景、表面、描邊）平移色相，
 * 對比度與玻璃質感的既有調校因此全部保留，不需要為每個色板重調一遍。
 */
enum class AccentStyle(val hueShift: Float) {
    Wallpaper(0f),
    Green(0f),
    Blue(82f),
    Purple(132f),
    Amber(265f),
    Rose(207f);

    val usesWallpaper: Boolean get() = this == Wallpaper
}

/** 使用者的外觀選擇。 */
data class Appearance(
    val darkMode: DarkMode = DarkMode.System,
    val accent: AccentStyle = AccentStyle.Green
)

/**
 * 外觀偏好持久化。
 *
 * v1.13 之前是四擋的 `theme_mode`（含 Dynamic），這裡做一次性遷移：
 * Dynamic → 跟隨系統 + 跟隨桌布，其餘依原值映射，舊 key 讀完即刪。
 */
object AppearanceStore {
    private const val PREFS = "deviceinfo_ui"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_ACCENT = "accent_style"
    private const val LEGACY_KEY = "theme_mode"

    fun load(context: Context): Appearance {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateLegacy(prefs)
        val dark = prefs.getString(KEY_DARK, null)
            ?.let { runCatching { DarkMode.valueOf(it) }.getOrNull() }
            ?: DarkMode.System
        val accent = prefs.getString(KEY_ACCENT, null)
            ?.let { runCatching { AccentStyle.valueOf(it) }.getOrNull() }
            ?: AccentStyle.Green
        return Appearance(dark, accent)
    }

    fun save(context: Context, appearance: Appearance) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK, appearance.darkMode.name)
            .putString(KEY_ACCENT, appearance.accent.name)
            .apply()
    }

    private fun migrateLegacy(prefs: android.content.SharedPreferences) {
        if (!prefs.contains(LEGACY_KEY)) return
        val legacy = prefs.getString(LEGACY_KEY, null)
        val editor = prefs.edit().remove(LEGACY_KEY)
        when (legacy) {
            "Dynamic" -> editor
                .putString(KEY_DARK, DarkMode.System.name)
                .putString(KEY_ACCENT, AccentStyle.Wallpaper.name)
            "Dark" -> editor.putString(KEY_DARK, DarkMode.Dark.name)
            "Light" -> editor.putString(KEY_DARK, DarkMode.Light.name)
            else -> Unit
        }
        editor.apply()
    }
}

/**
 * 桌布取色的即時性。
 *
 * Material You 的 `dynamicXColorScheme(context)` 讀的是系統調色盤資源，
 * 換桌布後這些資源在同一個進程裡可能還是舊值（快取沒失效），
 * 所以應用端要自己拿到「桌布主色色相」，把算出來的配色再對齊一次。
 * 這樣即使系統資源是舊的，畫面顏色也會立刻跟著新桌布走。
 */
object WallpaperPalette {

    /** 桌布主色的色相（0–360）；取不到或近乎無彩色時回 null。 */
    fun primaryHue(context: Context): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return runCatching {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return null
            // Android 15 把 getPrimaryColor() 的回傳型別從 @ColorInt int 換成了
            // android.graphics.Color，直接呼叫會讓 minSdk 24 的 APK 在舊機上
            // NoSuchMethodError；反射拿值、兩種型別都吃，跨版本安全。
            val raw = colors.javaClass.getMethod("getPrimaryColor").invoke(colors)
            val argb = when (raw) {
                null -> return null
                is Int -> raw
                else -> raw.javaClass.getMethod("toArgb").invoke(raw) as? Int ?: return null
            }
            hueOf(argb)
        }.getOrNull()
    }

    private fun hueOf(argb: Int): Float? {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        // 灰白黑桌布沒有可用的色相，強行上色會把整個介面染成怪色
        return if (hsv[1] < 0.10f) null else hsv[0]
    }
}

/**
 * 桌布變更的重繪信號。
 *
 * 兩個觸發源：
 *  1. `ACTION_WALLPAPER_CHANGED` —— 換桌布當下（部分 ROM 不發，所以還需要第 2 條）；
 *  2. 回到前景時（懸浮窗 / 桌面小工具改完設定回來也會走這裡）。
 *
 * 收到信號時順手把 Resources 的 configuration 重刷一遍，
 * 讓系統調色盤有機會重新解析，能更新就更新，不能更新也有色相對齊兜底。
 */
@Composable
fun rememberWallpaperRevision(): Int {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                runCatching {
                    context.resources.updateConfiguration(
                        context.resources.configuration,
                        context.resources.displayMetrics
                    )
                }
                revision++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return revision
}

/**
 * 把整套配色沿色相環平移 [delta] 度。
 *
 * 只動「有彩色」的欄位：接近中性的文字色、以及 Warn / Danger / Info 這些
 * 有語義的顏色都不動 —— 錯誤紅就算桌布是藍色也該是紅的。
 */
internal fun DeviceColors.shiftedHue(delta: Float): DeviceColors {
    if (abs(delta) < 0.5f) return this
    return copy(
        Background = Background.hueRotated(delta),
        BackgroundTop = BackgroundTop.hueRotated(delta),
        BackgroundBottom = BackgroundBottom.hueRotated(delta),
        BackgroundElevated = BackgroundElevated.hueRotated(delta),
        Surface = Surface.hueRotated(delta),
        SurfaceHigh = SurfaceHigh.hueRotated(delta),
        SurfaceHigher = SurfaceHigher.hueRotated(delta),
        Outline = Outline.hueRotated(delta),
        Accent = Accent.hueRotated(delta),
        AccentDim = AccentDim.hueRotated(delta),
        AccentDeep = AccentDeep.hueRotated(delta)
    )
}

/** 單色沿色相環旋轉；近乎中性（飽和度 < 6%）的顏色原樣返回。 */
internal fun Color.hueRotated(delta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    if (hsv[1] < 0.06f) return this
    hsv[0] = (hsv[0] + delta + 360f) % 360f
    val alpha = android.graphics.Color.alpha(toArgb())
    return Color(android.graphics.Color.HSVToColor(alpha, hsv))
}

/**
 * 取色強調色的可讀性下限。
 *
 * Material You 在深色模式下給的 primary 是「淺而淡」的色調（實測飽和度只有 20%），
 * 放在本應是螢光強調色的位置會顯得發灰、和次要文字分不開。
 * 這裡保色相、只把飽和度抬到 55%，深色模式再保證亮度夠高，淺色模式則壓暗以保對比。
 */
internal fun Color.vividAccent(dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.55f)
    hsv[2] = if (dark) hsv[2].coerceAtLeast(0.80f) else hsv[2].coerceAtMost(0.46f)
    val alpha = android.graphics.Color.alpha(toArgb())
    return Color(android.graphics.Color.HSVToColor(alpha, hsv))
}

/** 兩個色相之間的最短距離（-180…180）。 */
internal fun hueDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}
