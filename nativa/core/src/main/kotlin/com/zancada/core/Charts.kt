package com.zancada.core

import kotlin.math.max
import kotlin.math.min

/** Serie para una gráfica: [x] (km o minutos) y [y], del mismo tamaño. */
data class Series(val x: List<Double>, val y: List<Double>) {
    val size get() = x.size
    fun avg(): Double = if (y.isEmpty()) Double.NaN else y.average()
}

/**
 * Ritmo (s/km) a lo largo de la carrera, en ventanas de unos 200 m, suavizado con la media de 3.
 * Los tramos con pausa ([Point.gap]) no cuentan. Ritmos imposibles (más de 20 min/km o menos de 2) se descartan.
 */
fun paceSeries(pts: List<Point>, win: Double = 200.0): Series {
    if (pts.size < 3) return Series(emptyList(), emptyList())
    val xs = mutableListOf<Double>(); val ys = mutableListOf<Double>()
    var dist = 0.0; var d0 = 0.0; var t0 = pts[0].t
    for (i in 1 until pts.size) {
        val p = pts[i]; val q = pts[i - 1]
        val step = haversine(q.lat, q.lon, p.lat, p.lon)
        if (p.gap) { dist += step; d0 = dist; t0 = p.t; continue }
        dist += step
        if (dist - d0 >= win) {
            val pace = (p.t - t0) / 1000.0 / ((dist - d0) / 1000)
            if (pace in 120.0..1200.0) { xs.add(dist / 1000); ys.add(pace) }
            d0 = dist; t0 = p.t
        }
    }
    return Series(xs, smooth(ys))
}

/** Altitud (m) frente a km recorridos. */
fun elevSeries(pts: List<Point>): Series {
    val xs = mutableListOf<Double>(); val ys = mutableListOf<Double>()
    var dist = 0.0
    pts.forEachIndexed { i, p ->
        if (i > 0) dist += haversine(pts[i - 1].lat, pts[i - 1].lon, p.lat, p.lon)
        p.a?.let { xs.add(dist / 1000); ys.add(it.toDouble()) }
    }
    if (xs.size < 4) return Series(emptyList(), emptyList())
    // Como mucho 240 puntos: la gráfica no gana nada con más y el dedo va más suelto.
    val step = max(1, xs.size / 240)
    val ix = xs.indices.filter { it % step == 0 || it == xs.size - 1 }
    return Series(ix.map { xs[it] }, smooth(ix.map { ys[it] }))
}

/** Pulso (ppm) frente a minutos de carrera, desde los pares [segundo, ppm] guardados. */
fun hrSeries(hrs: List<List<Int>>?): Series {
    val ok = hrs.orEmpty().filter { it.size >= 2 && it[1] in 30..240 }
    if (ok.size < 6) return Series(emptyList(), emptyList())
    return Series(ok.map { it[0] / 60.0 }, smooth(ok.map { it[1].toDouble() }))
}

/** Índice del punto más cercano a [x] (para recorrer la gráfica con el dedo). */
fun Series.nearest(x0: Double): Int {
    if (x.isEmpty()) return -1
    var lo = 0; var hi = x.size - 1
    while (hi - lo > 1) { val m = (lo + hi) / 2; if (x[m] < x0) lo = m else hi = m }
    return if (kotlin.math.abs(x[lo] - x0) <= kotlin.math.abs(x[hi] - x0)) lo else hi
}

private fun smooth(v: List<Double>): List<Double> = v.indices.map { i ->
    val a = max(0, i - 1); val b = min(v.size - 1, i + 1)
    (a..b).sumOf { v[it] } / (b - a + 1)
}
