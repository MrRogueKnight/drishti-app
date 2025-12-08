package io.github.mrroguekknight.drishti.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

class ImuManager(context: Context): SensorEventListener {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val acc: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  private val handlerThread = HandlerThread("ImuSensorThread")
  private val sensorHandler: Handler

  init {
    handlerThread.start()
    sensorHandler = Handler(handlerThread.looper)
  }

  fun start() {
    sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
    sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
  }

  fun stop() {
    sensorManager.unregisterListener(this)
    handlerThread.quitSafely()
  }

  override fun onSensorChanged(event: SensorEvent) {
    // It's good practice to use the event timestamp for sensor fusion
    val timestamp = event.timestamp

    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
      val ax = event.values[0]
      val ay = event.values[1]
      val az = event.values[2]
      // TODO: Package and send to a repository/flow
    } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
      val gx = event.values[0]
      val gy = event.values[1]
      val gz = event.values[2]
      // TODO: Package and send to a repository/flow
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
      // Can be used to assess sensor reliability
  }
}
