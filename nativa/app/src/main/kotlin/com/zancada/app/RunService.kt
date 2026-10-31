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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zancada.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Servicio en primer plano que graba la carrera: sigue recibiendo el GPS con la pantalla apagada
 * o con otra app delante, con una notificación siempre visible. Aquí vive todo lo de la carrera en marcha
 * (medir, guía por tramos, navegación por la ruta, fantasma, avisos de voz), así funciona igual con el móvil bloqueado.
 * La lógica está en :core (RunSession y LiveRun), probada contra la web.
 */
class RunService : LifecycleService() {
    companion object {
        const val PREP = "prep"; const val START = "start"; const val PAUSE = "pause"; const val RESUME = "resume"
        const val FINISH = "finish"; const val SAVE_SHORT = "save_short"; const val DISCARD = "discard"; const val STOP = "stop"
        /** Retomar una carrera que Android cerró a medias (la guarda [LiveStore]). */
        const val RECOVER = "recover"
        private const val CHANNEL = "run"; private const val NOTIF_ID = 1
        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, RunService::class.java).setAction(action)
            if (action == PREP || action == RECOVER || action == START) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private var session = RunSession()
    private var live: LiveRun? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var ticker: Job? = null
    private var listening = false
    private val store by lazy { LiveStore(this) }
    private val cfgFile by lazy { File(filesDir, "live.cfg.json") }
    private var ticks = 0
    private var foreground = false
    private val steps by lazy { StepMeter(this) }
    private val sos by lazy { SosSender(this) }
    /** Tiempo de carrera cuando empezó a contar el sensor de pasos (tras recuperar una carrera no es 0). */
    private var stepsFromSec = 0.0

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
        Repo.init(this); Voice.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Android puede volver a arrancar el servicio sin orden (intent nulo) tras cerrarlo; o la pantalla pide RECOVER.
        if (!session.active && (intent == null || intent.action == RECOVER)) recover()
        when (intent?.action) {
            PREP -> if (!session.active) { goForeground("Buscando GPS…"); startLocation(); RunBus.state.value = RunUi(phase = Phase.PREP, acc = session.lastFix?.acc, fix = session.lastFix?.let { LatLon(it.lat, it.lon) }) }
            START -> if (!session.active) start()
            PAUSE -> { session.pause(System.currentTimeMillis()); persist(); publish() }
            RESUME -> { session.resume(System.currentTimeMillis()); persist(); publish() }
            FINISH -> finishRun(force = false)
            SAVE_SHORT -> finishRun(force = true)
            // Se pidió recuperar pero no había nada (o ya se borró): cerrar limpio. Android exige mostrar la notificación igualmente.
            RECOVER -> if (!session.active) { goForeground("Zancada"); shutdown() }
            null -> if (!session.active) stopSelf()
            DISCARD, STOP -> { if (!session.active || intent.action == DISCARD) { if (intent.action == DISCARD) sos.end(session.dist, session.elapsed(System.currentTimeMillis())); clearLive(); RunBus.state.value = RunUi(); shutdown() } }
        }
        // Con carrera en marcha, si Android mata el servicio le pedimos que lo vuelva a arrancar.
        return if (session.active) START_STICKY else START_NOT_STICKY
    }

    private fun newSession() = RunSession(autopause = Repo.data.value.settings.autopause == "1")

    private fun wire(cfg: RunConfig, startPos: LatLon?) {
        val app = Repo.app()
        val ghost = cfg.route?.let { app.bestOnRoute(it.id) }
        val lv = LiveRun(app, cfg.sess, cfg.route, ghost, startPos, speak = { Voice.say(it, buzz = true) }, toast = { RunBus.toast.value = System.currentTimeMillis() to it })
        live = lv
        session.onKm = { km, split -> lv.onKm(km, split, session, System.currentTimeMillis()) }
        session.onPause = { auto -> Voice.say(if (auto) "Pausa automática" else "Pausa", buzz = true) }
        session.onResume = { Voice.say("Seguimos", buzz = true) }
        session.onPoint = { p -> lv.onPoint(session, p, System.currentTimeMillis()); RunBus.track.value = RunBus.track.value + LatLon(p.lat, p.lon) }
    }

    private fun start() {
        val cfg = RunBus.config
        val prevFix = session.lastFix
        session = newSession()
        prevFix?.let { session.onFix(it.copy(arrival = 0), it.arrival) }
        goForeground("Grabando tu carrera")
        startLocation()
        runCatching { cfgFile.writeText(Store.json.encodeToString(RunConfig.serializer(), cfg)) }
        RunBus.track.value = emptyList(); RunBus.cele.value = null
        wire(cfg, prevFix?.let { LatLon(it.lat, it.lon) })
        session.begin(System.currentTimeMillis())
        stepsFromSec = 0.0; steps.start()
        sos.begin(System.currentTimeMillis(), prevFix?.lat, prevFix?.lon)
        startTicker(); persist(); publish()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                session.tick(now); live?.tick(session, now)
                steps.paused = session.paused
                sos.tick(now, session.dist, session.elapsed(now), session.lastFix?.lat, session.lastFix?.lon, manualPause = session.paused && !session.autoPaused)
                publish()
                if (++ticks % 10 == 0) persist()   // cada 5 s
                delay(500)
            }
        }
    }

    /** Guarda la foto de la carrera en curso. Si falla (disco lleno…), la carrera sigue en memoria. */
    private fun persist() { if (session.active) runCatching { store.save(session.snapshot()) } }
    private fun clearLive() { store.clear(); cfgFile.delete() }

    /** Retoma la carrera guardada, si la hay. El tramo sin GPS mientras estuvo cerrada se une en línea recta. */
    private fun recover() {
        val snap = store.load() ?: return
        try { goForeground("Recuperando tu carrera…") } catch (e: Exception) {
            // Android no deja arrancar el GPS desde segundo plano: se recupera al abrir la app.
            stopSelf(); return
        }
        val cfg = runCatching { Store.json.decodeFromString(RunConfig.serializer(), cfgFile.readText()) }.getOrDefault(RunConfig())
        RunBus.config = cfg
        session = newSession()
        session.restore(snap)
        wire(cfg, null)
        RunBus.track.value = session.points.map { LatLon(it.lat, it.lon) }
        startLocation(); startTicker()
        stepsFromSec = session.elapsed(System.currentTimeMillis()); steps.start()
        sos.begin(System.currentTimeMillis(), session.lastFix?.lat, session.lastFix?.lon, resumed = true)
        Voice.say("Carrera recuperada")
        publish()
    }

    private fun finishRun(force: Boolean) {
        val now = System.currentTimeMillis()
        val res = session.finish(now, forceShort = force)
        ticker?.cancel()
        when (res) {
            is RunSession.Result.Empty -> { sos.cancel(); clearLive(); RunBus.state.value = RunUi(gpsError = "Sin recorrido: no hay nada que guardar"); shutdown() }
            is RunSession.Result.TooShort -> RunBus.state.value = RunBus.state.value.copy(tooShort = res.meters)   // la pantalla pregunta
            is RunSession.Result.Saved -> {
                val cfg = RunBus.config
                val w = Repo.wxFresh()?.let { RunWx(it.t.toInt(), it.wind.toInt(), if (it.now) 1 else 0) }
                val cele = Repo.mutate {
                    val prevBest = runs.maxOfOrNull { it.dist } ?: 0.0
                    val r = decorateRun(res.run, cfg.sess, cfg.route, live?.ghostRef, w).copy(cad = cadenceOf(steps.steps, res.run.dur - stepsFromSec))
                    val nb = addRun(r)
                    val wk = weekStats()
                    Voice.say(finishSpeech(r, nb))
                    Cele(r, nb, celeText(r, nb, prevBest), "${wk.count} de ${goalOf()}")
                }
                Repo.saveNow()
                steps.stop(); sos.end(cele.run.dist, cele.run.dur)
                clearLive()   // solo después de guardarla de verdad
                Reminders.sync(this)
                RunBus.cele.value = cele
                RunBus.state.value = RunUi(phase = Phase.DONE)
                if (listening) { fused.removeLocationUpdates(callback); listening = false }
                lifecycleScope.launch { delay(6000); shutdown() }   // deja terminar la frase de voz
            }
        }
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val el = if (session.active) session.elapsed(now) else 0.0
        val cur = RunBus.state.value
        val lv = live
        RunBus.state.value = cur.copy(
            phase = if (session.active) Phase.RUNNING else cur.phase,
            acc = session.lastFix?.acc, fix = session.lastFix?.let { LatLon(it.lat, it.lon) },
            dist = session.dist, elapsed = el,
            pace = session.currentPace(now),
            avg = if (session.dist > 50) el / (session.dist / 1000) else Double.NaN,
            bestKm = session.bestKm, paused = session.paused, autoPaused = session.autoPaused,
            live = lv?.state ?: cur.live, lockLine = lv?.lockLine() ?: "",
            cad = if (session.active) steps.live(now) else null, sos = sos.active,
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
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zancada).setColor(0xFFD4FF3A.toInt())
            .setContentTitle(if (session.active) "Zancada está grabando tu carrera" else "Zancada · preparando la carrera").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // Botones en la notificación y en la pantalla de bloqueo: pausar, seguir y terminar sin abrir la app.
        if (session.active) {
            if (session.paused) b.addAction(0, "Seguir", action(RESUME)) else b.addAction(0, "Pausa", action(PAUSE))
            b.addAction(0, "Terminar", PendingIntent.getActivity(this, 9, Intent(this, MainActivity::class.java).setAction(FINISH).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
        }
        return b.build()
    }

    private fun action(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, RunService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun goForeground(text: String) {
        ServiceCompat.startForeground(this, NOTIF_ID, notification(text), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        foreground = true
    }

    private fun updateNotification(text: String) {
        if (!foreground) return
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) } catch (e: SecurityException) { }
    }

    private fun shutdown() {
        steps.stop()
        if (listening) { fused.removeLocationUpdates(callback); listening = false }
        foreground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        if (listening) fused.removeLocationUpdates(callback)
        super.onDestroy()
    }
}
