package com.zancada.core

import java.time.ZoneId

const val DAY = 86_400_000L
const val WEEK = 7 * DAY

/** Todo lo que la web guardaba en localStorage. */
data class AppData(
    val runs: List<Run> = emptyList(),
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val plan: Plan? = null,
    val shoes: List<Shoe> = emptyList(),
    val savedRoutes: List<Route> = emptyList(),
    val badgeSeen: List<String> = emptyList(),
    val coach: List<CoachMsg> = emptyList(),
    val photos: Map<String, PhotoCache> = emptyMap(),
    val pois: Map<String, PoiCache> = emptyMap(),
    val routeCache: Map<String, List<Route>> = emptyMap(),
)

/**
 * La lógica de Zancada, traducida de la web (index.html) función a función.
 * Se comprueba contra lo que calcula la web con los mismos datos (tests/golden/app.json, AppParityTest).
 * [webCompat] = true reproduce también los fallos conocidos de la web (solo para esa comprobación);
 * la app usa false, con los fallos arreglados.
 */
class App(
    var data: AppData,
    /** Rutas recomendadas (routes-data.js). */
    val zr: List<Route> = emptyList(),
    val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault(),
    val webCompat: Boolean = false,
) {
    val es = EsDate(zone)
    val runs get() = data.runs
    val settings get() = data.settings
    val profile get() = data.profile
    val plan get() = data.plan
    val shoes get() = data.shoes
    val savedRoutes get() = data.savedRoutes

    fun goalOf() = maxOf(1, if (profile.goal == 0) 3 else profile.goal)
    fun pace(secPerKm: Double) = fmtPace(secPerKm, web = webCompat)

    /** Lunes 00:00 de la semana de [ts], en hora local. */
    fun weekStart(ts: Long): Long {
        val d = es.z(ts)
        return d.toLocalDate().minusDays((d.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun dayStart(ts: Long): Long = es.z(ts).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Ritmo habitual: mediana de las 5 últimas carreras (6:30 si no hay). */
    fun userPace(): Double {
        val ps = runs.take(5).map { it.dur / (it.dist / 1000) }.filter { it.isFinite() && it > 150 && it < 1200 }.sorted()
        return if (ps.isNotEmpty()) ps[ps.size / 2] else 390.0
    }
}
