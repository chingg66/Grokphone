package com.deviceinfo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.deviceinfo.ui.theme.DeviceColors
import androidx.compose.ui.graphics.toArgb
import com.example.liquidglass.LiquidGlassView
import kotlin.math.roundToInt

/**
 * 玻璃的折射介質。
 *
 * 為什麼不捕獲真實背景：玻璃以 AndroidView 嵌在 Compose 樹裡，任何「把宿主畫進
 * Bitmap」的做法都有兩條死路，均已在真機復現並確認——
 *
 * 1. 在玻璃 onDraw 內同步重畫宿主：宿主 GraphicsLayer 正在錄製，
 *    重入 dispatchDraw 觸發 IllegalStateException: Recording currently in progress。
 * 2. 在繪製流程之外用軟體畫布快照宿主：Compose 的拉伸回彈
 *    （DrawStretchOverscrollModifier）與列表動畫節點只能用硬體畫布繪製，
 *    一滾動就 IllegalArgumentException: Software rendering doesn't support drawRenderNode。
 *
 * 因此改為提供一張與頁面根背景同源的漸變作為折射介質：庫的 SDF 折射、色散、
 * 邊緣高光、自適應染色全部照常跑 GPU 管線，只是背景內容固定為環境光而不是
 * 滾動中的卡片。在這種低對比漸變界面上，觀感與真折射幾乎無差別，
 * 而開銷是「每個尺寸×主題渲染一次，之後全程複用」。
 */
internal class GlassBackdrop private constructor(
    private val colors: DeviceColors
) {

    private var cached: Bitmap? = null
    private var cachedWidth = 0
    private var cachedHeight = 0

    companion object {
        /**
         * 接到玻璃上。回呼只回快取點陣圖，絕不畫任何視圖，因此不可能重入。
         * 顏色在組合期捕獲，主題切換時 AndroidView 重建 factory 會拿到新配色。
         */
        fun install(glass: LiquidGlassView, colors: DeviceColors) {
            glass.enableDynamicBackground = false
            val backdrop = GlassBackdrop(colors)
            glass.setCustomBackdropCapture { bounds -> backdrop.acquire(bounds) }
        }
    }

    private fun acquire(bounds: RectF): Bitmap? {
        val w = bounds.width().roundToInt().coerceAtLeast(1)
        val h = bounds.height().roundToInt().coerceAtLeast(1)
        val base = ensure(w, h)
        // 管線會回收回呼返回的 Bitmap，每次回一份新的裁切副本
        return Bitmap.createBitmap(base, 0, 0, w, h)
    }

    private fun ensure(w: Int, h: Int): Bitmap {
        cached?.let { if (cachedWidth == w && cachedHeight == h && !it.isRecycled) return it }
        cached?.recycle()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val width = w.toFloat()
        val height = h.toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                colors.BackgroundTop.toArgb(),
                colors.Background.toArgb(),
                colors.BackgroundBottom.toArgb()
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)

        // 左上輝光：與根背景的 radial 光暈方向一致，讓折射有明暗可依
        val glowRadius = width * 0.9f
        paint.shader = RadialGradient(
            width * 0.15f, -height * 0.6f, glowRadius,
            intArrayOf(colors.Accent.copy(alpha = 0.10f).toArgb(), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)

        cached = bitmap
        cachedWidth = w
        cachedHeight = h
        return bitmap
    }
}
