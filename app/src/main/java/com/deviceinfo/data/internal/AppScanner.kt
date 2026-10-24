package com.deviceinfo.data.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.deviceinfo.data.model.AppEntry
import com.deviceinfo.data.model.AppsSummary
import java.io.File

/**
 * 判定「列表被過濾」的套件數下限。
 * 正常手機的系統 + 使用者套件動輒上百，這裡取一個非常保守的門檻，
 * 只為了抓出「只回傳自己 1 個套件」這種明顯被 ROM 攔截的情況。
 */
private const val MIN_REASONABLE_PACKAGES = 10

/**
 * 已安裝應用掃描。
 *
 * Android 11+ 依賴 manifest 中的 QUERY_ALL_PACKAGES 才能拿到完整列表；
 * 若被系統拒絕，只會拿到「可見」的少量套件。這種情況仍照常返回結果，
 * 但把 [AppsSummary.fullAccess] 標記為 false，由 UI 顯示權限卡片引導用戶處理。
 */
internal class AppScanner(private val context: Context) {

    fun scan(): AppsSummary {
        val packageManager = context.packageManager
        val queryAllGranted = AppListAccess.isGranted(context)
        val packages = try {
            packageManager.getInstalledPackages(0)
        } catch (_: Throwable) {
            return AppsSummary(
                total = 0,
                systemCount = 0,
                userCount = 0,
                apps = emptyList(),
                queryAllGranted = queryAllGranted,
                fullAccess = false
            )
        }

        val entries = ArrayList<AppEntry>(packages.size)
        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val size = apkSize(appInfo)
            entries += AppEntry(
                label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName,
                packageName = info.packageName,
                versionName = info.versionName ?: "—",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                },
                sizeBytes = size,
                isSystem = isSystem,
                targetSdk = appInfo.targetSdkVersion,
                installedAtMillis = info.firstInstallTime
            )
        }

        val sorted = entries.sortedWith(
            compareByDescending<AppEntry> { it.sizeBytes }.thenBy { it.label.lowercase() }
        )
        val systemCount = entries.count { it.isSystem }
        return AppsSummary(
            total = entries.size,
            systemCount = systemCount,
            userCount = entries.size - systemCount,
            apps = sorted,
            queryAllGranted = queryAllGranted,
            // 任何一台真實裝置的套件數都遠高於這個下限；低於它說明列表被 ROM 過濾了
            fullAccess = queryAllGranted && entries.size >= MIN_REASONABLE_PACKAGES
        )
    }

    private fun apkSize(appInfo: ApplicationInfo): Long {
        var total = 0L
        try {
            appInfo.sourceDir?.let { total += File(it).length() }
            appInfo.splitPublicSourceDirs?.forEach { total += File(it).length() }
        } catch (_: Throwable) {
            // 單個應用讀取失敗忽略
        }
        return total
    }
}
