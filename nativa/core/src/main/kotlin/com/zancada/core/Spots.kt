package com.zancada.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sitios para correr: parques, riberas, pistas y bosques con su forma real (OpenStreetMap).
 * Varios trozos con el mismo nombre (Madrid Río son muchos) se juntan en un solo sitio.
 */
data class Spot(
    val id: String,
    val name: String,
    /** "parque", "rio", "pista", "bosque". */
    val kind: String,
    val rings: List<List<LatLon>>,
    val areaM2: Double,
) {
    val center: LatLon by lazy { val all = rings.flatten(); LatLon(all.sumOf { it.lat } / all.size, all.sumOf { it.lon } / all.size) }
    /** Lo más largo del sitio (entre los dos puntos más alejados), en metros. */
    val lengthM: Double by lazy { longAxis(rings.flatten()).let { (a, b) -> hav(a, b) } }
    /** Perímetro del trozo más grande, en metros. */
    val perimM: Double by lazy { rings.maxByOrNull { ringArea(it) }?.let { r -> r.zipWithNext().sumOf { (a, b) -> hav(a, b) } + hav(r.last(), r.first()) } ?: 0.0 }
    /** Alargado y estrecho (riberas, paseos): lo largo al cuadrado mucho mayor que el área. */
    val elongated: Boolean get() = lengthM * lengthM / max(1.0, areaM2) > 9
    fun contains(p: LatLon) = rings.any { pointInRing(p, it) }
    fun toRoute(): Route {
        val r = rings.maxByOrNull { ringArea(it) } ?: rings.first()
        return Route("spot-$id", name, (r + r.first()).map { listOf(it.lat, it.lon) }, jsRound(perimM / 100) / 10.0, src = "spot")
    }
}

val SPOT_KIND_L = mapOf("parque" to "Parque", "rio" to "Ribera", "pista" to "Pista de atletismo", "bosque" to "Bosque")

fun spotsUrl(c: LatLon, radiusM: Int = 5000): String {
    val a = "(around:$radiusM,${toFixed(c.lat, 5)},${toFixed(c.lon, 5)})"
    val q = "[out:json][timeout:25];(" +
        "way[\"leisure\"~\"^(park|nature_reserve)$\"][\"name\"]$a;relation[\"leisure\"~\"^(park|nature_reserve)$\"][\"name\"]$a;" +
        "way[\"landuse\"=\"forest\"][\"name\"]$a;way[\"natural\"=\"wood\"][\"name\"]$a;" +
        "way[\"leisure\"=\"track\"][\"sport\"~\"athletics|running\"]$a;" +
        ");out geom qt 400;"
    return "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")
}

/** Une trozos de línea (miembros «outer» de una relación) en anillos cerrados. */
fun joinRings(parts: List<List<LatLon>>): List<List<LatLon>> {
    val left = parts.filter { it.size >= 2 }.toMutableList(); val out = mutableListOf<List<LatLon>>()
    fun same(a: LatLon, b: LatLon) = abs(a.lat - b.lat) < 1e-7 && abs(a.lon - b.lon) < 1e-7
    while (left.isNotEmpty()) {
        val ring = left.removeAt(0).toMutableList()
        var grew = true
        while (!same(ring.first(), ring.last()) && grew) {
            grew = false
            val i = left.indexOfFirst { same(it.first(), ring.last()) || same(it.last(), ring.last()) }
            if (i >= 0) { val p = left.removeAt(i); ring.addAll((if (same(p.first(), ring.last())) p else p.reversed()).drop(1)); grew = true }
        }
        if (ring.size >= 4) out.add(if (same(ring.first(), ring.last())) ring.dropLast(1) else ring)
    }
    return out
}

private fun normName(n: String) = n.lowercase().trim()
    .replace(Regex("^(parque|jardines?|jardín|paseo|pinar|bosque|monte)\\s+(de\\s+la\\s+|del\\s+|de\\s+los\\s+|de\\s+las\\s+|de\\s+)?"), "")
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')

