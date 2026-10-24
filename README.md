# 機懂（Grokphone）

Android 裝置資訊檢視器。Kotlin + Jetpack Compose + Material 3，UI 走深色 + 螢光綠的儀表盤風格，
中性灰底搭配毛玻璃質感浮層。

- **名稱**：機懂（Grokphone）
- **包名**：`com.grokphone.app`（原始碼 namespace 仍為 `com.deviceinfo`）
- **minSdk**：24（Android 7.0）· **targetSdk / compileSdk**：35
- **版本**：1.18.0（versionCode 20）
- **APK**：`Grokphone-v1.18.0-debug.apk`（約 18 MB）
- **語言**：繁中（預設）/ 簡中 / EN / 日本語 / 한국어 / Español / Français / Deutsch / Русский / Português，跟隨系統語言自動切換
- **UI 語言**：可跟隨系統，也可在應用內單獨指定（歡迎頁 / 更新日誌 → 重新開啟歡迎頁）

---

## 1. 功能

| 頁面 | 內容 |
|---|---|
| 儀表板 | CPU 各核即時頻率 + 負載波形、裝置摘要置於 CPU 狀態頂端；電池、網路、應用數量、螢幕、記憶體、儲存、工具入口 |
| 硬體 | SoC 型號與廠商（官方品牌標誌：驍龍火焰圖騰、MediaTek / SAMSUNG / UNISOC 字標、Google G、華為花瓣）、CPU 集群（每集群即時頻率條）、GPU、顯示面板（解析度 / 刷新率 / HDR / 尺寸） |
| 系統 | 裝置識別（型號 / 產品 / 主板 / SKU / 基頻 / 指紋）、作業系統（Android 版本 + API + ROM + 核心 + 安全補丁）、構建資訊 |
| 電池 | 電量環、狀態、溫度、電壓、瞬時電流、功率、健康度、設計容量、當前電荷量 |
| 網路 | 連線類型、IPv4/IPv6、鏈路頻寬、**下載測速（首字節延遲 / 即時 / 平均 / 峰值）**、Wi-Fi 協商速率與頻段、訊號強度、累計流量 |
| 應用程式 | **先取得你的明示同意**才展示；總數 / 使用者 / 系統統計，可按類別篩選，按安裝包體積排序並載入圖示；權限受限時給出狀態與修復入口 |
| 相機 | 每個 Camera2 裝置的像素、解析度、光圈、焦距、感光元件尺寸、變焦範圍、最大幀率、閃光燈 |
| 感測器 | 全部可用感測器，展開後顯示三軸即時讀數與單位、量程、解析度、功耗 |

**工具入口**（儀表板底部）：只保留效能評估、電池監測器、小工具三項。
- 效能評估：點擊後選 15 / 30 / 45 / 60 秒，後台跑 CPU 滿載壓力測試，每秒採樣負載/頻率/溫度/電量，完成後給出摘要。
- 電池監測器：**電池懸浮窗開關**。首次點擊引導授權「顯示在其他應用上層」，授權後點擊開/關懸浮窗——實時顯示溫度、電量、功耗。
- 小工具：**桌面小工具**。長按桌面 → 添加小工具 → 「電池 / 記憶體 / 儲存」一覽。

### 外觀：過渡動畫與深淺色

- **頁面切換**：`HorizontalPager` 支援左右滑動切換 8 個面板，滑動時頁面隨手指同步位移，
  鬆手後自動貼合最近頁面；頂部 `GlassTabBar` 與下方分頁指示器雙向同步，
  指示器的當前頁是加長膠囊，其餘為小圓點（v1.10.0 起）。
  - 點擊 tab 會以動畫滾動到對應頁面；儀表板內的「硬體 / 系統 / 電池…」快捷入口也會呼叫同一個滾動。
  - 各面板保持獨立垂直滾動，橫向手勢只會被 pager 攔截，不會與列表滾動衝突。
- **下拉刷新**：八個頁面皆可下拉刷新，指示器落在內文起點；刷新期間保留原畫面只更新數值。
- **深淺色 / 動態取色**：右上角圖示按鈕四態循環 **跟隨系統 → 動態取色 → 深色 → 淺色**，
  圖示隨狀態變（自動亮度 / 調色盤 / 月亮 / 太陽），選擇寫入 `SharedPreferences`（`ThemeStore`）即時生效。
  - 動態取色走 Android 12+ 的 Material You：`dynamicLight/DarkColorScheme` 依桌布取色，
    再把 `ColorScheme` 映射進 `DeviceColors`（主色 → Accent、容器色 → 表面、onBackground → 主文字），
    玻璃質感與可讀性規則不變；Android 11 及以下自動回落為跟隨系統。
- 配色不是簡單反轉：深色是螢光綠儀表盤，淺色壓深綠（`#1E7C40`）保證對比；
  語義色全部收斂到 `DeviceColors`，透過 `LocalDeviceColors` 下發，
  所以 `DevicePalette.Accent` 這類呼叫點在主題切換時自動跟著變，不需要重寫 UI 程式碼。

### v1.2.0 UI 優化

針對你截圖裡的三個痛點做了調整：

1. **頂部導航不再截斷**
   - `GlassTabBar` 在螢幕寬度 < 400dp 時自動壓縮 tab 內距與圖示大小。
   - 左右兩側加 16dp 漸層遮罩，提示後方還有更多 tab 可橫向滾動。
   - 切換 tab 時自動把選中項捲到可視區，避免再被切半。

2. **處理器卡片層次更清楚**
   - SoC 型號（如 `SM8750`）放大到 `headlineLarge` 並改為主文字色，成為卡片第一視覺焦點。
   - 「處理器」標題維持輔助性強調色，三個規格 pill 與下方鍵值網格不再搶戲。

3. **電池卡片環形儀表修正**
   - `RingGauge` 原本被強制寬 110dp × 高 64dp，在電池卡片裡拉成橢圓；
     現在改用 `diameter` 參數，一律保持正圓。
   - 電池卡片左側加大到 128dp 正圓環，右側改用「狀態 pill + 大字溫度 + 電源說明」三層排列，
     與儀表板 RAM/儲存卡片風格統一。

### v1.3.0 UI 優化 + 權限支援

1. **頂部導航重做**：8 個 tab 圖標在上、文字在下，按 weight 等分整行。
   不再橫向滾動 —— 滾動式會把最後一個 tab 切一半「貼邊」，
   等分佈局讓所有 tab 永遠完整可見、文字居中。

2. **處理器卡片（參照系統資訊工具排版）**：新增 `SocNames` 映射表
   （`Build.SOC_MODEL` 的硬體代號 → 市場名，如 `SM8750` → `Snapdragon 8 Elite`）。
   卡片佈局：圓形廠商徽章 + 品牌 → 市場名 → 代號三級標示，
   規格 pill、內嵌「CPU 組態」子卡（核心數 + 頻率區間 + 即時頻率條）、鍵值網格。

