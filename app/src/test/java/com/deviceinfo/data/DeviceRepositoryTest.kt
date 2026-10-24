package com.deviceinfo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据层冒烟测试。
 *
 * Robolectric 环境下 /sys、/proc、getprop、PackageManager 的行为与真机不同，
 * 这恰好能验证「所有采集项都失败时依然返回完整对象」这条兜底路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun loadSnapshot_returnsCompleteObjectWithoutThrowing() = runBlocking {
        val repository = DeviceRepository(context)
        val snapshot = repository.loadSnapshot()

        assertNotNull(snapshot.identity.model)
        assertTrue(snapshot.os.sdkInt > 0)
        assertTrue(snapshot.soc.coreCount >= 1)
        assertTrue(snapshot.ram.totalBytes >= 0)
        assertTrue(snapshot.storage.dataTotalBytes >= 0)
        assertNotNull(snapshot.network.transportLabel)
        assertNotNull(snapshot.screen.refreshRateHz)
        assertTrue(snapshot.uptimeMillis >= 0)
        assertTrue(snapshot.sleepMillis >= 0)
    }

    @Test
    fun observeLive_emitsSnapshotWithinTimeout() = runBlocking {
        val repository = DeviceRepository(context)
        val snapshot = repository.loadSnapshot()
        val peaks = IntArray(snapshot.soc.coreCount.coerceAtLeast(1)) { 3000 }

        val live = withTimeout(5_000) {
            repository.observeLive(peaks, intervalMs = 100).first()
        }

        assertTrue(live.coreFreqsMhz.size >= 0)
        assertTrue(live.loadRatio in 0f..1f)
    }

    @Test
    fun batteryProbe_neverThrows() {
        val repository = DeviceRepository(context)
        val snapshot = runBlocking { repository.loadSnapshot() }
        assertNotNull(snapshot)
    }
}
