package com.deviceinfo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.core.Fmt
import com.deviceinfo.ui.theme.DevicePalette

// ---------------------------------------------------------------------------
// 卡片容器
// ---------------------------------------------------------------------------

/**
 * 資訊卡片。全應用統一用這一層外殼：圓角、1dp 描邊、微漸變，
 * 保證幾十個卡片在視覺上是同一套材質。
 */
@Composable
fun DeviceInfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // 卡片統一使用輕量玻璃表面：低階裝置不建立原生折射 View，
    // 避免大量資訊卡同時存在時造成額外 GPU / 記憶體負擔。
    GlassFallback(
        modifier = modifier,
        cornerRadius = 22.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding)
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (titleIcon != null) {
                        Icon(
                            imageVector = titleIcon,
                            contentDescription = null,
                            tint = DevicePalette.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = DevicePalette.Accent,
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// 鍵值展示
// ---------------------------------------------------------------------------

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DevicePalette.TextPrimary,
    labelColor: Color = DevicePalette.TextTertiary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

/**
 * 兩列鍵值網格。寬屏下自動變四列，是響應式布局最基礎的落點。
 */
@Composable
fun KeyValueGrid(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    valueColor: Color = DevicePalette.TextPrimary
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        items.chunked(columns).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = DevicePalette.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = valueColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 小標籤
// ---------------------------------------------------------------------------

@Composable
fun PillTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DevicePalette.Accent
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DevicePalette.Accent.copy(alpha = 0.16f))
            .border(1.dp, DevicePalette.Accent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = DevicePalette.Accent
        )
    }
}

// ---------------------------------------------------------------------------
// 資料可視化
// ---------------------------------------------------------------------------

/**
 * 平滑折線 + 漸變填充。CPU 負載曲線用它，風格對齊系統監視器的波形圖。
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = DevicePalette.Accent,
    strokeWidth: Dp = 2.dp,
    showBaseline: Boolean = true
) {
    Canvas(modifier = modifier) {
        if (values.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        val stepX = size.width / (values.size - 1)
        val path = Path()
        var prevX = 0f
        var prevY = size.height * (1f - values[0].coerceIn(0f, 1f))
        path.moveTo(prevX, prevY)

        for (index in 1 until values.size) {
            val x = index * stepX
            val y = size.height * (1f - values[index].coerceIn(0f, 1f))
            val midX = (prevX + x) / 2f
            path.cubicTo(midX, prevY, midX, y, x, y)
            prevX = x
            prevY = y
        }

        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        if (showBaseline) {
            drawLine(
                color = Color(0x14FFFFFF),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f
            )
        }
    }
}

/** 環形進度。記憶體 / 儲存佔用用它的表現最直觀。 */
@Composable
fun RingGauge(
    ratio: Float,
    centerText: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    trackColor: Color = Color(0xFF232E28),
    progressColor: Color = DevicePalette.Accent,
    strokeWidth: Dp = 7.dp
) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "ring"
    )
    Box(
        modifier = modifier
            .size(diameter)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            // 無論 Canvas 被外部約束拉伸成什麼比例，一律取短邊畫正圓並居中
            val d = kotlin.math.min(size.width, size.height) - stroke
            val left = (size.width - d) / 2f
            val top = (size.height - d) / 2f
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(left + stroke / 2f, top + stroke / 2f),
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(left + stroke / 2f, top + stroke / 2f),
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.titleMedium,
            color = DevicePalette.TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 核心頻率網格，列優先排列（左列 CPU0..3，右列 CPU4..7），
 * 和系統監視工具的習慣保持一致。
 */
@Composable
fun CoreFrequencyGrid(
    freqs: List<Int>,
    maxFreqs: List<Int>,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    if (freqs.isEmpty()) return
    val rows = (freqs.size + columns - 1) / columns

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(columns) { column ->
                    val index = column * rows + row
                    val freq = freqs.getOrNull(index)
                    Box(modifier = Modifier.weight(1f).padding(vertical = 5.dp)) {
                        if (freq != null) {
                            Column {
                                Text(
                                    text = "CPU$index",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DevicePalette.TextTertiary
                                )
                                Text(
                                    text = Fmt.mhz(freq),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DevicePalette.Accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 橫向佔比條，用於記憶體/儲存的次級展示。 */
@Composable
fun RatioBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    color: Color = DevicePalette.Accent,
    trackColor: Color = Color(0xFF232E28),
    height: Dp = 6.dp
) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "ratioBar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
    }
}

// ---------------------------------------------------------------------------
// 交互行
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = DevicePalette.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/**
 * 可展開的資訊行。感測器列表用它：收起時顯示名稱與型號，展開後展示實時讀數。
 */
@Composable
fun ExpandableInfoRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color = DevicePalette.Accent,
    detail: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DevicePalette.SurfaceHigh, DevicePalette.Surface)))
            .border(1.dp, DevicePalette.OutlineSoft, shape)
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DevicePalette.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.cmp_collapse) else stringResource(R.string.cmp_expand),
                tint = DevicePalette.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                detail()
            }
        }
    }
}

/** 儀錶板裡的工具入口方塊。 */
@Composable
fun ToolTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(DevicePalette.SurfaceHigh)
            .border(1.dp, DevicePalette.OutlineSoft, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DevicePalette.Accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DevicePalette.TextSecondary,
            maxLines = 1
        )
    }
}

/** 大字數值 + 小字單位，儀錶板摘要用。 */
@Composable
fun MetricValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DevicePalette.Accent,
    labelColor: Color = DevicePalette.TextTertiary
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 簡單的展開狀態記憶器，避免每個調用點重複寫 remember。 */
@Composable
fun rememberExpandedState(): Pair<Boolean, () -> Unit> {
    var expanded by remember { mutableStateOf(false) }
    return expanded to { expanded = !expanded }
}
