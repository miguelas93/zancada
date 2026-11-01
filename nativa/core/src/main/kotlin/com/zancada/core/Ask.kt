package com.zancada.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * «Pídesela a Zeta»: describes la ruta con palabras («8 km llanos, con sombra, pasando por el río»)
 * y se buscan bucles desde donde estás que lo cumplan. La IA solo traduce la frase a [AskSpec];
 * las rutas salen del enrutador de OpenStreetMap y se puntúan con datos reales (parques, agua y fuentes
 * de OpenStreetMap, desnivel). Si la IA no responde, se entiende la frase con reglas sencillas.
 */
data class AskSpec(
    val km: Double = 5.0,
    val flat: Boolean = false,
    val hills: Boolean = false,
    /** "verde" (parques, árboles, sombra, tierra), "agua" (río, lago, canal) y "fuente". */
    val want: Set<String> = emptySet(),
    /** Rumbo preferido en grados (norte 0), si lo pide. */
    val dir: Double? = null,
)

val ASK_EXAMPLES = listOf(
    "8 km llanos pasando por el río",
    "5 km por parques, con sombra",
    "45 minutos con cuestas",
    "6 km con alguna fuente para beber",
)

private val DIRS = mapOf("norte" to 0.0, "noreste" to 45.0, "este" to 90.0, "sureste" to 135.0, "sur" to 180.0, "suroeste" to 225.0, "oeste" to 270.0, "noroeste" to 315.0)

private fun norm(t: String) = t.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

/** Entiende la frase sin IA: distancia o tiempo, llano/cuestas, verde, agua, fuente y dirección. */
fun askRules(text: String, paceSec: Double = 360.0): AskSpec {
    val t = norm(text)
    val km = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|kilometro|k\\b)").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        ?: Regex("(\\d+(?:[.,]\\d+)?)\\s*(min|minuto)").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.let { it * 60 / paceSec }
        ?: Regex("(\\d+(?:[.,]\\d+)?)\\s*(h\\b|hora)").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.let { it * 3600 / paceSec }
        ?: (if (t.contains("media hora")) 1800 / paceSec else if (t.contains("una hora")) 3600 / paceSec else null)
        ?: 5.0
    val want = mutableSetOf<String>()
    if (Regex("parque|verde|arbol|sombra|bosque|pinar|jardin|tierra|campo|naturaleza|monte|pinos").containsMatchIn(t)) want.add("verde")
    if (Regex("\\brio\\b|agua|lago|canal|embalse|pantano|estanque|orilla|playa|mar\\b|arroyo|ria\\b").containsMatchIn(t)) want.add("agua")
    if (Regex("fuente|beber|agua potable|hidrat").containsMatchIn(t)) want.add("fuente")
    val flat = Regex("llan|plan[oa]|sin cuesta|sin desnivel|suave|facil").containsMatchIn(t)
    val hills = !flat && Regex("cuesta|subida|desnivel|monta|colina|repecho|rampa").containsMatchIn(t)
    val dir = DIRS.entries.sortedByDescending { it.key.length }.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(t) }?.value
    return AskSpec(km.coerceIn(1.0, 42.0), flat, hills, want, dir)
}

const val ASK_SYS = "Convierte la petición de ruta de un corredor en JSON. Responde SOLO con un objeto JSON, sin texto alrededor, con estas claves: " +
    "\"km\" (número; si dice minutos u horas, conviértelo con el ritmo indicado), \"llano\" (true/false), \"cuestas\" (true/false), " +
    "\"verde\" (true si quiere parques, árboles, sombra, tierra o naturaleza), \"agua\" (true si quiere río, lago, canal o mar), " +
    "\"fuente\" (true si quiere fuentes para beber), \"direccion\" (\"norte\", \"sur\", \"este\", \"oeste\"… o null)."

fun askBody(text: String, paceSec: Double): String = buildJsonObject {
    put("model", "openai")
    put("messages", buildJsonArray {
        add(buildJsonObject { put("role", "system"); put("content", ASK_SYS) })
        add(buildJsonObject { put("role", "user"); put("content", "Ritmo del corredor: ${fmtPace(paceSec)} min/km. Petición: $text") })
    })
    put("private", true); put("referrer", "zancada")
}.toString()