3. **電池環形儀表**：`RingGauge` 加 `aspectRatio(1f)` 鎖死比例，
   繪製時取短邊居中畫正圓，任何外部約束下都不會再拉成橢圓。

4. **運行時權限**（新）：
   - **定位權限**（網路頁申請）：解鎖 Wi-Fi SSID / BSSID（Android 10+ 系統要求），授權後自動重新採集。
     明確不用於 GPS 定位。
   - **相機權限**（相機頁申請）：解鎖「即時幀檢測」—— 真正打開鏡頭以最大 YUV 尺寸串流約 2 秒，
     測量實際輸出解析度與實測幀率，與標稱像素對照（`CameraLiveProbe`，全程容錯、逾時保險）。

5. **相機精確像素**：相機卡片顯示一位小數 MP + 精確總像素數
   （如 `50.3 MP · 8192 × 6144 · 精確像素：50,331,648`），不再取整掩蓋真實感測器規格。

### v1.4.0 Dashboard 與效能評估

1. **儀表板頂端顯示裝置摘要**
   - 把原本在底部的「品牌 pill + 型號 + Android 版本 + SoC 市場名」搬到 CPU 狀態卡片頂端，
     寬度佔滿整卡，型號過長自動截斷，不再貼邊或被擠壓。

2. **工具入口只保留三項**
   - 效能評估、電池監測器、小工具。其餘入口移除，避免工具區過擠。

3. **效能評估（可選時長）**
   - 點擊後彈出 15 / 30 / 45 / 60 秒選擇。
   - 後台啟動與核心數相同的 worker 線程跑密集計算，讓 CPU 滿載。
   - 每秒採樣平均負載、平均頻率、峰值溫度、當前電量，結束後給出
     平均/峰值負載、平均/峰值頻率、電量變化、溫度變化、平均功耗摘要。
   - 狀態由 `DeviceViewModel` 的 `BenchmarkState` 統一承載，切換頁面不會中斷測試。

4. **螢幕卡片優化**
   - 標題從「手機」改為「螢幕」；卡片獨佔一行，寬度最大化。
   - 嘗試從 `ro.vendor.display.panel_name` 等屬性與 `/sys/class/drm/...` 讀取面板供應商，
     能讀到就顯示（如 Samsung Display / BOE），讀不到顯示「—」。

5. **處理器風格化品牌徽標**
   - 新增 `SocBrandLogo`：高通用紅底金「Q」、聯發科用藍底白「M」、三星用藍底白「S」、
     Google 用深灰底多彩「G」、麒麟用紫底白「K」、UNISOC 用紫底白「U」等。
   - 不使用官方商標，用品牌色 + 首字母的風格化方塊，避免版權問題。

### v1.5.0 桌面小工具與電池懸浮窗

1. **桌面小工具**（`widget/BatteryWidgetProvider` + `WidgetDataLoader`）
   - 桌面顯示電池電量與溫度、記憶體佔用、儲存佔用三行資訊，帶進度條。
   - 資料讀取走獨立的輕量 `WidgetDataLoader`（不走全量 `DeviceCollector`，避免掃應用列表）。
   - 更新策略：系統 `updatePeriodMillis` 30 分鐘（平台硬下限）+ 應用打開時即時刷新 + 點擊打開應用。
   - RemoteViews 佈局，深色圓角卡片與應用內材質對齊。

2. **電池懸浮窗**（`service/BatteryOverlayService`）
   - 儀表板「電池監測器」變成懸浮窗開關。
   - 首次點擊：說明 + 引導跳轉系統「顯示在其他應用上層」授權頁。
   - 授權後點擊：以 `TYPE_APPLICATION_OVERLAY` 掛載懸浮窗，實時顯示電量、溫度、
     瞬時功率（電壓 × 電流，標記充/放電方向），2 秒刷新，右上角 ✕ 關閉。
   - 前台服務 + 常駐通知保活（Android 14+ 聲明 `specialUse` 類型）。

### v1.5.1 懸浮窗閃退修復

真機（Android 17 / API 37）點擊「電池監測器」即閃退，logcat 給出唯一原因：

```
FATAL EXCEPTION: battery-overlay
android.view.ViewRootImpl$CalledFromWrongThreadException:
  Only the original thread that created a view hierarchy can touch its views.
  Expected: main  Calling: battery-overlay
    at BatteryOverlayService.updateOverlay(BatteryOverlayService.kt:119)
```

**根因**：輪詢掛在自建的 `HandlerThread` 上，卻在該執行緒裡直接 `TextView.setText`。
View 只能在建立它的執行緒（此處是主執行緒）上觸碰，一碰就拋。

修復與順帶的修正：

1. **輪詢移到主執行緒**：`Handler(Looper.getMainLooper())` 取代 `HandlerThread`。
   `BatteryProbe.read()` 只讀幾個 system property 與 BatteryManager 緩存值，微秒級，
   放主執行緒不會造成掉幀——比「背景讀 + 主執行緒回拋」的兩段式寫法更簡單可靠。
2. **功率單位修正**：`voltageMv`（毫伏）× `currentUa`（微安）相乘得到「毫伏·微安」，
   換成毫瓦要乘 **1e-6**，原先乘了 1e-3，導致懸浮窗顯示 `87797 W` 這種荒唐讀數。
3. **充放電方向**：不再用電流正負判斷——各廠商對 `BATTERY_PROPERTY_CURRENT_NOW`
   的符號約定不一致（本機負值代表充電）。改為 `BatteryLive.isPlugged` 判斷。
4. **開關狀態不再落盤**：服務與 UI 同進程（未宣告 `android:process`），
   靜態 `isRunning` 就是唯一真相。改用 SharedPreferences 反而會在「進程被殺、
   `onDestroy` 沒跑到」時殘留 `true`，讓 UI 以為懸浮窗還開著，點一下變成「關閉」卻沒有視窗可關。
5. **補上拖動**：按住懸浮窗可拖動（位移 > 12px 才算拖動，避免吃掉 ✕ 的點擊）。
6. **容錯**：`addView` / `startForeground` / 讀數全部 `runCatching` 包裹，
   權限被收回時服務自行退場而不是崩進程。

已在真機驗證：開啟 → 顯示 `88% / 33.9°C / 396 mW 入 / USB` → 拖動 → 再點關閉，全程無崩潰。

