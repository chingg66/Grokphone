package com.deviceinfo.data.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 檢測結果：成功帶 [CameraLiveProbe.LiveResult]，失敗帶錯誤訊息。 */
private typealias LiveOutcome = kotlin.Result<CameraLiveProbe.LiveResult>

/**
 * 相機即時幀檢測：真正打開鏡頭，用感測器支援的最大 YUV 尺寸建立串流，
 * 採樣一段時間後回報「實際輸出解析度」與「實測幀率」。
 *
 * 這是 CAMERA 權限的實際用途 —— 靜態參數（像素、光圈）不需要權限，
 * 只有把鏡頭打開跑起來，才能驗證標稱參數與真實輸出的差異。
 *
 * 所有環節都包在 runCatching 裡：檢測失敗只是該卡片顯示錯誤，絕不拖垮頁面。
 */
internal object CameraLiveProbe {

    data class LiveResult(
        val cameraId: String,
        val facingLabel: String,
        val width: Int,
        val height: Int,
        val fps: Float,
        val frames: Int,
        val nominalPixelCount: Long
    )

    /** 打開預設後置鏡頭（沒有就用第一個可用的），量測 [durationMs] 毫秒。 */
    @SuppressLint("MissingPermission") // 呼叫方（UI 層）保證已取得 CAMERA 權限
    suspend fun measure(context: Context, durationMs: Long = 1800L): LiveOutcome {
        return suspendCancellableCoroutine { cont ->
            val manager = try {
                context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            } catch (_: Throwable) {
                null
            }
            if (manager == null || cont.isCancelled) {
                cont.resume(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_no_service))))
                return@suspendCancellableCoroutine
            }

            val ids = try {
                manager.cameraIdList.toList()
            } catch (_: Throwable) {
                emptyList()
            }
            val cameraId = ids.firstOrNull { id ->
                runCatching {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }.getOrNull() == true
            } ?: ids.firstOrNull()

            if (cameraId == null || cont.isCancelled) {
                cont.resume(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_none_available))))
                return@suspendCancellableCoroutine
            }

            val characteristics = try {
                manager.getCameraCharacteristics(cameraId)
            } catch (_: Throwable) {
                cont.resume(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_read_failed))))
                return@suspendCancellableCoroutine
            }

            val facingLabel = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> L10n.s(R.string.prb_cam_back)
                CameraCharacteristics.LENS_FACING_FRONT -> L10n.s(R.string.prb_cam_front)
                else -> L10n.s(R.string.prb_cam_lens)
            }
            val nominal = characteristics
                .get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                ?.let { it.width.toLong() * it.height } ?: 0L

            val sizes = try {
                characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.YUV_420_888)
                    ?.toList()
                    .orEmpty()
            } catch (_: Throwable) {
                emptyList()
            }
            val target = sizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(640, 480)

            val thread = HandlerThread("deviceinfo-livecam").apply { start() }
            val handler = Handler(thread.looper)

            var device: CameraDevice? = null
            var reader: ImageReader? = null
            var resumed = false
            val timestamps = ArrayList<Long>(64)
            var actualSize: Size? = null

            fun finish(result: LiveOutcome) {
                if (resumed) return
                resumed = true
                try {
                    device?.close()
                } catch (_: Throwable) {
                }
                try {
                    reader?.close()
                } catch (_: Throwable) {
                }
                thread.quitSafely()
                if (cont.isActive) cont.resume(result)
            }

            cont.invokeOnCancellation {
                try {
                    device?.close()
                } catch (_: Throwable) {
                }
                try {
                    reader?.close()
                } catch (_: Throwable) {
                }
                thread.quitSafely()
            }

            fun computeResult(): LiveOutcome {
                val size = actualSize ?: target
                val frames = timestamps.size
                val fps = if (timestamps.size >= 2) {
                    val spanNanos = timestamps.last() - timestamps.first()
                    if (spanNanos > 0) (timestamps.size - 1) * 1_000_000_000f / spanNanos else 0f
                } else {
                    0f
                }
                return if (frames > 0) {
                    LiveOutcome.success(
                        LiveResult(
                            cameraId = cameraId,
                            facingLabel = facingLabel,
                            width = size.width,
                            height = size.height,
                            fps = fps,
                            frames = frames,
                            nominalPixelCount = nominal
                        )
                    )
                } else {
                    LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_no_frames)))
                }
            }

            try {
                reader = ImageReader.newInstance(
                    target.width, target.height, ImageFormat.YUV_420_888, 4
                )
                reader!!.setOnImageAvailableListener({ r ->
                    try {
                        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                        if (actualSize == null) {
                            actualSize = Size(image.width, image.height)
                        }
                        timestamps.add(image.timestamp)
                        image.close()
                    } catch (_: Throwable) {
                    }
                }, handler)

                val stateCallback = object : CameraDevice.StateCallback() {
                    override fun onOpened(opened: CameraDevice) {
                        device = opened
                        try {
                            opened.createCaptureSession(
                                listOf(reader!!.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            val request = opened.createCaptureRequest(
                                                CameraDevice.TEMPLATE_PREVIEW
                                            ).apply {
                                                addTarget(reader!!.surface)
                                                set(
                                                    CaptureRequest.CONTROL_MODE,
                                                    CameraMetadata.CONTROL_MODE_AUTO
                                                )
                                            }.build()
                                            session.setRepeatingRequest(request, null, handler)
                                            handler.postDelayed(
                                                { finish(computeResult()) },
                                                durationMs
                                            )
                                        } catch (e: Throwable) {
                                            finish(LiveOutcome.failure(e))
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        finish(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_stream_failed))))
                                    }
                                },
                                handler
                            )
                        } catch (e: Throwable) {
                            finish(LiveOutcome.failure(e))
                        }
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        try {
                            d.close()
                        } catch (_: Throwable) {
                        }
                        finish(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_disconnected))))
                    }

                    override fun onError(d: CameraDevice, error: Int) {
                        try {
                            d.close()
                        } catch (_: Throwable) {
                        }
                        finish(LiveOutcome.failure(IllegalStateException(L10n.s(R.string.prb_cam_open_failed, error))))
                    }
                }

                // 逾時保險：無論卡在哪一步，2.5 秒後必定收場
                handler.postDelayed({ finish(computeResult()) }, durationMs + 2500)
                manager.openCamera(cameraId, stateCallback, handler)
            } catch (e: Throwable) {
                finish(LiveOutcome.failure(e))
            }
        }
    }

    fun formatResult(result: LiveResult): String = L10n.s(
        R.string.prb_cam_live_result,
        result.facingLabel,
        result.cameraId,
        result.width,
        result.height,
        result.fps,
        result.frames
    )
}
