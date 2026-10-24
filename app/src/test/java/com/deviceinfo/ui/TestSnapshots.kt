package com.deviceinfo.ui

import com.deviceinfo.R
import com.deviceinfo.core.L10n
import com.deviceinfo.data.model.AppEntry
import com.deviceinfo.data.model.AppsSummary
import com.deviceinfo.data.model.BatteryLive
import com.deviceinfo.data.model.CameraSpec
import com.deviceinfo.data.model.CpuClusterSpec
import com.deviceinfo.data.model.DeviceIdentity
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.GpuInfo
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.data.model.NetworkStatus
import com.deviceinfo.data.model.OsInfo
import com.deviceinfo.data.model.RamInfo
import com.deviceinfo.data.model.ScreenInfo
import com.deviceinfo.data.model.SensorSpec
import com.deviceinfo.data.model.SocInfo
import com.deviceinfo.data.model.StorageInfo

/** 测试用的固定数据，同时也用来验证各种边界值下的渲染稳定性。 */
object TestSnapshots {

    fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            manufacturer = "Xiaomi",
            brand = "POCO",
            model = "24122RKC7G",
            device = "miro",
            product = "miro_global",
            board = "sun",
            hardware = "qcom",
            sku = "miro",
            fingerprint = "POCO/miro_global/miro:16/BP2A.250705.008/OS3.0.3.0:user/release-keys",
            buildId = "BP2A.250705.008",
            bootloader = "unknown",
            baseband = "MPSS.DE.7.0-00123",
            serial = "—"
        ),
        os = OsInfo(
            release = "16",
            codename = "Baklava",
            sdkInt = 36,
            securityPatch = "2026-09-01",
            buildType = "user",
            buildTags = "release-keys",
            buildTime = 1_760_000_000_000L,
            kernel = "Linux version 6.6.30-android15",
            javaVm = "2.1.0",
            romName = "HyperOS 3.0.303.0",
            romVersion = "OS3.0.303.0.WOCMIXM",
            is64Bit = true,
            abis = listOf("arm64-v8a", "armeabi-v7a")
        ),
        soc = SocInfo(
            name = "SM8750",
            marketingName = "Snapdragon 8 Elite",
            manufacturer = "Qualcomm",
            vendorLabel = "Qualcomm®",
            hardware = "qcom",
            architecture = "arm64-v8a",
            coreCount = 8,
            bitness = "64-bit"
        ),
        gpu = GpuInfo(
            renderer = "Adreno (TM) 830",
            vendor = "Qualcomm",
            glVersion = "OpenGL ES 3.2",
            source = "egl"
        ),
        clusters = listOf(
            CpuClusterSpec(0, "超大核 · 2 核 · 4320 MHz", listOf(0, 1), 4320, 1017),
            CpuClusterSpec(1, "性能核 · 6 核 · 3532 MHz", listOf(2, 3, 4, 5, 6, 7), 3532, 384)
        ),
        ram = RamInfo(
            totalBytes = 17_179_869_184L,
            availableBytes = 6_442_450_944L,
            thresholdBytes = 536_870_912L,
            isLowMemory = false
        ),
        storage = StorageInfo(
            dataTotalBytes = 512_000_000_000L,
            dataFreeBytes = 210_000_000_000L,
            externalTotalBytes = null,
            externalFreeBytes = null
        ),
        screen = ScreenInfo(
            widthPx = 1440,
            heightPx = 3200,
            densityDpi = 560,
            refreshRateHz = 120f,
            supportedRefreshRates = listOf(60f, 90f, 120f),
            diagonalInches = 6.73f,
            hdrTypes = listOf("HDR10", "HLG"),
            densityBucket = "xxxhdpi",
            supplier = "Samsung Display"
        ),
        network = NetworkStatus(
            isConnected = true,
            transportLabel = "Wi-Fi",
            ipv4 = "192.168.1.42",
            ipv6 = "2408:8207:1234::1",
            linkDownKbps = 866_000,
            linkUpKbps = 866_000,
            wifiRssiDbm = -57,
            wifiLinkSpeedMbps = 864,
            wifiFrequencyMhz = 5180,
            carrier = "中國移動",
            mobileType = "5G NR",
            wifiSsid = "Home-5G",
            wifiBssid = "AA:BB:CC:DD:EE:FF",
            rxBytes = 8_589_934_592L,
            txBytes = 1_073_741_824L
        ),
        apps = AppsSummary(
            total = 121,
            systemCount = 28,
            userCount = 93,
            apps = listOf(
                AppEntry("相機", "com.android.camera", "6.2.1", 6020100, 268_435_456L, false, 35, 1_700_000_000_000L),
                AppEntry("設定", "com.android.settings", "16.0", 1600, 134_217_728L, true, 36, 1_690_000_000_000L)
            )
        ),
        cameras = listOf(
            CameraSpec(
                id = "0",
                facingLabel = L10n.s(R.string.prb_cam_back),
                megapixels = 50f,
                pixelCount = 50_331_648L,
                resolution = "8192 × 6144",
                aperture = 1.6f,
                focalLengthMm = 6.8f,
                sensorSizeMm = "9.60 × 7.20 mm",
                hasFlash = true,
                maxFps = 60,
                zoomRange = "1.0× – 10.0×"
            )
        ),
        sensors = listOf(
            SensorSpec(
                name = "STMicro LSM6DSV Accelerometer",
                vendor = "STMicroelectronics",
                type = 1,
                typeLabel = L10n.s(R.string.prb_sensor_accelerometer),
                version = 1,
                maxRange = 78.4f,
                resolution = 0.0024f,
                powerMilliAmp = 0.17f,
                isWakeUp = false
            )
        ),
        uptimeMillis = 78_321_000L,
        sleepMillis = 2_152_000L,
        capturedAtMillis = 1_760_000_000_000L
    )

    fun live(): LiveSnapshot = LiveSnapshot(
        coreFreqsMhz = listOf(2400, 2400, 2400, 2400, 2400, 2400, 2246, 2246),
        loadRatio = 0.42f,
        loadHistory = List(24) { index -> (0.2f + 0.5f * kotlin.math.sin(index / 3.0).toFloat()) },
        battery = BatteryLive(
            levelPercent = 97,
            statusLabel = "放電",
            temperatureC = 37f,
            voltageMv = 4321,
            currentUa = -420_000,
            healthLabel = "良好",
            pluggedLabel = "未連接電源",
            isPlugged = false,
            technology = "Li-ion",
            designCapacityUah = 5_400_000L,
            chargeCounterUah = 5_200_000L
        ),
        ramUsedRatio = 0.63f,
        ramAvailableBytes = 6_442_450_944L
    )

    /** 极端数据：所有集合为空、数值为 0，用来验证“什么都没读到”时界面不崩。 */
    fun empty(): Pair<DeviceSnapshot, LiveSnapshot> {
        val base = snapshot()
        return base.copy(
            identity = base.identity.copy(serial = "—", baseband = "—"),
            clusters = emptyList(),
            cameras = emptyList(),
            sensors = emptyList(),
            apps = AppsSummary(0, 0, 0, emptyList()),
            network = NetworkStatus(
                isConnected = false,
                transportLabel = "未連接",
                ipv4 = null,
                ipv6 = null,
                linkDownKbps = 0,
                linkUpKbps = 0,
                wifiRssiDbm = null,
                wifiLinkSpeedMbps = null,
                wifiFrequencyMhz = null,
                carrier = null,
                mobileType = null,
                wifiSsid = null,
                wifiBssid = null,
                rxBytes = 0,
                txBytes = 0
            ),
            ram = RamInfo(0, 0, 0, false),
            storage = StorageInfo(0, 0, null, null)
        ) to LiveSnapshot(
            coreFreqsMhz = emptyList(),
            loadRatio = 0f,
            loadHistory = emptyList(),
            battery = null,
            ramUsedRatio = 0f,
            ramAvailableBytes = 0
        )
    }
}