### v1.18.0 Google Sans、官方版本 logo、液態導航拖動、設定頁、電池校正與報告
1. **字體** 全域 Typography 改用 Google Sans（`res/font/google_sans_{regular,medium,bold}.ttf`），CJK 自動回退系統字體。
2. **導航列拖動換頁** `GlassTabBar` 掛 `detectHorizontalDragGestures`：膠囊中心跟手指、拖動中加寬 18% / 壓扁 6%，鬆手吸附最近一格並換頁；手勢 lambda 用 `rememberUpdatedState` 避免捕獲舊的 selectedIndex。
3. **設定頁** `SettingsDialog`：外觀（抽出 `AppearanceSection` 共用）、溫度單位 °C/°F、電池校正（雙電芯 ÷2、功耗倍率 50–200% 步進 5%）、更新日誌與歡迎頁入口；頂部列只留一個齒輪。設定存 `SettingsStore`（deviceinfo_ui）。
4. **溫度單位** `AppSettings.temperature()` 統一格式化，電池頁、懸浮窗、桌面小工具同步套用。
5. **電池健康度** `BatteryLive.chargeFullUah`（sysfs `charge_full` / `bms/charge_full`），健康度 = 滿充 ÷ 設計容量，≥90 優良 / ≥80 良好 / ≥70 衰退 / 以下建議更換；讀不到（SELinux 擋）就隱藏該區塊。
6. **手機報告** 電池頁新增報告卡：裝置 + Android + SoC + 電池摘要，`ACTION_SEND` 分享（上傳）或複製到剪貼簿。
7. **預見性返回** manifest 開 `enableOnBackInvokedCallback`（API 33+ 系統預覽）；API<33 在第一頁左緣掛 20dp 熱區，拖曳顯示箭頭膠囊預覽，過閾值 finish()。

### v1.18.0 Android 版本徽章改用 Google 官方 logo
1. **官方素材** 來自 Wikimedia Commons「Android version logos」分類，以 512px PNG 打包進 `res/drawable-nodpi/android_logo_<sdk>.png`。黑色 wordmark（10–13 / 15 / 16）離線轉白以適配深藍底。
2. **三種呈現**：完整徽章版（14 / 17 官方 logo 本身即徽章，整幅取代花瓣外框）、文字標識版（wordmark 居中於花瓣印章內）、圖形素材版（7 牛軋糖機器人 / 8 奧利奧環 / 9 字母 P，上移保留下方版本號）。未知版本回退自繪彩蛋圖案（餅乾 / 派 / 時鐘 / 氣泡 / 星芒 / 冰淇淋 / 小花 / 肉桂捲）。
3. **授權**：Android 機器人與版本標識依 CC BY 3.0（Google）授權使用；本應用僅作展示，未修改其比例與構成（wordmark 僅做白色化處理）。
4. **字串** 無新增；官網版號與下載更新至 v1.18.0。

> v1.18.0 真機驗證（Redmi K70 / Android 17，API 37）：12 個版本（含未知版本回退）以網格預覽逐一確認 logo 清晰居中，無裁切。

### v1.17.0 圖標、Android 版本徽章、品牌顯示
1. **應用圖標** 改為晶片輪廓 + 脈衝波形；自適應前景與 API<26 的舊式圖標同步，官網 favicon 與導覽圖標一併更新。
2. **Android 版本徽章** 新增 `ui/components/AndroidVersionBadge.kt`：兩層錯位的 20 瓣花形外圈、深藍內圈、綠圓與斜向紋理，弧形「ANDROID」以逐字旋轉繪製，下方為版本號。
3. **品牌顯示** `DeviceIdentity` 新增 `marketName`，依序讀取 `ro.product.marketname` / odm / vendor / bootimage / `ro.config.marketing_name` / oplus / oppo，取不到才退回 `Build.MODEL`；卡片主標與頂部標題列改用行銷名稱，內部型號退為副標。
4. **字串** 無新增；官網版號與下載更新至 v1.17.0。

> v1.17.0 真機驗證（Redmi K70 / Android 17）：徽章在 104dp 下文字與花瓣無裁切；`ro.product.marketname` = Redmi K70 取代 `23113RKC6C`。

### v1.16.0 應用架構詳情（LibChecker 風格）

1. **點列表看架構**
   - 應用程式列表任一行可點，彈出詳情對話框：ABI 徽章、版本、最低 / 目標 SDK、
     安裝來源、安裝 / 更新日期、分包 APK 數、元件統計（活動 / 服務 / 廣播 / 提供者）、權限數。
2. **ABI 怎麼判定**
   - 把 base APK 與所有 split APK 當 zip 開，枚舉 `lib/<abi>/` 下的 `.so` 條目。
     只看 `splitPublicSourceDirs` 名稱或 `nativeLibraryDir` 都只反映「目前裝的那一份」，
     解包才能看到 App 實際帶了哪些 ABI（arm64-v8a / armeabi-v7a / x86_64 / x86 …）。
   - 同一個 `.so` 名稱會按 ABI 分組列出，單一 ABI 超過 40 個以「… 另有 N 個」收合。
3. **資料正確性處理**
   - `requestedPermissions` 需帶 `GET_PERMISSIONS` 旗標才會被填滿，否則永遠是 0。
   - 預裝應用的 `firstInstallTime` 常是 1970 / 0，早於 2000 年一律顯示「—」。
4. **字串** 新增 17 條，10 種語言同步；官網相冊新增「04 應用架構」與下載更新至 v1.16.0。

> v1.16.0 真機驗證（Redmi 23113RKC6C / Android 17）：
> WebView 顯示 `arm64-v8a` + `armeabi-v7a` 兩組 ABI、原生庫 4 個；
> 超級小愛 108 個原生庫（收合為 40 + 68）、權限 263 項、371 活動；
> 小米筆記 18 個原生庫；預裝應用日期正確顯示「—」，logcat 0 FATAL。

### v1.15.0 GPU 測試、Android 版本卡、中性灰背景

1. **GPU 渲染效能測試**
   - 新增 `GpuBenchmarkEngine`：EGL 離屏 pbuffer + OpenGL ES 2.0 context，
     渲染 1280×1280 fragment shader workload（24 loop、900ms 預熱），`glFinish()` 測實際吞吐。
   - 回報平均 / 峰值 / 最低 FPS、幀數、GPU renderer 與溫度變化，score = avgFps × 1000
     （真機 Snapdragon 8 Gen 2 實測 avg 426.8 FPS、score 約 4268）。
   - 效能評估改為 CPU / GPU 雙選項，共用進度與結果對話框。
2. **Android 版本卡重構**：Android robot icon + `ANDROID` 標籤 + 版本號 + 代號
   層級式呈現，附 `API` 與 `64-bit` pill。
3. **移除「液態玻璃」字樣**：字串資源與建構資訊卡不再出現此描述。
4. **背景調整為中性灰**：Dark `0xFF121312` / Light `0xFFF4F4F3`，
   Material You 動態取色用 `lerp()` 把大面積背景壓回灰，綠色光暈減弱。
5. **品牌全面更名**：App 名稱「機懂 / 机懂 / Grokphone」、網站品牌、
   `applicationId` 改為 `com.grokphone.app`（namespace 仍為 `com.deviceinfo`）。

> v1.15.0 真機驗證（Redmi 23113RKC6C / Android 17）：GPU 測試正常完成並顯示結果、
> Android 版本卡顯示 `17 / Cinnamon Bun / API 37 / 64-bit`、建構資訊無液態玻璃字樣、
> 深色背景偏中性灰。

