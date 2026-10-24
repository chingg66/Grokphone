package com.deviceinfo.data.model

/**
 * 設備靜態快照。由 [com.deviceinfo.data.DeviceRepository] 一次性採集，
 * 之後不再變化的部分都收斂到這裡，避免 UI 反覆請求。
 */
data class DeviceSnapshot(
    val identity: DeviceIdentity,
    val os: OsInfo,
    val soc: SocInfo,
    val gpu: GpuInfo,
    val clusters: List<CpuClusterSpec>,
    val ram: RamInfo,
    val storage: StorageInfo,
    val screen: ScreenInfo,
    val network: NetworkStatus,
    val apps: AppsSummary,
    val cameras: List<CameraSpec>,
    val sensors: List<SensorSpec>,
    val uptimeMillis: Long,
    val sleepMillis: Long,
    val capturedAtMillis: Long,
)

data class DeviceIdentity(
    val manufacturer: String,
    val brand: String,
    val model: String,
    /** 行銷名稱（例如 Redmi K70）；取不到時與 [model] 相同。 */
    val marketName: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val sku: String,
    val fingerprint: String,
    val buildId: String,
    val bootloader: String,
    val baseband: String,
    val serial: String,
)

data class OsInfo(
    val release: String,
    val codename: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildType: String,
    val buildTags: String,
    val buildTime: Long,
    val kernel: String,
    val javaVm: String,
    val romName: String,
    val romVersion: String,
    val is64Bit: Boolean,
    val abis: List<String>,
)

data class SocInfo(
    /** 硬體代號，例如 SM8750。 */
    val name: String,
    /** 市場名，例如 Snapdragon 8 Elite；查不到時與 [name] 相同。 */
    val marketingName: String,
    val manufacturer: String,
    /** 正規化後的廠商品牌名，例如 Qualcomm®。 */
    val vendorLabel: String,
    val hardware: String,
    val architecture: String,
    val coreCount: Int,
    val bitness: String,
)

data class GpuInfo(
    val renderer: String,
    val vendor: String,
    val glVersion: String,
    val source: String,
)

data class CpuCoreSpec(
    val index: Int,
    val maxFreqMhz: Int,
    val minFreqMhz: Int,
    val clusterIndex: Int,
)

data class CpuClusterSpec(
    val index: Int,
    val label: String,
    val coreIndexes: List<Int>,
    val maxFreqMhz: Int,
    val minFreqMhz: Int = 0,
)

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedRatio: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

data class StorageInfo(
    val dataTotalBytes: Long,
    val dataFreeBytes: Long,
    val externalTotalBytes: Long?,
    val externalFreeBytes: Long?,
) {
    val dataUsedBytes: Long get() = (dataTotalBytes - dataFreeBytes).coerceAtLeast(0L)
    val usedRatio: Float
        get() = if (dataTotalBytes <= 0L) 0f
        else (dataUsedBytes.toDouble() / dataTotalBytes).toFloat().coerceIn(0f, 1f)
}

data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val supportedRefreshRates: List<Float>,
    val diagonalInches: Float,
    val hdrTypes: List<String>,
    val densityBucket: String,
    /** 面板供應商，例如 BOE / Samsung Display / LG Display。多數 ROM 無法讀取，會是 null。 */
    val supplier: String?,
)

data class NetworkStatus(
    val isConnected: Boolean,
    val transportLabel: String,
    val ipv4: String?,
    val ipv6: String?,
    val linkDownKbps: Int,
    val linkUpKbps: Int,
    val wifiRssiDbm: Int?,
    val wifiLinkSpeedMbps: Int?,
    val wifiFrequencyMhz: Int?,
    val carrier: String?,
    val mobileType: String?,
    /** Wi-Fi 名稱。需要定位權限（Android 10+），未授權或離線時為 null。 */
    val wifiSsid: String?,
    /** 路由器地址。需要定位權限，未授權或離線時為 null。 */
    val wifiBssid: String?,
    val rxBytes: Long,
    val txBytes: Long,
)

data class AppsSummary(
    val total: Int,
    val systemCount: Int,
    val userCount: Int,
    val apps: List<AppEntry>,
    /** QUERY_ALL_PACKAGES 是否已安裝期授予（Android 11+）。 */
    val queryAllGranted: Boolean = true,
    /**
     * 是否真的拿到了完整清單。
     *
     * 只看權限不夠：MIUI / HyperOS 等 ROM 有一層自有的「讀取應用程式清單」開關，
     * 即使 QUERY_ALL_PACKAGES granted=true，系統仍會把結果過濾成只有自己，
     * 所以再用「可見數量是否合理」交叉判斷。
     */
    val fullAccess: Boolean = true,
) {
    val installedBytes: Long get() = apps.sumOf { it.sizeBytes }
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val isSystem: Boolean,
    val targetSdk: Int,
    val installedAtMillis: Long,
)

/** 單一 ABI 底下的原生庫清單（.so 去 lib 前綴 / .so 後綴後的名稱）。 */
data class AppAbiLibs(
    val abi: String,
    val libs: List<String>,
)

/**
 * 應用架構詳情（LibChecker 風格）。
 *
 * ABI 透過解壓 base + split APK 的 `lib/<abi>/` 條目判定，
 * 比單看 split 目錄名更可靠（split 命名各家 ROM 不完全一致）。
 */
