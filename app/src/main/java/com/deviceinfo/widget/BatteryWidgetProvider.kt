package com.deviceinfo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deviceinfo.MainActivity
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.core.L10n
import com.deviceinfo.data.internal.WidgetDataLoader

/**
 * 電池 / 記憶體 / 儲存桌面小工具。
 *
 * 更新策略：
 * - 系統 `updatePeriodMillis` 下限 30 分鐘，這是平台硬限制，改不了。
 * - 打開應用時由 MainActivity.onResume 觸發 [updateAll] 即時刷新。
 * - 點擊小工具打開應用。
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BatteryWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val data = WidgetDataLoader(context).read()
            val settings = com.deviceinfo.core.SettingsStore.load(context)

            val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
                val temp = if (data.temperatureC.isNaN()) "" else " · ${settings.temperature(data.temperatureC)}"
                val chargeState = if (data.isCharging) L10n.s(R.string.wdg_charging) else L10n.s(R.string.wdg_discharging)
                setTextViewText(R.id.widget_battery_pct, "${data.batteryPct}%")
                setTextViewText(R.id.widget_battery_detail, "$chargeState$temp")

                setProgressBar(R.id.widget_ram_bar, 100, (data.ramRatio * 100).toInt(), false)
                setTextViewText(
                    R.id.widget_ram_value,
                    "${Fmt.bytesShort(data.ramUsedBytes)} / ${Fmt.bytesShort(data.ramTotalBytes)}"
                )

                setProgressBar(
                    R.id.widget_storage_bar,
                    100,
                    (data.storageRatio * 100).toInt(),
                    false
                )
                setTextViewText(
                    R.id.widget_storage_value,
                    "${Fmt.bytesShort(data.storageUsedBytes)} / ${Fmt.bytesShort(data.storageTotalBytes)}"
                )
            }

            // RemoteViews 拿不到根佈局 id，點擊綁在每個可見子項上，效果等同整卡可點
            val launch = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            listOf(
                R.id.widget_battery_pct,
                R.id.widget_battery_detail,
                R.id.widget_ram_bar,
                R.id.widget_ram_value,
                R.id.widget_storage_bar,
                R.id.widget_storage_value
            ).forEach { id -> views.setOnClickPendingIntent(id, launch) }

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
