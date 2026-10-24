package com.deviceinfo.data.internal

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.NetworkStatus
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * 網路狀態採集。
 *
 * SSID / BSSID 在 Android 10+ 需要定位權限：未授權時跳過，授權後才讀取。
 */
internal class NetworkProbe(private val context: Context) {

    fun read(): NetworkStatus {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = try {
            connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        } catch (_: Throwable) {
            null
        }

        val transportLabel = when {
            capabilities == null -> L10n.s(R.string.prb_net_disconnected)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> L10n.s(R.string.prb_net_cellular)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> L10n.s(R.string.prb_net_ethernet)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> L10n.s(R.string.prb_net_bluetooth_tether)
            else -> L10n.s(R.string.prb_net_connected)
        }

        val wifi = readWifi()
        val identity = readWifiIdentity()
        val addresses = readIpAddresses()

        return NetworkStatus(
            isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            transportLabel = transportLabel,
            ipv4 = addresses.first,
            ipv6 = addresses.second,
            linkDownKbps = capabilities?.linkDownstreamBandwidthKbps ?: 0,
            linkUpKbps = capabilities?.linkUpstreamBandwidthKbps ?: 0,
            wifiRssiDbm = wifi?.rssiDbm,
            wifiLinkSpeedMbps = wifi?.linkSpeedMbps,
            wifiFrequencyMhz = wifi?.frequencyMhz,
            carrier = readCarrier(),
            mobileType = readMobileType(),
            wifiSsid = identity.first,
            wifiBssid = identity.second,
            rxBytes = TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: 0L,
            txBytes = TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: 0L
        )
    }

    private data class WifiSnapshot(val rssiDbm: Int, val linkSpeedMbps: Int, val frequencyMhz: Int)

    /**
     * SSID / BSSID。Android 10+ 必須有定位權限（且定位服務開啟）才拿得到，
     * 否則系統會回傳 `<unknown ssid>` / `02:00:00:00:00:00` 這類掩碼，這裡視為 null。
     */
    private fun readWifiIdentity(): Pair<String?, String?> {
        return try {
            val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fine) return null to null

            val manager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null to null
            @Suppress("DEPRECATION")
            val info = manager.connectionInfo ?: return null to null

            @Suppress("DEPRECATION")
            // 用字面量而非 WifiManager.UNKNOWN_SSID：後者 API 30 才引入，minSdk 24
            val ssid = info.ssid
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
            @Suppress("DEPRECATION")
            val bssid = info.bssid
                ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
            ssid to bssid
        } catch (_: Throwable) {
            null to null
        }
    }

    private fun readWifi(): WifiSnapshot? {
        return try {
            val manager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            @Suppress("DEPRECATION")
            val info = manager.connectionInfo ?: return null
            @Suppress("DEPRECATION")
            val rssi = info.rssi.takeIf { it in -127..0 }
            @Suppress("DEPRECATION")
            val speed = info.linkSpeed.takeIf { it > 0 }
            @Suppress("DEPRECATION")
            val freq = info.frequency.takeIf { it > 0 }
            if (rssi == null && speed == null && freq == null) null
            else WifiSnapshot(rssi ?: 0, speed ?: 0, freq ?: 0)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readIpAddresses(): Pair<String?, String?> {
        var ipv4: String? = null
        var ipv6: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null to null
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (address in networkInterface.inetAddresses) {
                    if (address.isLoopbackAddress) continue
                    when (address) {
                        is Inet4Address -> if (ipv4 == null) ipv4 = address.hostAddress
                        is Inet6Address -> {
                            val host = address.hostAddress ?: continue
                            // 只取全局地址，跳過 fe80 鏈路本地
                            if (ipv6 == null && !host.startsWith("fe80")) ipv6 = host.substringBefore('%')
                        }
                    }
                }
                if (ipv4 != null && ipv6 != null) break
            }
        } catch (_: Throwable) {
            // 忽略，返回已拿到的部分
        }
        return ipv4 to ipv6
    }

    private fun readCarrier(): String? = try {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephony?.networkOperatorName?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    /**
     * 移动网络制式。
     *
     * 本应用不申请 READ_PHONE_STATE：在 Android 10+ 上该调用会抛 SecurityException，
     * 由外层 try-catch 兜底，UI 自动隐藏该项。这是刻意取舍 —— 不为一个次要字段索取敏感权限。
     */
    @SuppressLint("MissingPermission")
    private fun readMobileType(): String? = try {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        @Suppress("DEPRECATION")
        val type = telephony?.dataNetworkType ?: 0
        when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            0 -> null
            else -> L10n.s(R.string.prb_net_type_fallback, type)
        }
    } catch (_: Throwable) {
        null
    }
}
