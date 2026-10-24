package com.deviceinfo.data.internal

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

/**
 * 桌面小工具專用的輕量資料讀取。
 *
 * 不走 [DeviceCollector]（它會掃應用列表、相機、感測器，太重），
 * 只取小工具需要的三項：電池、記憶體、儲存。
 */
internal class WidgetDataLoader(private val context: Context) {

    data class WidgetData(
        val batteryPct: Int,
        val temperatureC: Float,
        val isCharging: Boolean,
        val ramUsedBytes: Long,
        val ramTotalBytes: Long,
        val storageUsedBytes: Long,
        val storageTotalBytes: Long
    ) {
        val ramRatio: Float
            get() = if (ramTotalBytes <= 0) 0f
            else (ramUsedBytes.toFloat() / ramTotalBytes).coerceIn(0f, 1f)

        val storageRatio: Float
            get() = if (storageTotalBytes <= 0) 0f
            else (storageUsedBytes.toFloat() / storageTotalBytes).coerceIn(0f, 1f)
    }

    private val batteryProbe = BatteryProbe(context)
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()

    fun read(): WidgetData {
        val battery = runCatching { batteryProbe.read() }.getOrNull()
        val ram = runCatching {
            activityManager?.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem to (memoryInfo.totalMem - memoryInfo.availMem)
        }.getOrDefault(0L to 0L)

        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total to (total - free)
        }.getOrDefault(0L to 0L)

        return WidgetData(
            batteryPct = battery?.levelPercent ?: 0,
            temperatureC = battery?.temperatureC ?: Float.NaN,
            isCharging = battery?.isPlugged == true,
            ramUsedBytes = ram.second,
            ramTotalBytes = ram.first,
            storageUsedBytes = storage.second,
            storageTotalBytes = storage.first
        )
    }
}
