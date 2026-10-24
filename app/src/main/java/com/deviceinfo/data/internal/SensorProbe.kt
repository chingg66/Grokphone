package com.deviceinfo.data.internal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.SensorReading
import com.deviceinfo.data.model.SensorSpec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 感測器枚舉 + 實時訂閱。
 *
 * 訂閱採用 callbackFlow：UI 展開某個感測器時才註冊，離開即註銷，
 * 避免幾十個感測器常駐監聽把電耗打上去。
 */
internal class SensorProbe(private val context: Context) {

    private val manager: SensorManager? by lazy {
        try {
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        } catch (_: Throwable) {
            null
        }
    }

    fun scan(): List<SensorSpec> {
        val sensorManager = manager ?: return emptyList()
        val sensors = try {
            sensorManager.getSensorList(Sensor.TYPE_ALL)
        } catch (_: Throwable) {
            return emptyList()
        }
        return sensors.map { sensor ->
            SensorSpec(
                name = sensor.name ?: L10n.s(R.string.prb_sensor_unnamed),
                vendor = sensor.vendor ?: "—",
                type = sensor.type,
                typeLabel = labelOf(sensor.type, sensor.stringType),
                version = sensor.version,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMilliAmp = sensor.power,
                isWakeUp = sensor.isWakeUpSensor
            )
        }.sortedBy { it.type }
    }

    fun observe(type: Int): Flow<SensorReading> = callbackFlow {
        val sensorManager = manager
        if (sensorManager == null) {
            close()
            return@callbackFlow
        }
        val sensor = try {
            sensorManager.getDefaultSensor(type)
        } catch (_: Throwable) {
            null
        }
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val data = event ?: return
                trySend(
                    SensorReading(
                        type = data.sensor.type,
                        values = data.values.copyOf().toList(),
                        timestampNanos = data.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Throwable) {
            false
        }
        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Throwable) {
                // 註銷失敗無副作用
            }
        }
    }

    private fun labelOf(type: Int, stringType: String?): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> L10n.s(R.string.prb_sensor_accelerometer)
        Sensor.TYPE_MAGNETIC_FIELD -> L10n.s(R.string.prb_sensor_magnetic_field)
        Sensor.TYPE_GYROSCOPE -> L10n.s(R.string.prb_sensor_gyroscope)
        Sensor.TYPE_LIGHT -> L10n.s(R.string.prb_sensor_light)
        Sensor.TYPE_PROXIMITY -> L10n.s(R.string.prb_sensor_proximity)
        Sensor.TYPE_PRESSURE -> L10n.s(R.string.prb_sensor_pressure)
        Sensor.TYPE_GRAVITY -> L10n.s(R.string.prb_sensor_gravity)
        Sensor.TYPE_LINEAR_ACCELERATION -> L10n.s(R.string.prb_sensor_linear_accel)
        Sensor.TYPE_ROTATION_VECTOR -> L10n.s(R.string.prb_sensor_rotation_vector)
        Sensor.TYPE_RELATIVE_HUMIDITY -> L10n.s(R.string.prb_sensor_humidity)
        Sensor.TYPE_AMBIENT_TEMPERATURE -> L10n.s(R.string.prb_sensor_ambient_temp)
        Sensor.TYPE_ORIENTATION -> L10n.s(R.string.prb_sensor_orientation)
        Sensor.TYPE_STEP_COUNTER -> L10n.s(R.string.prb_sensor_step_counter)
        Sensor.TYPE_STEP_DETECTOR -> L10n.s(R.string.prb_sensor_step_detector)
        Sensor.TYPE_HEART_RATE -> L10n.s(R.string.prb_sensor_heart_rate)
        Sensor.TYPE_GAME_ROTATION_VECTOR -> L10n.s(R.string.prb_sensor_game_rotation)
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> L10n.s(R.string.prb_sensor_geomagnetic_rotation)
        Sensor.TYPE_SIGNIFICANT_MOTION -> L10n.s(R.string.prb_sensor_significant_motion)
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> L10n.s(R.string.prb_sensor_magnetic_uncalibrated)
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> L10n.s(R.string.prb_sensor_gyro_uncalibrated)
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> L10n.s(R.string.prb_sensor_accel_uncalibrated)
        Sensor.TYPE_POSE_6DOF -> L10n.s(R.string.prb_sensor_pose_6dof)
        Sensor.TYPE_STATIONARY_DETECT -> L10n.s(R.string.prb_sensor_stationary_detect)
        Sensor.TYPE_MOTION_DETECT -> L10n.s(R.string.prb_sensor_motion_detect)
        Sensor.TYPE_HEART_BEAT -> L10n.s(R.string.prb_sensor_heart_beat)
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> L10n.s(R.string.prb_sensor_offbody_detect)
        else -> stringType?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: L10n.s(R.string.prb_sensor_type_fallback, type)
    }

    /** 單位換算：讓讀數帶物理意義，而不是一串裸數字。 */
    fun unitOf(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "μT"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> ""
        Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> L10n.s(R.string.prb_unit_step)
        Sensor.TYPE_HEART_RATE -> "bpm"
        else -> ""
    }
}