### v1.14.0 歡迎頁、外觀重構、動態取色即時跟隨

1. **首次啟動歡迎頁**
   - 一次講清楚五件事：通知（Android 13+ 執行期權限）、懸浮窗（`Settings.canDrawOverlays`）、
     應用程式列表（應用內同意 + ROM 攔截時跳系統頁）、介面語言、應用色彩。
   - 每行都有即時狀態（已開啟 / 已授權 / 已允許），從系統設定返回時自動重讀。
   - 「開始使用」與「略過」都會記住，之後直接進主頁；想重看可從更新日誌對話框點
     「重新開啟歡迎頁」（按鈕放在滾動區外，不會被十幾個版本的日誌埋掉）。

2. **外觀重構：深淺模式 + 應用色彩**
   - 舊版是四擋循環（跟隨系統 → 動態取色 → 深色 → 淺色），按好幾次才知道有幾種；
     現在拆成兩個獨立選項：深淺（跟隨系統 / 淺色 / 深色）+ 色彩（跟隨桌布 / 經典綠 /
     海藍 / 紫羅蘭 / 琥珀 / 玫瑰）。
   - 六個色板共用同一套 Handmade 配色，做法是整組色相旋轉：明度與飽和度不變，
     所以對比度、玻璃質感、警示色（紅 / 琥珀 / 藍）全部保持原樣，只需維護一份調校。
   - 舊的 `theme_mode` 偏好會自動遷移：`Dynamic → 跟隨系統 + 跟隨桌布`，其餘一一對應。

3. **動態取色即時跟隨桌布（修復「換壁紙要重啟」）**
   - 根因有兩層：`MainActivity` 宣告了 `configChanges=…|uiMode|density`，換桌布不會重建
     Activity；主題又把取色結果 `remember(darkTheme)` 快取住，所以畫面永遠是舊配色。
   - 現在 `rememberWallpaperRevision()` 同時監聽 `ACTION_WALLPAPER_CHANGED`、
     `ACTION_CONFIGURATION_CHANGED` 與 `ON_RESUME`，任一觸發就重算；
     並用 `WallpaperManager.getWallpaperColors()` 的桌布主色色相，把系統給的調色盤
     再對齊一次 —— 即使系統資源還是舊的，畫面也會立刻跟新桌布同調。
   - Android 15 把 `getPrimaryColor()` 的回傳型別從 `@ColorInt int` 改成 `android.graphics.Color`，
     直接呼叫會讓 minSdk 24 的 APK 在舊機 NoSuchMethodError；改用反射取値，兩種型別都吃。
   - 順手修掉 Material You 深色 primary「淺而淡」導致強調色發灰的問題：保色相、把飽和度
     抬到 55%，深色模式保證亮度 ≥ 0.80，淺色模式壓到 ≤ 0.46 保對比。

4. **導覽列標籤自動縮放**
   - 八個等分格裡，法語「Tableau de bord」、俄語「Приложения」等長譯名原本會被 ellipsis 截斷。
     現在用 `TextMeasurer` 量一次實際寬度再等比縮小（下限 6.5sp），並留 5% 餘量
     （整數像素捨入會讓「剛好放得下」的標籤被切掉最後半個字母）。
   - 法語 `tab_dashboard` 收短為「Accueil」、`tab_camera` 為「Caméra」，日語 `ダッシュボード` → `ホーム`。

5. **測試**
   - 新增 `AppearanceTest`（11 條）：色相旋轉保飽和度、中性色不染色、語義色不動、
     色相跨 0° 取最短路徑、強調色飽和度下限、偏好存取與舊值遷移。
   - 全量 29 條單測 + `assembleDebug` 通過。

> v1.14.0 真機驗證（Redmi 23113RKC6C / Android 17 / HyperOS）：歡迎頁五項設定、
> 通知授權、懸浮窗授權、應用內切換語言（繁中 / EN / DE / RU 皆即時生效且停留原頁）、
> 六個色板即時生效、淺色 + 經典綠對比正常、logcat 0 FATAL。
> 換桌布的端到端驗證無法用 adb 自動化（`cmd wallpaper` 只有 dim 指令），
> 已用「回到前景觸發重算」與色相數學單測覆蓋。

### v1.13.0 多國語言

1. **字串全部資源化**
   - UI 層（8 個頁面、玻璃元件、對話框）與資料層（探測器、ViewModel、懸浮窗服務、
     桌面小工具）共 373 條字串抽進 `res/values/strings*.xml`，硬編碼清零。
   - Composable 用 `stringResource(...)`；非 Compose 作用域（Repository / Probe / Service /
     Widget）走 `core/L10n.s(...)`（Application 啟動時注入 context，語言切換會重建進程，不存在過期翻譯）。

2. **語言覆蓋**
   - 預設（fallback）為繁體中文；另有 values-en / zh-rCN / ja / ko / es / fr / de / ru / pt
     九種 locale，跟隨系統語言自動選擇，Android 13+ 也支援「應用程式語言」單獨設定。
   - 佔位符用位置參數（`%1$s` / `%1$d` / `%1$.1f`），各語言可按語法調整語序；
     9 份翻譯經腳本校驗：key 集合一致、佔位符類型與數量一致。
   - 更新日誌的歷史條目保留繁中原文（歷史記錄性質，不翻譯）。

3. **單元測試改為資源驅動**
   - `ScreenRenderTest` 的斷言從硬編碼中文改為 `context.getString(R.string.*)`，
     Robolectric（en_US locale）與真機（繁中）兩種環境都能過；
     `TestSnapshots` 的範例資料改用 `L10n.s` 取標籤，並在 `@Before` 之後才建快照。

> 註：v1.13.0 打包時手機再次斷開（`adb devices` 為空）。構建、18 個單測、
> 9 locale 的 key/佔位符腳本校驗皆通過；真機語言切換驗證待手機重連後執行：
> `adb shell cmd locale set-app-locales com.deviceinfo --locales en-US`。

### v1.12.0 流暢度、動態取色、網路測速

1. **滑動流暢度**
   - 背景的三團環境光暈原本每幀重新建構 4 個 `Brush`，現在改走
     `Modifier.drawWithCache`：漸層只在尺寸變化時建立一次，繪製期只做重放。
   - `HorizontalPager` 補上穩定 `key`（以 tab 名為 key），滑動往返時頁面狀態不再被重建。
   - 真機 `dumpsys gfxinfo` 實測 8 次連續滑動：996 幀、janky 0.20%、
     50th 8 ms / 90th 20 ms / 99th 34 ms。

2. **動態取色（Material You）**
   - 主題循環加入「動態取色」擋位，Android 12+ 從桌布取色，Android 11 及以下回落跟隨系統。
   - 映射關係見上節；語義色仍是同一套 `DeviceColors`，所以八個頁面、玻璃導覽列、
     對話框全部自動跟著換色，沒有第二套 UI 分支。

