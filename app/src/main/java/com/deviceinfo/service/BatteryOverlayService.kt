package com.deviceinfo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import com.deviceinfo.core.SettingsStore
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.deviceinfo.R
import com.deviceinfo.core.L10n
import com.deviceinfo.data.internal.BatteryProbe
import com.deviceinfo.data.model.BatteryLive

/**
 * 電池懸浮窗服務。
 *
 * 三條硬性約束，踩中任何一條都會直接崩進程：
 *
 * 1. **View 只能由建立它的執行緒觸碰**。輪詢讀數放在主執行緒的 [Handler] 上跑，
 *    絕不另開執行緒後直接 `setText`——那會拋
 *    `ViewRootImpl$CalledFromWrongThreadException`。[BatteryProbe.read] 只讀幾個
 *    system property 與 BatteryManager 緩存值，微秒級，不會卡主執行緒。
 * 2. **前臺服務必須在 startForegroundService 後 5 秒內呼叫 startForeground**，
 *    否則系統拋 `ForegroundServiceDidNotStartInTimeException`。因此
 *    [startForegroundNotification] 是 [onCreate] 的第一件事。
 * 3. **懸浮窗權限可能被隨時收回**，[WindowManager.addView] 必須包容錯，
 *    失敗就自行退場而不是讓異常往外冒。
 */
class BatteryOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pctView: TextView? = null
    private var tempView: TextView? = null
    private var powerView: TextView? = null
    private var stateView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var batteryProbe: BatteryProbe? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateOverlay()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        batteryProbe = BatteryProbe(this)
        // 必須最先執行：Android 8+ 對 startForeground 有 5 秒時限
        startForegroundNotification()
        if (!setupOverlay()) {
            stopSelf()
            return
        }
        isRunning = true
        mainHandler.post(refreshRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服務被系統殺掉後重建時，onCreate 已重新掛窗；這裡只保證回傳黏性
        if (!isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        layoutParams = null
        pctView = null
        tempView = null
        powerView = null
        stateView = null
        isRunning = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用戶從最近任務劃掉：把懸浮窗一併收掉，避免留下孤兒窗口
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /** @return 懸浮窗是否成功掛上；失敗（通常是權限被收回）時呼叫方應立即停止服務。 */
    private fun setupOverlay(): Boolean {
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        windowManager = manager

        val view = runCatching {
            LayoutInflater.from(this).inflate(R.layout.overlay_battery, null)
        }.getOrNull() ?: return false

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        view.findViewById<View>(R.id.overlay_close)?.setOnClickListener { stopSelf() }
        pctView = view.findViewById(R.id.overlay_battery_pct)
        tempView = view.findViewById(R.id.overlay_battery_temp)
        powerView = view.findViewById(R.id.overlay_battery_power)
        stateView = view.findViewById(R.id.overlay_battery_state)
        attachDragHandler(view, params)

        return runCatching {
            manager.addView(view, params)
            overlayView = view
            layoutParams = params
        }.isSuccess
    }

    /** 按住拖動視窗；短按（位移小於觸控滑差）不觸發，以免吃掉關閉鈕的點擊。 */
    private fun attachDragHandler(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (!dragging && kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) dragging = true
                    if (dragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 拖動結束後吃掉這次點擊，避免誤觸關閉
                    dragging
                }

                else -> false
            }
        }
    }

    /** 只會在 main looper 上被呼叫：任何離開這裡的寫法都會炸 CalledFromWrongThread。 */
    private fun updateOverlay() {
        if (overlayView == null) return
        val battery: BatteryLive = runCatching { batteryProbe?.read() }.getOrNull() ?: return

        val settings = SettingsStore.load(this)
        runCatching {
            pctView?.text = "${battery.levelPercent}%"
            tempView?.text = if (battery.temperatureC.isNaN()) "--" else {
                settings.temperature(battery.temperatureC)
            }
            powerView?.text = powerLabel(battery, settings.powerFactor)
            stateView?.text = battery.pluggedLabel
        }
    }

    /**
     * 電壓 × 電流 = 瞬時功率。
     *
     * 單位換算是這裡最容易錯的地方：`voltageMv` 是毫伏，`currentUa` 是微安，
     * 直接相乘得到的是「毫伏·微安」，換成毫瓦要乘 1e-6（不是 1e-3）。
     * 少除三個數量級就會出現「87797 W」這種荒唐讀數。
     *
     * 方向不看電流正負——各廠商對 BATTERY_PROPERTY_CURRENT_NOW 的符號約定不一致
     * （小米負值代表充電），只看是否接著電源。
     */
    private fun powerLabel(battery: BatteryLive, powerFactor: Float = 1.0f): String {
        if (battery.currentUa == 0 || battery.voltageMv <= 0) return "—"
        val mw = battery.voltageMv.toFloat() * kotlin.math.abs(battery.currentUa) / 1_000_000f * powerFactor
        if (mw <= 0f) return "—"
        val direction = L10n.s(if (battery.isPlugged) R.string.svc_power_in else R.string.svc_power_out)
        return if (mw >= 1000f) {
            "%.2f W %s".format(java.util.Locale.US, mw / 1000f, direction)
        } else {
            "%.0f mW %s".format(java.util.Locale.US, mw, direction)
        }
    }

    private fun startForegroundNotification() {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_notification_channel),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
                manager?.createNotificationChannel(channel)
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle(getString(R.string.overlay_notification_channel))
                .setContentText(getString(R.string.overlay_notification_text))
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "battery_overlay"
        private const val NOTIFICATION_ID = 42
        private const val REFRESH_INTERVAL_MS = 2_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        /** 懸浮窗權限是否已授予。 */
        fun hasOverlayPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /**
         * 目前是否正在顯示懸浮窗。服務在應用進程內，因此這個靜態旗標
         * 在進程存活期間就是唯一真相來源。
         */
        fun isActive(): Boolean = isRunning

        fun start(context: Context) {
            if (!hasOverlayPermission(context)) return
            val intent = Intent(context, BatteryOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BatteryOverlayService::class.java)) }
            isRunning = false
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }
    }
}
