package com.deviceinfo.core

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 應用內語言切換。
 *
 * 兩條路徑：
 *  - Android 13+：交給系統的 per-app language（[LocaleManager]），
 *    好處是系統設定裡的「應用程式語言」會同步，切換由框架負責重建 Activity；
 *  - Android 12 及以下：沒有這個 API，只能在 `attachBaseContext` 手動換 Configuration。
 *
 * 兩種路徑都把選擇存在同一份 SharedPreferences，UI 讀的是這裡的結果。
 */
object LanguageStore {

    /** 空字串代表「跟隨系統」。 */
    const val SYSTEM = ""

    private const val PREFS = "deviceinfo_ui"
    private const val KEY = "app_language"

    /** 可選語言：標籤 → 原生語言名（語言名本身不翻譯，方便使用者找到自己的語言）。 */
    val options: List<Pair<String, String>> = listOf(
        SYSTEM to "", // 名稱由 UI 依當前語言取資源
        "zh-TW" to "繁體中文",
        "zh-CN" to "简体中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "ru" to "Русский",
        "pt" to "Português"
    )

    /** 目前生效的語言標籤；跟隨系統時回 [SYSTEM]。 */
    fun current(context: Context): String =
        prefs(context).getString(KEY, SYSTEM) ?: SYSTEM

    /**
     * 切換語言。一律回傳 true：呼叫端要自己 `recreate()`。
     *
     * 原本 API 33+ 走 `LocaleManager.setApplicationLocales`，但實測沒有
     * `android:localeConfig` 宣告時它只寫入系統狀態、不會重建 Activity，
     * 畫面字串完全沒變（只有選單上的選中項變了）。與其兩套機制互相牽制，
     * 不如全部由應用自己控制：寫偏好 → 換 Context 的 locale → 重建 Activity。
     */
    fun set(context: Context, tag: String): Boolean {
        prefs(context).edit().putString(KEY, tag).apply()
        // 若舊版本曾經寫過系統 per-app locale，這裡清掉，避免它蓋過我們的覆寫
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val manager = context.getSystemService(LocaleManager::class.java)
                manager.applicationLocales = LocaleList.getEmptyLocaleList()
            }
        }
        L10n.init(context.applicationContext)
        return true
    }

    /**
     * 依選擇把 Context 換成對應語言。
     * API 33+ 直接交給系統，避免框架與本地覆寫兩套同時生效打架。
     */
    fun wrap(base: Context): Context {
        val tag = prefs(base).getString(KEY, SYSTEM) ?: SYSTEM
        if (tag == SYSTEM) return base
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    // 注意：attachBaseContext 階段 applicationContext 還是 null，
    // 這裡必須直接用傳進來的 Context，不能先取 applicationContext。
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