3. **網路頁下載測速**
   - 新增「測速」卡片：`開始測速` → 量首字節延遲 → 串流下載最多 35 MB / 12 秒，
     每 150 ms 取樣一次即時速率，顯示延遲、即時、平均、峰值與使用節點。
   - 只用 JDK 標準庫（`HttpURLConnection` + `Dispatchers.IO`），不引入 OkHttp；
     下載全程可中斷，離開頁面自動取消。
   - 多節點回退：Cloudflare → OVH → Hetzner，若某節點 3 秒內吐不到 2 MB 就換下一個，
     避免把被中間鏈路截斷的假讀數當成結果上報。
   - 計時單位踩坑記錄：`System.nanoTime()` 是納秒，最初拿去和 12 000（毫秒）比較，
     導致每輪在 100 ms 左右就「讀完」，測出 0.06 Mbps 的假結果；換算成納秒後恢復正常
     （本機 Wi-Fi 6 實測 241 Mbps 均值 / 396 Mbps 峰值）。

> v1.12.0 已在真機（Redmi 23113RKC6C / Android 17 / HyperOS）驗證：
> 四擋主題循環、動態取色配色、測速 241 Mbps、連續滑動 janky 0.20%，全程無 FATAL。

### v1.11.0 處理器徽章改用官方品牌標誌

1. **不再用首字母代替**
   - 舊版是「品牌色漸變方塊 + Q / M / S / G / K / U」，看起來像隨手畫的佔位圖。
   - 新版直接顯示廠商標誌：
     | 廠商 | 標誌 |
     |---|---|
     | 高通驍龍 | 紅色火焰圖騰（取自 Snapdragon 字標左側圖形） |
     | 聯發科 | MEDIATEK 橙藍字標 |
     | 三星 | SAMSUNG 藍色字標 |
     | Google | 彩色 G |
     | 華為 / 海思麒麟 | 花瓣 + HUAWEI 字標 |
     | 紫光展銳 | UNISOC 紫色字標 |

2. **淺色牌底**
   - 標誌一律放在淺色圓角牌上（深色主題也用 #F2F3F4）：這些字標是為白底設計的，
     直接疊在深色玻璃卡上，MediaTek / HUAWEI 的深色字會整片消失。

3. **尺寸隨長寬比自適應**
   - 方形圖騰（驍龍、Google、華為）用 72×72；
   - 長條字標（aspect ≥ 1.3）把牌子放寬到 136×72，否則 9:1 的字標會縮成看不清的一條線。

4. **資源處理**
   - PNG 置於 `res/drawable-xxhdpi/`，取自 Wikimedia Commons 的官方 SVG 後柵格化，
     白底已轉透明、按內容裁切，長邊 320–384px（xxhdpi 下 1:1 不糊）。
   - 未收錄的廠商（NVIDIA / Intel / 未知）仍走原本的字母徽章，不會空白。

5. **商標說明**
   - 標誌僅用於標示本機 SoC 廠商（名義性使用），不代表與這些廠商有任何關聯或背書。

> 註：v1.11.0 打包完成時手機已從 USB 斷開（`adb devices` 為空），
> 因此這一版沒有新的真機截圖；`assembleDebug`、18 個單元測試（含硬體頁標誌節點斷言）與 lint 皆通過。

### v1.10.0 功耗換算修正 + 導覽列滑動膠囊 + 版本代號

1. **平均功耗顯示（真機曾出現 `-226500.4 mW`）**
   - 根因是 `computeAvgPowerMw()` 用了 `/ 1000f`：`voltageMv × currentUa` 是「毫伏·微安」，
     換成毫瓦要乘 **1e-6**。同一個坑在懸浮窗修過一次，跑分這條路徑漏掉了。
   - 數值一律取絕對值——電流正負各廠商約定不同（小米負值代表充電），
     方向改用取樣期間的 `isPlugged` 多數決定，顯示成 `6.74 W 入` / `420 mW 出`。
   - 大於 1 W 自動換成瓦，不再出現五位數毫瓦；`Fmt.power()` 由懸浮窗與跑分共用。
   - 讀不到電壓或電流時不再整行消失，改為顯示 `—` 並在下方說明原因。

2. **玻璃導覽列**
   - 選中態從「每個 tab 各畫一塊底色」改成**一塊滑動膠囊**，在 8 格之間以 280ms 緩動平移，
     點擊或左右滑頁時都有連續的方向感。
   - 內容層 `clip` 到膠囊形狀，最左／最右的選中底色不會再溢出玻璃圓角。
   - 圖標 17 → 18dp，列高 56 → 58dp，文字 `softWrap = false` + `Ellipsis`，窄屏也不會換行。
   - 高光反射收斂（0.18 → 0.13），頂部圖標不會被洗成灰白；按壓保留縮放 + ripple。

3. **分頁指示器**
   - 當前頁從「放大的圓點」改成**加長膠囊**（18×6dp），八點排列時基線更穩，
     其餘圓點維持 5dp，滑頁時寬高一起補間。

4. **作業系統版本代號**
   - `codeNameOf()` 補上 API 37 → **Cinnamon Bun**（Android 17），
     未知版本改回空字串，UI 只顯示 `Android xx`，不再出現 `Android 17 (Android)` 這種廢話。
   - 版本號與代號相同時也不會重複加括號。

真機驗證（Redmi 23113RKC6C / Android 17）：跑完 15 秒壓力測試顯示
`平均功耗 6.74 W 入`，下方註明「充電中，數值為淨輸入功率」；系統頁顯示
`Android 17 (Cinnamon Bun)`；導覽列滑動膠囊與指示器換頁同步，logcat 無 FATAL。

### v1.9.0 全域下拉刷新

1. **八個頁面一次到位**
   - 下拉刷新掛在共用的 `InfoScreen` 容器上，透過 `LocalPullRefresh`
     （`PullRefreshHandler`）下發，八個頁面不用各自實作、也不用改任何 Screen 簽名。
   - 指示器用 Material 3 `PullToRefreshDefaults.Indicator`，位置刻意往下挪到內文起點，
     否則預設會貼在畫面最頂端、被玻璃導覽列蓋住。

2. **刷新不清空畫面**
   - 新增 `DeviceViewModel.pullRefresh()`：只重採集一次快照並就地替換 `snapshot`，
     保留即時採樣迴圈與目前頁面，不會像 `refresh()` 那樣退回骨架屏。
   - 刷新中的 `refreshing` 是獨立 StateFlow，僅驅動指示器。

3. **手勢不打架**
   - 下拉走垂直嵌套滾動，翻頁走 pager 的橫向手勢，兩者互不攔截；
     實測在儀表板與硬體頁下拉都只刷新、不換頁。

---

### v1.8.0 應用程式列表權限

1. **先問再讀**
   - 應用程式頁首次進入只顯示「應用程式列表權限」卡片：說明用途（僅本機統計，不上傳），
     按「允許讀取」才寫入 `AppListConsent` 並展示清單；「暫不允許」後仍留一個重新允許的入口。

