package com.kaiharimoto.mastertool.ui.fx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.kaiharimoto.mastertool.core.motion.HeadTilt
import kotlin.math.PI

/**
 * `TYPE_GAME_ROTATION_VECTOR`, and the choice of sensor is the whole of it.
 *
 * Not `TYPE_ROTATION_VECTOR`, which folds in the magnetometer to know true
 * north. Nothing on this table cares which way north is, and the cost of asking
 * is that the reading **jumps** whenever the compass re-solves itself — near a
 * laptop, near a case magnet, near a radiator — which would show up as the room
 * lurching for no reason a person could connect to anything they did. The game
 * variant drifts slowly in yaw instead, which is invisible here because
 * `HeadSway` measures against its own reference rather than against the world.
 *
 * Falls back to `TYPE_GAME_ROTATION_VECTOR`'s absence gracefully: a device
 * without one gets no sensor and no samples, which `HeadSway` already treats as
 * "no posture has been seen", so the camera simply never sways.
 */
@Composable
actual fun DeviceTilt(enabled: Boolean, onTilt: (HeadTilt) -> Unit) {
    val context = LocalContext.current
    // The callback is captured by a listener that outlives the composition that
    // installed it, which is the same fact about Compose that cost this project
    // three releases of sliders resetting each other. `rememberUpdatedState` is
    // the sanctioned answer and `ui/dnd/DragSource.kt` carries the story.
    val latest = rememberUpdatedState(onTilt)

    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val rotation = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // `getRotationMatrixFromVector` wants at most four components and
                // throws on some vendors' five-element events, which is a crash
                // in a sensor callback and therefore not one the crash reporter
                // makes obvious.
                val values = if (event.values.size > 4) {
                    event.values.copyOf(4)
                } else {
                    event.values
                }
                runCatching {
                    SensorManager.getRotationMatrixFromVector(rotation, values)
                    SensorManager.getOrientation(rotation, angles)
                }.onFailure { return }

                // getOrientation gives azimuth, pitch, roll in radians. The
                // azimuth is deliberately dropped: it changes when you turn your
                // chair, and nothing about the table should.
                latest.value(
                    HeadTilt(
                        pitchDegrees = angles[1] * DEGREES,
                        rollDegrees = angles[2] * DEGREES,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // `SENSOR_DELAY_GAME` is 20ms, which is under a frame at 50Hz and well
        // under one at 120. Faster would be more samples for the low-pass to
        // throw away; `SENSOR_DELAY_UI` at 60ms is visibly behind a wrist.
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
}

/** Written the way `Rot3.DEGREES` is, which is a shape known to be const-legal. */
private const val DEGREES = 180f / PI.toFloat()
