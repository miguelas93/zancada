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
