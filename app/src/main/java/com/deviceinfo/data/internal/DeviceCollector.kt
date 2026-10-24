package com.deviceinfo.data.internal

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.AppsSummary
import com.deviceinfo.data.model.CpuCoreSpec
import com.deviceinfo.data.model.DeviceIdentity
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.GpuInfo
import com.deviceinfo.data.model.NetworkStatus
import com.deviceinfo.data.model.OsInfo
import com.deviceinfo.data.model.RamInfo
import com.deviceinfo.data.model.ScreenInfo
import com.deviceinfo.data.model.SocInfo
import com.deviceinfo.data.model.StorageInfo
import java.util.Locale
import kotlin.math.sqrt

/**
 * 靜態設備資訊採集。所有欄位都做了兜底：
 * 拿不到就給 「—」，絕不讓某一塊資訊拖垮整個頁面。
 */
internal class DeviceCollector(private val context: Context) {

    private val props: Map<String, String> by lazy { readAllProps() }

    fun collect(): DeviceSnapshot {
        val cpu = CpuReader()
        val cores = guard(emptyList()) { cpu.readCores() }
        val elapsed = SystemClock.elapsedRealtime()
        val uptime = SystemClock.uptimeMillis()

        // 每一项独立兜底：某个采集器在特定 ROM 上炸了，只让那一张卡片显示“—”，
        // 而不是让整个应用停在错误页。
        return DeviceSnapshot(
            identity = guard(EMPTY_IDENTITY) { readIdentity() },
            os = guard(EMPTY_OS) { readOs(cores) },
            soc = guard(EMPTY_SOC) { readSoc(cores) },
            gpu = guard(EMPTY_GPU) { GpuProbe.probe() },
            clusters = guard(emptyList()) { cpu.readClusters(cores) },
            ram = guard(RamInfo(0L, 0L, 0L, false)) { readRam() },
            storage = guard(StorageInfo(0L, 0L, null, null)) { readStorage() },
            screen = guard(EMPTY_SCREEN) { readScreen() },
            network = guard(EMPTY_NETWORK) { NetworkProbe(context).read() },
            apps = guard(AppsSummary(0, 0, 0, emptyList())) { AppScanner(context).scan() },
            cameras = guard(emptyList()) { CameraProbe(context).scan() },
            sensors = guard(emptyList()) { SensorProbe(context).scan() },
            uptimeMillis = uptime,
            sleepMillis = (elapsed - uptime).coerceAtLeast(0L),
            capturedAtMillis = System.currentTimeMillis()
        )
    }

    private inline fun <T> guard(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (_: Throwable) {
            fallback
        }

    private fun readIdentity(): DeviceIdentity = DeviceIdentity(
        manufacturer = Build.MANUFACTURER.orDash(),
        brand = Build.BRAND.orDash(),
        model = Build.MODEL.orDash(),
        marketName = readMarketName(),
        device = Build.DEVICE.orDash(),
        product = Build.PRODUCT.orDash(),
        board = Build.BOARD.orDash(),
        hardware = Build.HARDWARE.orDash(),
        sku = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SKU.orDash() else "—",
        fingerprint = Build.FINGERPRINT.orDash(),
        buildId = Build.ID.orDash(),
        bootloader = Build.BOOTLOADER.orDash(),
        baseband = runCatching { Build.getRadioVersion() }.getOrNull().orDash(),
        // Build.getSerial() 需要 READ_PRIVILEGED_PHONE_STATE（系统签名权限），普通应用调了也只会抛异常。
        // 改为读只读属性，拿不到就如实标注，不伪装成“未知”。
        serial = props["ro.serialno"]
            ?.takeIf { it.isNotBlank() && it != "unknown" && it != Build.UNKNOWN }
            .orDash()
    )

    /**
     * 行銷名稱。中國市場機型的 Build.MODEL 常是內部型號（如 23113RKC6C），對使用者沒有意義；
     * 各家 ROM 會把行銷名寫進各自的唯讀屬性，逐一嘗試後才退回型號。
     */
    private fun readMarketName(): String {
        val candidates = listOf(
            "ro.product.marketname",
            "ro.product.odm.marketname",
            "ro.product.vendor.marketname",
            "ro.product.bootimage.marketname",
            "ro.config.marketing_name",
            "ro.vendor.oplus.marketname",
            "ro.oppo.market.name",
            "ro.product.market_name"
        )
        for (key in candidates) {
            val value = props[key]?.trim().orEmpty()
            if (value.isNotEmpty() && value != Build.UNKNOWN && !value.equals("unknown", true)) {
                return value
            }
        }
        return Build.MODEL.orDash()
    }

    private fun readOs(cores: List<CpuCoreSpec>): OsInfo {
        val sdk = Build.VERSION.SDK_INT
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val is64 = abis.any { it.contains("64") }
        return OsInfo(
            release = Build.VERSION.RELEASE.orDash(),
            codename = codeNameOf(sdk),
            sdkInt = sdk,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH.orDash()
            } else {
                "—"
            },
            buildType = Build.TYPE.orDash(),
            buildTags = Build.TAGS.orDash(),
            buildTime = Build.TIME,
            kernel = readKernel(),
            javaVm = runCatching { System.getProperty("java.vm.version") }.getOrNull().orDash(),
            romName = readRomName(),
            romVersion = props["ro.build.version.incremental"].orDash(),
            is64Bit = is64,
            abis = abis.ifEmpty { listOf(System.getProperty("os.arch").orDash()) }
        )
    }

