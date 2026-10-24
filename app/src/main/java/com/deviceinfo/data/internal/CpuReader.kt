package com.deviceinfo.data.internal

import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.CpuClusterSpec
import com.deviceinfo.data.model.CpuCoreSpec

/**
 * CPU 頻率與負載採樣。
 *
 * - 頻率：讀 cpufreq 節點（國內 ROM 大多允許讀取）
 * - 負載：/proc/stat 兩次差分；Android 高版本可能拒絕訪問，此時用「頻率佔峰值比例」近似
 */
internal class CpuReader {

    private val coreCount: Int = Runtime.getRuntime().availableProcessors()

    private var lastTotalTicks = -1L
    private var lastIdleTicks = -1L

    fun readCores(): List<CpuCoreSpec> {
        val specs = ArrayList<CpuCoreSpec>(coreCount)
        for (index in 0 until coreCount) {
            val max = SysFs.firstInt(
                cpuPath(index, "cpufreq/cpuinfo_max_freq"),
                cpuPath(index, "cpufreq/scaling_max_freq")
            ) ?: 0
            val min = SysFs.firstInt(
                cpuPath(index, "cpufreq/cpuinfo_min_freq"),
                cpuPath(index, "cpufreq/scaling_min_freq")
            ) ?: 0
            specs += CpuCoreSpec(
                index = index,
                maxFreqMhz = max / 1000,
                minFreqMhz = min / 1000,
                clusterIndex = 0
            )
        }

        // 按峰值頻率分組得到集群，頻率高的排前面（超大核 → 大核 → 小核）
        val distinctPeaks = specs.map { it.maxFreqMhz }.filter { it > 0 }.distinct().sortedDescending()
        return specs.map { core ->
            val rank = distinctPeaks.indexOf(core.maxFreqMhz).let { if (it < 0) 0 else it }
            core.copy(clusterIndex = rank)
        }
    }

    fun readClusters(cores: List<CpuCoreSpec>): List<CpuClusterSpec> {
        if (cores.isEmpty()) return emptyList()
        val grouped = cores.groupBy { it.clusterIndex }
        val orderedPeaks = cores.map { it.clusterIndex }.distinct().sorted()
        return orderedPeaks.mapIndexed { order, clusterKey ->
            val members = grouped[clusterKey].orEmpty().sortedBy { it.index }
            val peak = members.maxOfOrNull { it.maxFreqMhz } ?: 0
            val floor = members.map { it.minFreqMhz }.filter { it > 0 }.minOrNull() ?: 0
            CpuClusterSpec(
                index = order,
                label = clusterLabel(order, orderedPeaks.size, members.size, peak),
                coreIndexes = members.map { it.index },
                maxFreqMhz = peak,
                minFreqMhz = floor
            )
        }
    }

    private fun clusterLabel(order: Int, count: Int, coreSize: Int, peakMhz: Int): String {
        val kind = when {
            count <= 1 -> L10n.s(R.string.prb_cluster_unified)
            order == 0 -> L10n.s(R.string.prb_cluster_prime)
            order == count - 1 -> L10n.s(R.string.prb_cluster_efficiency)
            else -> L10n.s(R.string.prb_cluster_performance)
        }
        val freq = if (peakMhz > 0) " · ${peakMhz} MHz" else ""
        return L10n.s(R.string.prb_cluster_label, kind, coreSize, freq)
    }

    fun readCurrentFreqs(): List<Int> {
        val freqs = ArrayList<Int>(coreCount)
        for (index in 0 until coreCount) {
            val khz = SysFs.firstInt(
                cpuPath(index, "cpufreq/scaling_cur_freq"),
                cpuPath(index, "cpufreq/cpuinfo_cur_freq")
            ) ?: 0
            freqs += khz / 1000
        }
        return freqs
    }

    /** 0f..1f。讀不到 /proc/stat 時返回 -1f，由調用方決定用頻率近似。 */
    fun sampleLoad(): Float {
        val line = SysFs.readFirstLine("/proc/stat") ?: return -1f
        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 5 || !parts.first().startsWith("cpu")) return -1f

        var total = 0L
        var idle = 0L
        for (i in 1 until parts.size) {
            val value = parts[i].toLongOrNull() ?: continue
            total += value
            // idle + iowait 視為空閒
            if (i == 4 || i == 5) idle += value
        }
        if (total <= 0L) return -1f

        val previousTotal = lastTotalTicks
        val previousIdle = lastIdleTicks
        lastTotalTicks = total
        lastIdleTicks = idle

        if (previousTotal < 0L || total <= previousTotal) return -1f

        val deltaTotal = (total - previousTotal).toFloat()
        val deltaIdle = (idle - previousIdle).toFloat()
        if (deltaTotal <= 0f) return -1f
        return ((deltaTotal - deltaIdle) / deltaTotal).coerceIn(0f, 1f)
    }

    private fun cpuPath(index: Int, suffix: String): String =
        "/sys/devices/system/cpu/cpu$index/$suffix"
}
