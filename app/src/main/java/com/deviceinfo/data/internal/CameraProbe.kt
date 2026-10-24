package com.deviceinfo.data.internal

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.CameraSpec
import java.util.Locale

/**
 * 相機參數採集（Camera2）。
 *
 * 只讀取靜態 Characteristic，不打開設備，因此不需要 CAMERA 權限。
 * 個別 ROM 的某個 id 會拋異常，這裡按 id 粒度隔離，壞一個不影響其它。
 */
internal class CameraProbe(private val context: Context) {

    fun scan(): List<CameraSpec> {
        val manager = try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (_: Throwable) {
            null
        } ?: return emptyList()

        val ids = try {
            manager.cameraIdList
        } catch (_: Throwable) {
            return emptyList()
        }

        return ids.mapNotNull { id -> runCatching { read(manager, id) }.getOrNull() }
    }

    private fun read(manager: CameraManager, id: String): CameraSpec? {
        val characteristics = manager.getCameraCharacteristics(id)

        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val facingLabel = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> L10n.s(R.string.prb_cam_back)
            CameraCharacteristics.LENS_FACING_FRONT -> L10n.s(R.string.prb_cam_front)
            CameraCharacteristics.LENS_FACING_EXTERNAL -> L10n.s(R.string.prb_cam_external)
            else -> L10n.s(R.string.prb_unknown)
        }

        val pixelArray: Size? = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val megapixels = pixelArray?.let { it.width * it.height / 1_000_000f } ?: 0f

        val aperture = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull()

        val focal = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()

        val physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorSize = physical?.let {
            String.format(Locale.US, "%.2f × %.2f mm", it.width, it.height)
        }

        val flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val fpsRanges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val maxFps = fpsRanges?.maxOfOrNull { it.upper } ?: 0

        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

        return CameraSpec(
            id = id,
            facingLabel = facingLabel,
            megapixels = megapixels,
            pixelCount = pixelArray?.let { it.width.toLong() * it.height } ?: 0L,
            resolution = pixelArray?.let { "${it.width} × ${it.height}" } ?: "—",
            aperture = aperture,
            focalLengthMm = focal,
            sensorSizeMm = sensorSize,
            hasFlash = flash,
            maxFps = maxFps.takeIf { it > 0 },
            zoomRange = maxZoom?.let { String.format(Locale.US, "1.0× – %.1f×", it) }
        )
    }
}
