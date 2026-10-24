package com.deviceinfo.ui.components

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android 版本印章：花瓣外圈 + 深藍內圈，中央放 Google 官方版本 logo
 * （Wikimedia Commons「Android version logos」，黑色文字標識已離線轉白）。
 *
 * 三種呈現：完整徽章版（14 / 17 的官方 logo 本身就是徽章，整幅取代外框）、
 * 文字標識版（10–13 / 15 / 16，寬幅 wordmark 居中）、圖形素材版（7 / 8 / 9，
 * 上移保留下方版本號）。未知版本回退到自繪圖案（餅乾 / 派 / 時鐘…）。
 * 配色固定為官方印章色系，不跟隨 Material You。
 */
private val PetalMagenta = Color(0xFFB31F7F)
private val PetalOverlap = Color(0xFF66245A)
private val BadgeNavy = Color(0xFF0D2430)
private val BadgeGreen = Color(0xFF62DE83)
private val BadgeGreenInk = Color(0xFF2F9C55)
private const val PETALS = 20
private const val PETAL_DEPTH = 0.11f

@Composable
fun AndroidVersionBadge(
    version: String,
    modifier: Modifier = Modifier,
    apiLevel: Int = 0,
    size: Dp = 96.dp
) {
    val context = LocalContext.current
    val logo = remember(apiLevel) {
        androidLogoRes(apiLevel)?.let { res ->
            runCatching {
                BitmapFactory.decodeResource(context.resources, res).asImageBitmap()
            }.getOrNull()
        }
    }
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Android $version" }
    ) {
        val radius = this.size.minDimension / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // 官方完整徽章（14 / 17 的 logo 本身就是圓形徽章）直接整幅呈現
        if (logo != null && apiLevel in FULL_BADGE_APIS) {
            drawLogoContain(logo, cx, cy, radius * 2f, radius * 2f)
            return@Canvas
        }

        // 兩層花瓣：聯集維持亮洋紅底色，只把第二層多出來的邊緣壓深，
        // 避免整片變暗紫（早期用交集填色就是這個問題）
        val lower = rosettePath(cx, cy, radius, 0f)
        val upper = rosettePath(cx, cy, radius, 180f / PETALS)
        val silhouette = Path().apply { op(lower, upper, PathOperation.Union) }
        val rim = Path().apply { op(upper, lower, PathOperation.Difference) }
        drawPath(silhouette, PetalMagenta)
        drawPath(rim, PetalOverlap.copy(alpha = 0.32f))

        // 深藍內圈
        drawPath(rosettePath(cx, cy, radius * 0.80f, 0f), BadgeNavy)

        if (logo != null && apiLevel in WORDMARK_APIS) {
            // 寬幅文字標識（已離線轉白）：居中放入內圈，不再疊弧形文字與版本號
            drawLogoContain(logo, cx, cy, radius * 1.26f, radius * 0.62f)
        } else if (logo != null) {
            // 圖形素材（牛軋糖機器人 / Oreo 圓環 / Pie 的 P）：上移一點，下方保留版本號
            drawLogoContain(logo, cx, cy - radius * 0.10f, radius * 1.02f, radius * 1.02f)
            drawSparkles(cx, cy, radius)
            drawBadgeText(version, cx, cy, radius, drawArc = false)
        } else {
            // 未知版本：沿用自繪圖案
            drawVersionMotif(apiLevel, cx, cy, radius * 0.345f)
            drawSparkles(cx, cy, radius)
            drawBadgeText(version, cx, cy, radius)
        }
    }
}

