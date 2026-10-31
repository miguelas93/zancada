package com.zancada.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Serializable
private data class RecRouteJson(
    val id: String, val km: Double, val gain: Int? = null, val loop: Boolean? = null, val ele: List<Int?>? = null, val enc: String,
    val name: String, val zone: String? = null, val city: String? = null, val surface: String? = null, val surf: String? = null,
    val why: String? = null, val access: String? = null,
)

/** Lee routes-data.js (el mismo archivo que usa la web) y decodifica los trazados. */
fun parseRecRoutes(js: String): List<Route> {
    val eq = js.indexOf("ZROUTES"); val a = js.indexOf('[', if (eq < 0) 0 else eq); val b = js.lastIndexOf(']')
    if (a < 0 || b < a) return emptyList()
    val list = Json { ignoreUnknownKeys = true }.decodeFromString<List<RecRouteJson>>(js.substring(a, b + 1))
    return list.map { r ->
        val ll = decodePoly6(r.enc)
        Route(r.id, r.name, ll, r.km, r.gain, r.ele, null, LatLon(ll[0][0], ll[0][1]), false, r.loop, r.zone, r.city, r.surface, r.surf, r.why, r.access, src = "rec")
    }
}

data class City(val name: String, val lat: Double, val lon: Double)
val CITIES = linkedMapOf("madrid" to City("Madrid", 40.44, -3.70), "sanfernando" to City("San Fernando", 36.46, -6.20))
val SURF = mapOf("tierra" to "Tierra", "asfalto" to "Asfalto", "pista" to "Pista", "mixto" to "Mixto")

data class RouteMeta(val time: Double, val diff: String, val diffCls: String)

fun App.routeMeta(r: Route): RouteMeta {
    val g = r.gain ?: 0
    val t = r.km * userPace() + g * 6
    return when { r.km < 7 && g < 80 -> RouteMeta(t, "Fácil", ""); r.km < 13 && g < 220 -> RouteMeta(t, "Media", "mid"); else -> RouteMeta(t, "Exigente", "hard") }
}

/** Filtros de la pantalla Rutas (rt de la web). */
data class RouteFilter(val here: LatLon? = null, val zone: String? = null, val dist: String = "all", val surf: String = "all", val sort: String = "near")

fun nearCity(here: LatLon?): String? {
    if (here == null) return null
    var best: Pair<String, Double>? = null
    for ((k, c) in CITIES) { val d = haversine(here.lat, here.lon, c.lat, c.lon); if (d < 40000 && (best == null || d < best.second)) best = k to d }
    return best?.first
}

fun withAway(list: List<Route>, here: LatLon?): List<Route> {
    if (here == null) return list
    return list.map { it.copy(away = hav(here, it.startLL()) / 1000) }.sortedBy { it.away }
}

fun App.zoneRoutes(zone: String, here: LatLon?) = zr.filter { if (zone == "cerca") here != null && hav(here, it.startLL()) < 40000 else it.city == zone }

fun App.recList(f: RouteFilter): List<Route> {
    val zone = f.zone ?: nearCity(f.here) ?: "madrid"
    var list = zoneRoutes(zone, f.here)
    val TB = mapOf("t20" to (0.0 to 25.0), "t30" to (22.0 to 38.0), "t45" to (36.0 to 50.0), "t60" to (48.0 to 1e9))
    TB[f.dist]?.let { (a, b) -> list = list.filter { val t = routeMeta(it).time / 60; t >= a && t <= b } }
    if (f.surf != "all") list = list.filter { it.surf == f.surf }
    val out = withAway(list, f.here)
    val done = runs.mapNotNull { it.route?.id }.toSet()
    val cmp: Comparator<Route>? = when (f.sort) {
        "short" -> compareBy { it.km }
        "long" -> compareByDescending { it.km }
        "flat" -> compareBy { (it.gain ?: 0) / it.km }
        "new" -> compareBy<Route> { if (it.id in done) 1 else 0 }.thenBy { it.away ?: 0.0 }
        else -> null
    }
    return if (cmp != null) out.sortedWith(cmp) else out
}

