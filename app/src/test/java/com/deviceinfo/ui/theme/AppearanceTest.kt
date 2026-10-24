package com.deviceinfo.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 外觀層的純邏輯測試：色相旋轉、色相差、強調色飽和度下限、偏好持久化與舊值遷移。
 *
 * 這一層不需要真機，Robolectric 就夠；換桌布那條鏈路（WallpaperPalette）依賴系統服務，
 * 留給真機驗證。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun hueOf(argb: Int): Float {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        return hsv[0]
    }

    private fun satOf(argb: Int): Float {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        return hsv[1]
    }

    @Test
    fun hueRotated_keepsColorWhenDeltaIsZero() {
        val accent = DarkColors.Accent
        assertEquals(accent, accent.hueRotated(0f))
    }

    @Test
    fun hueRotated_movesHueByRequestedAmount() {
        val base = DarkColors.Accent
        val shifted = base.hueRotated(AccentStyle.Blue.hueShift)
        val delta = hueDelta(hueOf(base.toArgb()), hueOf(shifted.toArgb()))
        assertTrue("實際偏移 ${delta}", abs(delta - AccentStyle.Blue.hueShift) < 2f)
    }

    @Test
    fun hueRotated_keepsSaturationAndValue() {
        val base = DarkColors.Accent
        val shifted = base.hueRotated(AccentStyle.Purple.hueShift)
        assertEquals(satOf(base.toArgb()), satOf(shifted.toArgb()), 0.02f)
    }

    @Test
    fun hueRotated_leavesNeutralColorsAlone() {
        // 近中性的文字色不該被染色，否則藍色主題下正文會泛藍
        val text = DarkColors.TextPrimary
        assertEquals(text, text.hueRotated(120f))
    }

    @Test
    fun shiftedHue_keepsSemanticColors() {
        val rotated = DarkColors.shiftedHue(AccentStyle.Rose.hueShift)
        assertEquals(DarkColors.Danger, rotated.Danger)
        assertEquals(DarkColors.Warn, rotated.Warn)
        assertEquals(DarkColors.Info, rotated.Info)
        assertNotEquals(DarkColors.Accent, rotated.Accent)
    }

    @Test
    fun hueDelta_takesShortestPathAcrossZero() {
        assertEquals(20f, hueDelta(350f, 10f), 0.01f)
        assertEquals(-20f, hueDelta(10f, 350f), 0.01f)
        assertEquals(180f, abs(hueDelta(0f, 180f)), 0.01f)
    }

    @Test
    fun vividAccent_raisesLowSaturationPrimary() {
        // 模擬 Material You 在深色模式給的「淺而淡」primary
        val pale = androidx.compose.ui.graphics.Color(0xFFBDCDEB)
        val vivid = pale.vividAccent(dark = true)
        // 8 位元整數轉換會有 1–2‰ 的捨入誤差，門檻放寬到 0.54
        assertTrue("飽和度 ${satOf(vivid.toArgb())}", satOf(vivid.toArgb()) >= 0.54f)
        // 色相不能被改掉，否則就不再跟桌布同調
        assertEquals(hueOf(pale.toArgb()), hueOf(vivid.toArgb()), 0.5f)
    }

    @Test
    fun vividAccent_darkensAccentInLightTheme() {
        val pale = androidx.compose.ui.graphics.Color(0xFFD8E2F8)
        val vivid = pale.vividAccent(dark = false)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(vivid.toArgb(), hsv)
        assertTrue("亮度 ${hsv[2]}", hsv[2] <= 0.46f)
    }

    @Test
    fun appearanceStore_roundTrips() {
        AppearanceStore.save(context, Appearance(DarkMode.Dark, AccentStyle.Blue))
        assertEquals(Appearance(DarkMode.Dark, AccentStyle.Blue), AppearanceStore.load(context))
    }

    @Test
    fun appearanceStore_migratesLegacyDynamicMode() {
        // v1.13 的四擋主題把 Dynamic 當模式；搬過來要變成「跟隨系統 + 跟隨桌布」
        context.getSharedPreferences("deviceinfo_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("theme_mode", "Dynamic")
            .commit()

        val appearance = AppearanceStore.load(context)

        assertEquals(DarkMode.System, appearance.darkMode)
        assertEquals(AccentStyle.Wallpaper, appearance.accent)
    }

    @Test
    fun appearanceStore_migratesLegacyDarkMode() {
        context.getSharedPreferences("deviceinfo_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("theme_mode", "Dark")
            .commit()

        assertEquals(DarkMode.Dark, AppearanceStore.load(context).darkMode)
    }
}
