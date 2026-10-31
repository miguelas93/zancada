package com.zancada.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Distancia en metros entre dos puntos (fórmula del haversine, la misma que la web). */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val toRad = Math.PI / 180
    val dLat = (lat2 - lat1) * toRad
    val dLon = (lon2 - lon1) * toRad
    val h = sin(dLat / 2).let { it * it } + cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}