/** Mis recorridos: las rutas guardadas y las carreras propias para repetirlas. */
fun App.myRoutes(): List<Route> {
    val fromRuns = runs.filter { it.points.size > 5 && it.dist > 500 }.take(20).map { r ->
        Route("run-" + r.id, r.name, r.points.map { listOf(it.lat, it.lon) }, jsRound(r.dist / 100) / 10.0, r.gain, src = "run", sub = es.fmtDate(r.start))
    }
    return savedRoutes.map { it.copy(src = "saved", sub = "Guardada") } + fromRuns.filter { r -> savedRoutes.none { it.id == r.id } }
}

data class DoneTag(val times: Int, val best: Double)
/** En cada recomendada: cuántas veces la has corrido y tu mejor tiempo completo. */
fun App.routeDone(r: Route): DoneTag? {
    if (r.src != "rec") return null
    val mine = runs.filter { it.route?.id == r.id }
    if (mine.isEmpty()) return null
    val full = mine.filter { it.dist >= r.km * 1000 * 0.9 }
    return DoneTag(mine.size, if (full.isNotEmpty()) full.minOf { it.dur } else 0.0)
}

data class Today(val r: Route, val why: List<String>)

/** "Para hoy": una propuesta razonada entre las recomendadas; cambia cada día. */
fun App.pickToday(list: List<Route>): Today? {
    if (list.size < 2) return null
    val week = now() - 7 * DAY
    val recent = runs.filter { it.start > week && it.route != null }.map { it.route!!.id }.toSet()
    val ses = plan?.let { p -> runCatching { planStatus(p).flatten().firstOrNull { it.state == "today" && it.run == null && it.type != "carrera" } }.getOrNull() }
    val goalKm = if (ses != null) ses.m / 1000 else 0.0
    data class Sc(val r: Route, val sc: Double, val why: List<String>)
    val scored = list.map { r ->
        val t = routeMeta(r).time / 60; val why = mutableListOf<String>(); var sc = 0.0
        if (goalKm > 0) {
            val n = jsRound(goalKm / r.km)
            if (abs(r.km - goalKm) <= max(0.6, goalKm * 0.15)) { sc += 3.5; why.add("encaja con tu ${ses!!.title.lowercase()} de ${fmtKmS(ses.m)} km") }
            else if (r.loop != false && n in 2..3 && abs(n * r.km - goalKm) <= max(0.6, goalKm * 0.12)) { sc += 2.5; why.add("$n vueltas = ${fmtKmS(n * r.km * 1000)} km, tu sesión de hoy") }
            else sc -= 1
        }
        val aw = r.away
        if (aw != null) { if (aw < 2) { sc += 3; why.add(fmtAway(aw)) } else if (aw < 6) { sc += 1.5; why.add(fmtAway(aw)) } else sc -= aw / 10 }
        if (goalKm == 0.0 && t >= 25 && t <= 50) sc += 2
        why.add("${jsRound(t)} min a tu ritmo")
        if (r.id in recent) sc -= 3 else if (runs.none { it.route?.id == r.id }) { sc += 1; why.add("aún no la has corrido") } else why.add("ya la conoces")
        if (r.surf == "tierra" || r.surf == "mixto") sc += .5
        if (r.surf != null) SURF[r.surf]?.let { why.add(it.lowercase()) }
        Sc(r, sc, why)
    }.sortedByDescending { it.sc }
    val day = Math.floorDiv(now(), DAY)
    val top = scored.take(min(3, scored.size)).filter { it.sc >= scored[0].sc - 1.5 }
    val x = top[(day % top.size).toInt()]
    return Today(x.r, x.why)
}