/** 等比縮放（contain）把 logo 畫進以 (cx, cy) 為中心、maxW × maxH 的框。 */
private fun DrawScope.drawLogoContain(
    logo: androidx.compose.ui.graphics.ImageBitmap,
    cx: Float, cy: Float, maxW: Float, maxH: Float
) {
    val scale = minOf(maxW / logo.width, maxH / logo.height)
    val w = (logo.width * scale).toInt()
    val h = (logo.height * scale).toInt()
    drawImage(
        image = logo,
        dstOffset = androidx.compose.ui.unit.IntOffset((cx - w / 2f).toInt(), (cy - h / 2f).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(w, h),
        filterQuality = FilterQuality.High
    )
}

/** SDK → 官方 logo 資源（來源：Wikimedia Commons「Android version logos」，詳見 README 授權說明）。 */
private fun androidLogoRes(apiLevel: Int): Int? = when (apiLevel) {
    24, 25 -> com.deviceinfo.R.drawable.android_logo_24   // Nougat
    26, 27 -> com.deviceinfo.R.drawable.android_logo_26   // Oreo
    28 -> com.deviceinfo.R.drawable.android_logo_28       // Pie
    29 -> com.deviceinfo.R.drawable.android_logo_29
    30 -> com.deviceinfo.R.drawable.android_logo_30
    31, 32 -> com.deviceinfo.R.drawable.android_logo_31   // 12 / 12L
    33 -> com.deviceinfo.R.drawable.android_logo_33
    34 -> com.deviceinfo.R.drawable.android_logo_34
    35 -> com.deviceinfo.R.drawable.android_logo_35
    36 -> com.deviceinfo.R.drawable.android_logo_36
    37 -> com.deviceinfo.R.drawable.android_logo_37
    else -> null
}

/** logo 本身就是完整徽章的版本，整幅取代花瓣外框。 */
private val FULL_BADGE_APIS = setOf(34, 37)

/** 寬幅文字標識版本（logo 內含版本號，徽章不再重複）。 */
private val WORDMARK_APIS = setOf(29, 30, 31, 33, 35, 36)

/**
 * 花瓣路徑：每片花瓣由兩個三次貝茲構成，控制點拉到 1.04R，
 * 讓波峰保持圓潤、波谷形成尖角——這是官方徽章「花瓣」而非「鋸齒波浪」的關鍵。
 */
private fun rosettePath(cx: Float, cy: Float, radius: Float, rotationDeg: Float): Path {
    val valley = radius * (1f - PETAL_DEPTH)
    val control = radius * 1.04f
    val step = (2 * PI / PETALS).toFloat()
    val rotation = Math.toRadians(rotationDeg.toDouble()).toFloat()
    val path = Path()
    for (i in 0 until PETALS) {
        val from = -PI.toFloat() / 2f + rotation + i * step
        val to = from + step
        val startX = cx + cos(from) * valley
        val startY = cy + sin(from) * valley
        val endX = cx + cos(to) * valley
        val endY = cy + sin(to) * valley
        val c1Angle = from + step * 0.24f
        val c2Angle = to - step * 0.24f
        if (i == 0) path.moveTo(startX, startY)
        path.cubicTo(
            cx + cos(c1Angle) * control, cy + sin(c1Angle) * control,
            cx + cos(c2Angle) * control, cy + sin(c2Angle) * control,
            endX, endY
        )
    }
    path.close()
    return path
}

/** 四角小星芒，落在文字與花瓣之間的深藍區域。 */
private fun DrawScope.drawSparkles(cx: Float, cy: Float, radius: Float) {
    val distance = radius * 0.715f
    val s = radius * 0.032f
    val squeeze = 0.28f
    listOf(228f, 312f, 132f, 48f).forEach { angleDeg ->
        val angle = Math.toRadians(angleDeg.toDouble())
        val px = cx + (cos(angle) * distance).toFloat()
        val py = cy + (sin(angle) * distance).toFloat()
        val path = Path().apply {
            moveTo(px, py - s)
            lineTo(px + s * squeeze, py - s * squeeze)
            lineTo(px + s, py)
            lineTo(px + s * squeeze, py + s * squeeze)
            lineTo(px, py + s)
            lineTo(px - s * squeeze, py + s * squeeze)
            lineTo(px - s, py)
            lineTo(px - s * squeeze, py - s * squeeze)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.8f))
    }
}

/**
 * 弧形「ANDROID」與版本號。逐字旋轉比路徑文字更容易控制字距，
 * 也避開不同 Android 版本對 drawTextOnPath 的度量差異。
 */