/** Lee la respuesta de Overpass, une los trozos con el mismo nombre y quita lo pequeño. */
fun parseSpots(text: String, here: LatLon? = null, max: Int = 40): List<Spot> = runCatching {
    val els = (Json.parseToJsonElement(text) as JsonObject)["elements"] as? JsonArray ?: return emptyList()
    data class Piece(val id: Long, val name: String, val kind: String, val rings: List<List<LatLon>>)
    fun pts(a: JsonArray?) = a?.mapNotNull { p -> (p as? JsonObject)?.let { o -> val la = (o["lat"] as? JsonPrimitive)?.doubleOrNull; val lo = (o["lon"] as? JsonPrimitive)?.doubleOrNull; if (la != null && lo != null) LatLon(la, lo) else null } } ?: emptyList()
    val pieces = els.mapNotNull { e0 ->
        val e = e0 as? JsonObject ?: return@mapNotNull null
        val tags = e["tags"] as? JsonObject
        fun tag(k: String) = (tags?.get(k) as? JsonPrimitive)?.contentOrNull
        val id = (e["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        val kind = when { tag("leisure") == "track" -> "pista"; tag("landuse") == "forest" || tag("natural") == "wood" -> "bosque"; else -> "parque" }
        val name = tag("name") ?: if (kind == "pista") "Pista de atletismo" else return@mapNotNull null
        val rings = if ((e["type"] as? JsonPrimitive)?.contentOrNull == "relation")
            joinRings(((e["members"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { m -> (m as? JsonObject)?.takeIf { (it["role"] as? JsonPrimitive)?.contentOrNull != "inner" }?.let { pts(it["geometry"] as? JsonArray) } })
        else pts(e["geometry"] as? JsonArray).let { g -> if (g.size >= 4) listOf(if (g.first() == g.last()) g.dropLast(1) else g) else emptyList() }
        if (rings.isEmpty()) null else Piece(id, name, kind, rings)
    }
    // Juntar por nombre (si están cerca: dos «Parque Norte» en ciudades distintas no son el mismo).
    val groups = mutableListOf<MutableList<Piece>>()
    for (p in pieces) {
        val c = p.rings.flatten().let { LatLon(it.sumOf { q -> q.lat } / it.size, it.sumOf { q -> q.lon } / it.size) }
        val g = groups.firstOrNull { g -> p.kind != "pista" && normName(g[0].name) == normName(p.name) && g.any { q -> q.rings.flatten().any { hav(it, c) < 2500 } } }
        if (g != null) g.add(p) else groups.add(mutableListOf(p))
    }
    val spots = groups.map { g ->
        val rings = g.flatMap { it.rings }.map { simplifyRing(it, 240) }
        val name = g.map { it.name }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        val area = rings.sumOf { ringArea(it) }
        val kind0 = g[0].kind
        val s = Spot(g.minOf { it.id }.toString(), name, kind0, rings, area)
        if (kind0 == "parque" && (s.elongated || Regex("r[ií]o|ribera|canal|orilla", RegexOption.IGNORE_CASE).containsMatchIn(name)) && s.lengthM > 1500) s.copy(kind = "rio") else s
    }.filter { it.kind == "pista" || it.areaM2 >= 30_000 || it.lengthM >= 1200 }
    val scored = spots.map { s -> s to (sqrt(s.areaM2) + s.lengthM) / (1 + (here?.let { hav(it, s.center) } ?: 0.0) / 2500) }
    scored.sortedByDescending { it.second }.take(max).map { it.first }
}.getOrDefault(emptyList())

// ---------- geometría ----------

/** Área aproximada de un anillo en m² (proyección local). */
fun ringArea(r: List<LatLon>): Double {
    if (r.size < 3) return 0.0
    val la0 = r[0].lat; val k = 111_320.0; val kx = k * cos(la0 * PI / 180)
    var s = 0.0
    for (i in r.indices) { val a = r[i]; val b = r[(i + 1) % r.size]; s += (a.lon * kx) * (b.lat * k) - (b.lon * kx) * (a.lat * k) }
    return abs(s) / 2
}

fun pointInRing(p: LatLon, r: List<LatLon>): Boolean {
    var inside = false; var j = r.size - 1
    for (i in r.indices) {
        val a = r[i]; val b = r[j]
        if ((a.lat > p.lat) != (b.lat > p.lat) && p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon) inside = !inside
        j = i
    }
    return inside
}

/** Los dos puntos más alejados (aproximado: dos pasadas). */
fun longAxis(pts: List<LatLon>): Pair<LatLon, LatLon> {
    if (pts.isEmpty()) return LatLon(0.0, 0.0) to LatLon(0.0, 0.0)
    val a = pts.maxByOrNull { hav(pts[0], it) }!!; val b = pts.maxByOrNull { hav(a, it) }!!
    return a to b
}

/** Quita puntos para que el anillo tenga como mucho [maxPts]. */
fun simplifyRing(r: List<LatLon>, maxPts: Int): List<LatLon> { if (r.size <= maxPts) return r; val step = r.size.toDouble() / maxPts; return (0 until maxPts).map { r[(it * step).toInt()] } }

// ---------- ficha del sitio ----------

data class SpotWorkout(val title: String, val text: String)

/** Entrenamientos que encajan en el sitio según su forma y tamaño. */
fun spotWorkouts(s: Spot, paceSec: Double): List<SpotWorkout> {
    val out = mutableListOf<SpotWorkout>()
    val lap = s.perimM; val len = s.lengthM
    fun km(m: Double) = fmtKmS((m / 100).roundToInt() * 100.0)
    when {
        s.kind == "pista" -> {
            out += SpotWorkout("Series en pista", "6 × 400 m a ritmo de 5 km con 200 m trotando. Cada vuelta a la calle 1 son 400 m exactos.")
            out += SpotWorkout("Test de 5 km", "12 vueltas y media a ritmo constante: la mejor forma de saber tu ritmo real.")
            out += SpotWorkout("Técnica y cambios de ritmo", "Rectas rápidas y curvas suaves, 8 a 10 veces, después de calentar.")
        }
        s.elongated || s.kind == "rio" -> {
            out += SpotWorkout("Rodaje de ida y vuelta", "Hasta ${km(min(len, 5000.0))} de ida y vuelta por el sitio: ${km(2 * min(len, 5000.0))} sin cruzar calles.")
            if (len >= 800) out += SpotWorkout("Series en la recta", "${if (len >= 1500) "6 × 800 m" else "8 × 400 m"}, aprovechando lo largo y llano: vas y vuelves trotando.")
            if (len >= 3000) out += SpotWorkout("Progresivo", "${km(min(len, 4000.0))} de ida a ritmo cómodo y la vuelta cada km un poco más rápido.")
        }
        else -> {
            if (lap >= 800) out += SpotWorkout("Rodaje por vueltas", "Una vuelta al borde son unos ${km(lap)}: ${max(2, (5000 / lap).roundToInt())} vueltas ≈ ${km(max(2, (5000 / lap).roundToInt()) * lap)}.")
            out += SpotWorkout("Fartlek", "30 min alternando 1 min rápido y 2 min suave por los caminos del parque.")
            if (s.areaM2 >= 400_000) out += SpotWorkout("Tirada larga", "Combina varias vueltas y caminos interiores para sumar kilómetros sin aburrirte.")
            else out += SpotWorkout("Series cortas", "10 × 200 m en el camino más recto, volviendo al trote.")
        }
    }
    return out
}

fun spotSub(s: Spot): String = buildList {
    add(SPOT_KIND_L[s.kind] ?: "Sitio")
    if (s.kind == "rio" || s.elongated) add("${fmtKmS((s.lengthM / 100).roundToInt() * 100.0)} km de largo")
    else if (s.kind != "pista") add("${max(1, (s.areaM2 / 10_000).roundToInt())} ha")
}.joinToString(" · ")
