package com.deviceinfo.core

import com.deviceinfo.R

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 全應用統一的數值格式化，避免各處 "%.2f GB" 口徑不一致。 */
object Fmt {

    private val decimal = Locale.US

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) {
            size /= 1024.0
            unit++
        }
        return if (unit <= 1) {
            "%d %s".format(decimal, size.toLong(), units[unit])
        } else {
            "%.2f %s".format(decimal, size, units[unit])
        }
    }

    /** 精確到 MB 的容量，用於大容量展示（528 GB 總容量）。 */
    fun bytesShort(value: Long): String {
        if (value <= 0L) return "0 B"
        val gb = value.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return "%.0f GB".format(decimal, gb)
        val mb = value.toDouble() / (1024.0 * 1024.0)
        return "%.0f MB".format(decimal, mb)
    }

    fun mhz(value: Int): String = if (value <= 0) "—" else "$value MHz"

    fun ghz(valueMhz: Int): String = "%.2f GHz".format(decimal, valueMhz / 1000f)

    fun duration(millis: Long): String {
        if (millis <= 0L) return L10n.s(R.string.fmt_duration_zero)
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return buildString {
            if (days > 0) append(L10n.s(R.string.fmt_unit_day, days))
            if (hours > 0 || days > 0) append(L10n.s(R.string.fmt_unit_hour, hours))
            if (minutes > 0 || hours > 0 || days > 0) append(L10n.s(R.string.fmt_unit_minute, minutes))
            append(L10n.s(R.string.fmt_unit_second, seconds))
        }.trim()
    }

    fun durationShort(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> L10n.s(R.string.fmt_duration_d_h, days, hours)
            hours > 0 -> L10n.s(R.string.fmt_duration_h_m, hours, minutes)
            else -> L10n.s(R.string.fmt_duration_m, minutes)
        }
    }

    fun percent(ratio: Float): String = "%.0f%%".format(decimal, (ratio * 100f).coerceIn(0f, 100f))

    fun temperature(celsius: Float): String =
        if (!celsius.isFinite() || celsius <= 0f) "—" else "%.1f°C".format(decimal, celsius)

    /** 幀率：保留一位小數，避免測試過程中的即時值跳動太劇烈。 */
    fun fps(value: Float): String =
        if (!value.isFinite() || value <= 0f) "—" else "%.1f FPS".format(decimal, value)

    /**
     * 功率顯示。輸入單位固定為毫瓦，超過 1 W 換算成瓦，避免出現「226500.4 mW」這種讀數。
     */
    fun power(milliwatts: Float?): String {
        if (milliwatts == null || !milliwatts.isFinite() || milliwatts <= 0f) return "—"
        return if (milliwatts >= 1000f) {
            "%.2f W".format(decimal, milliwatts / 1000f)
        } else {
            "%.0f mW".format(decimal, milliwatts)
        }
    }

    /** 功率 + 充放電方向（入 = 充電淨輸入，出 = 放電）。方向未知時只顯示數值。 */
    fun power(milliwatts: Float?, plugged: Boolean?): String {
        val base = power(milliwatts)
        if (base == "—" || plugged == null) return base
        return "$base ${L10n.s(if (plugged) R.string.fmt_power_in else R.string.fmt_power_out)}"
    }

    fun dateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    fun date(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

    fun signed(value: Int, unit: String): String =
        if (value >= 0) "+$value $unit" else "$value $unit"

    fun orDash(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "—"
}
