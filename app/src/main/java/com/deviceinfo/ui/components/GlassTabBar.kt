package com.deviceinfo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.deviceinfo.ui.navigation.DeviceTab
import com.deviceinfo.ui.theme.DevicePalette

private val BarHeight = 58.dp
private val BarPadding = 5.dp

/**
 * 懸浮式頂部導航。外層是液態玻璃（SDF 折射 / 物理色散 / 重力感應高光由玻璃庫提供），
 * 內容層是 8 個等分標籤。
 *
 * 互動：除了點擊，整條導航支援**按住橫拖**——選中膠囊跟手指移動並加寬壓扁，
 * 鬆手吸附到最近的一格並換頁（酷安式拖動切換）。拖動手勢掛在獨立容器上，
 * 不影響各 tab 的點擊。
 *
 * 玻璃層在下、內容在上，原因見 [GlassPanel]：LiquidGlassView 會無條件消費觸摸事件。
 */
@Composable
fun GlassTabBar(
    tabs: List<DeviceTab>,
    selected: DeviceTab,
    onSelect: (DeviceTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight),
        cornerRadius = 999.dp,
        tintColor = DevicePalette.GlassTint,
        dispersion = 0.16f,
        dynamicBackground = true
    ) {
        val sheen = DevicePalette.TextPrimary
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .padding(horizontal = BarPadding, vertical = BarPadding)
                .testTag("tab_list")
                .drawBehind { drawGlossReflection(sheen) }
        ) {
            val slotWidth = maxWidth / tabs.size
            val density = LocalDensity.current
            val slotWidthPx = with(density) { slotWidth.toPx() }
            val totalWidthPx = slotWidthPx * tabs.size
            val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
            // 手勢 lambda 只在 key 變化時重組，捕獲最新值要走 rememberUpdatedState
            val currentIndex by rememberUpdatedState(selectedIndex)
            val currentTabs by rememberUpdatedState(tabs)
            val currentOnSelect by rememberUpdatedState(onSelect)

            // 拖動換頁：拖動時膠囊中心跟手指走，鬆手吸附到最近一格。
            // 拖動中選中色膠囊加寬 18%、高度壓扁 6%，模擬液體被拉扯的形變。
            var dragCenterPx by remember { mutableStateOf<Float?>(null) }
            val dragging = dragCenterPx != null
            val pillCenterPx by animateFloatAsState(
                targetValue = dragCenterPx ?: (slotWidthPx * (selectedIndex + 0.5f)),
                animationSpec = if (dragging) snap() else tween(280, easing = FastOutSlowInEasing),
                label = "tabPillCenter"
            )
            val widthStretch by animateFloatAsState(
                targetValue = if (dragging) 1.18f else 1f,
                animationSpec = tween(160),
                label = "tabPillStretch"
            )
            val haptic = LocalHapticFeedback.current

            val pillWidthPx = slotWidthPx * widthStretch
            val pillLeftPx = (pillCenterPx - pillWidthPx / 2f)
                .coerceIn(0f, totalWidthPx - pillWidthPx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabs.size, slotWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragCenterPx = offset.x.coerceIn(0f, totalWidthPx)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragCenterPx = ((dragCenterPx ?: change.position.x) + dragAmount)
                                    .coerceIn(0f, totalWidthPx)
                            },
                            onDragEnd = {
                                val center = dragCenterPx
                                dragCenterPx = null
                                if (center != null) {
                                    val target = (center / slotWidthPx).toInt()
                                        .coerceIn(0, currentTabs.size - 1)
                                    if (target != currentIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentOnSelect(currentTabs[target])
                                    }
                                }
                            },
                            onDragCancel = { dragCenterPx = null }
                        )
                    }
            ) {
                // 滑動膠囊：墊在圖標與文字下面
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillLeftPx.roundToInt(), 0) }
                        .width(with(density) { pillWidthPx.toDp() })
                        .fillMaxHeight()
                        .graphicsLayer(scaleY = if (dragging) 0.94f else 1f)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(DevicePalette.Accent.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = DevicePalette.Accent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        TabItem(
                            tab = tab,
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            slotWidth = slotWidth,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: DeviceTab,
    selected: Boolean,
    onClick: () -> Unit,
    slotWidth: Dp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor by animateColorAsState(
        targetValue = if (selected) DevicePalette.Accent else DevicePalette.TextSecondary,
        animationSpec = tween(220),
        label = "tabContent"
    )
    val pressTint by animateColorAsState(
        targetValue = if (isPressed) DevicePalette.TextPrimary.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(140),
        label = "tabPressTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "tabPressScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(999.dp))
            .background(pressTint)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .testTag("tab:" + tab.name)
            .padding(vertical = 4.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tabLabel = stringResource(tab.labelRes)
        Icon(
            imageVector = tab.icon,
            contentDescription = tabLabel,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(3.dp))
        // 各語言標籤長度差很大（法語「Tableau de bord」、俄語「Приложения」），
        // 固定 9sp 會被 ellipsis 截成「Dashbo…」。這裡量一次實際文字寬度，
        // 放不下就按比例縮小，下限 6.5sp，保證整詞完整顯示。
        val baseStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // 再乘 0.95 留安全餘量：測量值是整數像素，理論「剛好等於」可用寬度時
        // 實際排版會多出 1–2px，最後一個字母照樣被切掉半個。
        val availPx = with(density) { (slotWidth - 4.dp).toPx() * 0.95f }.toInt().coerceAtLeast(1)
        val labelSp = remember(tabLabel, availPx) {
            val base = 9f
            val width = measurer.measure(
                text = tabLabel,
                style = baseStyle.copy(fontSize = base.sp),
                softWrap = false,
                maxLines = 1
            ).size.width
            (if (width <= availPx) base else base * availPx / width.toFloat())
                .coerceIn(6.5f, base)
                .sp
        }
        Text(
            text = tabLabel,
            style = baseStyle.copy(fontSize = labelSp, lineHeight = (labelSp.value * 1.22f).sp),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center
        )
    }
}

/** 玻璃導覽列頂部的高光反射：模擬環境光在玻璃上緣形成的白色光帶。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlossReflection(sheen: Color) {
    // 高光只留上緣一條，透明度也比上一版收斂 —— 太強會讓頂部圖標發灰、文字發虛。
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                sheen.copy(alpha = 0.13f),
                sheen.copy(alpha = 0.04f),
                Color.Transparent
            ),
            startY = 0f,
            endY = size.height * 0.5f
        ),
        size = Size(size.width, size.height * 0.5f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                sheen.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.22f, size.height * 0.12f),
            radius = size.width * 0.38f
        ),
        radius = size.width * 0.38f,
        center = Offset(size.width * 0.22f, size.height * 0.12f)
    )
}
