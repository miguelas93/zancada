package com.zancada.core

import kotlin.math.max
import kotlin.math.min

/**
 * Una carrera en marcha: recibe lecturas del GPS y el paso del tiempo, y decide qué puntos valen,
 * cuánta distancia suma, los parciales por km, las pausas y la autopausa.
 * Es una traducción línea a línea de runFix / tick / pauseRun / resumeRun / checkAutoPause / finishRun
 * de la web, y se comprueba contra las mismas carreras de prueba (ver RunSessionParityTest).
 * No sabe nada de Android: el reloj ([now]) se lo pasa quien la usa.
 */
class RunSession(private val autopause: Boolean = true) {
    var active = false; private set
    var paused = false; private set
    var autoPaused = false; private set
    var start = 0L; private set
    var pausedMs = 0L; private set
    private var pauseAt = 0L
    var dist = 0.0; private set
    var lastKm = 0; private set
    val points = mutableListOf<Point>()
    val liveSplits = mutableListOf<Split>()
    private val liveSplitTimes = mutableListOf<Double>()
    var bestKm = Double.POSITIVE_INFINITY; private set
    private var gap = false
    private var clk: Long? = null
    var lastFix: Fix? = null; private set
    private var pausePos: Fix? = null

    /** Avisos para la voz y la vibración. */
    var onKm: ((km: Int, split: Double) -> Unit)? = null
    var onPause: ((auto: Boolean) -> Unit)? = null
    var onResume: (() -> Unit)? = null

    fun begin(now: Long) {
        active = true; paused = false; autoPaused = false; start = now; pausedMs = 0; pauseAt = 0
        dist = 0.0; lastKm = 0; points.clear(); liveSplits.clear(); liveSplitTimes.clear(); bestKm = Double.POSITIVE_INFINITY
        gap = false; clk = null
        lastFix?.let { runFix(it, now) }   // la última lectura de la preparación es el primer punto
        tick(now)
    }

    /** Llega una lectura del GPS (también antes de empezar, para tener posición). */
    fun onFix(f: Fix, now: Long) {
        val fix = f.copy(arrival = now)
        lastFix = fix
        if (active) { if (paused && autoPaused) checkAutoPause(now); runFix(fix, now) }
    }

    private fun runFix(f: Fix, now: Long) {
        if (!active || paused) return
        if (f.acc > 50) return
        val ft = if (f.fixTime != 0L) f.fixTime else if (f.arrival != 0L) f.arrival else now
        val lag = now - ft
        if (clk == null || lag < clk!!) clk = lag
        val t = max(0L, min(now, ft + clk!!) - start - pausedMs)
        val a = f.alt?.let { jsRound(it).toInt() }
        val prev = points.lastOrNull()
        var p = Point(f.lat, f.lon, t, a)
        if (prev != null && gap) { gap = false; p = p.copy(gap = true) }   // primer punto tras una pausa: lo andado no cuenta
        else if (prev != null) {
            val d = haversine(prev.lat, prev.lon, p.lat, p.lon)
            val dt = (p.t - prev.t) / 1000.0
            if (dt <= 0.2) return
            if (d / dt > 8) return       // salto imposible para un corredor
            if (d < 2) return            // ruido estando quieto
            dist += d
            while (dist >= (lastKm + 1) * 1000.0) {
                lastKm++
                val tAtKm = p.t / 1000.0 - (dist - lastKm * 1000.0) / (d / dt)
                val prevT = liveSplitTimes.lastOrNull() ?: 0.0
                val split = tAtKm - prevT
                liveSplits.add(Split(lastKm, split)); liveSplitTimes.add(tAtKm)
                bestKm = min(bestKm, split)
                onKm?.invoke(lastKm, split)
            }
        }
        points.add(p)
    }

    /** Cada medio segundo (y al empezar). */
    fun tick(now: Long) { if (active) checkAutoPause(now) }

