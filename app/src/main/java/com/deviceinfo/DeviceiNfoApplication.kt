package com.deviceinfo

import android.app.Application
import android.content.Context
import com.deviceinfo.core.L10n
import com.deviceinfo.core.LanguageStore
import com.deviceinfo.data.DeviceRepository

class DeviceiNfoApplication : Application() {

    /** 手動依賴注入：只有一個倉庫，沒必要引入 DI 框架。 */
    val repository: DeviceRepository by lazy { DeviceRepository(this) }

    /**
     * 應用內語言：非 Compose 作用域（探測器、懸浮窗、小工具）取字串都經過
     * `applicationContext`，所以這裡也要套一次 locale，否則資料層會留在舊語言。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageStore.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // 非 Compose 作用域的字串都要靠它（見 core/L10n.kt）
        L10n.init(this)
    }
}
