package com.deviceinfo.data.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 應用列表存取能力。
 *
 * Android 11（API 30）起，第三方應用預設只能看到「可見」的套件；
 * 要讀取完整已安裝清單，必須在 manifest 宣告 `QUERY_ALL_PACKAGES`。
 * 這是安裝期授予的一般權限（無執行期彈窗），因此這裡做兩件事：
 *  1. 查詢權限是否真的生效，供 UI 顯示「已取得 / 受限」；
 *  2. 權限受限時把用戶導到系統的應用程式設定頁，讓他自己確認或放行。
 */
object AppListAccess {

    const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

    /** 是否已取得讀取完整應用清單的能力（Android 10 及以下恆為 true）。 */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching {
            context.packageManager.checkPermission(
                PERMISSION_QUERY_ALL_PACKAGES,
                context.packageName
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * 把用戶帶到「可以放行應用列表」的頁面。
     *
     * 優先嘗試 MIUI / HyperOS 的權限編輯頁——這些 ROM 的開關叫「讀取應用程式清單」，
     * 藏在 權限管理 → 其他權限 裡，只跳應用程式詳情頁用戶找不到。
     * 其餘 ROM 退到應用程式詳情頁 / 應用程式列表頁 / 系統設定首頁。
     */
    fun openSystemSettings(context: Context): Boolean {
        val candidates = buildList {
            add(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                ).putExtra("extra_pkgname", context.packageName)
            )
            add(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                    )
                ).putExtra("extra_pkgname", context.packageName)
            )
            add(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
            add(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
