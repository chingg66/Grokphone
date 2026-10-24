package com.deviceinfo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device smoke tests.
 *
 * The crash classes this project hit (GraphicsLayer re-entrancy, software-canvas
 * drawRenderNode) only reproduce on a real hardware rendering pipeline; Robolectric's
 * fake renderer cannot catch them. Every screen draw, list scroll, glass tab switch,
 * glass dialog and the theme toggle is exercised here for real; any draw-time
 * exception fails the test.
 *
 * Note: assertions use testTag only. Chinese string literals in test sources get
 * mangled by the toolchain encoding, so text/content-description matching is avoided.
 * Lazy containers only compose visible items, so navigation uses performScrollToNode.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val tabs = listOf(
        "tab:Dashboard", "tab:Hardware", "tab:System", "tab:Battery",
        "tab:Network", "tab:Apps", "tab:Camera", "tab:Sensors"
    )

    private fun goto(tag: String) {
        compose.onNodeWithTag("tab_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun launchShowsHeader() {
        compose.onNodeWithTag("theme_toggle").assertExists()
    }

    @Test
    fun visitEveryTabAndScroll() {
        tabs.forEach { tag ->
            goto(tag)

            // 上下各滾一次，覆蓋列表拉伸回彈與項目動畫的繪製路徑
            compose.onNodeWithTag("content_list").performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onNodeWithTag("content_list").performTouchInput { swipeDown() }
            compose.waitForIdle()
        }
    }

    @Test
    fun glassDialogOpenAndDismiss() {
        goto("tab:Dashboard")
        compose.onNodeWithTag("content_list").performScrollToNode(hasTestTag("tool:benchmark"))
        compose.onNodeWithTag("tool:benchmark").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("dialog_confirm").performClick()
        compose.waitForIdle()
    }

    @Test
    fun themeToggleCyclesThroughAllModes() {
        repeat(3) {
            compose.onNodeWithTag("theme_toggle").performClick()
            compose.waitForIdle()
        }
        // 轉完一圈回到跟隨系統，按鈕仍在
        compose.onNodeWithTag("theme_toggle").assertExists()
    }

    @Test
    fun repeatedTabSwitchingStaysAlive() {
        repeat(3) {
            tabs.forEach { tag ->
                goto(tag)
            }
        }
        compose.onNodeWithTag("theme_toggle").assertExists()
    }
}
