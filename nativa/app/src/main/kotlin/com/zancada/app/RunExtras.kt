package com.zancada.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Cadencia con el contador de pasos del propio móvil (sin reloj ni banda).
 * Solo cuenta los pasos con la carrera en marcha (no en pausa).
 */
class StepMeter(private val ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private var last = -1f
    var steps = 0; private set
    private val window = ArrayDeque<Pair<Long, Int>>()
    var paused = false
    private var on = false

    fun allowed() = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun start(initial: Int = 0) {
        if (on || !allowed()) return
        val s = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        steps = initial; last = -1f; window.clear()
        on = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() { if (on) sm?.unregisterListener(this); on = false }

    override fun onSensorChanged(e: SensorEvent) {
        val v = e.values.firstOrNull() ?: return
        if (last >= 0 && v >= last && !paused) steps += (v - last).toInt()
        last = v
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    /** Pasos por minuto en los últimos 30 s. */
    fun live(now: Long): Int? {
        if (!on) return null
        window.addLast(now to steps)
        while (window.size > 1 && now - window.first().first > 30_000) window.removeFirst()
        val (t0, s0) = window.first()
        val dt = (now - t0) / 1000.0
        if (dt < 10 || paused) return null
        return ((steps - s0) / dt * 60).toInt().takeIf { it in 60..260 }
    }
}

