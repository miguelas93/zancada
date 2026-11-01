package com.zancada.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
@Serializable
data class Point(
    val lat: Double,
    val lon: Double,
    val t: Long,
    val a: Int? = null,
    val gap: Boolean = false,
    var d: Double = 0.0,
)

@Serializable
data class Split(val km: Int, val split: Double, val partial: Boolean = false)

/** Sesión del plan o entreno por tramos con el que se corrió (lo que guarda la web en run.session). */
@Serializable
data class SessionRef(
    val key: String? = null, val week: Int? = null, val title: String = "", val m: Double = 0.0, val type: String = "suave",
    val light: Boolean = false, val custom: Boolean = false, val min: Int? = null,
)

@Serializable
data class RouteRef(val id: String, val name: String = "", val auto: Boolean = false)

@Serializable
data class RunWx(val t: Int = 0, val wind: Int = 0, val rain: Int = 0)

@Serializable
data class GhostRef(val ref: String = "", val refDur: Double = 0.0)

/** Una carrera guardada. Mismo formato que zancada.runs.v1 de la web. */
@Serializable
data class Run(
    val id: String,
    val start: Long,
    val name: String = "Carrera",
    val points: List<Point> = emptyList(),
    val dist: Double = 0.0,
    val dur: Double = 0.0,
    val splits: List<Split> = emptyList(),
    val gain: Int? = null,
    /** Añadida a mano (cinta, reloj…): sin puntos de GPS. */
    val manual: Boolean = false,
    val imported: Boolean = false,
    val feel: Int? = null,
    val note: String? = null,
    val session: SessionRef? = null,
    val route: RouteRef? = null,
    val goal: String? = null,
    val shoe: String? = null,
    val wx: RunWx? = null,
    val ghost: GhostRef? = null,
    /** Cadencia media (pasos por minuto), con el sensor de pasos del móvil. Solo la nativa. */
    val cad: Int? = null,
    /** Pulso medio y máximo (pulsómetro Bluetooth). Solo la nativa. */
    val hr: Int? = null,
    val hrMax: Int? = null,
    /** Pulso a lo largo de la carrera: pares [segundo de carrera, ppm] cada 5 s. Solo la nativa. */
    val hrs: List<List<Int>>? = null,
)

@Serializable
data class TramosCustom(val t: Boolean = false, val reps: Int = 6, val on: Int = 400, val off: Int = 200)

/** Ajustes (zancada.settings.v1). La web guarda casi todo como texto ("1", "0", "auto"…). */
@Serializable
data class Settings(
    val voice: String = "1",
    val night: String = "auto",
    val map: String = "sat",
    val autopause: String = "1",
    val shoe: String? = null,
    val monthkm: String = "0",
    val bigtext: String = "0",
    val vibrate: String? = null,
    val pacealert: String? = null,
    val newsSeen: String? = null,
    val startSeen: String? = null,
    val recapSeen: Long? = null,
    val zetaSeen: Int? = null,
    val tramos: String? = null,
    val tramosCustom: TramosCustom? = null,
    val goal: String? = null,
    val bigm: String? = null,
    val pacemap: String? = null,
    /** Contacto de seguridad (solo la nativa): se le avisa con un toque al salir y al llegar. sosOn/sosEvery ya no se usan. */
    val sosOn: String? = null,
    val sosPhone: String? = null,
    val sosName: String? = null,
    val sosEvery: Int? = null,
    /** Guardar las carreras en Health Connect (solo la nativa). */
    val hc: String? = null,
    /** Pulsómetro Bluetooth elegido (solo la nativa). */
    val hrAddr: String? = null,
    val hrName: String? = null,
    /** Pantalla de carrera: tipo de mapa y vista ("map" o "data", solo datos). */
    val runMap: String? = null,
    val runView: String? = null,
)

@Serializable
data class Profile(val name: String = "", val goal: Int = 3, val done: Boolean = false)

@Serializable
data class Review(val week: Int, val done: Int, val total: Int, val doneKm: Double, val planKm: Double, val text: String)

@Serializable
data class Remind(val on: Boolean = false, val hour: Int = 8)

@Serializable
data class Plan(
    val goal: String = "10k",
    val weeks: Int = 8,
    val days: List<Int> = listOf(0, 2, 4, 6),
    val level: String = "medio",
    val start: Long = 0,
    val created: Long = 0,
    val factor: List<Double>? = null,
    val reviewed: Int = 0,
    val lastReview: Review? = null,
    val remind: Remind? = null,
    /** Plan para una carrera concreta (calendario de carreras): día, nombre e id. */
    val raceDate: Long? = null,
    val raceName: String? = null,
    val raceId: String? = null,
)

@Serializable
data class Shoe(val id: String, val name: String, val km0: Double = 0.0, val created: Long = 0)

@Serializable
data class LatLon(val lat: Double, val lon: Double)

/**
 * Ruta: las recomendadas (routes-data.js), las guardadas en "Mis recorridos" (zancada.routes.v1),
 * los bucles generados y las carreras propias para repetir. [ll] = lista de [lat, lon].
 */
@Serializable
data class Route(
    val id: String,
    val name: String = "Ruta",
    val ll: List<List<Double>> = emptyList(),
    val km: Double = 0.0,
    val gain: Int? = null,
    val ele: List<Int?>? = null,
    val created: Long? = null,
    val start: LatLon? = null,
    val imported: Boolean = false,
    // solo en las recomendadas
    val loop: Boolean? = null,
    val zone: String? = null,
    val city: String? = null,
    val surface: String? = null,
    val surf: String? = null,
    val why: String? = null,
    val access: String? = null,
    @Transient val src: String = "saved",
    @Transient val sub: String? = null,
    @Transient val away: Double? = null,
) {
    fun startLL(): LatLon = start ?: LatLon(ll[0][0], ll[0][1])
}

@Serializable
data class CoachMsg(val role: String, val content: String, val err: Boolean = false)

@Serializable
data class Photo(val src: String, val big: String, val by: String = "", val lic: String = "", val page: String = "")

@Serializable
data class PhotoCache(val t: Long, val v: Int = 2, val p: List<Photo> = emptyList())

@Serializable
data class Poi(val k: String, val lat: Double, val lon: Double)

@Serializable
data class Station(val n: String, val k: String, val d: Int)

@Serializable
data class PoiCache(val t: Long, val p: List<Poi> = emptyList(), val s: List<Station> = emptyList())
