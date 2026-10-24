package com.deviceinfo.data.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.deviceinfo.data.model.AppAbiLibs
import com.deviceinfo.data.model.AppDetail
import java.io.File
import java.util.zip.ZipFile

/**
 * 應用架構詳情探測（LibChecker 風格）。
 *
 * ABI 判定：把 base APK 與所有 split APK 當成 zip 開啟，枚舉 `lib/<abi>/` 底下的
 * `.so` 條目，這是唯一不看 ROM 臉色的可靠做法（split 目錄名、`nativeLibraryDir`
 * 都只反映「目前安裝的那一份」）。各 APK 的讀取失敗只會讓該檔案的貢獻缺席。
 */
internal class AppDetailProbe(private val context: Context) {

    fun load(packageName: String): AppDetail? {
        val pm = context.packageManager
        return runCatching { probe(pm, packageName) }.getOrNull()
    }

    private fun probe(pm: PackageManager, packageName: String): AppDetail? {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            // requestedPermissions 只有帶這個旗標才會被填滿
            PackageManager.GET_PERMISSIONS
        val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
        val appInfo = pkg.applicationInfo ?: return null

        val apkPaths = buildList {
            appInfo.sourceDir?.let { add(it) }
            appInfo.splitPublicSourceDirs?.forEach { add(it) }
        }

        // zip 條目裡的 ABI 用連字號（armeabi-v7a），排序按 64 位元優先
        val libsByAbi = LinkedHashMap<String, MutableSet<String>>()
        for (path in apkPaths) {
            runCatching {
                ZipFile(File(path)).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val parts = entry.name.split('/')
                        if (parts.size == 3 && parts[0] == "lib" && parts[2].endsWith(".so")) {
                            libsByAbi.getOrPut(parts[1]) { LinkedHashSet() } +=
                                parts[2].removePrefix("lib").removeSuffix(".so")
                        }
                    }
                }
            }
        }

        // 64 位元 ABI 排在前面，未知 ABI 依名稱墊底
        val abis = libsByAbi.keys.sortedBy { abi ->
            ABI_ORDER.indexOf(abi).takeIf { it >= 0 } ?: ABI_ORDER.size
        }
        val nativeLibs = abis.map { abi ->
            AppAbiLibs(
                abi = abi,
                libs = libsByAbi[abi].orEmpty().toList().sorted()
            )
        }

        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).let { source ->
                    source.initiatingPackageName ?: source.installingPackageName
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()

        return AppDetail(
            packageName = packageName,
            label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: packageName,
            versionName = pkg.versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            },
            minSdk = appInfo.minSdkVersion,
            targetSdk = appInfo.targetSdkVersion,
            abis = abis,
            nativeLibs = nativeLibs,
            splitCount = appInfo.splitPublicSourceDirs?.count { it.isNotBlank() } ?: 0,
            activityCount = pkg.activities?.size,
            serviceCount = pkg.services?.size,
            receiverCount = pkg.receivers?.size,
            providerCount = pkg.providers?.size,
            permissionCount = pkg.requestedPermissions?.size,
            installer = installer,
            installedAtMillis = pkg.firstInstallTime,
            updatedAtMillis = pkg.lastUpdateTime
        )
    }

    companion object {
        private val ABI_ORDER = listOf(
            "arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi", "mips", "mips64"
        )
    }
}