private fun DrawScope.drawBadgeText(version: String, cx: Float, cy: Float, radius: Float, drawArc: Boolean = true) {
    val canvas = drawContext.canvas.nativeCanvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(245, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    if (drawArc) {
        val arcRadius = radius * 0.585f
        paint.textSize = radius * 0.178f
        val gap = paint.textSize * 0.16f
        val widths = LABEL_TITLE.map { paint.measureText(it.toString()) }
        val total = widths.sum() + gap * (LABEL_TITLE.length - 1)
        var advance = -total / 2f
        LABEL_TITLE.forEachIndexed { index, ch ->
            val centerAngle = (advance + widths[index] / 2f) / arcRadius
            val angleDeg = Math.toDegrees(centerAngle.toDouble()).toFloat()
            canvas.save()
            canvas.rotate(angleDeg, cx, cy)
            canvas.drawText(ch.toString(), cx, cy - arcRadius + paint.textSize * 0.35f, paint)
            canvas.restore()
            advance += widths[index] + gap
        }
    }

    // 版本號：基線壓到 0.72R，確保與中央圖案留出空隙；過長版本號等比縮小
    paint.textSize = radius * 0.34f
    val maxWidth = radius * 1.0f
    val versionWidth = paint.measureText(version)
    if (versionWidth > maxWidth) {
        paint.textSize *= maxWidth / versionWidth
    }
    canvas.drawText(version, cx, cy + radius * 0.70f, paint)
}

private const val LABEL_TITLE = "ANDROID"

// ---------------------------------------------------------------------------
// 版本專屬中央圖案
// ---------------------------------------------------------------------------

private fun DrawScope.drawVersionMotif(sdkInt: Int, cx: Float, cy: Float, r: Float) {
    when (sdkInt) {
        26, 27 -> drawOreo(cx, cy, r)
        28 -> drawPie(cx, cy, r)
        29 -> drawDigitGlyphs(cx, cy, r, ring = true)
        30 -> drawDigitGlyphs(cx, cy, r, ring = false)
        31, 32 -> drawClock(cx, cy, r)
        33 -> drawBubbles(cx, cy, r)
        34 -> drawUdcStar(cx, cy, r)
        35 -> drawIceCream(cx, cy, r)
        36 -> drawMiniFlower(cx, cy, r)
        37 -> drawCinnamonSwirl(cx, cy, r)
        else -> drawDefaultGreen(cx, cy, r)
    }
}

/** 預設：綠色圓 + 斜向脈絡（未知版本回退）。 */
private fun DrawScope.drawDefaultGreen(cx: Float, cy: Float, r: Float) {
    drawCircle(BadgeGreen, r, Offset(cx, cy))
    clipPath(
        Path().apply {
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }
    ) {
        val ink = BadgeGreenInk.copy(alpha = 0.32f)
        val stroke = r * 0.07f
        val span = r * 2.2f
        listOf(-0.78f, -0.4f, -0.02f, 0.36f, 0.74f).forEach { offset ->
            val base = cy + r * offset
            drawLine(
                color = ink,
                start = Offset(cx - span, base + span),
                end = Offset(cx + span, base - span),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** 8.x Oreo：深色餅乾 + 奶油夾心 + 餅乾邊緣的浮雕小點。 */
private fun DrawScope.drawOreo(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFF2E241E), r, Offset(cx, cy))
    // 夾心：奶油圓 + 一層極淡的內環，暗示兩片餅乾夾住
    drawCircle(Color(0xFFF6F0E2), r * 0.54f, Offset(cx, cy))
    drawCircle(
        Color(0xFFD9CFBB).copy(alpha = 0.55f),
        r * 0.54f,
        Offset(cx, cy),
        style = Stroke(width = r * 0.06f)
    )
    val dot = r * 0.075f
    for (i in 0 until 10) {
        val angle = Math.toRadians((i * 36 + 18).toDouble())
        drawCircle(
            Color(0xFF1B1410),
            dot,
            Offset(cx + (cos(angle) * r * 0.80f).toFloat(), cy + (sin(angle) * r * 0.80f).toFloat())
        )
    }
}

/** 9 Pie：花邊派皮 + 奶油餡 + 一道切痕（不用大缺角，避免讀成吃豆人）。 */
private fun DrawScope.drawPie(cx: Float, cy: Float, r: Float) {
    // 派皮：沿外緣一圈小半圓做出捏邊
    drawCircle(Color(0xFFE0A357), r, Offset(cx, cy))
    val crimp = r * 0.10f
    for (i in 0 until 12) {
        val angle = Math.toRadians((i * 30).toDouble())
        drawCircle(
            Color(0xFFF2C179),
            crimp,
            Offset(cx + (cos(angle) * r * 0.97f).toFloat(), cy + (sin(angle) * r * 0.97f).toFloat())
        )
    }
    // 內餡
    drawCircle(Color(0xFFF6D79B), r * 0.70f, Offset(cx, cy))
    // 切痕：從中心到邊緣的一道斜線
    drawLine(
        Color(0xFF9C5F27).copy(alpha = 0.85f),
        Offset(cx, cy),
        Offset(cx + (cos(Math.toRadians(52.0)) * r * 0.98f).toFloat(), cy + (sin(Math.toRadians(52.0)) * r * 0.98f).toFloat()),
        r * 0.09f,
        StrokeCap.Round
    )
    drawCircle(Color(0xFFB76E2C).copy(alpha = 0.6f), r * 0.07f, Offset(cx - r * 0.16f, cy - r * 0.14f))
}

/** 10 / 11：直接以綠色字形畫在深藍底上——10 是「1 + 0（環）」，11 是雙豎條。 */
private fun DrawScope.drawDigitGlyphs(cx: Float, cy: Float, r: Float, ring: Boolean) {
    val stroke = r * 0.26f
    val color = BadgeGreen
    if (ring) {
        // 「1」
        drawLine(
            color,
            Offset(cx - r * 0.40f, cy - r * 0.52f),
            Offset(cx - r * 0.40f, cy + r * 0.52f),
            stroke,
            StrokeCap.Round
        )
        // 「0」：環
        drawCircle(color, r * 0.32f, Offset(cx + r * 0.26f, cy), style = Stroke(stroke))
    } else {
        drawLine(
            color,
            Offset(cx - r * 0.34f, cy - r * 0.52f),
            Offset(cx - r * 0.34f, cy + r * 0.52f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(cx + r * 0.34f, cy - r * 0.52f),
            Offset(cx + r * 0.34f, cy + r * 0.52f),
            stroke,
            StrokeCap.Round
        )
    }
}

/** 12 Snow Cone：薄荷錶盤 + 指針（復活節彩蛋的時鐘）。 */
private fun DrawScope.drawClock(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFF9ADBB8), r, Offset(cx, cy))
    drawCircle(Color(0xFFFDFDF6), r * 0.64f, Offset(cx, cy))
    val hand = Color(0xFF123B33)
    val w = r * 0.11f
    // 分針（豎直向上）
    drawLine(hand, Offset(cx, cy), Offset(cx, cy - r * 0.44f), w, StrokeCap.Round)
    // 時針（指向右下）
    drawLine(hand, Offset(cx, cy), Offset(cx + r * 0.28f, cy + r * 0.26f), w, StrokeCap.Round)
    drawCircle(hand, r * 0.09f, Offset(cx, cy))
}

/** 13 Tiramisu：綠底白氣泡（一大兩小，帶高光缺口）。 */
private fun DrawScope.drawBubbles(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFF2E9E63), r, Offset(cx, cy))
    val white = Color(0xFFF6FBF4)
    drawCircle(white, r * 0.40f, Offset(cx + r * 0.02f, cy + r * 0.10f))
    drawCircle(white, r * 0.17f, Offset(cx - r * 0.36f, cy - r * 0.30f))
    drawCircle(white, r * 0.10f, Offset(cx + r * 0.34f, cy - r * 0.42f))
    // 主氣泡的高光：留一個綠點，讓它讀成「泡」而不是「圓」
    drawCircle(Color(0xFF2E9E63).copy(alpha = 0.55f), r * 0.09f, Offset(cx - r * 0.16f, cy - r * 0.02f))
}

