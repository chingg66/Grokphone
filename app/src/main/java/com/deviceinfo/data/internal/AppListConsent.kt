package com.deviceinfo.data.internal

import android.content.Context

/**
 * 用戶在應用內對「讀取應用程式列表」的明示同意。
 *
 * 系統層沒有對應的執行期權限可彈窗，但列表本身屬於隱私資料，
 * 所以先在應用內問一次：同意後才掃描完整清單，之後不再重複打擾。
 * 這裡只存「用戶已同意」這一件事，不承擔權限是否真的生效的判斷。
 */
object AppListConsent {

    private const val PREFS = "app_list_access"
    private const val KEY_GRANTED = "consent_granted"

    fun isGranted(context: Context): Boolean = prefs(context).getBoolean(KEY_GRANTED, false)

    fun setGranted(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRANTED, granted).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