    /** Ritmo de los últimos ~30 s en s/km (NaN si no hay dato), como currentPace de la web. */
    fun currentPace(now: Long): Double {
        val pts = points; if (pts.size < 2) return Double.NaN
        val last = pts.last(); var i = pts.size - 2; var d = 0.0
        while (i > 0 && last.t - pts[i].t < 30000) { d += haversine(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon); i-- }
        d += haversine(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
        val dt = (last.t - pts[i].t) / 1000.0
        if (now - start - pausedMs - last.t > 15000) return Double.NaN
        return if (d >= 4) dt / (d / 1000) else Double.NaN
    }

    fun elapsed(now: Long): Double = (now - start - pausedMs - (if (paused) now - pauseAt else 0)) / 1000.0

    fun pause(now: Long, auto: Boolean = false) {
        if (paused) return
        paused = true; autoPaused = auto; pauseAt = now; pausePos = lastFix
        onPause?.invoke(auto)
    }

    fun resume(now: Long) {
        if (!paused) return
        pausedMs += now - pauseAt; paused = false; autoPaused = false; gap = true
        onResume?.invoke()
    }

    private fun checkAutoPause(now: Long) {
        if (!autopause || !active) return
        val lf = lastFix
        if (!paused) {
            val last = points.lastOrNull()
            if (last != null && dist > 30 && lf != null && now - lf.arrival < 6000 && lf.acc <= 30 && (now - start - pausedMs) - last.t > 10000) pause(now, true)
        } else if (autoPaused) {
            val pp = pausePos
            if (pp != null && lf != null && lf.acc <= 40 && haversine(pp.lat, pp.lon, lf.lat, lf.lon) > 12) resume(now)
        }
    }

    /** Foto completa del estado, para guardarla en disco y recuperar la carrera si Android cierra la app. */
    data class Snapshot(
        val active: Boolean, val paused: Boolean, val autoPaused: Boolean, val start: Long, val pausedMs: Long, val pauseAt: Long,
        val dist: Double, val lastKm: Int, val points: List<Point>, val liveSplits: List<Split>, val liveSplitTimes: List<Double>,
        val bestKm: Double, val gap: Boolean, val clk: Long?, val lastFix: Fix?, val pausePos: Fix?,
    )

    fun snapshot() = Snapshot(active, paused, autoPaused, start, pausedMs, pauseAt, dist, lastKm, points.map { it.copy() },
        liveSplits.toList(), liveSplitTimes.toList(), bestKm, gap, clk, lastFix, pausePos)

    fun restore(s: Snapshot) {
        active = s.active; paused = s.paused; autoPaused = s.autoPaused; start = s.start; pausedMs = s.pausedMs; pauseAt = s.pauseAt
        dist = s.dist; lastKm = s.lastKm; points.clear(); points.addAll(s.points.map { it.copy() })
        liveSplits.clear(); liveSplits.addAll(s.liveSplits); liveSplitTimes.clear(); liveSplitTimes.addAll(s.liveSplitTimes)
        bestKm = s.bestKm; gap = s.gap; clk = s.clk; lastFix = s.lastFix; pausePos = s.pausePos
    }

    sealed interface Result {
        data object Empty : Result
        data class TooShort(val meters: Double) : Result
        data class Saved(val run: Run) : Result
    }

    /** Terminar. [forceShort] = guardar aunque sean menos de 50 m. */
    fun finish(now: Long, forceShort: Boolean = false): Result {
        if (paused) pausedMs += now - pauseAt
        active = false
        // Igual que la web: si terminas en pausa, elapsed() descuenta la pausa dos veces (anotado en tests/README.md).
        val endT = elapsed(now)
        if (points.size < 2) return Result.Empty
        if (dist < 50 && !forceShort) return Result.TooShort(dist)
        return Result.Saved(finalizeRun("r$start", start, runName(start), points.map { it.copy() }, endT))
    }
}
