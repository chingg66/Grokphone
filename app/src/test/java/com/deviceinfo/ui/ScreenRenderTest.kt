package com.deviceinfo.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deviceinfo.R
import com.deviceinfo.core.L10n
import com.deviceinfo.data.internal.AppListConsent
import com.deviceinfo.data.model.BenchmarkState
import com.deviceinfo.ui.screens.AppsScreen
import com.deviceinfo.ui.screens.BatteryScreen
import com.deviceinfo.ui.screens.CameraScreen
import com.deviceinfo.ui.screens.DashboardScreen
import com.deviceinfo.ui.screens.HardwareScreen
import com.deviceinfo.ui.screens.NetworkScreen
import com.deviceinfo.ui.screens.SensorsScreen
import com.deviceinfo.ui.screens.SystemScreen
import com.deviceinfo.ui.theme.DeviceiNfoTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 在 JVM 上真实跑一遍 composition，确保任何页面在“满载数据”和“空数据”下都不会崩。
 *
 * 说明：Robolectric 的 ABI 不是 arm，[com.deviceinfo.ui.components.GlassSupport] 会自动走
 * 降级分支，所以这里覆盖的是降级路径。真机玻璃路径无法在无设备环境验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    // 注意：必須在 @Before 的 L10n.init 之後才建立（資料層標籤靠 L10n 取字串），
    // 所以用 getter 而不是欄位初始化。
    private val snapshot get() = TestSnapshots.snapshot()
    private val live get() = TestSnapshots.live()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** i18n：data 層字串走 L10n，測試環境要手動初始化；斷言一律用資源解析。 */
    @Before
    fun setUp() {
        L10n.init(ctx)
    }

    @Test
    fun dashboard_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme {
                DashboardScreen(
                    snapshot = snapshot,
                    live = live,
                    topPadding = 0.dp,
                    benchmarkState = BenchmarkState.Idle,
                    onStartBenchmark = {},
                    onDismissBenchmark = {},
                    onOpenTab = {}
                )
            }
        }
        compose.onNodeWithText(ctx.getString(R.string.dash_cpu_status)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.dash_battery)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.dash_memory)).assertExists()
        compose.onRoot().assertExists()
    }

    @Test
    fun dashboard_rendersWithEmptyData() {
        val (emptySnapshot, emptyLive) = TestSnapshots.empty()
        compose.setContent {
            DeviceiNfoTheme {
                DashboardScreen(
                    snapshot = emptySnapshot,
                    live = emptyLive,
                    topPadding = 0.dp,
                    benchmarkState = BenchmarkState.Idle,
                    onStartBenchmark = {},
                    onDismissBenchmark = {},
                    onOpenTab = {}
                )
            }
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun hardware_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme { HardwareScreen(snapshot, live, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.hw_soc)).assertExists()
        compose.onNodeWithText("GPU").assertExists()
        // v1.11.0：廠商徽章改用官方標誌，確認圖檔能正常解碼並掛上無障礙描述
        compose.onNodeWithContentDescription(ctx.getString(R.string.sbl_logo_desc, "Qualcomm®")).assertExists()
    }

    @Test
    fun hardware_rendersWithoutClusters() {
        val (emptySnapshot, emptyLive) = TestSnapshots.empty()
        compose.setContent {
            DeviceiNfoTheme { HardwareScreen(emptySnapshot, emptyLive, 0.dp) }
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun system_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme { SystemScreen(snapshot, 0.dp) }
        }
        compose.onAllNodesWithText(ctx.getString(R.string.sys_device))[0].assertExists()
        compose.onNodeWithText(ctx.getString(R.string.sys_os)).assertExists()
    }

    @Test
    fun battery_rendersWithData_andWithoutLiveBattery() {
        compose.setContent {
            DeviceiNfoTheme { BatteryScreen(snapshot, live, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.bat_title)).assertExists()
    }

    @Test
    fun battery_rendersWithoutBatteryData() {
        val (emptySnapshot, emptyLive) = TestSnapshots.empty()
        compose.setContent {
            DeviceiNfoTheme { BatteryScreen(emptySnapshot, emptyLive, 0.dp) }
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun network_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme { NetworkScreen(snapshot, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.net_title_connection)).assertExists()
    }

    @Test
    fun apps_showsPermissionCard_beforeConsent() {
        AppListConsent.setGranted(ApplicationProvider.getApplicationContext(), false)
        compose.setContent {
            DeviceiNfoTheme { AppsScreen(snapshot, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.apps_title_permission)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.apps_allow)).assertExists()
    }

    @Test
    fun apps_rendersWithData_afterConsent() {
        AppListConsent.setGranted(ApplicationProvider.getApplicationContext(), true)
        compose.setContent {
            DeviceiNfoTheme { AppsScreen(snapshot, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.apps_title)).assertExists()
    }

    @Test
    fun apps_rendersEmptyState() {
        val (emptySnapshot, _) = TestSnapshots.empty()
        AppListConsent.setGranted(ApplicationProvider.getApplicationContext(), true)
        compose.setContent {
            DeviceiNfoTheme { AppsScreen(emptySnapshot, 0.dp) }
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun camera_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme { CameraScreen(snapshot, 0.dp) }
        }
        compose.onNodeWithText(ctx.getString(R.string.cam_facing_camera, ctx.getString(R.string.prb_cam_back))).assertExists()
    }

    @Test
    fun camera_rendersEmptyState() {
        val (emptySnapshot, _) = TestSnapshots.empty()
        compose.setContent {
            DeviceiNfoTheme { CameraScreen(emptySnapshot, 0.dp) }
        }
        compose.onRoot().assertExists()
    }

    @Test
    fun sensors_rendersWithData() {
        compose.setContent {
            DeviceiNfoTheme {
                SensorsScreen(
                    snapshot = snapshot,
                    topPadding = 0.dp,
                    observeSensor = { emptyFlow() },
                    sensorUnit = { "" }
                )
            }
        }
        compose.onNodeWithText(ctx.getString(R.string.prb_sensor_accelerometer)).assertExists()
    }

    @Test
    fun sensors_rendersEmptyState() {
        val (emptySnapshot, _) = TestSnapshots.empty()
        compose.setContent {
            DeviceiNfoTheme {
                SensorsScreen(
                    snapshot = emptySnapshot,
                    topPadding = 0.dp,
                    observeSensor = { emptyFlow() },
                    sensorUnit = { "" }
                )
            }
        }
        compose.onRoot().assertExists()
    }
}
