package com.deviceinfo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deviceinfo.core.LanguageStore
import com.deviceinfo.core.OnboardingStore
import com.deviceinfo.core.SettingsStore
import com.deviceinfo.ui.DeviceViewModel
import com.deviceinfo.ui.DeviceiNfoRoot
import com.deviceinfo.ui.screens.OnboardingScreen
import com.deviceinfo.ui.theme.AppearanceStore
import com.deviceinfo.ui.theme.DeviceiNfoTheme

class MainActivity : ComponentActivity() {

    /** 應用內語言：Android 12 及以下靠這裡換 Configuration（13+ 交給系統 per-app locale）。 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val repository = (application as DeviceiNfoApplication).repository

        setContent {
            // 外觀（深淺 + 色彩）常駐記憶體，改動即時生效並落盤
            var appearance by remember { mutableStateOf(AppearanceStore.load(this)) }
            // 設定（溫度單位 / 電池校正）同樣即時生效
            var appSettings by remember { mutableStateOf(SettingsStore.load(this)) }
            // 用 rememberSaveable：換語言會 recreate，若用 remember 會被重置成
            // 「已完成」而把使用者踢回主頁（從更新日誌重開歡迎頁時特別明顯）。
            var showOnboarding by rememberSaveable { mutableStateOf(!OnboardingStore.isDone(this)) }

            DeviceiNfoTheme(appearance = appearance) {
                Crossfade(
                    targetState = showOnboarding,
                    animationSpec = tween(320),
                    label = "onboardingTransition"
                ) { onboarding ->
                    if (onboarding) {
                        OnboardingScreen(
                            appearance = appearance,
                            onAppearanceChange = { next ->
                                appearance = next
                                AppearanceStore.save(this, next)
                            },
                            onFinish = {
                                OnboardingStore.markDone(this)
                                showOnboarding = false
                            },
                            onSkip = {
                                OnboardingStore.markDone(this)
                                showOnboarding = false
                            }
                        )
                    } else {
                        val viewModel: DeviceViewModel = viewModel(
                            factory = DeviceViewModel.factory(repository)
                        )
                        DeviceiNfoRoot(
                            viewModel = viewModel,
                            appearance = appearance,
                            onAppearanceChange = { next ->
                                appearance = next
                                AppearanceStore.save(this, next)
                            },
                            onReopenOnboarding = { showOnboarding = true },
                            settings = appSettings,
                            onSettingsChange = { next ->
                                appSettings = next
                                SettingsStore.save(this, next)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 系統對小工具的定時更新下限是 30 分鐘，打開應用時順手刷一次
        com.deviceinfo.widget.BatteryWidgetProvider.updateAll(this)
    }
}