    private fun readSoc(cores: List<CpuCoreSpec>): SocInfo {
        val socName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            props["ro.soc.model"]
        }?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            ?: props["ro.board.platform"]
            ?: props["ro.hardware"]
            ?: L10n.s(R.string.prb_unknown)

        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER
        } else {
            props["ro.soc.manufacturer"]
        }?.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.MANUFACTURER.orDash()

        val bitness = when {
            Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true &&
                Build.SUPPORTED_32_BIT_ABIS?.isNotEmpty() == true -> "64-bit / 32-bit"
            Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true -> "64-bit"
            else -> "32-bit"
        }

        return SocInfo(
            name = socName,
            marketingName = SocNames.marketingName(socName) ?: socName,
            manufacturer = socManufacturer,
            vendorLabel = SocNames.vendorLabel(socManufacturer),
            hardware = Build.HARDWARE.orDash(),
            architecture = System.getProperty("os.arch").orDash(),
            coreCount = cores.size,
            bitness = bitness
        )
    }

    private fun readRam(): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        return try {
            activityManager?.getMemoryInfo(info)
            RamInfo(
                totalBytes = info.totalMem,
                availableBytes = info.availMem,
                thresholdBytes = info.threshold,
                isLowMemory = info.lowMemory
            )
        } catch (_: Throwable) {
            RamInfo(0L, 0L, 0L, false)
        }
    }

    private fun readStorage(): StorageInfo {
        val data = readStatFs(Environment.getDataDirectory().path)
        val externalPath = context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile
        val external = externalPath?.let { readStatFs(it.path) }
        return StorageInfo(
            dataTotalBytes = data?.first ?: 0L,
            dataFreeBytes = data?.second ?: 0L,
            externalTotalBytes = external?.first,
            externalFreeBytes = external?.second
        )
    }

    private fun readStatFs(path: String): Pair<Long, Long>? = try {
        val stat = StatFs(path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        total to free
    } catch (_: Throwable) {
        null
    }

    private fun readScreen(): ScreenInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val display: Display? = windowManager?.defaultDisplay
        val metrics = DisplayMetrics()
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        } else {
            metrics.setTo(context.resources.displayMetrics)
        }

        val refreshRate = try {
            display?.refreshRate ?: 60f
        } catch (_: Throwable) {
            60f
        }

        val supportedRates = try {
            display?.supportedModes
                ?.map { it.refreshRate }
                ?.distinctBy { it.toInt() }
                ?.sorted()
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        val hdrTypes = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                display?.hdrCapabilities?.supportedHdrTypes?.map { hdrLabel(it) }.orEmpty()
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonal = sqrt(widthInches * widthInches + heightInches * heightInches)

        return ScreenInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRateHz = refreshRate,
            supportedRefreshRates = supportedRates,
            diagonalInches = if (diagonal.isFinite() && diagonal > 0f) diagonal else 0f,
            hdrTypes = hdrTypes,
            densityBucket = densityBucket(metrics.densityDpi),
            supplier = readScreenSupplier()
        )
    }

    /** 嘗試從多個屬性與 sysfs 路徑讀取面板供應商；不同 OEM 差異極大，讀不到正常。 */
    private fun readScreenSupplier(): String? {
        val propKeys = listOf(
            "ro.vendor.display.panel_name",
            "vendor.display.panel_name",
            "ro.display.panel_name",
            "ro.panel.name",
            "ro.vendor.display.type",
            "ro.display.type",
            "persist.panel.name",
            "ro.vendor.display.panelvendor"
        )
        propKeys.forEach { key ->
            props[key]?.takeIf { it.isNotBlank() && it != "unknown" }?.let { return it }
        }

        val sysfsPaths = listOf(
            "/sys/class/drm/card0-DSI-1/name",
            "/sys/class/drm/card0-eeprom-1/name",
            "/sys/class/graphics/fb0/name"
        )
        sysfsPaths.forEach { path ->
            SysFs.readFirstLine(path)?.takeIf { it.isNotBlank() && it != "unknown" }?.let { return it }
        }
        return null
    }

    private fun hdrLabel(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "HDR($type)"
    }

    private fun densityBucket(dpi: Int): String = when {
        dpi >= 560 -> "xxxhdpi"
        dpi >= 400 -> "xxhdpi"
        dpi >= 320 -> "xhdpi"
        dpi >= 240 -> "hdpi"
        dpi >= 160 -> "mdpi"
        else -> "ldpi"
    }

    private fun readKernel(): String {
        val version = System.getProperty("os.version").orEmpty()
        val procVersion = SysFs.readFirstLine("/proc/version").orEmpty()
        return when {
            procVersion.isNotBlank() -> procVersion
            version.isNotBlank() -> "Linux $version"
            else -> "—"
        }
    }

    private fun readRomName(): String {
        val candidates = listOf(
            "ro.mi.os.version.name" to "HyperOS",
            "ro.mi.os.version.incremental" to "HyperOS",
            "ro.miui.ui.version.name" to "MIUI",
            "ro.build.version.oplusrom" to "ColorOS",
            "ro.build.version.opporom" to "ColorOS",
            "ro.vivo.os.version" to "OriginOS",
            "ro.build.version.emui" to "EMUI",
            "ro.build.version.magic" to "MagicOS",
            "ro.oxygen.version" to "OxygenOS",
            "ro.build.version.realmeui" to "realme UI"
        )
        for ((key, label) in candidates) {
            val value = props[key]?.takeIf { it.isNotBlank() } ?: continue
            return if (value.startsWith(label, ignoreCase = true)) value else "$label $value"
        }
        return Build.DISPLAY.orDash()
    }

    /** getprop 全量讀取一次，比逐個反射 SystemProperties 穩定（隱藏 API 白名單問題）。 */
    private fun readAllProps(): Map<String, String> = try {
        val process = ProcessBuilder("getprop")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.lineSequence().mapNotNull { line ->
            val open = line.indexOf('[')
            val divider = line.indexOf("]: [")
            if (open != 0 || divider < 0 || !line.endsWith("]")) {
                null
            } else {
                val key = line.substring(1, divider)
                val value = line.substring(divider + 4, line.length - 1)
                key to value
            }
        }.toMap()
    } catch (_: Throwable) {
        emptyMap()
    }

    /**
     * API level → 甜點代號。表裡沒有的版本回空字串，
     * UI 就不顯示括號（以前回 "Android" 會變成「Android 17 (Android)」這種廢話）。
     */
    private fun codeNameOf(sdk: Int): String = when (sdk) {
        37 -> "Cinnamon Bun"
        36 -> "Baklava"
        35 -> "Vanilla Ice Cream"
        34 -> "Upside Down Cake"
        33 -> "Tiramisu"
        32, 31 -> "Snow Cone"
        30 -> "Red Velvet Cake"
        29 -> "Quince Tart"
        28 -> "Pie"
        27, 26 -> "Oreo"
        25, 24 -> "Nougat"
        23 -> "Marshmallow"
        22, 21 -> "Lollipop"
        else -> ""
    }

    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "—"

    fun formattedSizeLabel(bytes: Long): String =
        String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))

    companion object {
        /** 供 UI 側顯示的顯示管理器，避免每處重複寫 try-catch。 */
        fun displayManager(context: Context): DisplayManager? =
            context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

        private val EMPTY_IDENTITY = DeviceIdentity(
            manufacturer = "—", brand = "—", model = "—", marketName = "—", device = "—", product = "—",
            board = "—", hardware = "—", sku = "—", fingerprint = "—", buildId = "—",
            bootloader = "—", baseband = "—", serial = "—"
        )

        private val EMPTY_OS = OsInfo(
            release = "—", codename = "—", sdkInt = Build.VERSION.SDK_INT,
            securityPatch = "—", buildType = "—", buildTags = "—", buildTime = 0L,
            kernel = "—", javaVm = "—", romName = "—", romVersion = "—",
            is64Bit = false, abis = emptyList()
        )

        private val EMPTY_SOC = SocInfo(
            name = "—", marketingName = "—", manufacturer = "—", vendorLabel = "SoC",
            hardware = "—", architecture = "—", coreCount = 1, bitness = "—"
        )

        private val EMPTY_GPU = GpuInfo(
            renderer = L10n.s(R.string.prb_gpu_unknown), vendor = "—", glVersion = "—", source = "fallback"
        )

        private val EMPTY_SCREEN = ScreenInfo(
            widthPx = 0, heightPx = 0, densityDpi = 0, refreshRateHz = 0f,
            supportedRefreshRates = emptyList(), diagonalInches = 0f,
            hdrTypes = emptyList(), densityBucket = "—", supplier = null
        )

        private val EMPTY_NETWORK = NetworkStatus(
            isConnected = false, transportLabel = L10n.s(R.string.prb_net_disconnected), ipv4 = null, ipv6 = null,
            linkDownKbps = 0, linkUpKbps = 0, wifiRssiDbm = null,
            wifiLinkSpeedMbps = null, wifiFrequencyMhz = null, carrier = null,
            mobileType = null, wifiSsid = null, wifiBssid = null, rxBytes = 0, txBytes = 0
        )
    }
}