/** Lee el JSON de la IA (aunque venga con texto alrededor). Null si no se entiende. */
fun parseAskSpec(ai: String?, fallback: AskSpec): AskSpec? = runCatching {
    val s = ai ?: return null
    val a = s.indexOf('{'); val b = s.lastIndexOf('}'); if (a < 0 || b <= a) return null
    val j = Json.parseToJsonElement(s.substring(a, b + 1)) as JsonObject
    fun bool(k: String) = (j[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false
    val km = (j["km"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.5..60.0 } ?: fallback.km
    val want = buildSet { if (bool("verde")) add("verde"); if (bool("agua")) add("agua"); if (bool("fuente")) add("fuente") }
    val dir = (j["direccion"] as? JsonPrimitive)?.contentOrNull?.let { DIRS[norm(it).trim()] }
    val flat = bool("llano"); val hills = bool("cuestas") && !flat
    // Lo que las reglas ven claro se mantiene aunque la IA lo olvide.
    AskSpec(km.coerceIn(1.0, 42.0), flat || fallback.flat && !hills, hills || fallback.hills && !flat, want + fallback.want, dir ?: fallback.dir)
}.getOrNull()

fun AskSpec.summary(): String = buildList {
    add("${fmtKmS(km * 1000)} km")
    if (flat) add("llana"); if (hills) add("con cuestas")
    if ("verde" in want) add("zonas verdes"); if ("agua" in want) add("junto al agua"); if ("fuente" in want) add("con fuente")
    dir?.let { add("hacia el ${compass(it)}") }
}.joinToString(" · ")

// ---------- lugares de OpenStreetMap (Overpass) ----------

/** Zona (caja) o línea de un sitio: parques y agua como cajas; ríos como líneas; fuentes como puntos. */
data class AskFeat(val kind: String, val pts: List<LatLon> = emptyList(), val box: DoubleArray? = null) {
    fun center(): LatLon = box?.let { LatLon((it[0] + it[2]) / 2, (it[1] + it[3]) / 2) } ?: pts[pts.size / 2]
}

fun askOverpassUrl(c: LatLon, km: Double, want: Set<String>): String? {
    if (want.isEmpty()) return null
    val r = min(4500, max(1200, (km * 1000 / 2.6).roundToInt()))
    val a = "(around:$r,${toFixed(c.lat, 5)},${toFixed(c.lon, 5)})"
    val q = StringBuilder("[out:json][timeout:20];")
    if ("verde" in want) q.append("(way[\"leisure\"~\"^(park|garden|nature_reserve)$\"]$a;way[\"landuse\"~\"^(forest|recreation_ground)$\"]$a;way[\"natural\"=\"wood\"]$a;);out bb qt 150;")
    if ("agua" in want) q.append("way[\"waterway\"~\"^(river|canal)$\"]$a;out geom qt 60;way[\"natural\"=\"water\"]$a;out bb qt 60;")
    if ("fuente" in want) q.append("node[\"amenity\"=\"drinking_water\"]$a;out qt 200;")
    return "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q.toString(), "UTF-8").replace("+", "%20")
}

fun parseAskFeats(text: String): List<AskFeat> = runCatching {
    val els = (Json.parseToJsonElement(text) as JsonObject)["elements"] as? JsonArray ?: return emptyList()
    els.mapNotNull { e0 ->
        val e = e0 as? JsonObject ?: return@mapNotNull null
        val tags = e["tags"] as? JsonObject
        fun tag(k: String) = (tags?.get(k) as? JsonPrimitive)?.contentOrNull
        fun d(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.doubleOrNull
        when {
            tag("amenity") == "drinking_water" -> { val la = d(e, "lat") ?: return@mapNotNull null; AskFeat("fuente", listOf(LatLon(la, d(e, "lon") ?: return@mapNotNull null))) }
            tag("waterway") != null -> {
                val g = (e["geometry"] as? JsonArray)?.mapNotNull { p -> (p as? JsonObject)?.let { o -> val la = d(o, "lat"); val lo = d(o, "lon"); if (la != null && lo != null) LatLon(la, lo) else null } }
                if (g.isNullOrEmpty()) null else AskFeat("agua", g)
            }
            else -> {
                val b = e["bounds"] as? JsonObject ?: return@mapNotNull null
                val box = doubleArrayOf(d(b, "minlat") ?: return@mapNotNull null, d(b, "minlon") ?: return@mapNotNull null, d(b, "maxlat") ?: return@mapNotNull null, d(b, "maxlon") ?: return@mapNotNull null)
                AskFeat(if (tag("natural") == "water") "agua" else "verde", box = box)
            }
        }
    }
}.getOrDefault(emptyList())

/** Rumbos para los bucles: hacia los sitios que pide (los más grandes y a buena distancia) y, si faltan, repartidos. */
fun askBearings(start: LatLon, spec: AskSpec, feats: List<AskFeat>, n: Int = 5, seed: Int = 0): List<Double> {
    val out = mutableListOf<Double>()
    fun add(b: Double) { val x = ((b % 360) + 360) % 360; if (out.none { abs(angDiff(it, x)) < 40 }) out.add(x) }
    spec.dir?.let { add(it); add(it - 35); add(it + 35) }
    val reach = spec.km * 1000 / Math.PI   // lo más lejos que llega un bucle de esa distancia
    val cands = feats.filter { it.kind != "fuente" || "fuente" in spec.want }.mapNotNull { f ->
        val c = f.center(); val d = hav(start, c)
        if (d < 80 || d > reach * 1.1) null else Triple(bearing(start, c), d, f.size())
    }.sortedByDescending { (_, d, s) -> s * (1 - abs(d / reach - 0.5)) }
    cands.forEach { if (out.size < n) add(it.first) }
    var k = 0
    while (out.size < n && k < 24) { add(seed + k * 72.0 + (k / 5) * 36.0); k++ }
    return out.take(n)
}

private fun AskFeat.size(): Double = box?.let { (it[2] - it[0]) * (it[3] - it[1]) * 1e6 }?.coerceIn(0.5, 50.0) ?: if (kind == "agua") 6.0 else 1.0

data class AskResult(val score: Double, val why: String)

/** Puntúa un bucle según lo pedido y explica por qué encaja. */
fun scoreAsk(ll: List<List<Double>>, km: Double, gain: Int?, spec: AskSpec, feats: List<AskFeat>): AskResult {
    val pts = ll.map { LatLon(it[0], it[1]) }
    val step = max(1, pts.size / 300); val sample = pts.filterIndexed { i, _ -> i % step == 0 }
    val segKm = km / max(1, sample.size)
    var score = -abs(km - spec.km) / spec.km * 3
    val why = mutableListOf<String>()
    val gpk = (gain ?: 0) / max(0.5, km)
    if (spec.flat) { score -= gpk / 8; if (gain != null && gpk < 6) why.add("casi llana (+$gain m)") }
    if (spec.hills) { score += min(gpk, 30.0) / 8; if (gain != null && gpk >= 10) why.add("con cuestas (+$gain m)") }
    fun inBox(p: LatLon, b: DoubleArray, pad: Double) = p.lat >= b[0] - pad && p.lat <= b[2] + pad && p.lon >= b[1] - pad && p.lon <= b[3] + pad
    if ("verde" in spec.want) {
        val boxes = feats.filter { it.kind == "verde" && it.box != null }.map { it.box!! }
        val n = sample.count { p -> boxes.any { inBox(p, it, 0.0002) } }
        val frac = n.toDouble() / max(1, sample.size)
        score += frac * 3
        if (n * segKm >= 0.3) why.add("${fmtKmS(n * segKm * 1000)} km por parques o zonas verdes")
    }
    if ("agua" in spec.want) {
        val lines = feats.filter { it.kind == "agua" && it.box == null }.flatMap { f -> f.pts }
        val boxes = feats.filter { it.kind == "agua" && it.box != null }.map { it.box!! }
        val n = sample.count { p -> boxes.any { inBox(p, it, 0.0006) } || lines.any { q -> abs(q.lat - p.lat) < 0.0012 && abs(q.lon - p.lon) < 0.0016 && hav(p, q) < 110 } }
        score += n.toDouble() / max(1, sample.size) * 3
        if (n * segKm >= 0.3) why.add("${fmtKmS(n * segKm * 1000)} km junto al agua")
    }
    if ("fuente" in spec.want) {
        val fs = feats.filter { it.kind == "fuente" }.map { it.pts[0] }
        val at = sample.indexOfFirst { p -> fs.any { q -> abs(q.lat - p.lat) < 0.0006 && hav(p, q) < 45 } }
        val count = fs.count { q -> sample.any { p -> abs(q.lat - p.lat) < 0.0006 && hav(p, q) < 45 } }
        if (at >= 0) { score += 1 + min(count, 3) * 0.2; why.add(if (count > 1) "$count fuentes, la primera en el km ${fmtKmS(at * segKm * 1000)}" else "fuente en el km ${fmtKmS(at * segKm * 1000)}") }
    }
    if (why.isEmpty()) why.add(if (spec.want.isEmpty()) "${fmtKmS(km * 1000)} km desde donde estás" else "la que más se acerca a lo que pides")
    return AskResult(score, why.joinToString(" · ").replaceFirstChar { it.uppercase() })
}

// ---------- planes de ruta que pasan POR el sitio pedido y comprobación final ----------

/** Un plan: puntos de paso según un factor [f] (1 = tamaño estimado); se ajusta para clavar los km. */
class AskPlan(val label: String, val via: String?, val build: (Double) -> List<LatLon>)

private fun lerp(a: LatLon, b: LatLon, t: Double) = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

/** Puntos repartidos por dentro del borde de un anillo (a [inset] del centro hacia el borde), empezando por el más cercano a [from]. */
fun ringWaypoints(ring: List<LatLon>, center: LatLon, from: LatLon, n: Int, inset: Double): List<LatLon> {
    if (ring.isEmpty()) return emptyList()
    val i0 = ring.indices.minByOrNull { hav(ring[it], from) }!!
    return (0 until n).map { k -> lerp(center, ring[(i0 + k * ring.size / n) % ring.size], inset) }
}

/** Puntos a lo largo de una línea desde el más cercano a [from], hasta recorrer [m] metros. */
fun alongLine(line: List<LatLon>, from: LatLon, m: Double): List<LatLon> {
    if (line.size < 2) return line
    val i0 = line.indices.minByOrNull { hav(line[it], from) }!!
    // hacia el lado con más recorrido
    val fwd = line.subList(i0, line.size); val back = line.subList(0, i0 + 1).reversed()
    val dirPts = if (fwd.zipWithNext().sumOf { (a, b) -> hav(a, b) } >= back.zipWithNext().sumOf { (a, b) -> hav(a, b) }) fwd else back
    val out = mutableListOf(dirPts[0]); var d = 0.0; var lastKeep = 0.0
    for (i in 1 until dirPts.size) {
        d += hav(dirPts[i - 1], dirPts[i])
        if (d - lastKeep >= 400 || d >= m) { out.add(dirPts[i]); lastKeep = d }
        if (d >= m) break
    }
    return out
}

/**
 * Planes para lo pedido: por dentro de parques/bosques, a lo largo de riberas y ríos, o bucles normales.
 * Con «fuente», mete de paso la fuente más cercana al sitio elegido.
 */
fun askPlans(here: LatLon, spec: AskSpec, spots: List<Spot>, feats: List<AskFeat>, seed: Int = 0, place: Spot? = null): List<AskPlan> {
    val plans = mutableListOf<AskPlan>()
    val budget = spec.km * 1000
    val fountains = feats.filter { it.kind == "fuente" }.map { it.pts[0] }
    fun withFountain(pts: List<LatLon>): List<LatLon> {
        if ("fuente" !in spec.want || fountains.isEmpty() || pts.size < 2) return pts
        val f = fountains.minByOrNull { q -> pts.drop(1).minOf { hav(it, q) } }!!
        if (pts.drop(1).minOf { hav(it, f) } > 600) return pts
        val i = (1 until pts.size).minByOrNull { hav(pts[it], f) }!!
        return pts.take(i) + f + pts.drop(i)
    }
    val reach = budget * 0.42
    if (place != null) {
        val d = max(0.0, hav(here, place.center) - sqrt(place.areaM2 / PI))
        if (place.kind == "rio" || place.elongated) {
            val axis = longAxis(place.rings.flatten()).first.let { a -> place.rings.flatten().sortedBy { hav(a, it) } }
            plans += AskPlan("Por ${place.name}", place.name) { f -> val along = alongLine(axis, here, max(400.0, (budget - 2 * d) / 2 * f)); withFountain(listOf(here) + along + along.reversed().drop(1) + here) }
        } else {
            val ring = place.rings.maxByOrNull { ringArea(it) }!!
            plans += AskPlan("Por ${place.name}", place.name) { f ->
                val inner = max(600.0, budget - 2 * d) * f
                val laps = max(1, (inner / max(400.0, place.perimM * 0.7)).toInt()).coerceAtMost(4)
                val one = ringWaypoints(ring, place.center, here, 6, (inner / laps / max(400.0, place.perimM * 0.7)).coerceIn(0.35, 0.85))
                withFountain(listOf(here) + (1..laps).flatMap { one } + one.first() + here)
            }
        }
    }
    if ("verde" in spec.want) {
        spots.filter { it.kind == "parque" || it.kind == "bosque" || it.kind == "rio" }
            .map { it to max(0.0, hav(here, it.center) - sqrt(it.areaM2 / PI)) }
            .filter { (_, d) -> d < reach }
            .sortedByDescending { (s, d) -> min(s.perimM, budget - 2 * d) / budget - d / budget }
            .take(3).forEach { (s, _) ->
                val ring = s.rings.maxByOrNull { ringArea(it) }!!
                plans += AskPlan("Por ${s.name}", s.name) { f ->
                    val d = hav(here, s.center)
                    val inner = max(600.0, budget - 2 * d) * f
                    val laps = max(1, (inner / max(400.0, s.perimM * 0.7)).toInt()).coerceAtMost(4)
                    val inset = (inner / laps / max(400.0, s.perimM * 0.7)).coerceIn(0.35, 0.85)
                    val one = ringWaypoints(ring, s.center, here, 6, inset)
                    withFountain(listOf(here) + (1..laps).flatMap { one } + one.first() + here)
                }
            }
    }
    if ("agua" in spec.want) {
        val lines = feats.filter { it.kind == "agua" && it.box == null && it.pts.size >= 2 }.map { it.pts } +
            spots.filter { it.kind == "rio" }.map { s -> longAxis(s.rings.flatten()).let { (a, b) -> s.rings.flatten().sortedBy { p -> hav(a, p) } } }
        lines.map { l -> l to l.minOf { hav(here, it) } }.filter { (_, d) -> d < reach }.sortedBy { it.second }.take(2).forEachIndexed { k, (l, d) ->
            plans += AskPlan("Junto al agua", null) { f ->
                val along = alongLine(l, here, max(400.0, (budget - 2 * d) / 2 * f))
                withFountain(listOf(here) + along + along.reversed().drop(1) + here)
            }
        }
    }
    // Bucles normales (siempre, para comparar y por si lo anterior no sale).
    askBearings(here, spec, feats, if (plans.isEmpty()) 4 else 2, seed).forEach { brg ->
        plans += AskPlan("Bucle hacia el ${compass(brg)}", null) { f ->
            val r = budget * f / (2 * PI) / 1.25; val c = dest(here, brg, r)
            withFountain(listOf(here, dest(c, brg + 90, r), dest(c, brg, r), dest(c, brg - 90, r), here))
        }
    }
    return plans.take(6)
}

/** Ruta calculada para un plan, ajustando el tamaño hasta quedar a ±8 % de los km pedidos (como mucho 3 intentos). */
fun routePlan(p: AskPlan, km: Double, route: (List<LatLon>) -> Routed): Routed {
    var f = 1.0; var best: Routed? = null
    for (i in 0 until 3) {
        val r = route(p.build(f))
        if (best == null || abs(r.km - km) < abs(best.km - km)) best = r
        if (abs(r.km - km) / km <= 0.08) break
        f *= (km / max(0.3, r.km)).coerceIn(0.4, 2.5)
    }
    return best!!
}

data class AskCheck(val ok: Boolean, val miss: List<String>, val why: String, val score: Double)

/** Comprueba cada cosa pedida con datos reales; [miss] dice lo que no cumple. */
fun checkAsk(ll: List<List<Double>>, km: Double, gain: Int?, spec: AskSpec, feats: List<AskFeat>, spots: List<Spot>): AskCheck {
    val miss = mutableListOf<String>(); val why = mutableListOf<String>()
    val err = abs(km - spec.km) / spec.km
    if (err > 0.12) miss += "sale de ${fmtKmS(km * 1000)} km en vez de ${fmtKmS(spec.km * 1000)}"
    val gpk = (gain ?: 0) / max(0.5, km)
    if (spec.flat && gain != null) { if (gpk > 9) miss += "tiene +$gain m de desnivel" else why += "llana (+$gain m)" }
    if (spec.hills && gain != null) { if (gpk < 10) miss += "apenas tiene cuestas (+$gain m)" else why += "con cuestas (+$gain m)" }
    val pts = ll.map { LatLon(it[0], it[1]) }
    val step = max(1, pts.size / 400); val sample = pts.filterIndexed { i, _ -> i % step == 0 }
    val segKm = km / max(1, sample.size)
    if ("verde" in spec.want) {
        val boxes = feats.filter { it.kind == "verde" && it.box != null }.map { it.box!! }
        val green = spots.filter { it.kind != "pista" }
        val n = sample.count { p -> green.any { it.contains(p) } || boxes.any { b -> p.lat >= b[0] && p.lat <= b[2] && p.lon >= b[1] && p.lon <= b[3] } }
        val frac = n.toDouble() / max(1, sample.size)
        if (frac < 0.35) miss += "solo ${fmtKmS(n * segKm * 1000)} km por zonas verdes" else why += "${fmtKmS(n * segKm * 1000)} km por zonas verdes"
        spots.filter { s -> s.kind != "pista" && sample.count { s.contains(it) } * segKm >= 0.5 }.maxByOrNull { s -> sample.count { s.contains(it) } }?.let { why.add(0, "por ${it.name}") }
    }
    if ("agua" in spec.want) {
        val lines = feats.filter { it.kind == "agua" && it.box == null }.flatMap { it.pts }
        val riv = spots.filter { it.kind == "rio" }
        val n = sample.count { p -> riv.any { it.contains(p) } || lines.any { q -> abs(q.lat - p.lat) < 0.0012 && abs(q.lon - p.lon) < 0.0016 && hav(p, q) < 110 } }
        if (n.toDouble() / max(1, sample.size) < 0.3) miss += "solo ${fmtKmS(n * segKm * 1000)} km junto al agua" else why += "${fmtKmS(n * segKm * 1000)} km junto al agua"
    }
    if ("fuente" in spec.want) {
        val fs = feats.filter { it.kind == "fuente" }.map { it.pts[0] }
        val at = sample.indexOfFirst { p -> fs.any { q -> abs(q.lat - p.lat) < 0.0006 && hav(p, q) < 45 } }
        if (at < 0) miss += "no pasa por ninguna fuente" else why += "fuente en el km ${fmtKmS(at * segKm * 1000)}"
    }
    val s = scoreAsk(ll, km, gain, spec, feats).score - miss.size * 5
    return AskCheck(miss.isEmpty(), miss, why.ifEmpty { listOf("${fmtKmS(km * 1000)} km desde donde estás") }.joinToString(" · ").replaceFirstChar { it.uppercase() }, s)
}

/** Si la frase nombra un sitio de la lista («por Madrid Río», «en el Retiro»), ese sitio. */
fun matchPlace(text: String, spots: List<Spot>): Spot? {
    fun n(t: String) = t.lowercase().replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    val t = n(text)
    return spots.filter { s -> val k = n(s.name).replace(Regex("^(parque|jardines?|jardin|paseo|pinar|bosque|monte)\\s+(de\\s+la\\s+|del\\s+|de\\s+los\\s+|de\\s+las\\s+|de\\s+)?"), ""); k.length >= 4 && t.contains(k) }.maxByOrNull { it.name.length }
}
