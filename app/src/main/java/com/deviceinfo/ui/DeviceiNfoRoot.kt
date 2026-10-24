package com.deviceinfo.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.deviceinfo.R
import com.deviceinfo.data.model.BenchmarkState
import com.deviceinfo.data.model.DeviceSnapshot
import com.deviceinfo.data.model.DeviceUiState
import com.deviceinfo.data.model.LiveSnapshot
import com.deviceinfo.ui.components.ChangelogDialog
import com.deviceinfo.ui.components.DeviceInfoCard
import com.deviceinfo.ui.components.GlassPillButton
import com.deviceinfo.ui.components.GlassTabBar
import com.deviceinfo.ui.components.LocalPullRefresh
import com.deviceinfo.ui.components.PullRefreshHandler
import com.deviceinfo.ui.navigation.DeviceTab
import com.deviceinfo.ui.screens.AppsScreen
import com.deviceinfo.ui.screens.BatteryScreen
import com.deviceinfo.ui.screens.CameraScreen
import com.deviceinfo.ui.screens.DashboardScreen
import com.deviceinfo.ui.screens.HardwareScreen
import com.deviceinfo.ui.screens.NetworkScreen
import com.deviceinfo.ui.screens.SensorsScreen
import com.deviceinfo.ui.screens.SystemScreen
import androidx.compose.ui.text.style.TextOverflow
import com.deviceinfo.ui.screens.deviceDisplayName
import com.deviceinfo.ui.components.SettingsDialog
import com.deviceinfo.ui.theme.AccentStyle
import com.deviceinfo.ui.theme.Appearance
import com.deviceinfo.ui.theme.DeviceColors
import com.deviceinfo.ui.theme.DevicePalette

/** 頂部懸浮區高度：標題行 + 間距 + 玻璃 TabBar。內容滾動時會從它下方穿過。 */
private val TitleRowHeight = 64.dp
private val TabBarHeight = 58.dp
private val OverlayGap = 8.dp
private val PageIndicatorHeight = 10.dp
private val IndicatorGap = 4.dp
private val HeaderBlockHeight =
    TitleRowHeight + OverlayGap + TabBarHeight + IndicatorGap + PageIndicatorHeight
