package com.deviceinfo.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.deviceinfo.R

/** 頂部導航的全部目的地。順序即展示順序，和系統資訊工具的慣例一致。 */
enum class DeviceTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Dashboard(R.string.tab_dashboard, Icons.Outlined.Dashboard),
    Hardware(R.string.tab_hardware, Icons.Outlined.Memory),
    System(R.string.tab_system, Icons.Outlined.SettingsSuggest),
    Battery(R.string.tab_battery, Icons.Outlined.BatteryFull),
    Network(R.string.tab_network, Icons.Outlined.Wifi),
    Apps(R.string.tab_apps, Icons.Outlined.Apps),
    Camera(R.string.tab_camera, Icons.Outlined.PhotoCamera),
    Sensors(R.string.tab_sensors, Icons.Outlined.Sensors)
}
