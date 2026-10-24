package com.deviceinfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.DeviceRepository
import com.deviceinfo.data.model.AppDetailState
import com.deviceinfo.data.model.BenchmarkState
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.DeviceUiState
import com.deviceinfo.data.model.GpuBenchmarkState
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.data.model.SensorReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 單向資料流：
 *   UI --Intent--> ViewModel --調用--> Repository --Flow--> UiState --渲染--> UI
 * UI 不持有可變狀態，也不直接讀系統 API。
 */
class DeviceViewModel(private val repository: DeviceRepository) : ViewModel() {

    private val _state = MutableStateFlow<DeviceUiState>(DeviceUiState.Loading)
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState.asStateFlow()

    private val _gpuBenchmarkState = MutableStateFlow<GpuBenchmarkState>(GpuBenchmarkState.Idle)
    val gpuBenchmarkState: StateFlow<GpuBenchmarkState> = _gpuBenchmarkState.asStateFlow()

    /** 應用架構詳情（LibChecker 風格），點列表某一行時載入。 */
    private val _appDetailState = MutableStateFlow<AppDetailState>(AppDetailState.Idle)
    val appDetailState: StateFlow<AppDetailState> = _appDetailState.asStateFlow()

    /** 下拉刷新中的狀態：只影響指示器，不會把整頁打回 Loading 骨架。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var liveJob: Job? = null
    private var benchmarkJob: Job? = null
    private var gpuBenchmarkJob: Job? = null
    private var maxFreqs: IntArray = IntArray(0)
    private var gpuName: String = "GPU"

    init {
        refresh()
    }

    fun refresh() {
        liveJob?.cancel()
        _state.value = DeviceUiState.Loading
        viewModelScope.launch {
            runCatching { repository.loadSnapshot() }
                .onSuccess { snapshot ->
                    repository.cacheTotalRam(snapshot.ram.totalBytes)
                    maxFreqs = buildMaxFreqs(snapshot)
                    gpuName = snapshot.gpu.renderer.takeIf { it.isNotBlank() } ?: "GPU"
                    _state.value = DeviceUiState.Ready(
                        snapshot = snapshot,
                        live = LiveSnapshot(
                            coreFreqsMhz = List(snapshot.soc.coreCount) { 0 },
                            loadRatio = 0f
                        )
                    )
                    startLiveUpdates()
                }
                .onFailure { throwable ->
                    _state.value = DeviceUiState.Error(
                        title = L10n.s(R.string.vm_load_failed),
                        detail = throwable.message ?: throwable::class.java.simpleName
                    )
                }
            _refreshing.value = false
        }
    }

    /**
     * 下拉刷新：保留目前畫面，只重採集一次快照。
     *
     * 與 [refresh] 的差別在於不清空 state——骨架屏會讓下拉手勢變得「閃一下」，
     * 而這裡用戶想要的是原資料還在、只把數字更新。
     */
    fun pullRefresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            runCatching { repository.loadSnapshot() }
                .onSuccess { snapshot ->
                    repository.cacheTotalRam(snapshot.ram.totalBytes)
                    maxFreqs = buildMaxFreqs(snapshot)
                    gpuName = snapshot.gpu.renderer.takeIf { it.isNotBlank() } ?: "GPU"
                    val current = _state.value
                    if (current is DeviceUiState.Ready) {
                        _state.value = current.copy(snapshot = snapshot)
                    } else {
                        _state.value = DeviceUiState.Ready(
                            snapshot = snapshot,
                            live = LiveSnapshot(
                                coreFreqsMhz = List(snapshot.soc.coreCount) { 0 },
                                loadRatio = 0f
                            )
                        )
                        startLiveUpdates()
                    }
                }
            _refreshing.value = false
        }
    }

    private fun startLiveUpdates() {
        liveJob = viewModelScope.launch {
            repository.observeLive(maxFreqs)
                .catch { /* 單次採樣失敗不打斷循環，下一次繼續 */ }
                .collect { live ->
                    val current = _state.value
                    if (current is DeviceUiState.Ready) {
                        _state.value = current.copy(live = live)
                    }
                }
        }
    }

    fun observeSensor(type: Int): Flow<SensorReading> = repository.observeSensor(type)

    /**
     * 應用程式頁專用的重掃：只替換 snapshot.apps，其餘資料與即時採樣保持不動，
     * 用戶按「允許讀取 / 重新掃描」時不會看到整頁重載。
     */
    fun reloadApps() {
        viewModelScope.launch {
            val summary = runCatching { repository.loadApps() }.getOrNull() ?: return@launch
            val current = _state.value
            if (current is DeviceUiState.Ready) {
                _state.value = current.copy(snapshot = current.snapshot.copy(apps = summary))
            }
        }
    }

    /** 點擊應用列表某一行：讀取該應用的架構詳情。 */
    fun openAppDetail(packageName: String) {
        _appDetailState.value = AppDetailState.Loading
        viewModelScope.launch {
            val detail = runCatching { repository.loadAppDetail(packageName) }.getOrNull()
            _appDetailState.value = if (detail != null) {
                AppDetailState.Ready(detail)
            } else {
                AppDetailState.Failed(packageName)
            }
        }
    }

    fun dismissAppDetail() {
        _appDetailState.value = AppDetailState.Idle
    }

    fun sensorUnit(type: Int): String = repository.sensorUnit(type)

    fun startBenchmark(durationMs: Long) {
        gpuBenchmarkJob?.cancel()
        _gpuBenchmarkState.value = GpuBenchmarkState.Idle
        benchmarkJob?.cancel()
        benchmarkJob = viewModelScope.launch {
            repository.runBenchmark(durationMs, maxFreqs)
                .catch { /* 測試異常時直接回到閒置，不彈全局錯誤 */ }
                .collect { _benchmarkState.value = it }
        }
    }

    fun startGpuBenchmark(durationMs: Long) {
        benchmarkJob?.cancel()
        _benchmarkState.value = BenchmarkState.Idle
        gpuBenchmarkJob?.cancel()
        gpuBenchmarkJob = viewModelScope.launch {
            repository.runGpuBenchmark(durationMs, gpuName)
                .catch { throwable ->
                    _gpuBenchmarkState.value = GpuBenchmarkState.Failed(
                        throwable.message ?: throwable::class.java.simpleName
                    )
                }
                .collect { _gpuBenchmarkState.value = it }
        }
    }

    fun dismissBenchmark() {
        benchmarkJob?.cancel()
        benchmarkJob = null
        _benchmarkState.value = BenchmarkState.Idle
    }

    fun dismissGpuBenchmark() {
        gpuBenchmarkJob?.cancel()
        gpuBenchmarkJob = null
        _gpuBenchmarkState.value = GpuBenchmarkState.Idle
    }

    private fun buildMaxFreqs(snapshot: DeviceSnapshot): IntArray {
        val size = snapshot.soc.coreCount.coerceAtLeast(1)
        val peaks = IntArray(size)
        snapshot.clusters.forEach { cluster ->
            cluster.coreIndexes.forEach { index ->
                if (index in peaks.indices) peaks[index] = cluster.maxFreqMhz
            }
        }
        return peaks
    }

    companion object {
        fun factory(repository: DeviceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DeviceViewModel(repository) }
        }
    }
}