data class AppDetail(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    /** 依 APK 內容判定的 ABI，按裝置偏好排序；空列表代表純 Kotlin/Java。 */
    val abis: List<String>,
    val nativeLibs: List<AppAbiLibs>,
    /** Split APK 數量（不含 base）。 */
    val splitCount: Int,
    val activityCount: Int?,
    val serviceCount: Int?,
    val receiverCount: Int?,
    val providerCount: Int?,
    val permissionCount: Int?,
    val installer: String?,
    val installedAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val hasNativeLibs: Boolean get() = nativeLibs.isNotEmpty()
}

data class CameraSpec(
    val id: String,
    val facingLabel: String,
    val megapixels: Float,
    /** 感測器總像素數（精確值），例如 50331648。 */
    val pixelCount: Long,
    val resolution: String,
    val aperture: Float?,
    val focalLengthMm: Float?,
    val sensorSizeMm: String?,
    val hasFlash: Boolean,
    val maxFps: Int?,
    val zoomRange: String?,
)

data class SensorSpec(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeLabel: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMilliAmp: Float,
    val isWakeUp: Boolean,
)

/** 實時資料：CPU 頻率、負載、電池、記憶體佔用，按 tick 刷新。 */
data class LiveSnapshot(
    val coreFreqsMhz: List<Int>,
    val loadRatio: Float,
    val loadHistory: List<Float> = emptyList(),
    val battery: BatteryLive? = null,
    val ramUsedRatio: Float = 0f,
    val ramAvailableBytes: Long = 0L,
)

data class BatteryLive(
    val levelPercent: Int,
    val statusLabel: String,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentUa: Int,
    val healthLabel: String,
    val pluggedLabel: String,
    /** 是否接著電源。電流正負號各家廠商約定不一致，判斷充放電方向必須靠這個旗標。 */
    val isPlugged: Boolean,
    val technology: String,
    val designCapacityUah: Long?,
    val chargeCounterUah: Long?,
    /** 目前滿充容量（µAh）：與設計容量的比值即容量健康度。 */
    val chargeFullUah: Long?,
)

/** 感測器實時讀數（僅訂閱當前展開的感測器，避免常駐註冊耗電）。 */
data class SensorReading(
    val type: Int,
    val values: List<Float>,
    val timestampNanos: Long,
)

/** UI 層的三態。所有頁面共享同一個狀態機，避免出現「半載入」界面。 */
sealed interface DeviceUiState {
    data object Loading : DeviceUiState
    data class Error(val title: String, val detail: String?) : DeviceUiState
    data class Ready(
        val snapshot: DeviceSnapshot,
        val live: LiveSnapshot,
    ) : DeviceUiState
}

/** 效能評估測試狀態。 */
sealed interface BenchmarkState {
    data object Idle : BenchmarkState
    data class InProgress(
        val elapsedMs: Long,
        val targetMs: Long,
        val avgLoad: Float,
        val avgFreqMhz: Float,
        val peakTempC: Float,
        val currentBatteryPct: Int,
        /** 即時合成的 Geekbench 風格跑分，每秒更新。 */
        val score: Int
    ) : BenchmarkState
    data class Finished(val result: BenchmarkResult) : BenchmarkState
}

/** 效能評估結果。 */
data class BenchmarkResult(
    val targetMs: Long,
    val sampleCount: Int,
    val avgLoad: Float,
    val peakLoad: Float,
    val avgFreqMhz: Float,
    val peakFreqMhz: Int,
    val startBatteryPct: Int,
    val endBatteryPct: Int,
    val startTempC: Float,
    val endTempC: Float,
    /** 平均功率的絕對值（毫瓦）。取樣期間電壓或電流無效時為 null。 */
    val avgPowerMw: Float?,
    /** 測試期間是否接著電源，用於標示功率方向（入 / 出）。 */
    val powerPlugged: Boolean? = null,
    /** 最終跑分（測試期間即時分的平均值）。 */
    val score: Int
)

/** GPU 渲染效能測試狀態。 */
sealed interface GpuBenchmarkState {
    data object Idle : GpuBenchmarkState
    data class InProgress(
        val elapsedMs: Long,
        val targetMs: Long,
        val currentFps: Float,
        val avgFps: Float,
        val frameCount: Long,
        val score: Int,
        val gpuName: String
    ) : GpuBenchmarkState
    data class Finished(val result: GpuBenchmarkResult) : GpuBenchmarkState
    data class Failed(val message: String) : GpuBenchmarkState
}

/** 應用架構詳情的載入狀態。 */
sealed interface AppDetailState {
    data object Idle : AppDetailState
    data object Loading : AppDetailState
    data class Ready(val detail: AppDetail) : AppDetailState
    data class Failed(val packageName: String) : AppDetailState
}

/** GPU 渲染效能測試結果。 */
data class GpuBenchmarkResult(
    val targetMs: Long,
    val frameCount: Long,
    val avgFps: Float,
    val peakFps: Float,
    val minFps: Float,
    val score: Int,
    val gpuName: String,
    val startTempC: Float,
    val endTempC: Float
)

