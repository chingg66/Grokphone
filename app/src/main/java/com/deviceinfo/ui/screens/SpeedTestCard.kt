package com.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.core.L10n
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.KeyValueRow
import com.deviceinfo.ui.components.PillTag
import com.deviceinfo.ui.components.RatioBar
import com.deviceinfo.ui.theme.DevicePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max

/**
 * 下載測速。
 *
 * 節點按順序嘗試：若某個節點在 3 秒內只吐不到 2 MB（被中間設備或運營商截斷的典型表現），
 * 就自動換下一個，而不是把「0.06 Mbps」這種假結果當成結論報給用戶。
 * 全部節點都失敗才顯示錯誤。只用 JDK 標準庫，不引入 OkHttp。
 */
private data class SpeedEndpoint(val label: String, val url: String)

private val SPEED_ENDPOINTS = listOf(
    SpeedEndpoint("Cloudflare", "https://speed.cloudflare.com/__down?bytes=35000000"),
    SpeedEndpoint("OVH", "https://proof.ovh.net/files/100Mb.dat"),
    SpeedEndpoint("Hetzner", "https://speed.hetzner.de/100MB.bin")
)

private const val MAX_BYTES = 35_000_000L
private const val MAX_DURATION_MS = 12_000L
private const val SAMPLE_INTERVAL_MS = 150L
/** 低於這個量級視為節點不可用：正常鏈路 3 秒至少能拉幾 MB。 */
private const val MIN_VIABLE_BYTES = 2_000_000L

private enum class SpeedPhase { Idle, Latency, Downloading, Done, Error }