/** 14 UDC：深藍盤 + 金色四角星芒 + 幾點碎星。 */
private fun DrawScope.drawUdcStar(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFF1B3E59), r, Offset(cx, cy))
    drawCircle(
        Color(0xFFE8B34B).copy(alpha = 0.28f),
        r * 0.88f,
        Offset(cx, cy),
        style = Stroke(width = r * 0.05f)
    )
    val gold = Color(0xFFE8B34B)
    val outer = r * 0.56f
    val waist = r * 0.14f
    val star = Path().apply {
        moveTo(cx, cy - outer)
        lineTo(cx + waist, cy - waist)
        lineTo(cx + outer, cy)
        lineTo(cx + waist, cy + waist)
        lineTo(cx, cy + outer)
        lineTo(cx - waist, cy + waist)
        lineTo(cx - outer, cy)
        lineTo(cx - waist, cy - waist)
        close()
    }
    drawPath(star, gold)
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.055f, Offset(cx - r * 0.44f, cy - r * 0.36f))
    drawCircle(Color.White.copy(alpha = 0.65f), r * 0.04f, Offset(cx + r * 0.46f, cy + r * 0.30f))
}

/** 15 Vanilla Ice Cream：奶油球 + 甜筒（筒身加高、加邊沿，避免讀成蘑菇）。 */
private fun DrawScope.drawIceCream(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFFF3E4C8), r, Offset(cx, cy))
    // 甜筒（倒三角）+ 筒口邊沿
    val coneTop = cy + r * 0.02f
    val cone = Path().apply {
        moveTo(cx - r * 0.34f, coneTop)
        lineTo(cx + r * 0.34f, coneTop)
        lineTo(cx, cy + r * 0.76f)
        close()
    }
    drawPath(cone, Color(0xFFC98A4B))
    drawLine(
        Color(0xFF9C6529).copy(alpha = 0.8f),
        Offset(cx - r * 0.36f, coneTop),
        Offset(cx + r * 0.36f, coneTop),
        r * 0.07f,
        StrokeCap.Round
    )
    // 奶油球：比筒口寬一點，明顯蓋在筒上
    drawArc(
        Color(0xFFFFFFFF),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - r * 0.42f, cy - r * 0.72f),
        size = androidx.compose.ui.geometry.Size(r * 0.84f, r * 0.74f)
    )
    // 球底一道陰影，讓球和筒分開
    drawArc(
        Color(0xFFE7D3B2),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.42f, cy - r * 0.72f),
        size = androidx.compose.ui.geometry.Size(r * 0.84f, r * 0.74f),
        style = Stroke(width = r * 0.06f)
    )
    // 筒身格紋
    drawLine(
        Color(0xFFB0763B).copy(alpha = 0.6f),
        Offset(cx - r * 0.18f, cy + r * 0.22f),
        Offset(cx + r * 0.02f, cy + r * 0.50f),
        r * 0.05f,
        StrokeCap.Round
    )
    drawLine(
        Color(0xFFB0763B).copy(alpha = 0.6f),
        Offset(cx + r * 0.18f, cy + r * 0.22f),
        Offset(cx + r * 0.04f, cy + r * 0.42f),
        r * 0.05f,
        StrokeCap.Round
    )
}

