package com.deviceinfo.data.internal

/**
 * SoC 代號 → 市場名映射。
 *
 * Android 只會給硬體代號（`Build.SOC_MODEL`，例如 `SM8750`），
 * 不會給消費者看到的市場名（`Snapdragon 8 Elite`）。
 * 這張表把常見代號補成市場名；查不到時如實顯示代號，不臆造。
 *
 * 維護原則：只收錄有公開資料可查的代號，寧缺勿錯。
 */
internal object SocNames {

    private val marketingNames: Map<String, String> = mapOf(
        // ---------------- Qualcomm Snapdragon 8 系列 ----------------
        "SM8750" to "Snapdragon 8 Elite",
        "SM8750P" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8650P" to "Snapdragon 8 Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8550P" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865",
        "SM8150" to "Snapdragon 855",
        "SDM845" to "Snapdragon 845",
        "MSM8998" to "Snapdragon 835",

        // ---------------- Qualcomm Snapdragon 7 / 6 / 4 系列 ----------------
        "SM7675" to "Snapdragon 7+ Gen 3",
        "SM7635" to "Snapdragon 7s Gen 3",
        "SM7550" to "Snapdragon 7 Gen 3",
        "SM7475" to "Snapdragon 7+ Gen 2",
        "SM7450" to "Snapdragon 7 Gen 1",
        "SM7350" to "Snapdragon 778G",
        "SM7325" to "Snapdragon 778G",
        "SM6375" to "Snapdragon 695",
        "SM6225" to "Snapdragon 680",
        "SM6115" to "Snapdragon 662",
        "SM4250" to "Snapdragon 460",

        // ---------------- Qualcomm 其他平台 ----------------
        "X1E80100" to "Snapdragon X Elite",
        "X1E78100" to "Snapdragon X Elite",
        "QCM6490" to "Snapdragon 778G (QCM6490)",

        // ---------------- MediaTek Dimensity ----------------
        "MT6989" to "Dimensity 9300",
        "MT6985" to "Dimensity 9200",
        "MT6983" to "Dimensity 9000",
        "MT6896" to "Dimensity 8200",
        "MT6893" to "Dimensity 1200",
        "MT6891" to "Dimensity 1100",
        "MT6877" to "Dimensity 900",
        "MT6833" to "Dimensity 700",
        "MT6853" to "Dimensity 720",
        "MT6789" to "Helio G99",
        "MT6769" to "Helio G85",
        "MT6765" to "Helio P35",

        // ---------------- Samsung Exynos ----------------
        "EXYNOS2400" to "Exynos 2400",
        "EXYNOS2200" to "Exynos 2200",
        "EXYNOS2100" to "Exynos 2100",
        "EXYNOS1380" to "Exynos 1380",
        "EXYNOS1280" to "Exynos 1280",
        "EXYNOS990" to "Exynos 990",

        // ---------------- Google Tensor ----------------
        "TENSOR G4" to "Google Tensor G4",
        "TENSOR G3" to "Google Tensor G3",
        "TENSOR G2" to "Google Tensor G2",
        "TENSOR" to "Google Tensor",

        // ---------------- HiSilicon Kirin ----------------
        "KIRIN9000S" to "Kirin 9000S",
        "KIRIN9000" to "Kirin 9000",
        "KIRIN990" to "Kirin 990",
        "KIRIN980" to "Kirin 980",

        // ---------------- UNISOC ----------------
        "T820" to "UNISOC T820",
        "T760" to "UNISOC T760",
        "T616" to "UNISOC T616"
    )

    /** 硬體代號 → 市場名。查不到時返回 null，由呼叫方回退到代號本身。 */
    fun marketingName(raw: String?): String? {
        val key = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        marketingNames[key]?.let { return it }

        // SM8750-AB / SM8750-AC 這類後綴，截到第一個 '-' 再試一次
        val base = key.substringBefore('-').trim()
        marketingNames[base]?.let { return it }

        // 最後嘗試前綴匹配（例如 "MT6989V/A"）
        return marketingNames.entries
            .firstOrNull { key.startsWith(it.key) }
            ?.value
    }

    /** 把 Build.SOC_MANUFACTURER 的各種寫法收斂成統一品牌名。 */
    fun vendorLabel(raw: String?): String {
        val value = raw?.trim().orEmpty().uppercase()
        return when {
            value.contains("QUALCOMM") || value == "QTI" -> "Qualcomm®"
            value.contains("MEDIATEK") || value == "MTK" -> "MediaTek®"
            value.contains("SAMSUNG") -> "Samsung"
            value.contains("GOOGLE") -> "Google"
            value.contains("HISILICON") || value.contains("KIRIN") -> "HiSilicon"
            value.contains("UNISOC") || value.contains("SPREADTRUM") -> "UNISOC"
            value.contains("NVIDIA") -> "NVIDIA"
            value.contains("INTEL") -> "Intel"
            value.isBlank() || value == "UNKNOWN" || value == "—" -> "SoC"
            else -> raw!!.trim()
        }
    }
}