2. **真的去查權限，而不是假設**
   - `AppListAccess.isGranted()` 用 `checkPermission(QUERY_ALL_PACKAGES)` 確認安裝期權限；
     Android 10 及以下直接視為已授予。
   - 但只有權限不夠：MIUI / HyperOS 另有一層「讀取應用程式清單」開關，
     即使 `QUERY_ALL_PACKAGES granted=true`，`getInstalledPackages()` 仍會被攔成只回自己。
     因此再用「可見套件數是否合理」交叉判斷（`MIN_REASONABLE_PACKAGES`），
     兩種受限原因在 UI 上分開顯示：**權限未授予** / **ROM 攔截**。

3. **一鍵修復**
   - 「開啟權限設定」優先跳 MIUI 權限編輯頁（`PermissionsEditorActivity`，帶 `extra_pkgname`），
     其餘 ROM 依序退到應用程式詳情頁、應用程式列表、系統設定首頁。
   - 從設定返回時 `LifecycleEventEffect(ON_RESUME)` 自動重掃，不用手動按「重新掃描」。

4. **只重掃應用**
   - 新增 `DeviceRepository.loadApps()` + `DeviceViewModel.reloadApps()`：
     授權/重掃只替換 `snapshot.apps`，不重跑整包採集。

真機驗證（Redmi / Android 17 / HyperOS）：初始顯示「ROM 攔截 · 可見 1 個」→
點「開啟權限設定」→ 權限管理 → 其他權限 → 取得應用程式清單 → 始終允許 →
返回 App 自動重掃，列出 **367 個應用（使用者 20 / 系統 347，3.81 GB）**，卡片消失、狀態轉為「完整列表」。

---

### v1.7.0 系統設定捷徑 + 效能評估跑分 + 玻璃導覽列強化 + 更新日誌

1. **儀表板卡片跳轉系統設定**
   - 電池卡片 → `Intent.ACTION_POWER_USAGE_SUMMARY`（系統電池用量）。
   - 儲存卡片 → `Settings.ACTION_INTERNAL_STORAGE_SETTINGS`（手機空間管理）。
   - 網路卡片 → `Settings.ACTION_WIFI_SETTINGS`（Wi-Fi 設定）。
   - 記憶體卡片 → AOSP 原生記憶體管理介面（`com.android.settings.Settings$AppMemoryUsageActivity`），
     找不到時改開「開發人員選項」作為備援。
   - 所有跳轉都帶 fallback，ROM 移除對應 Activity 時不會閃退。

2. **效能評估新增即時跑分**
   - 測試過程以 `平均頻率 × 平均負載 × 核心數 / 10` 合成 Geekbench 風格即時分數，
     每秒更新並顯示在大型圓環進度條中央。
   - 完成後顯示「最終跑分」，與平均/峰值負載、頻率、電量/溫度變化等摘要並列。

3. **玻璃導覽列強化**
   - 頂部加入白色高光反射（`drawGlossReflection`），模擬玻璃受光。
   - Tab 項目加入按壓縮放（0.92x）與 Material 3 ripple，點擊更有手感。
   - 稍微提高 dispersion，讓折射更明顯。

4. **頁面滑動更沉靜**
   - `HorizontalPager` 加入 14dp 頁間距、視差位移與相鄰頁淡入淡出/微縮，
     滑動時視覺更柔和。

5. **新增「更新日誌」頁面**
   - 右上角主題按鈕左側新增資訊按鈕，點擊開啟滾動式更新日誌對話框，
     列出 v1.0.0 至今每個版本的改動。

---

### v1.6.0 Material 3 風格強化 + 左右滑動切換面板

1. **左右滑動切換面板**
   - 主內容改為 `HorizontalPager`，8 個面板可左右滑動切換。
   - 頂部 `GlassTabBar` 仍保留，點擊 tab 會以動畫滾動到對應頁面；tab 選中狀態與 pager 當前頁雙向同步。
   - TabBar 下方新增 **膠囊分頁指示器**，當前頁小點放大並使用強調色，其餘為半透明輪廓色。

2. **Material 3 一致性強化**
   - 顏色、字階、形狀已全部對齊 `MaterialTheme.colorScheme`、`typography`、`shapes`：
     資訊卡片與可展開行使用 `shapes.large`（24dp），工具方塊使用 `shapes.medium`（18dp），
     膠囊按鈕使用完全圓角。
   - 所有可點擊元件（卡片、方塊、按鈕、tab）均保留 Material 3 `ripple` 觸控回饋，
     並與玻璃/磨砂背景共存。
   - 深淺色主題沿用既有語義色表（`DeviceColors` / `LocalDeviceColors`），
     同時完整映射到 `MaterialTheme.colorScheme` 的 light / dark scheme，
     確保 Material 3 元件（進度條、對話框、開關…）風格一致。

---

## 2. 技術選型

| 項目 | 版本 |
|---|---|
| Kotlin | 2.0.21 |
| AGP / Gradle | 8.7.3 / 8.9 |
| Compose BOM | 2024.12.01（Material 3 1.3.1） |
| 液態玻璃 | `com.github.QWEA0:liquidglass:v2.0.11`（JitPack） |

沒有引入 Navigation、Hilt、Retrofit 這類框架：整個 App 只有一個倉庫、八個頁面，
用 `enum` 目的地 + `HorizontalPager` 切換就夠，少一層依賴少一層故障面。

---

## 3. 架構：單向資料流

```
                        intent (refresh / 展開感測器 / 切頁)
   ┌──────────────┐  ─────────────────────────────────────▶  ┌────────────────┐
   │   Compose    │                                          │  DeviceViewModel│
   │   Screens    │  ◀─────────────────────────────────────  │  (StateFlow)    │
   └──────────────┘        DeviceUiState (Loading/Error/Ready)└───────┬────────┘
                                                                      │ suspend / Flow
                                                              ┌───────▼────────┐
                                                              │DeviceRepository│
                                                              └───────┬────────┘
                                        ┌─────────────┬───────────────┼──────────────┐
                                   DeviceCollector  CpuReader     BatteryProbe   SensorProbe
                                   (Build/getprop)  (/sys/proc)   (BatteryMgr)   (SensorMgr)
```

- `DeviceUiState` 是 sealed interface：`Loading` / `Error(title, detail)` / `Ready(snapshot, live)`。
  UI 不持有可變狀態，只渲染傳入的 state，所有互動都往上拋意圖。
- 靜態資訊（機型、SoC、應用清單…）載入一次；動態資訊（CPU 頻率、負載、電池、記憶體）由
  `observeLive()` 以 1 秒為間隔發送 `LiveSnapshot`，`loadHistory` 保留 64 個採樣點給波形圖。
- 感測器用 `callbackFlow`：**只有在展開某一項時才註冊監聽**，收起或離開頁面立即註銷。
  幾十個感測器常駐監聽會直接把待機功耗拉起來。