/** 16：綠底白小花（呼應官方花瓣徽章）。 */
private fun DrawScope.drawMiniFlower(cx: Float, cy: Float, r: Float) {
    drawCircle(BadgeGreen, r, Offset(cx, cy))
    val white = Color(0xFFF9FDF7)
    val orbit = r * 0.30f
    val petal = r * 0.21f
    for (i in 0 until 5) {
        val angle = Math.toRadians((i * 72.0) - 90.0)
        drawCircle(
            white,
            petal,
            Offset(cx + (cos(angle) * orbit).toFloat(), cy + (sin(angle) * orbit).toFloat())
        )
    }
    drawCircle(Color(0xFF2F9C55), r * 0.14f, Offset(cx, cy))
}

/** 17 Cinnamon Bun：奶油色麵團 + 棕色肉桂螺旋。 */
private fun DrawScope.drawCinnamonSwirl(cx: Float, cy: Float, r: Float) {
    drawCircle(Color(0xFFEBD3AC), r, Offset(cx, cy))
    val spiral = Path()
    val turns = 2.4f
    val total = turns * 2f * PI.toFloat()
    val steps = 72
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = t * total
        val radius = r * (0.10f + 0.46f * t)
        val px = cx + cos(angle) * radius
        val py = cy + sin(angle) * radius
        if (i == 0) spiral.moveTo(px, py) else spiral.lineTo(px, py)
    }
    drawPath(spiral, Color(0xFF8A5230), style = Stroke(width = r * 0.14f, cap = StrokeCap.Round))
    // 右上小小的奶油滴
    drawCircle(Color(0xFFFDF6E8), r * 0.12f, Offset(cx + r * 0.52f, cy - r * 0.50f))
}