private val ContentTopGap = 12.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceiNfoRoot(
    viewModel: DeviceViewModel,
    appearance: Appearance = Appearance(),
    onAppearanceChange: (Appearance) -> Unit = {},
    onReopenOnboarding: () -> Unit = {},
    settings: com.deviceinfo.core.AppSettings = com.deviceinfo.core.AppSettings(),
    onSettingsChange: (com.deviceinfo.core.AppSettings) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val gpuBenchmarkState by viewModel.gpuBenchmarkState.collectAsStateWithLifecycle()
    val appDetailState by viewModel.appDetailState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var showChangelog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { DeviceTab.entries.size })
    val selectedTab by remember {
        derivedStateOf { DeviceTab.entries[pagerState.currentPage] }
    }
    val scope = rememberCoroutineScope()

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentTopPadding = statusBarPadding + HeaderBlockHeight + ContentTopGap
    // 組合期取一次語義配色，供 draw 作用域使用（DrawScope 裡不能讀 CompositionLocal）
    val ambientColors = DevicePalette

    CompositionLocalProvider(
        // 八個頁面共用同一個下拉刷新入口；刷新時只更新數字，不閃骨架屏
        LocalPullRefresh provides PullRefreshHandler(
            refreshing = refreshing,
            onRefresh = viewModel::pullRefresh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientColors.Background)
                // 流暢度關鍵：漸層 Brush 只在尺寸變化時重建一次，
                // 每一幀滑動都直接複用快取結果，不再每幀分配 4 個漸層物件
                .drawWithCache {
                    val brushes = AmbientBackgroundBrushes(ambientColors, size)
                    onDrawBehind { brushes.draw(this) }
                }
        ) {
            when (val current = state) {
                is DeviceUiState.Loading -> LoadingState(contentTopPadding)

                is DeviceUiState.Error -> ErrorState(
                    title = current.title,
                    detail = current.detail,
                    topPadding = contentTopPadding,
                    onRetry = viewModel::refresh
                )

            is DeviceUiState.Ready -> {
                // 左右滑動切換面板：與頂部 TabBar / 分頁指示器雙向同步
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> DeviceTab.entries[page].name },
                    beyondViewportPageCount = 1,
                    pageSpacing = 14.dp,
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                    userScrollEnabled = true
                ) { page ->
                    val pageOffset = (page - pagerState.currentPage) + pagerState.currentPageOffsetFraction
                    val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 1f - absOffset * 0.25f
                                scaleX = 1f - absOffset * 0.06f
                                scaleY = 1f - absOffset * 0.06f
                                translationX = size.width * pageOffset * 0.04f
                                shadowElevation = absOffset * 6.dp.toPx()
                            }
                    ) {
                        when (DeviceTab.entries[page]) {
                            DeviceTab.Dashboard -> DashboardScreen(
                                snapshot = current.snapshot,
                                live = current.live,
                                topPadding = contentTopPadding,
                                benchmarkState = benchmarkState,
                                gpuBenchmarkState = gpuBenchmarkState,
                                onStartBenchmark = viewModel::startBenchmark,
                                onStartGpuBenchmark = viewModel::startGpuBenchmark,
                                onDismissBenchmark = viewModel::dismissBenchmark,
                                onDismissGpuBenchmark = viewModel::dismissGpuBenchmark,
                                onOpenTab = { tab ->
                                    scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                                }
                            )

                            DeviceTab.Hardware -> HardwareScreen(
                                snapshot = current.snapshot,
                                live = current.live,
                                topPadding = contentTopPadding
                            )

                            DeviceTab.System -> SystemScreen(
                                snapshot = current.snapshot,
                                topPadding = contentTopPadding
                            )

                            DeviceTab.Battery -> BatteryScreen(
                                snapshot = current.snapshot,
                                live = current.live,
                                topPadding = contentTopPadding,
                                settings = settings
                            )

                            DeviceTab.Network -> NetworkScreen(
                                snapshot = current.snapshot,
                                topPadding = contentTopPadding,
                                onRefresh = viewModel::refresh
                            )

                            DeviceTab.Apps -> AppsScreen(
                                snapshot = current.snapshot,
                                topPadding = contentTopPadding,
                                onReloadApps = viewModel::reloadApps,
                                appDetailState = appDetailState,
                                onOpenAppDetail = viewModel::openAppDetail,
                                onDismissAppDetail = viewModel::dismissAppDetail
                            )

                            DeviceTab.Camera -> CameraScreen(
                                snapshot = current.snapshot,
                                topPadding = contentTopPadding
                            )

                            DeviceTab.Sensors -> SensorsScreen(
                                snapshot = current.snapshot,
                                topPadding = contentTopPadding,
                                observeSensor = viewModel::observeSensor,
                                sensorUnit = viewModel::sensorUnit
                            )
                        }
                    }
                }
            }
        }

        TopOverlay(
            deviceName = (state as? DeviceUiState.Ready)?.snapshot?.identity?.let { identity ->
                deviceDisplayName(identity)
            },
            selectedTab = selectedTab,
            statusBarPadding = statusBarPadding,
            appearance = appearance,
            onShowSettings = { showSettings = true },
            onSelectTab = { tab ->
                scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            }
        )

        // 預見性返回（自製）：API<33 沒有系統預覽，這裡在第一頁的左緣
        // 提供拖曳箭頭預覽；API 33+ 走 manifest 的 enableOnBackInvokedCallback，
        // 兩套並存會重複觸發，所以自製版只在舊系統啟用。
        if (android.os.Build.VERSION.SDK_INT < 33 && pagerState.currentPage == 0) {
            PredictiveBackEdge(
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

            if (showChangelog) {
                ChangelogDialog(
                    onDismissRequest = { showChangelog = false },
                    onOpenOnboarding = {
                        showChangelog = false
                        onReopenOnboarding()
                    }
                )
            }

            if (showSettings) {
                SettingsDialog(
                    appearance = appearance,
                    onAppearanceChange = onAppearanceChange,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onOpenChangelog = {
                        showSettings = false
                        showChangelog = true
                    },
                    onReopenOnboarding = {
                        showSettings = false
                        onReopenOnboarding()
                    },
                    onDismiss = { showSettings = false }
                )
            }
        }
    }
}