/** Rutas parecidas de la misma ciudad (por distancia). */
fun App.similarRoutes(r: Route): List<Route> = if (r.src != "rec") emptyList() else zr.filter { it.id != r.id && it.city == r.city }.sortedBy { abs(it.km - r.km) }.take(3)

// ---------- guía por la ruta ----------

data class Turn(val i: Int, val d: Double, val dv: Double, val dir: String, val sharp: Boolean)

class Nav(val pts: List<LatLon>, val cum: List<Double>, val total: Double, val turns: List<Turn>) {
    var idx = 0; var turnSaid = -1; var phase = "route"; var done = false; var joinN = 0; var saidAt = 0L
    fun at(d: Double): LatLon { var i = idx; while (i < pts.size - 1 && cum[i + 1] < d) i++; return pts[i] }
}

fun buildNav(ll: List<List<Double>>): Nav {
    val pts = mutableListOf<LatLon>(); val cum = mutableListOf(0.0)
    ll.forEach { (lat, lon) -> val q = LatLon(lat, lon); if (pts.isEmpty()) pts.add(q) else { val d = hav(pts.last(), q); if (d >= 1) { cum.add(cum.last() + d); pts.add(q) } } }
    val total = cum.last()
    data class T(var i: Int, var d: Double, var dv: Double)
    val turns = mutableListOf<T>()
    var lo = 0; var hi = 0
    for (i in 1 until pts.size - 1) {
        val d = cum[i]; if (d < 20 || total - d < 20) continue
        while (cum[lo + 1] < d - 20) lo++
        while (hi < pts.size - 1 && cum[hi] < d + 20) hi++
        val dv = angDiff(bearing(pts[lo], pts[i]), bearing(pts[i], pts[hi]))
        if (abs(dv) < 70) continue
        val last = turns.lastOrNull()
        if (last != null && d - last.d < 60) { if (abs(dv) > abs(last.dv)) { last.i = i; last.d = d; last.dv = dv } } else turns.add(T(i, d, dv))
    }
    return Nav(pts, cum, total, turns.map { Turn(it.i, it.d, it.dv, if (it.dv > 0) "derecha" else "izquierda", abs(it.dv) > 120) })
}

data class NavPos(val off: Double, val i: Int)

fun navProgress(n: Nav, p: LatLon, dry: Boolean = false): NavPos {
    var best = Double.POSITIVE_INFINITY; var bi = n.idx
    val hi = min(n.pts.size - 1, n.idx + 80)
    for (i in n.idx..hi) { val d = hav(p, n.pts[i]); if (d < best) { best = d; bi = i } }
    if (best > 60) {
        for (i in n.pts.indices) { val d = hav(p, n.pts[i]); if (d < best) { best = d; bi = i } }
        for (i in 0 until bi) if (hav(p, n.pts[i]) <= best + 15) { bi = i; break }
    }
    if (best <= 60 && !dry) n.idx = bi
    return NavPos(best, bi)
}

// ---------- GPX de rutas ----------

fun xmlEsc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun routeGpx(r: Route): String {
    val pts = r.ll.mapIndexed { i, (la, lo) -> "<rtept lat=\"${toFixed(la, 6)}\" lon=\"${toFixed(lo, 6)}\">${r.ele?.getOrNull(i)?.let { "<ele>$it</ele>" } ?: ""}</rtept>" }.joinToString("")
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Zancada\" xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><name>${xmlEsc(r.name)}</name></metadata><rte><name>${xmlEsc(r.name)}</name>$pts</rte></gpx>"
}

fun routeShareText(r: Route): String {
    val st = r.startLL()
    return "${r.name} · ${commaNum(r.km)} km${if (r.gain != null) " · +${r.gain} m" else ""}${if (r.surface != null) " · ${r.surface}" else ""}\nSalida: https://maps.google.com/?q=${toFixed(st.lat, 5)},${toFixed(st.lon, 5)}\n${if (r.why != null) r.why + "\n" else ""}Ruta de Zancada · https://miguelas93.github.io/zancada/"
}
