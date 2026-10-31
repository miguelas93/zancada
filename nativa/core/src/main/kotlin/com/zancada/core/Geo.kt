package com.zancada.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Distancia en metros entre dos puntos (fórmula del haversine, la misma que la web). */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val toRad = PI / 180
    val dLat = (lat2 - lat1) * toRad
    val dLon = (lon2 - lon1) * toRad
    val h = sin(dLat / 2).let { it * it } + cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}
fun hav(a: LatLon, b: LatLon) = haversine(a.lat, a.lon, b.lat, b.lon)

/** Rumbo de a hacia b en grados (0 = norte). */
fun bearing(a: LatLon, b: LatLon): Double {
    val r = PI / 180; val la1 = a.lat * r; val la2 = b.lat * r; val dl = (b.lon - a.lon) * r
    val y = sin(dl) * cos(la2); val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dl)
    return (atan2(y, x) * 180 / PI + 360) % 360
}

/** De a hacia b, −180..180; positivo = a la derecha. */
fun angDiff(a: Double, b: Double) = ((b - a + 540) % 360) - 180

/** Punto a [m] metros de [p] con rumbo [brg]. */
fun dest(p: LatLon, brg: Double, m: Double): LatLon {
    val R = 6371000.0; val b = brg * PI / 180; val la1 = p.lat * PI / 180; val lo1 = p.lon * PI / 180; val dr = m / R
    val la2 = asin(sin(la1) * cos(dr) + cos(la1) * sin(dr) * cos(b))
    val lo2 = lo1 + atan2(sin(b) * sin(dr) * cos(la1), cos(dr) - sin(la1) * sin(la2))
    return LatLon(la2 * 180 / PI, lo2 * 180 / PI)
}

private val COMPASS = listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste")
fun compass(b: Double) = COMPASS[(jsRound((((b % 360) + 360) % 360) / 45) % 8).toInt()]

/** Polyline con 6 decimales (Valhalla y routes-data.js) → lista de [lat, lon]. */
fun decodePoly6(str: String): List<List<Double>> {
    val out = mutableListOf<List<Double>>()
    var i = 0; var lat = 0L; var lon = 0L
    while (i < str.length) {
        for (k in 0..1) {
            var sh = 0; var res = 0L; var b: Int
            do { b = str[i++].code - 63; res = res or ((b and 31).toLong() shl sh); sh += 5 } while (b >= 32)
            // como la web: operaciones de 32 bits
            val r32 = res.toInt()
            val d = if (r32 and 1 != 0) (r32 shr 1).inv() else r32 shr 1
            if (k == 0) lat += d else lon += d
        }
        out.add(listOf(lat / 1e6, lon / 1e6))
    }
    return out
}
