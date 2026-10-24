package com.deviceinfo.core

import android.content.Context

/**
 * 首次啟動引導的狀態。
 *
 * 只記「走完 / 跳過」這一件事：兩種都算看過了，之後直接進主頁。
 * 使用者想重看可以從更新日誌對話框重新打開（不影響這個旗標）。
 */
object OnboardingStore {

    private const val PREFS = "deviceinfo_ui"
    private const val KEY_DONE = "onboarding_done"

    fun isDone(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
