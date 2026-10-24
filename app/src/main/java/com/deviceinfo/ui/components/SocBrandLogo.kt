package com.deviceinfo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deviceinfo.R

/**
 * 處理器廠商徽標。
 *
 * v1.11.0 起改用**官方品牌標誌**（名義性使用：僅用於標示這台裝置的 SoC 廠商），
 * 不再畫「首字母 + 品牌色漸變」的替代圖形。
 *
 * 標誌放在淺色牌子上（深色主題也一樣）：每家廠商的原色標誌都是為白底設計的，
 * 若直接疊在深色玻璃卡上，黑字（MediaTek / HUAWEI 字標）會消失。
 *
 * 牌子尺寸隨標誌長寬比自適應：方形圖騰（驍龍、Google G、華為花瓣）用 72×72，
 * 長條字標（MediaTek、SAMSUNG、UNISOC）放寬到 136×72，否則字標會縮成看不清的一條線。
 */
@Composable
fun SocBrandLogo(
    vendorLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val brand = SocBrand.of(vendorLabel)
    val shape = RoundedCornerShape(16.dp)
    val logoRes = brand.logoRes

    if (logoRes == null) {
        // 沒有收錄官方標誌的廠商（NVIDIA / Intel / 未知）保留字母徽章
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(brand.gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = brand.letter,
                style = MaterialTheme.typography.headlineLarge,
                color = brand.letterColor,
                fontWeight = FontWeight.ExtraBold
            )
        }
        return
    }

    val plateColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFF2F3F4)
    } else {
        Color(0xFFFFFFFF)
    }
    val plateWidth = if (brand.logoAspect >= 1.3f) 136.dp else size

    Box(
        modifier = modifier
            .width(plateWidth)
            .height(size)
            .clip(shape)
            .background(plateColor)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = stringResource(R.string.sbl_logo_desc, vendorLabel),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private data class SocBrand(
    val letter: String,
    val gradient: List<Color>,
    val letterColor: Color,
    /** 官方標誌圖；沒有收錄時為 null，退回字母徽章。 */
    @DrawableRes val logoRes: Int? = null,
    /** 標誌的長寬比，用來決定牌子要方形還是長條。 */
    val logoAspect: Float = 1f
) {
    companion object {
        fun of(label: String): SocBrand {
            val key = label.trim().uppercase()
            return when {
                key.contains("QUALCOMM") || key == "QTI" || key.contains("SNAPDRAGON") -> SocBrand(
                    letter = "Q",
                    gradient = listOf(Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFFEF5350)),
                    letterColor = Color(0xFFFFD54F),
                    logoRes = R.drawable.soc_logo_qualcomm,
                    logoAspect = 1.0f
                )
                key.contains("MEDIATEK") || key == "MTK" || key.contains("DIMENSITY") -> SocBrand(
                    letter = "M",
                    gradient = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB)),
                    letterColor = Color(0xFFFFFFFF),
                    logoRes = R.drawable.soc_logo_mediatek,
                    logoAspect = 9.1f
                )
                key.contains("SAMSUNG") || key.contains("EXYNOS") -> SocBrand(
                    letter = "S",
                    gradient = listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2)),
                    letterColor = Color(0xFFFFFFFF),
                    logoRes = R.drawable.soc_logo_samsung,
                    logoAspect = 6.5f
                )
                key.contains("GOOGLE") || key.contains("TENSOR") -> SocBrand(
                    letter = "G",
                    gradient = listOf(Color(0xFF263238), Color(0xFF37474F)),
                    letterColor = Color(0xFF8AB4F8),
                    logoRes = R.drawable.soc_logo_google,
                    logoAspect = 0.98f
                )
                key.contains("HISILICON") || key.contains("KIRIN") || key.contains("HUAWEI") -> SocBrand(
                    letter = "K",
                    gradient = listOf(Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFF7B1FA2)),
                    letterColor = Color(0xFFFFFFFF),
                    logoRes = R.drawable.soc_logo_huawei,
                    logoAspect = 0.98f
                )
                key.contains("UNISOC") || key.contains("SPREADTRUM") -> SocBrand(
                    letter = "U",
                    gradient = listOf(Color(0xFF4A148C), Color(0xFF7B1FA2), Color(0xFFAB47BC)),
                    letterColor = Color(0xFFFFFFFF),
                    logoRes = R.drawable.soc_logo_unisoc,
                    logoAspect = 5.5f
                )
                key.contains("NVIDIA") -> SocBrand(
                    letter = "N",
                    gradient = listOf(Color(0xFF33691E), Color(0xFF558B2F), Color(0xFF7CB342)),
                    letterColor = Color(0xFFFFFFFF)
                )
                key.contains("INTEL") -> SocBrand(
                    letter = "I",
                    gradient = listOf(Color(0xFF006064), Color(0xFF00838F), Color(0xFF0097A7)),
                    letterColor = Color(0xFFFFFFFF)
                )
                else -> SocBrand(
                    letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
                    gradient = listOf(Color(0xFF37474F), Color(0xFF455A64)),
                    letterColor = Color(0xFFB0BEC5)
                )
            }
        }
    }
}
