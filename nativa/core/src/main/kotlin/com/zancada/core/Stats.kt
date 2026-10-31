package com.zancada.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

// ---------- carrera ----------

/** Recalcula distancia, parciales, duración y desnivel a partir de los puntos (finalizeRun de la web). */
fun finalizeRun(id: String, start: Long, name: String, pts: List<Point>, end: Double, gainFn: (List<Point>) -> Int? = ::elevGain): Run {
    var dist = 0.0
    val splits = mutableListOf<Split>()
    var lastKm = 0
    var prevT = 0.0
    for (i in 1 until pts.size) {
        val d = if (pts[i].gap) 0.0 else haversine(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        dist += d
        while (dist >= (lastKm + 1) * 1000.0) {
            lastKm++
            val frac = (dist - lastKm * 1000.0) / d
            val tAt = (pts[i].t - (pts[i].t - pts[i - 1].t) * frac) / 1000.0
            splits.add(Split(lastKm, tAt - prevT))
            prevT = tAt
        }
        pts[i].d = dist
    }
    if (pts.isNotEmpty()) pts[0].d = 0.0
    val dur = max(pts.last().t / 1000.0, end)
    val gain = gainFn(pts)
    if (dist - lastKm * 1000.0 > 100) splits.add(Split(lastKm + 1, (dur - prevT) / ((dist - lastKm * 1000.0) / 1000.0), partial = true))
    return Run(id, start, name, pts, dist, dur, splits, gain)
}

/** Desnivel positivo con histéresis de 4 m (web). Null si hay 10 alturas o menos. */
fun elevGain(pts: List<Point>): Int? = elevGainOf(pts.map { it.a })

fun elevGainOf(alts: List<Int?>): Int? {
    var g = 0.0
    var base: Int? = null
    var n = 0
    for (a in alts) {
        if (a == null) continue
        n++
        if (base == null) { base = a; continue }
        if (a - base >= 4) { g += a - base; base = a } else if (base - a >= 4) base = a
    }
    return if (n > 10) jsRound(g).toInt() else null
}

/**
 * Desnivel arreglado (app nativa): el altímetro del GPS tiene ±3–5 m de ruido que la histéresis de 4 m sola
 * no frena (en la prueba de 60 m de subida la web daba +108 m). Primero se suaviza la altura con la mediana
 * y la media de unos 30 s, y luego se aplica la histéresis.
 */
fun elevGainSmooth(pts: List<Point>): Int? {
    val idx = pts.indices.filter { pts[it].a != null }
    if (idx.size <= 10) return null
    val a = idx.map { pts[it].a!!.toDouble() }
    val t = idx.map { pts[it].t }
    val win = 15_000L
    // 1) mediana móvil (quita picos), 2) media móvil (quita el temblor)
    fun window(i: Int): IntRange { var lo = i; var hi = i; while (lo > 0 && t[i] - t[lo - 1] <= win) lo--; while (hi < a.size - 1 && t[hi + 1] - t[i] <= win) hi++; return lo..hi }
    val med = a.indices.map { i -> window(i).map { a[it] }.sorted().let { s -> s[s.size / 2] } }
    val avg = a.indices.map { i -> window(i).let { r -> r.sumOf { med[it] } / (r.last - r.first + 1) } }
    var g = 0.0
    var base = avg[0]
    for (x in avg.drop(1)) { if (x - base >= 2) { g += x - base; base = x } else if (base - x >= 2) base = x }
    return jsRound(g).toInt()
}

/** Tramo más rápido de [meters] dentro de la carrera, en segundos (Infinity si no hay). */
fun bestEffort(r: Run, meters: Double): Double {
    val p = r.points
    if (p.isEmpty() || r.dist < meters) return Double.POSITIVE_INFINITY
    var best = Double.POSITIVE_INFINITY
    var j = 0
    for (i in 1 until p.size) {
        while (j < i && p[i].d - p[j + 1].d >= meters) j++
        val span = p[i].d - p[j].d
        if (span >= meters) best = min(best, ((p[i].t - p[j].t) / 1000.0) * (meters / span))
    }
    return best
}

/** Mejor tiempo en [meters] (múltiplo de 1000) sumando parciales completos (o escalado si es a mano). */
fun bestFor(r: Run, meters: Int): Double? {
    if (r.manual) return if (r.dist >= meters && r.dist < meters * 1.15) r.dur * meters / r.dist else null
    val ok = r.splits.filter { !it.partial }
    if (ok.size * 1000 < meters) return null
    val t = mutableListOf(0.0)
    ok.forEach { t.add(t.last() + it.split) }
    val n = meters / 1000
    var best = Double.POSITIVE_INFINITY
    var i = 0
    while (i + n < t.size) { best = min(best, t[i + n] - t[i]); i++ }
    return if (best.isFinite()) best else null
}

fun runName(startMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val h = java.time.Instant.ofEpochMilli(startMs).atZone(zone).hour
    return if (h < 12) "Carrera de la mañana" else if (h < 20) "Carrera de la tarde" else "Carrera nocturna"
}

fun Run.paceSec() = dur / (dist / 1000)

// ---------- semana, racha y récords ----------

data class WeekStats(val count: Int, val daysDone: Set<Int>, val streak: Int, val today: Int)

fun App.weekStats(): WeekStats {
    val ws = weekStart(now())
    val thisWeek = runs.filter { it.start >= ws }
    val daysDone = thisWeek.map { es.dow(it.start) }.toSet()
    var streak = 0
    var cursor = ws
    fun ok(from: Long) = runs.count { it.start >= from && it.start < from + WEEK } >= goalOf()
    if (ok(cursor)) streak++
    cursor -= WEEK
    while (ok(cursor)) { streak++; cursor -= WEEK }
    return WeekStats(thisWeek.size, daysDone, streak, es.dow(now()))
}

fun App.bestStreak(): Int {
    if (runs.isEmpty()) return 0
    val byW = runs.groupingBy { weekStart(it.start) }.eachCount()
    var best = 0; var cur = 0; var prev: Long? = null
    for (w in byW.keys.sorted()) {
        if (byW[w]!! >= goalOf()) { cur = if (prev != null && w - prev == WEEK) cur + 1 else 1; best = max(best, cur) } else cur = 0
        prev = w
    }
    return best
}

data class Record(val label: String, val v: Double, val value: String, val run: Run?)

fun App.personalRecords(): List<Record> {
    val out = mutableListOf<Record>()
    var bk: Pair<Double, Run>? = null; var b5: Pair<Double, Run>? = null; var b10: Pair<Double, Run>? = null
    var lg: Pair<Double, Run>? = null; var gn: Pair<Double, Run>? = null
    for (r in runs) {
        val ok = r.splits.filter { !it.partial }
        val k = if (ok.isNotEmpty()) ok.minOf { it.split } else null
        if (k != null && k != 0.0 && (bk == null || k < bk.first)) bk = k to r
        val f5 = bestFor(r, 5000); if (f5 != null && f5 != 0.0 && (b5 == null || f5 < b5.first)) b5 = f5 to r
        val f10 = bestFor(r, 10000); if (f10 != null && f10 != 0.0 && (b10 == null || f10 < b10.first)) b10 = f10 to r
        if (lg == null || r.dist > lg.first) lg = r.dist to r
        val g = r.gain; if (g != null && g > 20 && (gn == null || g > gn.first)) gn = g.toDouble() to r
    }
    bk?.let { out.add(Record("Mejor km", it.first, fmtTime(it.first), it.second)) }
    b5?.let { out.add(Record("Mejor 5 km", it.first, fmtTime(it.first), it.second)) }
    b10?.let { out.add(Record("Mejor 10 km", it.first, fmtTime(it.first), it.second)) }
    lg?.let { out.add(Record("Más larga", it.first, fmtKm(it.first) + " km", it.second)) }
    gn?.let { out.add(Record("Más desnivel", it.first, "+" + jsRound(it.first) + " m", it.second)) }
    val bs = bestStreak(); if (bs >= 2) out.add(Record("Mejor racha", bs.toDouble(), "$bs semanas", null))
    return out
}

data class RaceBase(val d: Double, val t: Double, val srcTxt: String)

/** Base para predecir tiempos (Riegel): tu mejor 10 km o 5 km; si no hay, tu ritmo reciente. */
fun App.raceBase(): RaceBase {
    var base: Pair<Double, Double>? = null
    for (r in runs) {
        val f5 = bestFor(r, 5000); val f10 = bestFor(r, 10000)
        if (f10 != null && f10 != 0.0 && (base == null || base.first < 10000 || f10 < base.second)) base = 10000.0 to f10
        else if (f5 != null && f5 != 0.0 && (base == null || (base.first == 5000.0 && f5 < base.second))) base = 5000.0 to f5
    }
    return if (base != null) RaceBase(base.first, base.second, "según tu mejor ${jsNum(base.first / 1000)} km")
    else RaceBase(5000.0, userPace() * 5, "según tu ritmo reciente")
}

fun RaceBase.predict(d: Double) = t * (d / this.d).pow(1.06)

val PACE_K = mapOf("suave" to 1.12, "largo" to 1.08, "tempo" to 0.93, "series" to 0.86, "carrera" to 0.95)
fun App.targetPaceSec(type: String) = userPace() * (PACE_K[type] ?: 1.0)
fun App.targetPace(type: String) = pace(targetPaceSec(type))

// ---------- insignias ----------

data class Badge(val id: String, val short: String, val name: String, val `when`: Long)

const val SHOE_LIMIT = 700

private fun firstWhen(R: List<Run>, f: (Run) -> Boolean): Long { for (i in R.indices.reversed()) if (f(R[i])) return R[i].start; return 0 }
private fun totalWhen(R: List<Run>, m: Double): Long { var a = 0.0; for (i in R.indices.reversed()) { a += R[i].dist; if (a >= m) return R[i].start }; return 0 }
private fun App.streakWhen(R: List<Run>, n: Int): Long {
    val byWeek = R.groupingBy { weekStart(it.start) }.eachCount()
    val weeks = byWeek.keys.sorted()
    var run0 = 0
    for (i in weeks.indices) {
        val ok = byWeek[weeks[i]]!! >= goalOf()
        run0 = if (ok && i > 0 && weeks[i] - weeks[i - 1] == WEEK && run0 > 0) run0 + 1 else if (ok) 1 else 0
        if (run0 >= n) {
            val rs = R.filter { weekStart(it.start) == weeks[i] }.sortedBy { it.start }
            return rs[min(rs.size, goalOf()) - 1].start
        }
    }
    return 0
}
private fun isRec(r: Run) = r.route != null && !r.route.id.startsWith("run-")

fun App.badges(R: List<Run> = runs): List<Badge> {
    val defs: List<Triple<String, String, Pair<String, () -> Long>>> = listOf(
        Triple("primera", "1.ª", "Primera carrera" to { if (R.isNotEmpty()) R.last().start else 0L }),
        Triple("5k", "5K", "5 km de una vez" to { firstWhen(R) { it.dist >= 5000 } }),
        Triple("10k", "10K", "10 km de una vez" to { firstWhen(R) { it.dist >= 10000 } }),
        Triple("21k", "21K", "Media maratón" to { firstWhen(R) { it.dist >= 21097 } }),
        Triple("x10", "10", "10 carreras" to { if (R.size >= 10) R[R.size - 10].start else 0L }),
        Triple("x50", "50", "50 carreras" to { if (R.size >= 50) R[R.size - 50].start else 0L }),
        Triple("km100", "100", "100 km en total" to { totalWhen(R, 100000.0) }),
        Triple("km500", "500", "500 km en total" to { totalWhen(R, 500000.0) }),
        Triple("km1000", "1000", "1000 km en total" to { totalWhen(R, 1000000.0) }),
        Triple("sem3", "3 sem", "3 semanas seguidas" to { streakWhen(R, 3) }),
        Triple("sem10", "10 sem", "10 semanas seguidas" to { streakWhen(R, 10) }),
        Triple("alba", "< 7 h", "Madrugador" to { firstWhen(R) { es.hour(it.start) < 7 } }),
        Triple("noche", "> 21 h", "Nocturno" to { firstWhen(R) { es.hour(it.start) >= 21 } }),
        Triple("plan", "Plan", "Sesión del plan cumplida" to { firstWhen(R) { val s = it.session; s != null && !s.custom && it.dist >= s.m * 0.9 } }),
        Triple("ruta", "Ruta", "Una ruta recomendada" to { firstWhen(R) { isRec(it) } }),
        Triple("rutas5", "5 rutas", "5 rutas distintas" to {
            val seen = mutableSetOf<String>(); var w = 0L
            for (i in R.indices.reversed()) { if (isRec(R[i])) seen.add(R[i].route!!.id); if (seen.size >= 5) { w = R[i].start; break } }
            w
        }),
    )
    return defs.map { (id, short, nf) -> Badge(id, short, nf.first, nf.second()) }
}

/** Bronce, plata u oro. */
val TIER = mapOf("10k" to "s", "km100" to "s", "rutas5" to "s", "sem10" to "s", "x50" to "g", "21k" to "g", "km500" to "g", "km1000" to "g")
fun tierOf(id: String) = TIER[id] ?: "b"
val TIER_NAME = mapOf("b" to "Medalla de bronce", "s" to "Medalla de plata", "g" to "Medalla de oro")

fun App.shoeKm(sh: Shoe) = sh.km0 + runs.filter { it.shoe == sh.id }.sumOf { it.dist / 1000 }

// ---------- comparar y leer una carrera ----------

val FEEL_L = listOf("", "Muy duro", "Duro", "Normal", "Bien", "Volando")

fun sessDone(r: Run): Boolean { val s = r.session ?: return false; return if (s.min != null) r.dur >= s.min * 60 * 0.9 else r.dist >= s.m * 0.9 }

fun App.compareRun(r: Run): String? {
    if (r.dist == 0.0 || r.dist < 500) return null
    val prev = runs.filter { x -> x.id != r.id && x.start < r.start && x.dist > 500 && ((r.route != null && x.route?.id == r.route.id) || abs(x.dist - r.dist) / r.dist <= 0.15) }
        .maxByOrNull { it.start } ?: return null
    val p1 = r.paceSec(); val p0 = prev.paceSec(); val d = jsRound(p0 - p1)
    val same = r.route != null && prev.route?.id == r.route.id
    val whenS = es.dayMon(prev.start)
    val who = if (same) "la última vez en esta ruta" else "tu última carrera parecida"
    return if (abs(d) < 3) "Mismo ritmo que $who ($whenS)." else "${abs(d)} s/km ${if (d > 0) "más rápido" else "más lento"} que $who ($whenS, ${pace(p0)}/km)."
}

fun App.runInsights(r: Run): String {
    if (r.dist < 800) return ""
    val out = mutableListOf<String>()
    val pace = r.paceSec()
    val prev = runs.filter { it.id != r.id && it.start < r.start && it.dist > 800 }.take(5)
    if (r.route != null) {
        val same = runs.filter { x -> x.id != r.id && x.start < r.start && x.route?.id == r.route.id && x.dist >= r.dist * 0.9 && x.dist <= r.dist * 1.1 }
        if (same.isNotEmpty()) {
            val best = same.minOf { it.dur }; val d = jsRound(best - r.dur)
            out.add(if (d > 0) "Mejor tiempo en esta ruta: ${if (d >= 60) fmtTime(d.toDouble()) else "$d s"} menos que tu marca anterior (${same.size + 1} veces aquí)."
            else if (d < -5) "A ${if (-d >= 60) fmtTime(-d.toDouble()) else "${-d} s"} de tu mejor tiempo en esta ruta (${fmtTime(best)})."
            else "Prácticamente tu mejor tiempo en esta ruta.")
        }
    }
    if (prev.size >= 2) {
        val ref = prev.map { it.paceSec() }.sorted()[prev.size / 2]; val d = jsRound(ref - pace)
        if (abs(d) > 120) out.add(if (d > 0) "Mucho más rápido que en tus últimas carreras." else "Mucho más tranquilo que en tus últimas carreras.")
        else if (abs(d) >= 8) out.add(if (d > 0) "Has ido $d s/km más rápido que en tus últimas carreras." else "Has ido ${-d} s/km más tranquilo que de costumbre${if (r.session?.type == "suave" || r.session?.type == "largo") ", que es justo lo que tocaba" else ""}.")
        else out.add("Ritmo en tu línea habitual.")
    }
    val ok = r.splits.filter { !it.partial }
    if (ok.size >= 4) {
        val h = ok.size / 2
        val a = ok.take(h).sumOf { it.split } / h; val b = ok.takeLast(h).sumOf { it.split } / h; val d = jsRound(a - b)
        if (d >= 6) out.add("Segunda mitad $d s/km más rápida: bien dosificado.")
        else if (d <= -10) out.add("Saliste ${-d} s/km más rápido de lo que luego pudiste mantener; la próxima, un poco más conservador al principio.")
        val sd = sqrt(ok.sumOf { (it.split - pace).pow(2) } / ok.size)
        if (sd < 8 && d > -6 && d < 6) out.add("Parciales muy regulares.")
    }
    val longest = if (prev.isNotEmpty()) prev.maxOf { it.dist } else 0.0
    if (prev.size >= 2 && r.dist > longest) out.add("Es tu carrera más larga hasta ahora.")
    val ws1 = weekStart(r.start)
    val wk = runs.count { it.start >= ws1 && it.start < ws1 + WEEK && it.start <= r.start }
    if (wk >= 3) out.add("Van $wk carreras esta semana.")
    val wkm = runs.filter { it.start >= ws1 && it.start < ws1 + WEEK && it.start <= r.start }.sumOf { it.dist }
    val p4 = runs.filter { it.start >= ws1 - 28 * DAY && it.start < ws1 }.sumOf { it.dist } / 4
    if (p4 > 3000 && wkm > p4 * 1.35) out.add("Esta semana llevas ${fmtKmS(wkm)} km, un ${jsRound((wkm / p4 - 1) * 100)} % más que tu media: subir más del 10 % por semana es la forma más fácil de lesionarse.")
    val s = r.session
    if (s != null && s.custom) out.add(if (sessDone(r)) "Entreno por tramos completado." else if (s.min != null) "Entreno por tramos a medias: ${jsRound(r.dur / 60)} de ${s.min} min." else "Entreno por tramos a medias: ${fmtKm(r.dist)} de ${fmtKmS(s.m)} km.")
    else if (s != null) out.add(if (r.dist >= s.m * 0.9) "Sesión del plan cumplida." else "Sesión del plan a medias: ${fmtKm(r.dist)} de ${fmtKmS(s.m)} km; el lunes lo tengo en cuenta.")
    return out.take(3).joinToString(" ")
}

data class PaceSeg(val ll: List<List<Double>>, val dist: Double, val pace: Double)

/** Trazado en tramos de ~150 m con su ritmo (para colorear el mapa y las zonas). */
fun paceSegments(pts: List<Point>): List<PaceSeg> {
    val seg = mutableListOf<PaceSeg>()
    fun add(cur: List<List<Double>>, t0: Long, t1: Long, dc: Double) { if (cur.size > 1 && dc > 20) seg.add(PaceSeg(cur, dc, (t1 - t0) / 1000.0 / (dc / 1000))) }
    var cur = mutableListOf(listOf(pts[0].lat, pts[0].lon)); var t0 = pts[0].t; var dc = 0.0
    for (i in 1 until pts.size) {
        val p = pts[i]; val q = pts[i - 1]
        if (p.gap) { add(cur, t0, q.t, dc); cur = mutableListOf(listOf(p.lat, p.lon)); t0 = p.t; dc = 0.0; continue }
        dc += haversine(q.lat, q.lon, p.lat, p.lon); cur.add(listOf(p.lat, p.lon))
        if (dc >= 150) { add(cur, t0, p.t, dc); cur = mutableListOf(listOf(p.lat, p.lon)); t0 = p.t; dc = 0.0 }
    }
    add(cur, t0, pts.last().t, dc)
    return seg
}

data class Zone(val name: String, val color: Long, val sec: Double, val pct: Long)
data class Zones(val zones: List<Zone>, val main: Int, val userPace: Double)

/** Tiempo en cada zona de esfuerzo según tu ritmo habitual. Null si la carrera no da para ello. */
fun App.zones(r: Run): Zones? {
    val seg = if (r.points.size > 5) paceSegments(r.points) else emptyList()
    if (seg.size < 3) return null
    val P = userPace()
    val Z = listOf(Triple("Suave", 0xFF7FB3FF, 1.04 to 1e9), Triple("Moderado", 0xFFD4FF3A, 0.96 to 1.04), Triple("Fuerte", 0xFFFFB020, 0.9 to 0.96), Triple("Muy fuerte", 0xFFFF4D4D, 0.0 to 0.9))
    val t = DoubleArray(4)
    seg.forEach { x -> val k = x.pace / P; val i = Z.indexOfFirst { k >= it.third.first && k < it.third.second }; t[if (i < 0) 0 else i] += x.pace * x.dist / 1000 }
    val tot = t.sum().let { if (it == 0.0) 1.0 else it }
    val mx = t.max(); val main = t.indexOfFirst { it == mx }
    return Zones(Z.mapIndexed { i, z -> Zone(z.first, z.second, t[i], jsRound(t[i] / tot * 100)) }, main, P)
}

/** Color por ritmo: lima rápido → ámbar → rojo lento. */
fun paceColor(f: Double): Long {
    val C = listOf(intArrayOf(212, 255, 58), intArrayOf(255, 176, 32), intArrayOf(255, 77, 77))
    val x = max(0.0, min(1.0, f)) * 2; val i = min(1, floor(x).toInt()); val k = x - i
    val c = (0..2).map { j -> jsRound(C[i][j] + (C[i + 1][j] - C[i][j]) * k).toInt() }
    return 0xFF000000 or (c[0].toLong() shl 16) or (c[1].toLong() shl 8) or c[2].toLong()
}

// ---------- rutas ya corridas ----------

data class Detected(val id: String, val name: String, val score: Double)

/** Si el trazado coincide con una ruta recomendada o guardada, la reconoce (así el fantasma funciona la próxima vez). */
fun App.detectRoute(r: Run): Detected? {
    val pts = r.points
    if (pts.size < 20 || r.dist < 800) return null
    val step = max(1, pts.size / 60)
    val sample = pts.filterIndexed { i, _ -> i % step == 0 }
    var best: Detected? = null
    for (route in zr + savedRoutes) {
        val ll = route.ll
        if (ll.size < 2 || r.dist < route.km * 1000 * 0.6) continue
        var minLa = 90.0; var maxLa = -90.0; var minLo = 180.0; var maxLo = -180.0
        ll.forEach { (la, lo) -> if (la < minLa) minLa = la; if (la > maxLa) maxLa = la; if (lo < minLo) minLo = lo; if (lo > maxLo) maxLo = lo }
        val pad = 0.001
        if (sample.count { it.lat > minLa - pad && it.lat < maxLa + pad && it.lon > minLo - pad && it.lon < maxLo + pad } < sample.size * 0.7) continue
        val near1 = sample.count { p -> var hit = false; var i = 0; while (i < ll.size) { if (haversine(p.lat, p.lon, ll[i][0], ll[i][1]) < 45) { hit = true; break }; i += 2 }; hit }.toDouble() / sample.size
        if (near1 >= 0.75 && (best == null || near1 > best.score)) best = Detected(route.id, route.name, near1)
    }
    return best
}

/** Tu mejor carrera completa en esa ruta (el "fantasma"). */
fun App.bestOnRoute(id: String): Run? {
    val cands = runs.filter { it.points.size > 5 && (it.route?.id == id || "run-" + it.id == id) }
    val km = if (cands.isNotEmpty()) cands.maxOf { it.dist } else 0.0
    return cands.filter { it.dist >= km * 0.9 }.minByOrNull { it.dur }
}

fun ghostDistAt(ref: Run, tMs: Double): Double {
    val p = ref.points
    if (tMs >= p.last().t) return ref.dist
    var lo = 0; var hi = p.size - 1
    while (hi - lo > 1) { val mid = (lo + hi) shr 1; if (p[mid].t <= tMs) lo = mid else hi = mid }
    val a = p[lo]; val b = p[hi]; val f = if (b.t > a.t) (tMs - a.t) / (b.t - a.t) else 0.0
    return a.d + (b.d - a.d) * f
}

fun ghostTimeAt(ref: Run, d: Double): Double {
    val p = ref.points
    if (d >= ref.dist) return ref.dur
    var lo = 0; var hi = p.size - 1
    while (hi - lo > 1) { val mid = (lo + hi) shr 1; if (p[mid].d <= d) lo = mid else hi = mid }
    val a = p[lo]; val b = p[hi]; val f = if (b.d > a.d) (d - a.d) / (b.d - a.d) else 0.0
    return (a.t + (b.t - a.t) * f) / 1000
}

fun roundTo(x: Double, step: Double) = round(x / step) * step
