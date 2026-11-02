package com.nakamura196.kotenocr.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Gravity-based camera level guide (`TiltMonitor.swift` + `LevelGuideView.swift` port).
 *
 * Detects whether the phone is held roughly flat over a desk (`Flat`) or upright facing a vertical
 * surface (`Upright`), and computes the 2D bubble offset and parallel-hold hint when tilted > 8°.
 */
class TiltMonitor(context: Context) : SensorEventListener {

    sealed interface TiltState {
        data class Flat(val deviation: Double) : TiltState
        data class Upright(val deviation: Double) : TiltState
        data object Tilted : TiltState

        val isActive: Boolean
            get() = this != Tilted

        val showsHint: Boolean
            get() = when (this) {
                is Flat -> deviation > HINT_THRESHOLD
                is Upright -> deviation > HINT_THRESHOLD
                Tilted -> false
            }
    }

    data class Gravity(
        val x: Double = 0.0,
        val y: Double = 0.0,
        val z: Double = -1.0
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravitySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _state = MutableStateFlow<TiltState>(TiltState.Tilted)
    val state: StateFlow<TiltState> = _state.asStateFlow()

    private val _gravity = MutableStateFlow(Gravity())
    val gravity: StateFlow<Gravity> = _gravity.asStateFlow()

    private var filteredX = 0.0
    private var filteredY = 0.0
    private var filteredZ = -1.0
    private var initialized = false

    fun start() {
        val sensor = gravitySensor ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        _state.value = TiltState.Tilted
        initialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        // Android TYPE_GRAVITY / TYPE_ACCELEROMETER reports upward reaction force in m/s²
        // (e.g. +9.81 on Z when lying face-up on a desk). Convert to iOS CMMotionManager
        // gravity convention in g units where downward gravity is (0, 0, -1).
        val gx = -event.values[0].toDouble() / GRAVITY_EARTH
        val gy = -event.values[1].toDouble() / GRAVITY_EARTH
        val gz = -event.values[2].toDouble() / GRAVITY_EARTH

        if (!initialized) {
            filteredX = gx
            filteredY = gy
            filteredZ = gz
            initialized = true
        } else {
            val alpha = 0.25
            filteredX += alpha * (gx - filteredX)
            filteredY += alpha * (gy - filteredY)
            filteredZ += alpha * (gz - filteredZ)
        }

        val g = Gravity(filteredX, filteredY, filteredZ)
        _gravity.value = g
        _state.value = classify(g.x, g.y, g.z)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val GRAVITY_EARTH = 9.80665
        const val ZONE_HALF_WIDTH = 30.0
        const val HINT_THRESHOLD = 8.0

        fun classify(x: Double, y: Double, z: Double): TiltState {
            val norm = sqrt(x * x + y * y + z * z)
            if (norm <= 0.3) return TiltState.Tilted
            val cosDown = (-z / norm).coerceIn(-1.0, 1.0)
            val fromDown = acos(cosDown) * 180.0 / PI
            if (fromDown <= ZONE_HALF_WIDTH) {
                return TiltState.Flat(deviation = fromDown)
            }
            val fromHorizontal = abs(fromDown - 90.0)
            if (fromHorizontal <= ZONE_HALF_WIDTH) {
                return TiltState.Upright(deviation = fromHorizontal)
            }
            return TiltState.Tilted
        }
    }
}
