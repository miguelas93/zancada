package com.zancada.core

import kotlin.math.max
import kotlin.math.min

/** Recalcula distancia, parciales, duración y desnivel a partir de los puntos (finalizeRun de la web). */
fun finalizeRun(id: String, start: Long, name: String, pts: List<Point>, end: Double): Run {
    var dist = 0.0
    val splits = mutableListOf<Split>()
    var lastKm = 0
    var prevT = 0.0
    for (i in 1 until pts.size) {
        val d = if (pts[i].gap) 0.0 else haversine(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        dist += d
        while (dist >= (lastKm + 1) * 1000.0) {
            lastKm++
            val frac = (dist - lastKm * 1000.0) / d
            val tAt = (pts[i].t - (pts[i].t - pts[i - 1].t) * frac) / 1000.0
            splits.add(Split(lastKm, tAt - prevT))
            prevT = tAt
        }
        pts[i].d = dist
    }
    if (pts.isNotEmpty()) pts[0].d = 0.0
    val dur = max(pts.last().t / 1000.0, end)
    val gain = elevGain(pts)
    if (dist - lastKm * 1000.0 > 100) splits.add(Split(lastKm + 1, (dur - prevT) / ((dist - lastKm * 1000.0) / 1000.0), partial = true))
    return Run(id, start, name, pts, dist, dur, splits, gain)
}

/** Desnivel positivo con histéresis de 4 m. Null si hay 10 alturas o menos. */
fun elevGain(pts: List<Point>): Int? {
    var g = 0.0
    var base: Int? = null
    var n = 0
    for (p in pts) {
        val a = p.a ?: continue
        n++
        if (base == null) { base = a; continue }
        if (a - base >= 4) { g += a - base; base = a } else if (base - a >= 4) base = a
    }
    return if (n > 10) jsRound(g).toInt() else null
}

/** Tramo más rápido de [meters] dentro de la carrera, en segundos (Infinity si no hay). */
fun bestEffort(r: Run, meters: Double): Double {
    val p = r.points
    if (p.isEmpty() || r.dist < meters) return Double.POSITIVE_INFINITY
    var best = Double.POSITIVE_INFINITY
    var j = 0
    for (i in 1 until p.size) {
        while (j < i && p[i].d - p[j + 1].d >= meters) j++
        val span = p[i].d - p[j].d
        if (span >= meters) best = min(best, ((p[i].t - p[j].t) / 1000.0) * (meters / span))
    }
    return best
}

/** Mejor marca en [meters] (múltiplo de 1000) sumando parciales completos. Null si no llega. */
fun bestFor(r: Run, meters: Int): Double? {
    val ok = r.splits.filter { !it.partial }
    if (ok.size * 1000 < meters) return null
    val t = mutableListOf(0.0)
    ok.forEach { t.add(t.last() + it.split) }
    val n = meters / 1000
    var best = Double.POSITIVE_INFINITY
    var i = 0
    while (i + n < t.size) { best = min(best, t[i + n] - t[i]); i++ }
    return if (best.isFinite()) best else null
}

fun runName(startMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val h = java.time.Instant.ofEpochMilli(startMs).atZone(zone).hour
    return if (h < 12) "Carrera de la mañana" else if (h < 20) "Carrera de la tarde" else "Carrera nocturna"
}
