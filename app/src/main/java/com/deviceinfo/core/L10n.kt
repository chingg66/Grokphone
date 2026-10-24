package com.deviceinfo.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * 非 Compose 作用域（Repository / Probe / Service / Widget / ViewModel）的字串取用入口。
 *
 * Composable 一律用 `stringResource(R.string.x)`，不要走這裡；
 * 這裡只服務「拿不到 CompositionLocal」的呼叫點。
 * 語言切換會重建進程，所以建立期取一次字串不會有過期翻譯問題。
 */
object L10n {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        // 應用內語言切換後要重新 init，所以這裡順手套上 locale 覆寫，
        // 否則資料層（探測器 / 懸浮窗 / 小工具）會停在舊語言。
        appContext = LanguageStore.wrap(context.applicationContext)
    }

    /** 取字串；尚未初始化（單測環境）時回空字串，不拋異常。 */
    fun s(@StringRes id: Int, vararg args: Any): String =
        appContext?.getString(id, *args) ?: ""
}
