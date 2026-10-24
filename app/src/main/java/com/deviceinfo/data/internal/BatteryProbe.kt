package com.deviceinfo.data.internal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.BatteryLive

/**
 * 電池實時狀態。資料源：ACTION_BATTERY_CHANGED 粘性廣播 + BatteryManager。
 *
 * 部分 ROM 不實現 BATTERY_PROPERTY_CURRENT_NOW，會返回 Integer.MIN_VALUE，
 * 這裡統一折算成 null，UI 顯示 「—」。
 */
internal class BatteryProbe(private val context: Context) {

    fun read(): BatteryLive {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val sticky = try {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }

        val rawLevel = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = when {
            rawLevel >= 0 && scale > 0 -> (rawLevel * 100f / scale).toInt()
            else -> manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        }

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperatureTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?: SysFs.firstText("/sys/class/power_supply/battery/technology")
            ?: "—"

        val currentUa = try {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (_: Throwable) {
            null
        }?.takeIf { it != Int.MIN_VALUE && it != 0 }

        val chargeCounter = try {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (_: Throwable) {
            null
        }?.takeIf { it > 0 }

        val designCapacity = SysFs.firstInt(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )?.toLong()?.takeIf { it > 0 }

        val chargeFull = SysFs.firstInt(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full"
        )?.toLong()?.takeIf { it > 0 }

        return BatteryLive(
            levelPercent = levelPercent,
            statusLabel = statusLabel(status),
            temperatureC = if (temperatureTenths > 0) temperatureTenths / 10f else Float.NaN,
            voltageMv = voltageMv,
            currentUa = currentUa ?: 0,
            healthLabel = healthLabel(health),
            pluggedLabel = pluggedLabel(plugged),
            isPlugged = plugged != 0,
            technology = technology,
            designCapacityUah = designCapacity,
            chargeCounterUah = chargeCounter?.toLong(),
            chargeFullUah = chargeFull
        )
    }

    private fun statusLabel(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> L10n.s(R.string.prb_battery_charging)
        BatteryManager.BATTERY_STATUS_DISCHARGING -> L10n.s(R.string.prb_battery_discharging)
        BatteryManager.BATTERY_STATUS_FULL -> L10n.s(R.string.prb_battery_full)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> L10n.s(R.string.prb_battery_not_charging)
        else -> L10n.s(R.string.prb_unknown)
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> L10n.s(R.string.prb_health_good)
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> L10n.s(R.string.prb_health_overheat)
        BatteryManager.BATTERY_HEALTH_DEAD -> L10n.s(R.string.prb_health_dead)
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> L10n.s(R.string.prb_health_over_voltage)
        BatteryManager.BATTERY_HEALTH_COLD -> L10n.s(R.string.prb_health_cold)
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> L10n.s(R.string.prb_health_failure)
        else -> L10n.s(R.string.prb_unknown)
    }

    private fun pluggedLabel(plugged: Int): String = when {
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> L10n.s(R.string.prb_battery_ac)
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> L10n.s(R.string.prb_battery_wireless)
        else -> L10n.s(R.string.prb_battery_not_plugged)
    }
}
