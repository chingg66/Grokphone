package com.deviceinfo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deviceinfo.R
import com.deviceinfo.ui.theme.DevicePalette

private data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>
)

private val changelog = listOf(
    ChangelogEntry(
        version = "v1.18.0",
        date = "2026-10-24",
        items = listOf(
            "全域字體改為 Google Sans（拉丁字母與數字），中文自動回退系統字體",
            "Android 版本徽章改用 Google 官方版本 logo：7 牛軋糖機器人、8 奧利奧圓環、9 字母 P、10–13 / 15 / 16 文字標識、14 與 17 直接使用官方完整徽章",
            "導航列支援按住橫拖換頁：膠囊跟手指移動並加寬變形，鬆手吸附最近標籤",
            "新增設定頁（齒輪圖標）：外觀色彩、溫度單位（°C / °F）、電池校正、更新日誌統一收納",
            "電池校正：雙電芯機型容量 ÷2、功耗校正倍率 50%–200%",
            "電池頁新增容量健康度（滿充 ÷ 設計容量，自動評級）與手機報告（可分享 / 複製）",
            "預見性返回：API 33+ 走系統手勢預覽，舊系統第一頁左緣拖曳有自製箭頭預覽"
        )
    ),
    ChangelogEntry(
        version = "v1.17.0",
        date = "2026-10-24",
        items = listOf(
            "全新應用圖標：晶片輪廓搭配脈衝波形，取代原本的手機造型",
            "Android 版本改為官方風格印章徽章：花瓣外圈、弧形 ANDROID 與版本號",
            "品牌顯示改用行銷名稱（如 Redmi K70），內部型號退為副標",
            "頂部標題列同步顯示行銷名稱，並在過長時省略"
        )
    ),
    ChangelogEntry(
        version = "v1.16.0",
        date = "2026-10-24",
        items = listOf(
            "應用程式列表支援點擊查看應用架構：ABI、原生庫、元件與權限一覽",
            "ABI 透過解析 APK 內 lib/<abi> 條目判定（含 Split APK），支援 arm64-v8a / armeabi-v7a / x86_64 / x86",
            "詳情同時顯示最低 / 目標 SDK、安裝來源與安裝 / 更新日期",
            "15 條新字串同步支援 10 種語言"
        )
    ),
    ChangelogEntry(
        version = "v1.15.0",
        date = "2026-10-24",
        items = listOf(
            "效能評估新增 GPU 渲染測試：離屏 OpenGL ES 量測平均 / 峰值 / 最低 FPS",
            "系統頁 Android 版本改為圖示、版本號與代號的層級式呈現",
            "移除建置資訊中的液態玻璃字樣",
            "背景調整為更中性的灰色調，保留綠色強調色"
        )
    ),
    ChangelogEntry(
        version = "v1.14.0",
        date = "2026-10-24",
        items = listOf(
            "新增首次啟動歡迎頁：通知、懸浮窗、應用程式列表權限與語言、色彩一次設定完",
            "動態取色改為即時跟隨桌布：換壁紙不再需要重啟，回到前景也會自動校正",
            "主題拆成深淺模式 + 應用色彩（跟隨桌布 / 經典綠 / 海藍 / 紫羅蘭 / 琥珀 / 玫瑰）",
            "導覽列標籤依語言長度自動縮放，法語、俄語等長譯名不再被截斷"
        )
    ),
    ChangelogEntry(
        version = "v1.13.0",
        date = "2026-10-23",
        items = listOf(
            "介面完整支援多國語言：依系統語言自動切換（繁中 / 簡中 / 英 / 日 / 韓 / 西 / 法 / 德 / 俄 / 葡）",
            "UI 與資料層共 370+ 條字串全部資源化，佔位符格式隨語言調整",
            "更新日誌的歷史條目保留繁中原文"
        )
    ),
    ChangelogEntry(
        version = "v1.12.0",
        date = "2026-10-23",
        items = listOf(
            "優化滑動流暢度：背景光暈漸層加入繪製快取，滑動時不再每幀重建漸層",
            "分頁加入穩定 key，頁面狀態在滑動往返中保持不丟",
            "新增動態取色（Android 12+）：跟隨桌布配色，主題循環加入動態擋位",
            "網路頁新增下載測速：多節點自動回退，顯示首字節延遲 / 即時 / 平均 / 峰值速率"
        )
    ),
    ChangelogEntry(
        version = "v1.11.0",
        date = "2026-10-23",
        items = listOf(
            "處理器廠商徽章改用官方品牌標誌，不再用首字母（Q / M / S…）代替",
            "高通驍龍用紅色火焰圖騰、聯發科 / 三星 / 紫光展銳用原色字標、Google 用彩色 G、華為用花瓣",
            "標誌放在淺色牌子上，長條字標自動放寬版面，深淺色主題都看得清",
            "未收錄的廠商（NVIDIA / Intel / 未知）仍保留字母徽章"
        )
    ),
    ChangelogEntry(
        version = "v1.10.0",
        date = "2026-10-23",
        items = listOf(
            "修正平均功耗單位換算（mV × µA 要乘 1e-6），不再出現 -226500 mW 這種讀數",
            "平均功耗改為絕對值並標示入／出方向，超過 1 W 自動換算成 W",
            "沒有電壓或電流讀數時直接說明原因，不再靜默隱藏該行",
            "玻璃導覽列改為滑動膠囊選中態，圖標放大並裁切內容避免溢出圓角",
            "分頁指示器改為加長膠囊，當前頁位置更清楚",
            "作業系統頁顯示版本代號（Android 17 (Cinnamon Bun)），未知版本不再顯示 (Android)"
        )
    ),
    ChangelogEntry(
        version = "v1.9.0",
        date = "2026-10-23",
        items = listOf(
            "八個頁面全部支援下拉刷新，指示器落在內文起點不擋導覽列",
            "刷新時保留原畫面只更新數值，不再閃骨架屏",
            "下拉刷新與左右滑動分頁手勢互不干擾"
        )
    ),
    ChangelogEntry(
        version = "v1.8.0",
        date = "2026-10-23",
        items = listOf(
            "應用程式頁新增「應用程式列表權限」明示同意卡片，同意後才展示清單",
            "偵測 Android 11+ QUERY_ALL_PACKAGES 是否生效，受限時顯示狀態與引導",
            "受限時可一鍵前往系統設定，返回後自動重新掃描",
            "應用列表改為獨立重掃，不再重跑整包採集"
        )
    ),
    ChangelogEntry(
        version = "v1.7.0",
        date = "2026-10-23",
        items = listOf(
            "儀表板電池 / 儲存 / 網路 / 記憶體卡片可點擊跳轉對應系統設定",
            "效能評估新增 Geekbench 風格即時跑分與最終跑分圓環",
            "玻璃導覽列加入頂部光澤反射與按壓縮放點擊回饋",
            "頁面滑動加入間距、視差與淡入淡出，手感更沉靜",
            "新增「更新日誌」頁面"
        )
    ),
    ChangelogEntry(
        version = "v1.6.0",
        date = "2026-10-23",
        items = listOf(
            "主內容改為 HorizontalPager，8 個面板支援左右滑動切換",
            "頂部 TabBar 下方新增膠囊分頁指示器",
            "強化 Material 3 一致性：顏色、字階、形狀、ripple 回饋"
        )
    ),
    ChangelogEntry(
        version = "v1.5.1",
        date = "2026-10-23",
        items = listOf(
            "修復電池懸浮窗 CalledFromWrongThreadException 閃退",
            "修正功率單位換算（mV × µA → mW）",
            "以 isPlugged 判斷充放電方向，避免電流正負號不一致",
            "刪除 SharedPreferences 開關旗標，改以同進程靜態變數為準",
            "懸浮窗加入拖動與整體容錯"
        )
    ),
    ChangelogEntry(
        version = "v1.5.0",
        date = "2026-10-23",
        items = listOf(
            "新增桌面小工具：電池 / 記憶體 / 儲存",
            "新增電池懸浮窗：溫度、電量、即時功耗",
            "前台服務聲明 specialUse 類型"
        )
    ),
    ChangelogEntry(
        version = "v1.4.0",
        date = "2026-10-23",
        items = listOf(
            "CPU 狀態卡片頂端顯示裝置摘要",
            "工具入口簡化為效能評估 / 電池監測器 / 小工具",
            "效能評估可選 15 / 30 / 45 / 60 秒壓力測試",
            "螢幕卡片改名並嘗試顯示面板供應商",
            "處理器品牌風格化徽標（高通 Q、聯發科 M 等）"
        )
    ),
    ChangelogEntry(
        version = "v1.3.0",
        date = "2026-10-23",
        items = listOf(
            "頂部 TabBar 改為 8 個等分 tab，圖標在上文字在下",
            "處理器卡片明確標示品牌名、市場名與代號",
            "RingGauge 鎖死正圓比例",
            "加入定位與相機權限申請，解鎖 SSID/BSSID 與相機實測幀率",
            "相機顯示精確總像素數"
        )
    ),
    ChangelogEntry(
        version = "v1.2.0",
        date = "2026-10-23",
        items = listOf(
            "修復頂部 TabBar 截斷與貼邊",
            "優化處理器卡片視覺層次",
            "修正電池環形儀表被拉成橢圓"
        )
    ),
    ChangelogEntry(
        version = "v1.1.0",
        date = "2026-10-23",
        items = listOf(
            "加入頁面切換滑動淡入淡出動畫",
            "加入深淺色主題切換按鈕"
        )
    ),
    ChangelogEntry(
        version = "v1.0.0",
        date = "2026-10-22",
        items = listOf(
            "首發：儀表板、硬體、系統、電池、網路、應用程式、相機、感測器八大面板",
            "Kotlin + Jetpack Compose + Material 3",
            "UDF 單向資料流架構"
        )
    )
)

@Composable
fun ChangelogDialog(
    onDismissRequest: () -> Unit,
    onOpenOnboarding: () -> Unit = {}
) {
    GlassDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cl_title),
        subtitle = stringResource(R.string.cl_subtitle),
        confirmLabel = stringResource(R.string.cl_close)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .verticalScroll(rememberScrollState())
                .padding(end = 4.dp)
        ) {
            changelog.forEach { entry ->
                ChangelogEntryItem(entry)
                Spacer(Modifier.height(18.dp))
            }
        }

        // 歡迎頁只出現一次，這裡留一個回頭路（重看權限 / 改語言與色彩）。
        // 必須放在滾動區外，否則會被埋在十幾個版本的日誌底下沒人找得到。
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GlassPillButton(
                text = stringResource(R.string.ob_reopen),
                onClick = onOpenOnboarding
            )
        }
    }
}

@Composable
private fun ChangelogEntryItem(entry: ChangelogEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${entry.version} · ${entry.date}",
            style = MaterialTheme.typography.titleSmall,
            color = DevicePalette.Accent
        )
        Spacer(Modifier.height(6.dp))
        entry.items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodySmall,
                color = DevicePalette.TextSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
            )
        }
    }
}
