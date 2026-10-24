package com.deviceinfo.core

import android.content.Context

/** 溫度單位。 */
enum class TempUnit { Celsius, Fahrenheit }

/**
 * 應用設定（外觀以外）：溫度單位、電池校正。
 *
 * - 雙電芯：串聯雙電芯機型的 sysfs / BatteryManager 讀數是兩顆電芯之和，
 *   開啟後容量類數值（設計容量、滿充容量、電量計）顯示為 ÷2。
 * - 功耗校正：部分機型 CURRENT_NOW 與真實功耗有固定偏差，
 *   用倍率校正顯示值（0.5x–2.0x），預設 1.0。
 */
data class AppSettings(
    val tempUnit: TempUnit = TempUnit.Celsius,
    val dualCell: Boolean = false,
    val powerFactor: Float = 1.0f
) {
    /** 容量校正（µAh → µAh）。 */
    fun capacityUah(raw: Long): Long = if (dualCell) raw / 2 else raw

    /** 功耗校正（mW → mW）。 */
    fun powerMw(raw: Float): Float = raw * powerFactor

    /** 溫度格式化（°C 輸入 → 依設定輸出 °C / °F）。 */
    fun temperature(celsius: Float): String {
        if (!celsius.isFinite() || celsius <= 0f) return "—"
        return when (tempUnit) {
            TempUnit.Celsius -> "%.1f°C".format(java.util.Locale.US, celsius)
            TempUnit.Fahrenheit -> "%.1f°F".format(java.util.Locale.US, celsius * 9f / 5f + 32f)
        }
    }
}

object SettingsStore {
    private const val PREFS = "deviceinfo_ui"
    private const val KEY_TEMP_UNIT = "temp_unit"
    private const val KEY_DUAL_CELL = "dual_cell"
    private const val KEY_POWER_FACTOR = "power_factor"

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            tempUnit = prefs.getString(KEY_TEMP_UNIT, null)
                ?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
                ?: TempUnit.Celsius,
            dualCell = prefs.getBoolean(KEY_DUAL_CELL, false),
            powerFactor = prefs.getFloat(KEY_POWER_FACTOR, 1.0f).coerceIn(0.5f, 2.0f)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEMP_UNIT, settings.tempUnit.name)
            .putBoolean(KEY_DUAL_CELL, settings.dualCell)
            .putFloat(KEY_POWER_FACTOR, settings.powerFactor)
            .apply()
    }
}