@Composable
private fun TopOverlay(
    deviceName: String?,
    selectedTab: DeviceTab,
    statusBarPadding: Dp,
    appearance: Appearance,
    onShowSettings: () -> Unit,
    onSelectTab: (DeviceTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TitleRowHeight)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = DevicePalette.TextPrimary
                )
                Text(
                    text = deviceName ?: stringResource(R.string.root_loading_model),
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 設定：外觀 / 溫度單位 / 電池校正 / 更新日誌都收在裡面
            IconButton(
                onClick = onShowSettings,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = if (appearance.accent == AccentStyle.Green) {
                        DevicePalette.TextSecondary
                    } else {
                        DevicePalette.Accent
                    }
                )
            }
        }

        GlassTabBar(
            tabs = DeviceTab.entries,
            selected = selectedTab,
            onSelect = onSelectTab,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .height(TabBarHeight)
        )
        Spacer(Modifier.height(IndicatorGap))
        PagerIndicator(
            tabs = DeviceTab.entries,
            selected = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .height(PageIndicatorHeight)
        )
    }
}

/**
 * 分頁指示器：Material 3 風格的膠囊小點。
 *
 * 當前頁是「加長的膠囊」而不是「放大的圓點」—— 八個點排成一行時，
 * 放大圓點會讓整排基線看起來忽高忽低，橫向膠囊在視覺上更穩、也更容易看出方向。
 */
@Composable
private fun PagerIndicator(
    tabs: List<DeviceTab>,
    selected: DeviceTab,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val width by animateDpAsState(
                targetValue = if (isSelected) 18.dp else 5.dp,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "indicatorWidth"
            )
            val height by animateDpAsState(
                targetValue = if (isSelected) 6.dp else 5.dp,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "indicatorHeight"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(width)
                    .height(height)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) DevicePalette.Accent else DevicePalette.Outline.copy(alpha = 0.45f)
                    )
            )
        }
    }
}

@Composable
private fun LoadingState(topPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = DevicePalette.Accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.width(34.dp).height(34.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.root_collecting),
            style = MaterialTheme.typography.bodyMedium,
            color = DevicePalette.TextSecondary
        )
    }
}

@Composable
private fun ErrorState(
    title: String,
    detail: String?,
    topPadding: Dp,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 18.dp, end = 18.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        DeviceInfoCard(title = stringResource(R.string.root_error)) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DevicePalette.Danger
                )
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassPillButton(text = stringResource(R.string.root_retry), onClick = onRetry)
                }
            }
        }
    }
}

/**
 * 統一的列表容器：所有資訊頁共用同一套內邊距與間距，
 * 滾動內容會從頂部玻璃導航下方穿過。
 *
 * 若上層提供了 [LocalPullRefresh]（正常 App 內一定有），
 * 這裡自動掛上 Material 3 的下拉刷新：八個頁面一次到位，不用各自實作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    topPadding: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 14.dp,
    content: LazyListScope.() -> Unit
) {
    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("content_list"),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 28.dp,
                start = horizontalPadding,
                end = horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }

    val refresh = LocalPullRefresh.current
    if (refresh == null) {
        listContent()
        return
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refresh.refreshing,
        onRefresh = refresh.onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            // 預設指示器會貼在 Box 頂端（被玻璃導覽列蓋住），這裡往下挪到內文起點
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refresh.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding)
            )
        }
    ) {
        listContent()
    }
}

/** 響應式列數：手機 2 列、摺疊屏/平板 3–4 列。 */
@Composable
fun rememberResponsiveColumns(compact: Int = 2, medium: Int = 3, expanded: Int = 4): Int {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> expanded
        widthDp >= 600 -> medium
        else -> compact
    }
}

