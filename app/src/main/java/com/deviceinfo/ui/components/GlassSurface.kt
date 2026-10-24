package com.deviceinfo.ui.components

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.deviceinfo.R
import com.deviceinfo.core.L10n
import com.deviceinfo.ui.theme.DevicePalette
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * 液態玻璃的啟用條件。
 *
 * 三條硬約束，缺一不可：
 * 1. API 33+：庫的 SDF 折射管線走 AGSL + RenderEffect，低版本會退到 CPU 位圖管線（慢且耗電）
 * 2. arm 架構：AAR 只打包了 arm64-v8a / armeabi-v7a 的 libnativegauss.so，
 *    x86 模擬器上 loadLibrary 會拋 Error，直接崩
 * 3. native 載入成功：庫的 NativeGauss 在 object 初始化裡 loadLibrary，失敗即 Error
 *
 * 任一不滿足就整體降級為 Compose 繪製的磨砂表面，視覺接近但不做折射。
 */
object GlassSupport {

    private val nativeLoaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("nativegauss")
            true
        }.getOrDefault(false)
    }

    private val isArm: Boolean by lazy {
        Build.SUPPORTED_ABIS?.any { it.startsWith("arm") } == true
    }

    val apiReady: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val enabled: Boolean get() = apiReady && isArm && nativeLoaded

    /** 供設定頁展示當前降級原因。 */
    fun reason(): String? = when {
        !apiReady -> L10n.s(R.string.gs_reason_api)
        !isArm -> L10n.s(R.string.gs_reason_arch, Build.SUPPORTED_ABIS?.firstOrNull() ?: L10n.s(R.string.gs_unknown_abi))
        !nativeLoaded -> L10n.s(R.string.gs_reason_native)
        else -> null
    }
}

/**
 * 液態玻璃面板。
 *
 * 使用方式遵循庫的設計：玻璃作為**背景層**繪製，內容疊在它上面。
 * 這樣既保留了 SDF 折射與邊緣高光，又不影響內容的點擊（LiquidGlassView 會無條件消費觸摸事件）。
 *
 * 背景折射不能讓庫直接捕獲 Compose 宿主：玻璃嵌在 Compose 樹裡，宿主的
 * GraphicsLayer 在繪製期間處於錄製狀態，庫在玻璃 onDraw 裡同步重畫宿主會
 * 對同一個圖層重複 beginRecording 而崩潰（真機已復現）。因此背景捕獲改走
 * [GlassBackdropCache]：在繪製流程之外定時快取宿主內容，draw 時只讀快取。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tintColor: Color = Color(0x26FFFFFF),
    dispersion: Float = 0.12f,
    sensorHighlight: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicBackground: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (!GlassSupport.enabled) {
        GlassFallback(modifier = modifier, cornerRadius = cornerRadius, content = content)
        return
    }

    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val tintArgb = tintColor.toArgb()
    val palette = DevicePalette

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                LiquidGlassView(context).apply {
                    // 觸摸交給上層內容，玻璃只負責外觀
                    enablePressEffect = false
                    isClickable = false
                    isFocusable = false

                    this.cornerRadius = radiusPx
                    material = GlassMaterial.REGULAR
                    glassTint = tintArgb
                    dispersionStrength = dispersion
                    enableSensorHighlight = sensorHighlight
                    enableAdaptiveTint = true
                    blurAmount = 0.09f
                    saturation = 150f
                    refractionHeight = 110f
                    bevelWidth = 24f
                    // 折射介質走合成漸變，避免捕獲宿主引發的重入與軟體畫布崩潰
                    GlassBackdrop.install(this, palette)
                }
            },
            update = { view ->
                view.cornerRadius = radiusPx
                view.glassTint = tintArgb
                view.dispersionStrength = dispersion
            }
        )
        // 淺色主題：庫的亮度自適應會強制壓暗玻璃，這裡補一層白霧拉回亮度
        if (palette.GlassVeil.alpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(palette.GlassVeil)
            )
        }
        content()
    }
}

/** 降級版玻璃：豎向漸變 + 頂部高光描邊，和真玻璃的觀感儘量拉齊。 */
@Composable
fun GlassFallback(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val sheen = DevicePalette.TextPrimary
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        sheen.copy(alpha = 0.12f),
                        sheen.copy(alpha = 0.06f),
                        sheen.copy(alpha = 0.03f)
                    )
                )
            )
            .background(DevicePalette.CardTopSheen)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        sheen.copy(alpha = 0.22f),
                        sheen.copy(alpha = 0.05f),
                        sheen.copy(alpha = 0.02f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * 液態玻璃對話框。
 *
 * 這裡不用 LiquidGlassView：Dialog 是獨立 Window，玻璃無法捕獲宿主 Activity 的內容。
 * 改用系統能力 —— API 31+ 的 Window.setBackgroundBlurRadius 對**整個螢幕背後**做實時模糊，
 * 這才是對話框場景下真正正確的「玻璃」，而且零額外開銷。
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    confirmLabel: String = stringResource(R.string.gs_dialog_confirm),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        val view = LocalView.current
        val density = LocalDensity.current
        val blurRadiusPx = with(density) { 26.dp.roundToPx() }

        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.42f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(blurRadiusPx)
            }
        }

        val shape = RoundedCornerShape(30.dp)
        val dialogBgTop = DevicePalette.SurfaceHigher.copy(alpha = 0.95f)
        val dialogBgBottom = DevicePalette.SurfaceHigh.copy(alpha = 0.95f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(dialogBgTop, dialogBgBottom)
                    )
                )
                .border(1.dp, DevicePalette.Outline.copy(alpha = 0.6f), shape)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = DevicePalette.TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DevicePalette.TextTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                content()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    GlassPillButton(
                        text = confirmLabel,
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("dialog_confirm")
                    )
                }
            }
        }
    }
}
