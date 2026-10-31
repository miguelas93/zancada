package com.zancada.core

/** Lectura del GPS tal como llega del móvil. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val acc: Double,
    val alt: Double?,
    /** Hora de la lectura según el GPS (ms). */
    val fixTime: Long,
    /** Hora a la que llegó a la app (ms). La pone [RunSession.onFix]. */
    val arrival: Long = 0,
)

/** Punto guardado de la carrera. [t] = ms de carrera (sin pausas), [d] = metros acumulados. */
data class Point(
    val lat: Double,
    val lon: Double,
    val t: Long,
    val a: Int? = null,
    val gap: Boolean = false,
    var d: Double = 0.0,
)

data class Split(val km: Int, val split: Double, val partial: Boolean = false)

data class Run(
    val id: String,
    val start: Long,
    val name: String,
    val points: List<Point>,
    val dist: Double,
    val dur: Double,
    val splits: List<Split>,
    val gain: Int?,
)