/**
 * 自製預見性返回：只在第一頁（pager 已無法再向右滑）掛一條 20dp 的左緣熱區。
 * 拖曳時左側浮出箭頭膠囊跟手移動，進度超過閾值變色提示；鬆手超過閾值就 finish()。
 * API 33+ 由系統（enableOnBackInvokedCallback）提供官方預覽，這裡補齊舊系統。
 */
@Composable
private fun PredictiveBackEdge(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val triggerPx = with(density) { 72.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var dragX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX = ((dragX ?: 0f) + amount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        val passed = (dragX ?: 0f) >= triggerPx
                        dragX = null
                        if (passed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            (context as? android.app.Activity)?.finish()
                        }
                    },
                    onDragCancel = { dragX = null }
                )
            }
    )

    val x = dragX ?: return
    val progress = (x / triggerPx).coerceIn(0f, 1f)
    val ready = progress >= 1f
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = -24.dp.toPx() + (x * 0.45f).coerceAtMost(triggerPx * 0.9f)
                    alpha = 0.25f + progress * 0.75f
                    scaleX = 0.8f + progress * 0.2f
                    scaleY = 0.8f + progress * 0.2f
                }
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (ready) DevicePalette.Accent.copy(alpha = 0.85f)
                    else DevicePalette.SurfaceHigher.copy(alpha = 0.92f)
                )
                .border(
                    1.dp,
                    if (ready) DevicePalette.Accent else DevicePalette.Outline.copy(alpha = 0.6f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back_preview),
                tint = if (ready) DevicePalette.Background else DevicePalette.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 背景不是純裝飾：液態玻璃的折射需要背景裡有明暗變化才看得出來。
 * 所以這裡刻意放了三團低飽和光暈，讓玻璃邊緣有東西可以「折射」。
 *
 * Brush 在 [androidx.compose.ui.draw.drawWithCache] 的快取期建立（尺寸不變就不重建），
 * [draw] 每幀只做便宜的重放——這是滑動流暢度優化的一部分。
 */
private class AmbientBackgroundBrushes(
    colors: DeviceColors,
    size: androidx.compose.ui.geometry.Size
) {
    private val width = size.width
    private val height = size.height

    private val vertical = Brush.verticalGradient(
        colors = listOf(colors.BackgroundTop, colors.Background, colors.BackgroundBottom)
    )
    private val glowTopLeft = Brush.radialGradient(
        colors = listOf(colors.Accent.copy(alpha = 0.055f), Color.Transparent),
        center = Offset(width * 0.18f, height * 0.06f),
        radius = width * 0.75f
    )
    private val glowMidRight = Brush.radialGradient(
        colors = listOf(colors.AccentDim.copy(alpha = 0.045f), Color.Transparent),
        center = Offset(width * 0.92f, height * 0.42f),
        radius = width * 0.6f
    )
    private val glowBottom = Brush.radialGradient(
        colors = listOf(colors.Accent.copy(alpha = 0.035f), Color.Transparent),
        center = Offset(width * 0.5f, height * 0.95f),
        radius = width * 0.8f
    )

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        scope.drawRect(brush = vertical)
        scope.drawCircle(glowTopLeft, radius = width * 0.75f, center = Offset(width * 0.18f, height * 0.06f))
        scope.drawCircle(glowMidRight, radius = width * 0.6f, center = Offset(width * 0.92f, height * 0.42f))
        scope.drawCircle(glowBottom, radius = width * 0.8f, center = Offset(width * 0.5f, height * 0.95f))
    }
}
