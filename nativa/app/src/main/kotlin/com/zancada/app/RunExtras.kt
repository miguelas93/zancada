package com.zancada.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.zancada.core.Sos

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

/** Modo seguridad: SMS al contacto elegido al empezar, cada N minutos, si te quedas parado y al terminar. */
class SosSender(private val ctx: Context) {
    var active = false; private set
    private var lastSent = 0L
    private var stoppedSince = 0L
    private var lastDist = 0.0
    private var stopAlerted = false
    private var phone = ""
    private var every = 10

    fun allowed() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun begin(now: Long, lat: Double?, lon: Double?, resumed: Boolean = false) {
        val s = Repo.data.value.settings
        phone = Sos.cleanPhone(s.sosPhone ?: "")
        every = (s.sosEvery ?: 10).coerceIn(5, 30)
        active = s.sosOn == "1" && Sos.validPhone(phone) && allowed()
        if (!active) return
        lastSent = now; stoppedSince = 0; stopAlerted = false
        if (!resumed) send(Sos.start(Repo.data.value.profile.name, every, lat, lon))
    }

    /** Cada medio segundo con la carrera en marcha. [manualPause]: el corredor pausó a propósito (no avisar de parada). */
    fun tick(now: Long, dist: Double, el: Double, lat: Double?, lon: Double?, manualPause: Boolean) {
        if (!active || lat == null || lon == null) return
        if (dist - lastDist > 15 || manualPause) { lastDist = dist; stoppedSince = now; stopAlerted = false }
        if (stoppedSince == 0L) stoppedSince = now
        val stopMin = ((now - stoppedSince) / 60_000).toInt()
        if (!stopAlerted && !manualPause && stopMin >= every) { stopAlerted = true; send(Sos.stopped(stopMin, lat, lon)); lastSent = now; return }
        if (now - lastSent >= every * 60_000L) { lastSent = now; send(Sos.update(dist, el, lat, lon)) }
    }

    fun end(dist: Double, el: Double) { if (active) send(Sos.end(dist, el)); active = false }
    fun cancel() { active = false }

    private fun send(text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            @Suppress("DEPRECATION")
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
        }
    }
}
