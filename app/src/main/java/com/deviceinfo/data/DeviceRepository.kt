package com.deviceinfo.data

import android.app.ActivityManager
import android.content.Context
import com.deviceinfo.data.internal.AppDetailProbe
import com.deviceinfo.data.internal.AppScanner
import com.deviceinfo.data.internal.BatteryProbe
import com.deviceinfo.data.internal.CpuReader
import com.deviceinfo.data.internal.DeviceCollector
import com.deviceinfo.data.internal.GpuBenchmarkEngine
import com.deviceinfo.data.internal.SensorProbe
import com.deviceinfo.data.model.AppDetail
import com.deviceinfo.data.model.AppsSummary
import com.deviceinfo.data.model.BenchmarkResult
import com.deviceinfo.data.model.BenchmarkState
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.GpuBenchmarkResult
import com.deviceinfo.data.model.GpuBenchmarkState
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.data.model.SensorReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 單一資料入口。UI 永遠只依賴這裡，不直接碰 Build / /sys / PackageManager。
 */
class DeviceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val collector = DeviceCollector(appContext)
    private val appScanner = AppScanner(appContext)
    private val appDetailProbe = AppDetailProbe(appContext)
    private val cpuReader = CpuReader()
    private val batteryProbe = BatteryProbe(appContext)
    private val sensorProbe = SensorProbe(appContext)
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private val memoryInfo = ActivityManager.MemoryInfo()

    suspend fun loadSnapshot(): DeviceSnapshot = withContext(Dispatchers.IO) {
        collector.collect()
    }

    /**
     * 只重掃應用列表。授權、重新掃描等操作不該重跑整包採集
     * （感測器、相機、網路都要幾百毫秒），所以單獨開一個入口。
     */
    suspend fun loadApps(): AppsSummary = withContext(Dispatchers.IO) {
        appScanner.scan()
    }

    /** 單一應用的架構詳情（ABI、原生庫、元件等），讀取失敗返回 null。 */
    suspend fun loadAppDetail(packageName: String): AppDetail? = withContext(Dispatchers.IO) {
        appDetailProbe.load(packageName)
    }

    /**
     * 1 秒一拍：CPU 頻率 / 負載 / 電池 / 記憶體。
     * 負載優先用 /proc/stat，讀不到時退化為「頻率佔峰值比例」——這在國產 ROM 上很常見。
     */
    fun observeLive(maxFreqMhz: IntArray, intervalMs: Long = 1000L): Flow<LiveSnapshot> = flow {
        val history = ArrayDeque<Float>(HISTORY_SIZE + 1)
        while (currentCoroutineContext().isActive) {
            val freqs = cpuReader.readCurrentFreqs()
            val statLoad = cpuReader.sampleLoad()
            val load = if (statLoad >= 0f) statLoad else frequencyRatio(freqs, maxFreqMhz)

            history.addLast(load)
            while (history.size > HISTORY_SIZE) history.removeFirst()

            val ramAvailable = readAvailableRam()

            emit(
                LiveSnapshot(
                    coreFreqsMhz = freqs,
                    loadRatio = load,
                    loadHistory = history.toList(),
                    battery = batteryProbe.read(),
                    ramUsedRatio = if (totalRamBytes > 0L) {
                        ((totalRamBytes - ramAvailable).toFloat() / totalRamBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    ramAvailableBytes = ramAvailable
                )
            )
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.Default)

    private var totalRamBytes: Long = 0L

    /** 記錄總記憶體，供負載比例計算；由 ViewModel 在拿到快照後調用一次。 */
    fun cacheTotalRam(bytes: Long) {
        totalRamBytes = bytes
    }

    private fun readAvailableRam(): Long = try {
        activityManager?.getMemoryInfo(memoryInfo)
        memoryInfo.availMem
    } catch (_: Throwable) {
        0L
    }

    fun observeSensor(type: Int): Flow<SensorReading> = sensorProbe.observe(type)

    fun sensorUnit(type: Int): String = sensorProbe.unitOf(type)

    /**
     * 後台 CPU 壓力測試。啟動與核心數相同的 worker 線程做密集計算，
     * 每秒採樣負載、頻率、電池、溫度，並 emit [BenchmarkState]。
     */
    fun runBenchmark(durationMs: Long, maxFreqMhz: IntArray): Flow<BenchmarkState> = flow {
        require(durationMs > 0)
        val stopSignal = java.util.concurrent.atomic.AtomicBoolean(false)
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val startBattery = batteryProbe.read()
        val samples = ArrayList<BenchmarkSample>()

        val workers = (0 until coreCount).map {
            Thread {
                var i = 0L
                while (!stopSignal.get()) {
                    var acc = 1.0
                    repeat(8_000) { k ->
                        val v = ((i + k) % 100_000 + 1).toDouble()
                        acc += kotlin.math.sqrt(v) * kotlin.math.sin(v * 0.01)
                    }
                    i += 8_000
                }
            }.apply { isDaemon = true; start() }
        }

        val startTime = android.os.SystemClock.elapsedRealtime()
        var previousProgress: BenchmarkState.InProgress? = null
        try {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
                val freqs = cpuReader.readCurrentFreqs()
                val statLoad = cpuReader.sampleLoad()
                val load = if (statLoad >= 0f) statLoad else frequencyRatio(freqs, maxFreqMhz)
                val battery = batteryProbe.read()
                samples += BenchmarkSample(elapsed, load, freqs, battery)

                val avgLoad = samples.map { it.load }.average().toFloat()
                val avgFreq = samples.flatMap { it.freqs }.average().toFloat()
                val peakTemp = samples.mapNotNull { it.battery?.temperatureC }.maxOrNull() ?: 0f
                val currentBatteryPct = battery?.levelPercent ?: startBattery?.levelPercent ?: 0
                val score = computeBenchmarkScore(avgFreq, avgLoad, coreCount)

                val progress = BenchmarkState.InProgress(
                    elapsedMs = elapsed.coerceAtMost(durationMs),
                    targetMs = durationMs,
                    avgLoad = avgLoad,
                    avgFreqMhz = avgFreq,
                    peakTempC = peakTemp,
                    currentBatteryPct = currentBatteryPct,
                    score = score
                )
                previousProgress = progress
                emit(progress)

                if (elapsed >= durationMs) break
            }
        } finally {
            stopSignal.set(true)
            workers.forEach { it.join(300) }
        }

        val endBattery = batteryProbe.read()
        val validFreqs = samples.flatMap { it.freqs }.filter { it > 0 }
        val avgLoadFinal = samples.map { it.load }.average().toFloat()
        val avgFreqFinal = validFreqs.average().toFloat()
        val result = BenchmarkResult(
            targetMs = durationMs,
            sampleCount = samples.size,
            avgLoad = avgLoadFinal,
            peakLoad = samples.maxOfOrNull { it.load } ?: 0f,
            avgFreqMhz = avgFreqFinal,
            peakFreqMhz = validFreqs.maxOrNull() ?: 0,
            startBatteryPct = startBattery?.levelPercent ?: 0,
            endBatteryPct = endBattery?.levelPercent ?: 0,
            startTempC = startBattery?.temperatureC ?: 0f,
            endTempC = endBattery?.temperatureC ?: 0f,
            avgPowerMw = computeAvgPowerMw(samples),
            powerPlugged = powerPluggedOf(samples),
            score = computeBenchmarkScore(avgFreqFinal, avgLoadFinal, coreCount)
        )
        emit(BenchmarkState.Finished(result))
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    /**
     * 離屏 OpenGL ES 渲染測試。分數 = 平均 FPS × 1000，
     * 以實際完成的幀數計算，不受螢幕 60 / 120Hz 上限影響。
     */
    fun runGpuBenchmark(durationMs: Long, gpuName: String): Flow<GpuBenchmarkState> = channelFlow {
        require(durationMs > 0)
        val startBattery = batteryProbe.read()
        val startTemp = startBattery?.temperatureC ?: 0f
        val engine = GpuBenchmarkEngine()
        send(
            GpuBenchmarkState.InProgress(
                elapsedMs = 0L,
                targetMs = durationMs,
                currentFps = 0f,
                avgFps = 0f,
                frameCount = 0L,
                score = 0,
                gpuName = gpuName
            )
        )
        val stats = engine.run(durationMs) { progress ->
            if (!isActive) return@run false
            trySend(
                GpuBenchmarkState.InProgress(
                    elapsedMs = progress.elapsedMs,
                    targetMs = durationMs,
                    currentFps = progress.currentFps,
                    avgFps = progress.avgFps,
                    frameCount = progress.frameCount,
                    score = GpuBenchmarkEngine.scoreFor(progress.avgFps),
                    gpuName = gpuName
                )
            )
            true
        }
        val endBattery = batteryProbe.read()
        if (isActive) {
            send(
                GpuBenchmarkState.Finished(
                    GpuBenchmarkResult(
                        targetMs = durationMs,
                        frameCount = stats.frameCount,
                        avgFps = stats.avgFps,
                        peakFps = stats.peakFps,
                        minFps = stats.minFps,
                        score = GpuBenchmarkEngine.scoreFor(stats.avgFps),
                        gpuName = stats.renderer,
                        startTempC = startTemp,
                        endTempC = endBattery?.temperatureC ?: startTemp
                    )
                )
            )
        }
    }.flowOn(Dispatchers.Default)

    /** 簡化的 Geekbench 風格跑分：頻率 × 負載 × 核心數，縮放後落在數千分級。 */
    private fun computeBenchmarkScore(avgFreqMhz: Float, avgLoad: Float, coreCount: Int): Int {
        return ((avgFreqMhz * avgLoad.coerceIn(0f, 1f) * coreCount) / 10f).toInt().coerceAtLeast(0)
    }

    /**
     * 平均功率（毫瓦）。
     *
     * 兩個曾經踩過的地雷：
     *  1. 單位：mV × µA 得到的是「毫伏·微安」，換成毫瓦要乘 1e-6，不是 1e-3，
     *     否則會出現 -226500 mW 這種荒唐值；
     *  2. 符號：`BATTERY_PROPERTY_CURRENT_NOW` 的正負各廠商不一致（小米負值代表充電），
     *     所以這裡一律取絕對值，方向另外用 `isPlugged` 標示。
     */
    private fun computeAvgPowerMw(samples: List<BenchmarkSample>): Float? {
        val powers = samples.mapNotNull { sample ->
            val battery = sample.battery ?: return@mapNotNull null
            if (battery.voltageMv <= 0 || battery.currentUa == 0) return@mapNotNull null
            battery.voltageMv.toFloat() * kotlin.math.abs(battery.currentUa) / 1_000_000f
        }
        return if (powers.isEmpty()) null else powers.average().toFloat()
    }

    /** 取樣期間多數時間是否接著電源；沒有任何有效讀數時回 null。 */
    private fun powerPluggedOf(samples: List<BenchmarkSample>): Boolean? {
        val flags = samples.mapNotNull { it.battery?.isPlugged }
        if (flags.isEmpty()) return null
        return flags.count { it } * 2 >= flags.size
    }

    private data class BenchmarkSample(
        val elapsedMs: Long,
        val load: Float,
        val freqs: List<Int>,
        val battery: com.deviceinfo.data.model.BatteryLive?
    )

    private fun frequencyRatio(current: List<Int>, peaks: IntArray): Float {
        if (current.isEmpty() || peaks.isEmpty()) return 0f
        var sum = 0L
        var peakSum = 0L
        current.forEachIndexed { index, freq ->
            val peak = peaks.getOrNull(index) ?: 0
            if (peak > 0) {
                sum += freq.coerceAtMost(peak)
                peakSum += peak
            }
        }
        if (peakSum <= 0L) return 0f
        return (sum.toFloat() / peakSum).coerceIn(0f, 1f)
    }

    private companion object {
        const val HISTORY_SIZE = 64
    }
}