### 錯誤處理的分層

1. **單項採集隔離**：`DeviceCollector.collect()` 裡每個欄位都包在 `guard(fallback) { ... }` 中。
   某個 ROM 上 GPU 節點讀不到、或 `CameraManager` 拋異常，只讓那張卡片顯示「—」，其他照常。
2. **節點讀取**：`SysFs` 對 `/sys`、`/proc` 一律「讀不到就 null」，不向外拋。
3. **權限受限欄位**：SSID、序號、網路制式等需要敏感權限的欄位直接降級為「—」或提示，不申請執行時權限。
4. **頂層**：ViewModel 捕獲整個快照的失敗，落到 `Error` 頁並提供重試。

---

## 4. 液態玻璃的接入方式（重點）

### 4.1 這個庫的實際形態

`QWEA0/Liquid-Glass-Android` 是 **View 體系**的庫（`LiquidGlassView : FrameLayout`），
沒有 Composable API。核心能力：

- 圓角矩形 SDF 驅動的折射（邊緣壓縮環）
- 三通道色散（邊緣光譜）
- 重力感測器驅動的鏡面高光
- 背景亮度自適應染色（Regular / Clear 兩檔材質）

管線分層：**API 33+ 走單 pass AGSL + RenderEffect**（零 Bitmap 分配）；24–32 走 C++/NEON 模糊（`libnativegauss.so`）。

### 4.2 Compose 裡的接法

`ui/components/GlassSurface.kt` 的 `GlassPanel`：

```kotlin
Box(modifier) {
    AndroidView(Modifier.matchParentSize(), factory = { ctx ->
        LiquidGlassView(ctx).apply {
            enablePressEffect = false      // 關鍵：否則它會吞掉點擊
            cornerRadius = radiusPx
            material = GlassMaterial.REGULAR
            glassTint = tintArgb
            dispersionStrength = 0.12f
            refractionHeight = 110f
            bevelWidth = 24f
            GlassBackdrop.install(this, palette)   // 折射介質：合成漸變，見 4.3
        }
    })
    // 淺色主題補一層白霧（見 4.3 末段）
    content()                              // 內容疊在玻璃上
}
```

兩個關鍵決策：

1. **玻璃在下、內容在上。** `LiquidGlassView.onTouchEvent()` 無條件 `return true`，
   把內容放在它下面會導致整塊區域點不動。所以玻璃只當背景材質，互動交給上面的 Compose 內容。
2. **只給浮層用。** 頂部導航條、對話框、工具入口。十幾張內容卡片不用玻璃 ——
   那是效能自殺，而且會把正文可讀性拉低。iOS 26 的語彙本身也是「玻璃用在 chrome 層」。

### 4.3 折射介質：為甚麼不能捕獲真實背景（附兩次真機崩潰的根因）

最初的做法是把 `backdropSource` 指向 `LocalView.current`（整個 Compose 宿主），
讓玻璃折射頁面真實內容。**這在真機上必崩**，而且兩條可能的迂迴路都是死路：

| 方案 | 崩潰 | 根因 |
|---|---|---|
| 玻璃 `onDraw` 內同步重畫宿主 | `IllegalStateException: Recording currently in progress - missing #endRecording() call?` | 玻璃是 `AndroidView`，它自己的繪製就發生在宿主的 `GraphicsLayer.record()` 內部；再重畫宿主 = 對同一圖層重複 `beginRecording` |
| 繪製流程外用**軟體畫布**快照宿主 | `IllegalArgumentException: Software rendering doesn't support drawRenderNode` | Compose 的拉伸回彈（`DrawStretchOverscrollModifier`）與 `LazyLayoutItemAnimator` 只能畫到硬體畫布，一滾動就炸 |

結論：**宿主內容根本無法在玻璃 draw 期間安全取得**。所以改為合成折射介質
（`ui/components/GlassBackdropCache.kt` 中的 `GlassBackdrop`）：

```kotlin
glass.setCustomBackdropCapture { bounds ->
    // 只回快取點陣圖，絕不畫任何 View → 不可能重入
    Bitmap.createBitmap(gradient(bounds), 0, 0, w, h)
}
```

漸變的色標直接取自主題（`BackgroundTop / Background / BackgroundBottom` + 綠色輝光），
和頁面根背景同源。庫的 SDF 折射、色散、邊緣高光、自適應染色照常跑 GPU 管線，
只是背景內容固定為環境光而非滾動中的卡片。在這種低對比深色漸變界面上，觀感與真折射幾乎無差別，
而開銷是「每個尺寸 × 主題渲染一次，之後全程複用」。

> 代價講清楚：玻璃掠過彩色內容（例如應用程式圖示牆）時，邊緣不會跟著內容扭曲。
> 換來的是零崩潰 —— 這是目前唯一能同時保留折射觀感與穩定性的做法。

**淺色主題的坑：** 庫的亮度自適應（`adaptiveTintColor`）偵測到亮背景時會強制疊
14–22% 黑「壓暗保可讀性」，會把玻璃壓成一塊灰、標籤也跟著發灰，且完全忽略傳入的
`glassTint`。解法是主題裡帶一個 `GlassVeil`（淺色 = 62% 白，深色 = 全透明），
在玻璃之上、內容之下補一層白霧把亮度拉回來。

### 4.4 啟用條件與降級

```kotlin
val enabled = apiReady && isArm && nativeLoaded
// apiReady   = SDK_INT >= 33（33 以下會退到 CPU 位圖管線，慢且耗電）
// isArm      = SUPPORTED_ABIS 含 arm（AAR 只帶 arm64-v8a / armeabi-v7a 的 .so）
// nativeLoaded = System.loadLibrary("nativegauss") 成功
```

任一條件不滿足 → `GlassFallback`：垂直漸變 + 頂部高光描邊 + 半透明疊層。
視覺接近，不做折射。系統頁「構建資訊 → 液態玻璃」會顯示目前是啟用還是降級、為什麼降級。

> 為什麼要擋 x86：AAR 裡只有 arm 的 `.so`，模擬器上 `loadLibrary` 會拋 `Error`（不是 Exception，
> `catch (e: Exception)` 接不住）。這是硬性攔截，不是保險。

### 4.5 對話框為什麼不用這個庫

`Dialog` 是獨立 Window，玻璃無法捕獲宿主 Activity 的內容（`backdropSource` 只能指向同一 Window 內的 View）。
所以對話框改用系統能力：

```kotlin
window.addFlags(FLAG_BLUR_BEHIND)
window.setBackgroundBlurRadius(26.dp.roundToPx())   // API 31+
window.setDimAmount(0.42f)
```

這是對整個螢幕背後做實時模糊，才是對話框場景下真正正確的玻璃，而且零額外開銷。
配合 `RoundedCornerShape(30.dp)` + 半透明深色漸變 + 1dp 高光描邊，得到圓角玻璃彈窗。

---

## 5. 專案結構

