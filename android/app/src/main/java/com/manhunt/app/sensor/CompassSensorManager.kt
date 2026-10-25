package com.manhunt.app.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp

/**
 * Publishes the direction the top edge of the screen faces, corrected to true north.
 * Rotation vector is preferred; accelerometer + magnetometer is used when unavailable.
 */
class CompassSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuthFlow = MutableStateFlow(0f)
    val azimuthFlow: StateFlow<Float> = _azimuthFlow.asStateFlow()

    private val _hasHeadingFlow = MutableStateFlow(false)
    val hasHeadingFlow: StateFlow<Boolean> = _hasHeadingFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val screenRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelerometerValues = FloatArray(3)
    private val magneticFieldValues = FloatArray(3)

    private var hasAccelerometerValues = false
    private var hasMagneticFieldValues = false
    private var isListening = false
    private var lastSensorTimestampNanos = 0L

    @Volatile
    private var magneticDeclinationDegrees = 0f

    fun startListening() {
        if (isListening) return

        val rotationRegistered = rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } == true
        if (rotationRegistered) {
            isListening = true
            return
        }

        val accelerometerRegistered = accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: false
        val magneticFieldRegistered = magneticFieldSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: false

        if (accelerometerRegistered && magneticFieldRegistered) {
            isListening = true
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        isListening = false
        hasAccelerometerValues = false
        hasMagneticFieldValues = false
        lastSensorTimestampNanos = 0L
        _hasHeadingFlow.value = false
    }

    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double = 0.0) {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            (latitude == 0.0 && longitude == 0.0)
        ) return

        magneticDeclinationDegrees = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitudeMeters.toFloat(),
            System.currentTimeMillis()
        ).declination
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                publishScreenHeading(event.timestamp)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(accelerometerValues)
                hasAccelerometerValues = true
                updateFallbackHeading(event.timestamp)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                event.values.copyInto(magneticFieldValues)
                hasMagneticFieldValues = true
                updateFallbackHeading(event.timestamp)
            }
        }
    }

    private fun updateFallbackHeading(timestampNanos: Long) {
        if (!hasAccelerometerValues || !hasMagneticFieldValues) return
        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerValues,
                magneticFieldValues
            )
        ) {
            publishScreenHeading(timestampNanos)
        }
    }

    private fun publishScreenHeading(timestampNanos: Long) {
        val (axisX, axisY) = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 ->
                SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 ->
                SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 ->
                SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else ->
                SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        if (!SensorManager.remapCoordinateSystem(
                rotationMatrix,
                axisX,
                axisY,
                screenRotationMatrix
            )
        ) return

        SensorManager.getOrientation(screenRotationMatrix, orientationAngles)
        val magneticHeading = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val trueHeading = normalizeDegrees(magneticHeading + magneticDeclinationDegrees)
        val previousHeading = _azimuthFlow.value

        if (!_hasHeadingFlow.value || lastSensorTimestampNanos == 0L) {
            _azimuthFlow.value = trueHeading
            _hasHeadingFlow.value = true
            lastSensorTimestampNanos = timestampNanos
            return
        }

        val elapsedSeconds = ((timestampNanos - lastSensorTimestampNanos).coerceAtLeast(1L) / 1_000_000_000.0)
            .coerceAtMost(1.0)
        val alpha = (1.0 - exp(-elapsedSeconds / 0.15)).toFloat().coerceIn(0.08f, 0.85f)
        val difference = ((trueHeading - previousHeading + 540f) % 360f) - 180f
        _azimuthFlow.value = normalizeDegrees(previousHeading + alpha * difference)
        lastSensorTimestampNanos = timestampNanos
    }

    private fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
