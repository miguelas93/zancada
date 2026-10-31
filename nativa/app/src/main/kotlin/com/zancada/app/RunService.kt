package com.zancada.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zancada.core.Fix
import com.zancada.core.RunSession
import com.zancada.core.fmtKm
import com.zancada.core.fmtPace
import com.zancada.core.fmtTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor

/**
 * Servicio en primer plano que graba la carrera: sigue recibiendo el GPS con la pantalla apagada
 * o con otra app delante (lo que la versión web no podía), con una notificación siempre visible.
 * Toda la lógica de qué puntos valen está en :core (RunSession), probada contra la web.
 */
class RunService : LifecycleService(), TextToSpeech.OnInitListener {
    companion object {
        const val PREP = "prep"; const val START = "start"; const val PAUSE = "pause"; const val RESUME = "resume"
        const val FINISH = "finish"; const val SAVE_SHORT = "save_short"; const val DISCARD = "discard"; const val STOP = "stop"
        /** Retomar una carrera que Android cerró a medias (la guarda [LiveStore]). */
        const val RECOVER = "recover"
        private const val CHANNEL = "run"; private const val NOTIF_ID = 1
        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, RunService::class.java).setAction(action)
            if (action == PREP || action == RECOVER) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private val session = RunSession(autopause = true)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var ticker: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private val live by lazy { LiveStore(this) }
    private var ticks = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Android puede entregar varias lecturas de golpe (pantalla apagada): cada una con su hora real.
            val kmBefore = session.lastKm
            for (l in result.locations) {
                session.onFix(Fix(l.latitude, l.longitude, l.accuracy.toDouble(), if (l.hasAltitude()) l.altitude else null, l.time), System.currentTimeMillis())
            }
            if (session.lastKm != kmBefore) persist()
            publish()
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        session.onKm = { km, split -> say("Kilómetro $km. ${floor(split / 60).toInt()} ${Math.round(split % 60)}."); buzz() }
        session.onPause = { auto -> say(if (auto) "Pausa automática" else "Pausa") }
        session.onResume = { say("Seguimos") }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.forLanguageTag("es-ES")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Android puede volver a arrancar el servicio sin orden (intent nulo) tras cerrarlo; o la pantalla pide RECOVER.
        if (!session.active && (intent == null || intent.action == RECOVER)) recover()
        when (intent?.action) {
            PREP -> { goForeground("Buscando GPS…"); startLocation(); RunBus.state.value = RunUi(phase = Phase.PREP) }
            START -> if (!session.active) {
                session.begin(System.currentTimeMillis())
                say("¡Vamos!")
                startTicker(); persist()
            }
            PAUSE -> { session.pause(System.currentTimeMillis()); persist(); publish() }
            RESUME -> { session.resume(System.currentTimeMillis()); persist(); publish() }
            FINISH -> finishRun(force = false)
            SAVE_SHORT -> finishRun(force = true)
            // Se pidió recuperar pero no había nada (o ya se borró): cerrar limpio. Android exige mostrar la notificación igualmente.
            RECOVER -> if (!session.active) { goForeground("Zancada"); shutdown() }
            null -> if (!session.active) stopSelf()
            DISCARD, STOP -> { live.clear(); RunBus.state.value = RunUi(); shutdown() }
        }
        // Con carrera en marcha, si Android mata el servicio le pedimos que lo vuelva a arrancar.
        return if (session.active) START_STICKY else START_NOT_STICKY
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (isActive) {
                session.tick(System.currentTimeMillis()); publish()
                if (++ticks % 10 == 0) persist()   // cada 5 s
                delay(500)
            }
        }
    }

    /** Guarda la foto de la carrera en curso. Si falla (disco lleno…), la carrera sigue en memoria. */
    private fun persist() { if (session.active) try { live.save(session.snapshot()) } catch (e: Exception) { } }

    /** Retoma la carrera guardada, si la hay. El tramo sin GPS mientras estuvo cerrada se une en línea recta. */
    private fun recover() {
        val snap = live.load() ?: return
        try { goForeground("Recuperando tu carrera…") } catch (e: Exception) {
            // Android no deja arrancar el GPS desde segundo plano: se recupera al abrir la app.
            stopSelf(); return
        }
        session.restore(snap)
        startLocation()
        startTicker()
        say("Carrera recuperada")
        publish()
    }

    private fun finishRun(force: Boolean) {
        val now = System.currentTimeMillis()
        val res = if (force) session.finish(now, forceShort = true) else session.finish(now)
        ticker?.cancel()
        when (res) {
            is RunSession.Result.Empty -> { live.clear(); RunBus.state.value = RunUi(gpsError = "Sin recorrido: no hay nada que guardar"); shutdown() }
            is RunSession.Result.TooShort -> RunBus.state.value = RunBus.state.value.copy(tooShort = res.meters)   // la pantalla pregunta
            is RunSession.Result.Saved -> {
                RunStore(this).add(res.run)
                live.clear()   // solo después de guardarla de verdad
                val r = res.run
                say("Carrera terminada. ${fmtKm(r.dist).replace(",", " coma ")} kilómetros. Ritmo ${fmtPace(r.dur / (r.dist / 1000)).replace(":", " ")}.")
                RunBus.state.value = RunUi(phase = Phase.DONE, result = r)
                if (listening) { fused.removeLocationUpdates(callback); listening = false }
                lifecycleScope.launch { delay(6000); shutdown() }   // deja terminar la frase de voz
            }
        }
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val el = if (session.active) session.elapsed(now) else 0.0
        val cur = RunBus.state.value
        RunBus.state.value = cur.copy(
            phase = if (session.active) Phase.RUNNING else cur.phase,
            acc = session.lastFix?.acc,
            dist = session.dist, elapsed = el,
            pace = session.currentPace(now),
            avg = if (session.dist > 50) el / (session.dist / 1000) else Double.NaN,
            bestKm = session.bestKm, paused = session.paused, autoPaused = session.autoPaused,
        )
        if (session.active) updateNotification("${fmtKm(session.dist)} km · ${fmtTime(el)}${if (session.paused) " · en pausa" else ""}")
        else session.lastFix?.let { updateNotification("GPS listo · ±${Math.round(it.acc)} m") }
    }

    @SuppressLint("MissingPermission")   // la actividad pide el permiso antes de arrancar el servicio
    private fun startLocation() {
        if (listening) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(500L).setWaitForAccurateLocation(false).build()
        try { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()); listening = true }
        catch (e: SecurityException) { RunBus.state.value = RunUi(gpsError = "Sin permiso de ubicación"); shutdown() }
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Grabación de carreras", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zancada).setColor(0xFFD4FF3A.toInt())
            .setContentTitle("Zancada está grabando tu carrera").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
    }

    private fun goForeground(text: String) {
        ServiceCompat.startForeground(this, NOTIF_ID, notification(text), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
    }

    private fun updateNotification(text: String) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) } catch (e: SecurityException) { }
    }

    private fun say(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "z") }

    private fun buzz() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Vibrator::class.java)
        try { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1)) } catch (e: Exception) { }
    }

    private fun shutdown() {
        if (listening) { fused.removeLocationUpdates(callback); listening = false }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        if (listening) fused.removeLocationUpdates(callback)
        tts?.shutdown()
        super.onDestroy()
    }
}