```
app/src/main/java/com/deviceinfo/
├── MainActivity.kt                  edge-to-edge 入口
├── DeviceiNfoApplication.kt         倉庫單例（手動 DI）
├── core/Fmt.kt                      統一格式化（容量 / 時長 / 百分比）
├── data/
│   ├── DeviceRepository.kt          唯一資料入口，suspend 快照 + Flow 實時流
│   ├── model/Models.kt              全部資料模型 + DeviceUiState
│   └── internal/
│       ├── SysFs.kt                 /sys /proc 安全讀取
│       ├── CpuReader.kt             頻率採樣 + 集群劃分 + /proc/stat 負載差分
│       ├── GpuProbe.kt              kgsl 節點 → 離屏 EGL → 未知
│       ├── BatteryProbe.kt          粘性廣播 + BatteryManager
│       ├── NetworkProbe.kt          ConnectivityManager / WifiManager / NetworkInterface
│       ├── AppScanner.kt            PackageManager + APK 體積
│       ├── CameraProbe.kt           Camera2 靜態參數（不開設備，免權限）
│       ├── SensorProbe.kt           感測器枚舉 + callbackFlow 訂閱
│       └── DeviceCollector.kt       Build/getprop 組裝 + 逐項容錯
└── ui/
    ├── DeviceViewModel.kt           UDF 狀態機
    ├── DeviceiNfoRoot.kt            背景光暈 / 頂部浮層 / 頁面切換 / 響應式列數
    ├── theme/Theme.kt               DeviceColors 深淺兩套 + 主題模式 + 字階 + 形狀
    ├── components/
    │   ├── GlassSurface.kt          GlassSupport / GlassPanel / GlassDialog
    │   ├── GlassBackdropCache.kt    折射介質（合成漸變，避開宿主捕獲重入）
    │   ├── GlassTabBar.kt           玻璃頂部導航
    │   ├── Components.kt            卡片 / 鍵值網格 / 波形圖 / 環形進度 / 工具格
    │   ├── SocBrandLogo.kt          處理器品牌風格化徽標
    │   ├── PullRefresh.kt           LocalPullRefresh（全域下拉刷新句柄）
    │   └── PermissionCard.kt        運行時權限申請卡片
├── widget/
│   └── BatteryWidgetProvider.kt    桌面小工具（電池 / 記憶體 / 儲存）
├── service/
│   └── BatteryOverlayService.kt    電池懸浮窗前台服務
    ├── navigation/DeviceTab.kt      8 個目的地
    └── screens/                     8 個頁面
```

## 6. 建置

用 Android Studio 直接開啟專案目錄即可（Gradle 會自行同步）。

命令列：

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug     # 產出 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # 18 個 Robolectric 測試
./gradlew :app:connectedDebugAndroidTest  # 5 個真機 UI 測試（需連裝置）
./gradlew :app:lintDebug
```

安裝：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `local.properties` 裡的 `sdk.dir` 指向本機 SDK 路徑，換機器時需要改。
> 首次同步會從 JitPack 拉液態玻璃的 AAR，需要能連 `jitpack.io`。

## 7. 已驗證與未驗證

**已做**
- `assembleDebug` 建置通過，APK 內含 arm64-v8a / armeabi-v7a 的 `libnativegauss.so`
- v1.4.0 完成 Dashboard 與效能評估功能後重新建置
- Lint 通過（0 error，僅剩依賴版本提示）
- **18 個 Robolectric 單元測試全部通過**：
  - 8 個頁面 × 「滿載資料 / 空資料（集合全空、數值全 0）」共 15 個 composition 測試
    （應用程式頁含「同意前只顯示權限卡片 / 同意後顯示清單」兩態）
  - 3 個資料層冒煙測試（含 `observeLive()` 在超時內發送）
- **5 個真機 instrumented 測試全部通過**（Xiaomi 24122RKC7G / Android 16）：
  - 8 個頁面逐頁切換 + 每頁上下滾動（覆蓋拉伸回彈與列表項目動畫的繪製路徑）
  - 玻璃對話框開 → 關
  - 主題按鈕連按三圈（跟隨系統 → 深 → 淺 → 跟隨系統）
  - 3 輪 8 頁往返切換後進程仍存活
- 三種主題模式都在真機截圖核對過配色（深色 / 淺色 / 跟隨系統）。
- 這兩批測試各抓到一個真實崩潰並已修復：
  1. `EGL14.EGL_NO_DISPLAY` 在部分實作下是 null，原本的 `var display: EGLDisplay = ...` 直接 NPE
     並拖垮整個採集（Robolectric 抓到，改為可空 + 逐項 `guard`）。
  2. 玻璃同步重畫宿主 → `Recording currently in progress`（真機抓到，見 4.3）。
  3. 軟體畫布快照宿主 → `Software rendering doesn't support drawRenderNode`（真機 + monkey 抓到，見 4.3）。
  4. 懸浮窗 `CalledFromWrongThreadException`（真機抓到，見 v1.5.1）。
- v1.5.1 在真機（Xiaomi 23113RKC6C / Android 17 / API 37）實測懸浮窗：
  開啟 → 讀數正確（`88% / 33.9°C / 396 mW 入 / USB`）→ 拖動 → 關閉，logcat 無 FATAL，
  `dumpsys activity services` 確認 `isForeground=true foregroundId=42 types=0x40000000`。

**未驗證**
- 各家 ROM 的 `/sys` 節點讀取成功率（HyperOS 部分機型限制 `/proc/stat`，此時 CPU 負載退化為頻率佔峰值比例）
- 對話框的 `setBackgroundBlurRadius` 在非 HyperOS 機型上的表現
- 平板上 3–4 列佈局的實際觀感（`rememberResponsiveColumns` 只按寬度分檔，未在真平板驗證）
- 桌面小工具在第三方啟動器（Nova / Lawnchair）上的 RemoteViews 渲染與尺寸適配

## 8. 已知限制

- **MIUI / HyperOS 會拒絕後台重啟前台服務**（logcat 中的 `Reject RestartService`），
  懸浮窗被系統清掉後不會自動恢復，需要重新點一次「電池監測器」。
  要自動恢復得在系統設定裡給本應用開「自啟動」。
- 懸浮窗位置不持久化，每次開啟都回到預設位置（狀態列下方）。
- 序列號、Wi-Fi SSID、藍牙 MAC 這類欄位在 Android 10+ 受系統限制，需要系統簽名權限或定位權限，
  本專案不申請，統一顯示「受系統限制」。
- `深度睡眠` 用 `elapsedRealtime - uptimeMillis` 近似（裝置休眠時前者走、後者不走），
  含淺睡，不是核心 suspend 統計。
- 應用圖示在列表滾動時異步解碼，首次進入應用程式頁會有短暫佔位。
- 部分 HyperOS / ColorOS 機型限制 `/proc/stat`，此時 CPU 負載退化為「頻率佔峰值比例」近似值。
