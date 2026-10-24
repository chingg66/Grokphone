package com.deviceinfo.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 下拉刷新句柄。
 *
 * 八個頁面都共用同一個列表容器（[com.deviceinfo.ui.InfoScreen]），
 * 所以刷新能力用 CompositionLocal 往下傳，不必讓每個 Screen 都多帶兩個參數。
 * 為 null 表示當前環境不提供下拉刷新（例如預覽、單頁測試）。
 */
data class PullRefreshHandler(
    val refreshing: Boolean,
    val onRefresh: () -> Unit
)

val LocalPullRefresh = staticCompositionLocalOf<PullRefreshHandler?> { null }