@Composable
fun SpeedTestCard(modifier: Modifier = Modifier) {
    DeviceInfoCard(title = stringResource(R.string.spd_title), modifier = modifier) {
        Column {
            var phase by remember { mutableStateOf(SpeedPhase.Idle) }
            var liveMbps by remember { mutableFloatStateOf(0f) }
            var avgMbps by remember { mutableFloatStateOf(0f) }
            var peakMbps by remember { mutableFloatStateOf(0f) }
            var latencyMs by remember { mutableIntStateOf(-1) }
            var progress by remember { mutableFloatStateOf(0f) }
            var nodeLabel by remember { mutableStateOf<String?>(null) }
            var errorText by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            var job by remember { mutableStateOf<Job?>(null) }

            DisposableEffect(Unit) {
                onDispose { job?.cancel() }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PillTag(
                    text = when (phase) {
                        SpeedPhase.Idle -> stringResource(R.string.spd_phase_idle)
                        SpeedPhase.Latency -> stringResource(R.string.spd_phase_latency)
                        SpeedPhase.Downloading -> stringResource(R.string.spd_phase_downloading)
                        SpeedPhase.Done -> stringResource(R.string.spd_phase_done)
                        SpeedPhase.Error -> stringResource(R.string.spd_phase_error)
                    },
                    color = when (phase) {
                        SpeedPhase.Done -> DevicePalette.Accent
                        SpeedPhase.Error -> DevicePalette.Danger
                        else -> DevicePalette.Info
                    }
                )
                Spacer(Modifier.weight(1f))
                GlassPillButton(
                    text = if (phase == SpeedPhase.Latency || phase == SpeedPhase.Downloading) {
                        stringResource(R.string.spd_stop)
                    } else {
                        stringResource(R.string.spd_start)
                    },
                    onClick = {
                        if (phase == SpeedPhase.Latency || phase == SpeedPhase.Downloading) {
                            job?.cancel()
                            phase = SpeedPhase.Idle
                            errorText = null
                        } else {
                            liveMbps = 0f
                            avgMbps = 0f
                            peakMbps = 0f
                            latencyMs = -1
                            progress = 0f
                            nodeLabel = null
                            errorText = null
                            job = scope.launch {
                                runSpeedTest(
                                    onPhase = { phase = it },
                                    onLive = { liveMbps = it },
                                    onAvg = { avgMbps = it },
                                    onPeak = { peakMbps = it },
                                    onLatency = { latencyMs = it },
                                    onProgress = { progress = it },
                                    onNode = { nodeLabel = it },
                                    onError = { errorText = it }
                                )
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            val hasReading = phase != SpeedPhase.Idle && phase != SpeedPhase.Error
            if (hasReading) {
                Text(
                    text = if (phase == SpeedPhase.Done) fmtMbps(avgMbps) else fmtMbps(liveMbps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DevicePalette.Accent
                )
                Text(
                    text = if (phase == SpeedPhase.Done) stringResource(R.string.spd_avg_label) else stringResource(R.string.spd_live_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.TextSecondary
                )
            } else {
                Text(
                    text = stringResource(R.string.spd_not_started),
                    style = MaterialTheme.typography.titleLarge,
                    color = DevicePalette.TextTertiary
                )
                Text(
                    text = stringResource(R.string.spd_download_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = DevicePalette.TextTertiary
                )
            }

            Spacer(Modifier.height(10.dp))
            RatioBar(ratio = progress, height = 5.dp)
            Spacer(Modifier.height(10.dp))

            KeyValueRow(label = stringResource(R.string.spd_label_ttfb), value = if (latencyMs >= 0) "$latencyMs ms" else "—")
            KeyValueRow(label = stringResource(R.string.spd_label_peak), value = if (peakMbps > 0f) fmtMbps(peakMbps) else "—")
            KeyValueRow(label = stringResource(R.string.spd_label_avg), value = if (avgMbps > 0f) fmtMbps(avgMbps) else "—")
            KeyValueRow(label = stringResource(R.string.spd_label_node), value = nodeLabel ?: "—")

            if (phase == SpeedPhase.Idle) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.spd_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary
                )
            }
            errorText?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.Danger
                )
            }
        }
    }
}

private fun fmtMbps(mbps: Float): String = when {
    mbps >= 100f -> "%.0f".format(mbps)
    mbps >= 10f -> "%.1f".format(mbps)
    else -> "%.2f".format(mbps)
}

private suspend fun runSpeedTest(
    onPhase: (SpeedPhase) -> Unit,
    onLive: (Float) -> Unit,
    onAvg: (Float) -> Unit,
    onPeak: (Float) -> Unit,
    onLatency: (Int) -> Unit,
    onProgress: (Float) -> Unit,
    onNode: (String) -> Unit,
    onError: (String?) -> Unit
) {
    onPhase(SpeedPhase.Latency)
    onError(null)

    var latency = -1
    var lastError: String? = null

    for (endpoint in SPEED_ENDPOINTS) {
        try {
            latency = withContext(Dispatchers.IO) { measureLatency(endpoint) }
            break
        } catch (e: IOException) {
            lastError = L10n.s(
                R.string.spd_error_node,
                endpoint.label,
                e.message ?: L10n.s(R.string.spd_error_conn_failed)
            )
        }
    }
    onLatency(latency)
    if (latency < 0 && lastError != null) {
        onPhase(SpeedPhase.Error)
        onError(L10n.s(R.string.spd_error_all_nodes, lastError))
        return
    }

    onPhase(SpeedPhase.Downloading)
    onProgress(0f)

    for (endpoint in SPEED_ENDPOINTS) {
        onNode(endpoint.label)
        try {
            val result = runDownload(endpoint, onLive, onAvg, onPeak, onProgress)
            if (result.bytes >= MIN_VIABLE_BYTES || result.elapsedMs >= 3_000L) {
                onPhase(SpeedPhase.Done)
                return
            }
            lastError = L10n.s(R.string.spd_error_low_bytes, endpoint.label, result.elapsedMs, result.bytes)
        } catch (e: IOException) {
            lastError = L10n.s(
                R.string.spd_error_node,
                endpoint.label,
                e.message ?: L10n.s(R.string.spd_error_download_interrupted)
            )
        }
        // 換節點前歸零，避免上一個節點的殘值混進結果
        onLive(0f)
        onProgress(0f)
    }

    onPhase(SpeedPhase.Error)
    onError(L10n.s(R.string.spd_error_failed, SPEED_ENDPOINTS.size, lastError ?: ""))
}

private class DownloadResult(val bytes: Long, val elapsedMs: Long)

/** TTFB 延遲：Cloudflare 用 1 位元組請求，大檔案節點讀一小段即斷開，取兩次最小值。 */
private fun measureLatency(endpoint: SpeedEndpoint): Int {
    var best = Int.MAX_VALUE
    val url = if (endpoint.label == "Cloudflare") {
        "https://speed.cloudflare.com/__down?bytes=1"
    } else {
        endpoint.url
    }
    repeat(2) {
        val start = System.nanoTime()
        val conn = openConnection(url)
        try {
            conn.inputStream.use { it.read(ByteArray(2048)) }
            best = minOf(best, ((System.nanoTime() - start) / 1_000_000L).toInt())
        } finally {
            conn.disconnect()
        }
    }
    return if (best == Int.MAX_VALUE) -1 else max(0, best)
}

/** 串流下載主體：每 150 ms 回報即時 / 平均 / 峰值與進度。 */
private suspend fun runDownload(
    endpoint: SpeedEndpoint,
    onLive: (Float) -> Unit,
    onAvg: (Float) -> Unit,
    onPeak: (Float) -> Unit,
    onProgress: (Float) -> Unit
): DownloadResult = withContext(Dispatchers.IO) {
    val conn = openConnection(endpoint.url)
    try {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        var lastSampleBytes = 0L
        var lastSampleNanos = System.nanoTime()
        val startNanos = lastSampleNanos
        var peak = 0f

        conn.inputStream.use { input ->
            while (currentCoroutineContext().isActive) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                val now = System.nanoTime()
                val sinceSample = (now - lastSampleNanos) / 1_000_000L
                if (sinceSample >= SAMPLE_INTERVAL_MS) {
                    val elapsedMs = ((now - startNanos) / 1_000_000L).coerceAtLeast(1)
                    val avg = bytesToMbps(total, elapsedMs)
                    val window = bytesToMbps(total - lastSampleBytes, sinceSample)
                    // 視窗速率做一次平滑，曲線不會因單個緩衝區而鋸齒亂跳
                    val live = if (peak > 0f) peak * 0.4f + window * 0.6f else window
                    peak = maxOf(peak, window)
                    onLive(live)
                    onAvg(avg)
                    onPeak(peak)
                    onProgress((elapsedMs.toFloat() / MAX_DURATION_MS).coerceIn(0f, 1f))
                    lastSampleBytes = total
                    lastSampleNanos = now
                }
                // 注意單位：now / startNanos 是納秒，上限是毫秒常數，必須換算後再比
                if (total >= MAX_BYTES || now - startNanos >= MAX_DURATION_MS * 1_000_000L) break
            }
        }
        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1)
        if (total > 0) {
            onAvg(bytesToMbps(total, elapsedMs))
            onPeak(peak)
        }
        onProgress(1f)
        DownloadResult(total, elapsedMs)
    } finally {
        conn.disconnect()
    }
}

private fun openConnection(url: String): HttpURLConnection {
    val conn = URL(url).openConnection() as HttpsURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 10_000
    conn.instanceFollowRedirects = true
    conn.useCaches = false
    // 關鍵：延遲探測會半途斷開（只讀前 2 KB），若連線被放回 Keep-Alive 池，
    // 下一次測速會拿到那條「殘留髒連線」，讀幾 KB 就 EOF，測出 0.06 Mbps 的假結果。
    conn.setRequestProperty("Connection", "close")
    // 關鍵：禁用透明壓縮，否則 gzip 後讀到的位元組數不反映真實鏈路吞吐
    conn.setRequestProperty("Accept-Encoding", "identity")
    conn.setRequestProperty("User-Agent", "DeviceiNfo/1.12 speedtest")
    return conn
}

private fun bytesToMbps(bytes: Long, elapsedMs: Long): Float =
    (bytes * 8f) / (elapsedMs / 1000f) / 1_000_000f
