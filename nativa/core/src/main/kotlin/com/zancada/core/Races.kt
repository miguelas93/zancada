package com.zancada.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * Carreras populares en España. Lista curada a mano (fechas revisadas el 1 nov 2026 en calendarios públicos:
 * hdsports.org e intervals.icu). Las fechas de 2027 pueden cambiar: la app siempre dice «confirma en la web oficial».
 * [url] es la web oficial cuando la conocemos con seguridad; si no, se busca por el nombre.
 */
data class Race(
    val id: String, val name: String, val city: String, val date: String, val dist: Double,
    val url: String? = null, val tag: String = "", val unsure: Boolean = false,
) {
    fun day(zone: ZoneId): Long = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    fun link(): String = url ?: ("https://www.google.com/search?q=" + java.net.URLEncoder.encode("$name ${date.take(4)} web oficial inscripción", "UTF-8"))
    /** Objetivo del plan que le corresponde (null = distancia sin plan propio). */
    fun goal(): String? = when {
        dist <= 6000 -> "5k"
        dist <= 12000 -> "10k"
        dist <= 25000 -> "media"
        dist in 40000.0..43000.0 -> "maraton"
        else -> null
    }
    fun distLabel(): String = when {
        dist in 42000.0..42300.0 -> "Maratón"
        dist in 21000.0..21200.0 -> "Media"
        else -> fmtKmS(dist) + " km"
    }
}

val RACES = listOf(
    Race("malaga-mar-26", "Maratón de Málaga", "Málaga", "2026-11-08", 42195.0, tag = "Llana y rápida"),
    Race("toledo-mar-26", "Maratón Imperial de Toledo", "Toledo", "2026-11-15", 42195.0, tag = "Con cuestas"),
    Race("behobia-26", "Behobia – San Sebastián", "Irún → Donostia", "2026-11-15", 20000.0, url = "https://www.behobia-sansebastian.com", tag = "Mítica"),
    Race("tenerife-mar-26", "Maratón de Santa Cruz de Tenerife", "Santa Cruz de Tenerife", "2026-11-15", 42195.0),
    Race("donostia-mar-26", "Maratón de Donostia", "San Sebastián", "2026-11-22", 42195.0, tag = "Llana"),
    Race("sevilla-media-26", "Medio Maratón de Sevilla", "Sevilla", "2026-11-29", 21097.5, tag = "Llana y rápida"),
    Race("lanzarote-mar-26", "Lanzarote International Marathon", "Lanzarote", "2026-12-05", 42195.0, tag = "También media"),
    Race("valencia-mar-26", "Maratón Valencia", "Valencia", "2026-12-06", 42195.0, url = "https://www.valenciaciudaddelrunning.com", tag = "La más rápida de España"),
    Race("alcobendas-10k-26", "10K Alcobendas", "Alcobendas (Madrid)", "2026-12-13", 10000.0),
    Race("vallecana-26", "San Silvestre Vallecana", "Madrid", "2026-12-31", 10000.0, url = "https://www.sansilvestrevallecana.com", tag = "Fin de año"),
    Race("nassos-26", "Cursa dels Nassos", "Barcelona", "2026-12-31", 10000.0, tag = "Fin de año"),
    Race("valencia-10k-27", "10K Valencia", "Valencia", "2027-01-10", 10000.0, tag = "Récords"),
    Race("granollers-27", "La Mitja de Granollers", "Granollers (Barcelona)", "2027-01-24", 21097.5),
    Race("santapola-27", "Mitja Marató de Santa Pola", "Santa Pola (Alicante)", "2027-01-24", 21097.5, tag = "Junto al mar"),
    Race("castello-media-27", "Medio Maratón de Castellón", "Castellón", "2027-01-31", 21097.5),
    Race("murcia-27", "Maratón de Murcia", "Murcia", "2027-02-07", 42195.0, tag = "También media"),
    Race("barcelona-media-27", "Mitja Marató de Barcelona", "Barcelona", "2027-02-14", 21097.5, tag = "Multitudinaria"),
    Race("sevilla-mar-27", "Zurich Maratón de Sevilla", "Sevilla", "2027-02-21", 42195.0, url = "https://www.zurichmaratonsevilla.es", tag = "La más llana de Europa"),
    Race("malaga-media-27", "Medio Maratón de Málaga", "Málaga", "2027-03-07", 21097.5),
    Race("barcelona-mar-27", "Zurich Marató de Barcelona", "Barcelona", "2027-03-14", 42195.0, url = "https://www.zurichmaratobarcelona.es", tag = "Grande"),
    Race("palma-media-27", "Mitja Marató Ciutat de Palma", "Palma", "2027-03-21", 21097.5),
    Race("madrid-media-27", "Medio Maratón de Madrid", "Madrid", "2027-04-04", 21097.5, tag = "Con cuestas"),
    Race("laredo-10k-27", "10 km Villa de Laredo", "Laredo (Cantabria)", "2027-04-10", 10000.0, tag = "Llana y rápida", unsure = true),
    Race("madrid-mar-27", "Maratón de Madrid", "Madrid", "2027-04-25", 42195.0, tag = "También 10K y media"),
    Race("cursa-27", "Cursa El Corte Inglés", "Barcelona", "2027-05-09", 10000.0, tag = "Popular", unsure = true),
)

/** Carreras que aún no se han corrido, de la más cercana a la más lejana. [goal] filtra por distancia. */
fun App.upcomingRaces(goal: String? = null): List<Race> {
    val today = dayStart(now())
    return RACES.filter { it.day(zone) >= today && (goal == null || it.goal() == goal) }.sortedBy { it.date }
}

/** Semanas mínimas para preparar cada distancia con cabeza. */
val RACE_MIN_WEEKS = mapOf("5k" to 3, "10k" to 4, "media" to 6, "maraton" to 10)
const val RACE_MAX_WEEKS = 24

/** Semanas de plan hasta el día de la carrera (la semana de la carrera incluida). */
fun App.weeksTo(r: Race): Int = Math.round((weekStart(r.day(zone)) - weekStart(now())).toDouble() / WEEK).toInt() + 1

/** Días que faltan. */
fun App.daysTo(r: Race): Int = Math.round((r.day(zone) - dayStart(now())) / DAY.toDouble()).toInt()

/** ¿Se puede preparar? null = sí; si no, el porqué en una frase. */
fun App.raceReady(r: Race): String? {
    val g = r.goal() ?: return "No hay plan para esta distancia."
    val w = weeksTo(r); val min = RACE_MIN_WEEKS[g] ?: 4
    return when {
        daysTo(r) < 0 -> "Ya se corrió."
        w < min -> "Muy justo: para ${GOALS[g]!!.name.lowercase()} hacen falta al menos $min semanas."
        w > RACE_MAX_WEEKS -> "Aún queda mucho: vuelve en unas ${w - RACE_MAX_WEEKS} semanas."
        else -> null
    }
}

/** Plan hasta la carrera: misma lógica que el asistente, con la fecha y el nombre de la carrera. */
fun App.newRacePlan(r: Race, days: List<Int>, level: String, remindOn: Boolean): Plan {
    val w = weeksTo(r).coerceIn(1, RACE_MAX_WEEKS)
    return newPlan(r.goal() ?: "10k", w, days, level, remindOn).copy(raceDate = r.day(zone), raceName = r.name, raceId = r.id)
}
