package com.zancada.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class Goal(val name: String, val race: Double, val peak: Double, val cap: Double)
data class Level(val base: Double, val max: Double, val reps: Pair<Int, Int>)

val GOALS = linkedMapOf(
    "5k" to Goal("5K", 5000.0, 22.0, 1.5), "10k" to Goal("10K", 10000.0, 32.0, 1.6),
    "media" to Goal("Media maratón", 21097.5, 48.0, 1.8), "constancia" to Goal("Constancia", 0.0, 0.0, 1.35),
)
val LEVELS = linkedMapOf("inicio" to Level(8.0, 30.0, 4 to 7), "medio" to Level(16.0, 50.0, 6 to 9), "avanzado" to Level(26.0, 70.0, 8 to 12))
val DAY_L = listOf("L", "M", "X", "J", "V", "S", "D")
val DAY_N = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

@kotlinx.serialization.Serializable
data class Struct(val warm: Int, val reps: Int, val on: Int, val off: Int, val cool: Int)

/** Un tramo de la guía por voz: por metros ([m]) o por tiempo ([sec]). */
@kotlinx.serialization.Serializable
data class Seg(val label: String, val say: String, val kind: String, val m: Double? = null, val sec: Int? = null)

/** Una sesión: del plan, de "tramos" o un objetivo de carrera libre. */
@kotlinx.serialization.Serializable
data class Sess(
    val key: String, val type: String, val title: String, val desc: String, val m: Double,
    val struct: Struct? = null, val segs: List<Seg>? = null,
    val dayIdx: Int = 0, val week: Int = 0, val date: Long = 0,
    val custom: Boolean = false, val timed: Boolean = false, val min: Int? = null, val goal: Boolean = false, val light: Boolean = false,
    val run: Run? = null, val moved: Boolean = false, val state: String = "next",
) {
    fun ref() = SessionRef(key, week, title, m, type, light, custom, if (timed) min else null)
}

fun App.curWeek(p: Plan) = floorDiv(dayStart(now()) - p.start, WEEK)
private fun floorDiv(a: Long, b: Long) = floor(a.toDouble() / b).toInt()

fun planWeekKm(p: Plan, w: Int): Double {
    val g = GOALS[p.goal]!!; val lv = LEVELS[p.level]!!
    val base = lv.base; val peak = min(lv.max, max(base * g.cap, g.peak))
    val n = p.weeks; val race = g.race > 0; val build = if (race) n - 1 else n
    val t = if (build > 1) min(1.0, w.toDouble() / (build - 1)) else 1.0
    var km = base + (peak - base) * t
    if (race && w == n - 1) km = base * 0.6                 // semana de carrera: afinar
    else if (w % 4 == 3 && w < build - 1) km *= 0.75        // descarga cada cuatro semanas
    return km * (p.factor?.getOrNull(w) ?: 1.0)
}

fun planSessions(p: Plan): List<List<Sess>> {
    val g = GOALS[p.goal]!!; val lv = LEVELS[p.level]!!; val days = p.days.sorted(); val n = days.size; val race = g.race > 0
    val unit = if (p.goal == "10k") 800 else if (p.goal == "media") 1000 else 400
    val warm = if (p.level == "inicio") 1000 else 1500
    val weeks = mutableListOf<List<Sess>>()
    for (w in 0 until p.weeks) {
        val km = planWeekKm(p, w); val last = race && w == p.weeks - 1
        val t = min(1.0, w.toDouble() / max(1, p.weeks - (if (race) 2 else 1)))
        var types = when { n <= 2 -> listOf("series", "largo"); n == 3 -> listOf("suave", "series", "largo"); n == 4 -> listOf("suave", "series", "suave", "largo")
            else -> listOf("suave", "series", "suave", "tempo", "largo") + List(n - 5) { "suave" } }
        if (w == 0 || (p.level == "inicio" && w < 2)) types = types.map { if (it == "series" || it == "tempo") "suave" else it }
        if (last) types = types.toMutableList().also { it[n - 1] = "carrera" }
        val reps = jsRoundI(lv.reps.first + (lv.reps.second - lv.reps.first) * t)
        val nEasy = types.count { it == "suave" }
        data class Tmp(val type: String, var m: Double?, val struct: Struct?, val title: String, val desc: String, val dayIdx: Int)
        val list = types.mapIndexed { i, type ->
            when (type) {
                "largo" -> Tmp(type, km * (if (n <= 2) 0.55 else 0.38) * 1000, null, "Rodaje largo", "Ritmo cómodo, puedes hablar. Es la sesión que más te hace mejorar.", days[i])
                "series" -> { val off = jsRoundI(unit / 2.0); val st = Struct(warm, reps, unit, off, 1000)
                    Tmp(type, (warm + reps * (unit + off) + 1000).toDouble(), st, "Series $reps×${if (unit >= 1000) "${unit / 1000} km" else "$unit m"}",
                        "Calienta ${fmtKmS(warm.toDouble())} km, $reps repeticiones fuertes de $unit m con $off m al trote entre ellas, y 1 km suave para acabar.", days[i]) }
                "tempo" -> { val on = max(2000, jsRoundI(km * 0.22 * 2) * 500); val st = Struct(warm, 1, on, 0, 1000)
                    Tmp(type, (warm + on + 1000).toDouble(), st, "Tempo ${fmtKmS(on.toDouble())} km", "Calienta ${fmtKmS(warm.toDouble())} km, ${fmtKmS(on.toDouble())} km a ritmo controlado (frases cortas, nada de charla) y 1 km suave.", days[i]) }
                "carrera" -> Tmp(type, g.race, null, "Carrera: ${g.name}", "El día. Sal conservador los primeros km y disfruta.", days[i])
                else -> Tmp(type, null, null, "Rodaje suave", "Ritmo fácil. Si no puedes hablar, frena.", days[i])
            }
        }
        val fixed = list.sumOf { it.m ?: 0.0 }; val rest = max(nEasy * 3000.0, km * 1000 - fixed)
        list.forEach { s -> if (s.m == null) s.m = rest / nEasy; if (s.type != "carrera") s.m = max(3000.0, jsRound(s.m!! / 500) * 500.0) }
        weeks.add(list.map { s -> Sess("w${w}d${s.dayIdx}", s.type, s.title, s.desc, s.m!!, s.struct, dayIdx = s.dayIdx, week = w, date = p.start + (w * 7L + s.dayIdx) * DAY) })
    }
    return weeks
}

/** Cruza las sesiones con las carreras: la del mismo día cuenta; otra de la semana cubre la pendiente más cercana. */
fun App.planStatus(p: Plan): List<List<Sess>> {
    val today = dayStart(now()); val created = dayStart(p.created)
    val byDay = runs.groupBy { dayStart(it.start) }
    return planSessions(p).mapIndexed { w, ss0 ->
        val w0 = p.start + w * WEEK; val w1 = w0 + WEEK; val used = mutableSetOf<String>()
        val ss = ss0.map { s -> val r = byDay[dayStart(s.date)]?.firstOrNull { it.id !in used }; if (r != null) { used.add(r.id); s.copy(run = r) } else s }.toMutableList()
        val extra = runs.filter { it.start >= w0 && it.start < w1 && it.id !in used }.sortedBy { it.start }.toMutableList()
        for (i in ss.indices) { val s = ss[i]; if (s.run == null && dayStart(s.date) >= created) { val r = extra.removeFirstOrNull(); if (r != null) ss[i] = s.copy(run = r, moved = true) } }
        ss.map { s -> val d = dayStart(s.date); s.copy(state = if (s.run != null) "ok" else if (d < created) "skip" else if (d < today) "miss" else if (d == today) "today" else "next") }
    }
}

/** Revisión semanal: mira las semanas ya pasadas y ajusta las siguientes. Devuelve el plan nuevo (o null si no cambia). */
fun App.reviewPlan(): Plan? {
    var p = plan ?: return null
    val weeks = planStatus(p); val wi = curWeek(p); var changed = false
    val factor = (p.factor ?: List(p.weeks) { 1.0 }).toMutableList().also { while (it.size < p.weeks) it.add(1.0) }
    var reviewed = p.reviewed; var lastReview = p.lastReview
    for (w in p.reviewed until min(wi, p.weeks)) {
        val ss = weeks[w].filter { it.state != "skip" }; reviewed = w + 1; changed = true
        if (ss.isEmpty()) continue
        val planKm = ss.sumOf { it.m }; val doneKm = ss.sumOf { it.run?.dist ?: 0.0 }; val done = ss.count { it.run != null }; val ratio = doneKm / planKm
        var f = 1.0; val text: String
        if (done == 0) { f = 0.85; text = "Semana en blanco: bajo un 15 % para retomar sin agobio." }
        else if (ratio < 0.6) { f = 0.9; text = "Quedó menos del 60 % de los km: bajo un 10 % las próximas semanas." }
        else if (done == ss.size && ratio >= 1.1) { f = 1.05; text = "Todo hecho y con margen: subo un 5 %." }
        else text = "Vas según lo previsto. Sigo con el plan tal cual."
        for (k in w + 1 until p.weeks) factor[k] = min(1.15, max(0.7, factor[k] * f))
        lastReview = Review(w, done, ss.size, doneKm, planKm, text)
    }
    if (!changed) return null
    p = p.copy(factor = factor, reviewed = reviewed, lastReview = lastReview)
    data = data.copy(plan = p)
    return p
}

fun lightSession(s: Sess): Sess {
    val st = s.struct
    return if (st != null) { val o = st.copy(reps = max(2, ceil(st.reps * 0.6).toInt())); s.copy(light = true, title = s.title + " · corta", struct = o, m = (st.warm + o.reps * (st.on + st.off) + st.cool).toDouble()) }
    else s.copy(light = true, title = s.title + " · corta", m = max(2000.0, jsRound(s.m * 0.6 / 500) * 500.0))
}

fun App.todaySession(): Sess? { val p = plan ?: return null; val wi = curWeek(p); if (wi < 0 || wi >= p.weeks) return null; return planStatus(p)[wi].firstOrNull { it.state == "today" } }
fun App.nextSession(weeks: List<List<Sess>>): Sess? { val n = dayStart(now()); return weeks.flatten().firstOrNull { it.run == null && it.state != "skip" && dayStart(it.date) >= n } }

data class HistKm(val km: Double, val n: Int)
fun App.histWeeklyKm(): HistKm { val to = weekStart(now()); val from = to - 4 * WEEK; val rs = runs.filter { it.start >= from && it.start < to }; return HistKm(rs.sumOf { it.dist } / 4000, rs.size) }

/** Nuevo plan desde el asistente. */
fun App.newPlan(goal: String, weeks: Int, days: List<Int>, level: String, remindOn: Boolean): Plan =
    Plan(goal, weeks, days.sorted(), level, weekStart(now()), now(), List(weeks) { 1.0 }, 0, null, Remind(remindOn, 8))

/** Calendario .ics con las sesiones pendientes. */
fun App.planIcs(p: Plan): Pair<String, Int>? {
    val left = planStatus(p).flatten().filter { it.state == "today" || it.state == "next" }
    if (left.isEmpty()) return null
    val h = p.remind?.hour ?: 8
    fun dt(t: Long) = es.z(t).let { "%04d%02d%02dT%02d%02d00".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }
    fun esc(v: String) = v.replace("\\", "\\\\").replace(Regex("[,;]")) { "\\" + it.value }.replace("\n", "\\n")
    val nowS = java.time.Instant.ofEpochMilli(now()).toString().replace(Regex("[-:]"), "").replace(Regex("\\.\\d+"), "")
    val ev = left.map { x ->
        val t0 = dayStart(x.date) + h * 3600_000L
        val min = max(20L, jsRound(x.m / 1000 * userPace() * (PACE_K[x.type] ?: 1.0) / 60) + 15)
        listOf("BEGIN:VEVENT", "UID:zancada-${p.created}-${x.key}@zancada", "DTSTAMP:$nowS", "DTSTART:${dt(t0)}", "DTEND:${dt(t0 + min * 60_000)}",
            "SUMMARY:${esc("Zancada · ${x.title} · ${fmtKmS(x.m)} km")}", "DESCRIPTION:${esc(x.desc)}", "BEGIN:VALARM", "TRIGGER:-PT30M", "ACTION:DISPLAY", "DESCRIPTION:Hoy toca correr", "END:VALARM", "END:VEVENT").joinToString("\r\n")
    }
    return (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Zancada//Plan//ES", "CALSCALE:GREGORIAN", "X-WR-CALNAME:Zancada") + ev + "END:VCALENDAR").joinToString("\r\n") + "\r\n" to left.size
}

/** Avisos de los próximos 21 días: (id, hora, título, texto). */
data class Reminder(val id: Int, val at: Long, val title: String, val body: String)
fun App.reminders(): List<Reminder> {
    val p = plan ?: return emptyList(); val r = p.remind ?: return emptyList(); if (!r.on) return emptyList()
    val n = now()
    return planStatus(p).flatten().filter { it.run == null && it.state != "skip" }.mapNotNull { s ->
        val at = es.z(s.date).withHour(r.hour).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        if (at > n && at < n + 21 * DAY) Reminder(1000 + s.week * 7 + s.dayIdx, at, "Hoy toca correr", "${s.title} · ${fmtKmS(s.m)} km. ${s.desc}") else null
    }
}

// ---------- guía de la sesión ----------

fun buildGuide(s: Sess?): List<Seg>? {
    if (s == null) return null
    if (s.segs != null) return s.segs
    val st = s.struct ?: return listOf(Seg(s.title, "${s.title}, ${fmtKmS(s.m).replace(",", " coma ")} kilómetros.", "suave", m = s.m))
    val segs = mutableListOf(Seg("Calentamiento", "Calentamiento, ${st.warm} metros suaves.", "suave", m = st.warm.toDouble()))
    for (k in 1..st.reps) {
        segs.add(if (st.reps == 1) Seg("Tempo · ritmo controlado", "Tempo. ${fmtKmS(st.on.toDouble()).replace(",", " coma ")} kilómetros a ritmo controlado.", "fuerte", m = st.on.toDouble())
        else Seg("Serie $k de ${st.reps} · fuerte", "Serie $k. Fuerte, ${st.on} metros.", "fuerte", m = st.on.toDouble()))
        if (st.off > 0 && k < st.reps) segs.add(Seg("Recupera al trote", "Recupera, ${st.off} metros al trote.", "suave", m = st.off.toDouble()))
    }
    segs.add(Seg("Vuelta a la calma", "Vuelta a la calma, un kilómetro suave.", "suave", m = st.cool.toDouble()))
    return segs
}

data class Tramo(val key: String, val title: String, val desc: String, val struct: Struct?, val type: String, val pyramid: List<Pair<Int, Int>>? = null, val timed: TimedT? = null, val prog: Boolean = false)
data class TimedT(val warm: Int, val reps: Int, val on: Int, val off: Int, val cool: Int, val walk: Boolean = false)

val TRAMOS = listOf(
    Tramo("6x400", "6 × 400 m", "Series cortas: 6 repeticiones de 400 m fuertes con 200 m al trote.", Struct(1000, 6, 400, 200, 800), "series"),
    Tramo("8x200", "8 × 200 m", "Velocidad: 8 repeticiones de 200 m con 200 m al trote.", Struct(1000, 8, 200, 200, 800), "series"),
    Tramo("5x1000", "5 × 1000 m", "Series largas: 5 repeticiones de 1 km con 400 m al trote.", Struct(1200, 5, 1000, 400, 800), "series"),
    Tramo("tempo4", "Tempo 4 km", "Calienta, 4 km a ritmo controlado (incómodo pero sostenible) y vuelta a la calma.", Struct(1200, 1, 4000, 0, 800), "tempo"),
    Tramo("piramide", "Pirámide", "200-400-600-800-600-400-200 m fuertes con 200 m al trote entre ellas.", null, "series", pyramid = listOf(200, 400, 600, 800, 600, 400, 200).map { it to 200 }),
    Tramo("anda1", "Corre 1 min · anda 1,5 ×8", "Para empezar a correr (semana 1): 5 min andando, 8 veces 1 min corriendo y 1,5 min andando, y 5 min andando.", null, "suave", timed = TimedT(300, 8, 60, 90, 300, true)),
    Tramo("anda3", "Corre 3 min · anda 1,5 ×5", "Siguiente paso: 5 min andando, 5 veces 3 min corriendo y 1,5 min andando, y 5 min andando.", null, "suave", timed = TimedT(300, 5, 180, 90, 300, true)),
    Tramo("fartlek", "Fartlek 10 × 1 min", "Por tiempo: 10 min suaves, 10 veces 1 min fuerte y 1 min suave, y 5 min de calma.", null, "series", timed = TimedT(600, 10, 60, 60, 300)),
    Tramo("progresivo", "Progresivo 6 km", "3 km suaves, 2 km a ritmo medio y 1 km fuerte: acabas más rápido de lo que empiezas.", null, "tempo", prog = true),
)

fun App.tramosSession(id: String, custom: TramosCustom? = null): Sess? {
    var t = TRAMOS.firstOrNull { it.key == id }
    if (id == "custom" && custom != null && custom.t) t = Tramo("custom", "${custom.reps} × ${fmtMin(custom.on)}", "${custom.reps} repeticiones de ${fmtMin(custom.on)} fuertes${if (custom.off > 0) " con ${fmtMin(custom.off)} suaves" else ""}, con 5 min de calentamiento y de calma.", null, "series", timed = TimedT(300, custom.reps, custom.on, custom.off, 300))
    else if (id == "custom" && custom != null) t = Tramo("custom", "${custom.reps} × ${custom.on} m", "${custom.reps} repeticiones de ${custom.on} m fuertes${if (custom.off > 0) " con ${custom.off} m al trote" else ""}.", Struct(1000, custom.reps, custom.on, custom.off, 800), "series")
    if (t == null) return null
    t.struct?.let { st -> return Sess(t.key, t.type, t.title, t.desc, (st.warm + st.reps * st.on + max(0, st.reps - 1) * st.off + st.cool).toDouble(), st, custom = true) }
    t.timed?.let { x ->
        val w = x.walk
        val segs = mutableListOf(Seg(if (w) "Calienta andando" else "Calentamiento suave", if (w) "Empieza andando ${fmtMin(x.warm)}." else "Calentamiento, ${fmtMin(x.warm)} suaves.", "suave", sec = x.warm))
        for (k in 1..x.reps) {
            segs.add(Seg("${if (w) "Corre" else "Fuerte"} $k de ${x.reps} · ${fmtMin(x.on)}", "${if (w) "Corre" else "Fuerte"}, ${fmtMin(x.on)}.", "fuerte", sec = x.on))
            if (x.off > 0 && k < x.reps) segs.add(Seg(if (w) "Anda" else "Suave", if (w) "Anda ${fmtMin(x.off)}." else "Suave, ${fmtMin(x.off)}.", "suave", sec = x.off))
        }
        segs.add(Seg(if (w) "Acaba andando" else "Vuelta a la calma", if (w) "Ya está. Acaba andando ${fmtMin(x.cool)}." else "Vuelta a la calma, ${fmtMin(x.cool)} suaves.", "suave", sec = x.cool))
        val sec = segs.sumOf { it.sec!! }
        return Sess(t.key, t.type, t.title, t.desc, jsRound(sec / userPace() * 1000 * (if (w) 0.7 else 1.0)).toDouble(), segs = segs, custom = true, timed = true, min = jsRoundI(sec / 60.0))
    }
    if (t.prog) return Sess(t.key, t.type, t.title, t.desc, 6000.0, segs = listOf(Seg("Suave", "Empieza suave, 3 kilómetros.", "suave", m = 3000.0), Seg("Ritmo medio", "Sube a ritmo medio, 2 kilómetros.", "fuerte", m = 2000.0), Seg("Fuerte · último km", "Último kilómetro, fuerte.", "fuerte", m = 1000.0)), custom = true)
    val ex = t.pyramid!!
    val segs = mutableListOf(Seg("Calentamiento", "Calentamiento, mil metros suaves.", "suave", m = 1000.0))
    ex.forEachIndexed { i, (on, off) ->
        segs.add(Seg("Tramo ${i + 1} de ${ex.size} · $on m fuerte", "Tramo ${i + 1}. Fuerte, $on metros.", "fuerte", m = on.toDouble()))
        if (i < ex.size - 1) segs.add(Seg("Recupera al trote", "Recupera, $off metros al trote.", "suave", m = off.toDouble()))
    }
    segs.add(Seg("Vuelta a la calma", "Vuelta a la calma, 800 metros suaves.", "suave", m = 800.0))
    return Sess(t.key, t.type, t.title, t.desc, segs.sumOf { it.m!! }, segs = segs, custom = true)
}

/** Objetivo sencillo de la carrera libre: "5000" (metros) o "t30" (minutos). */
fun goalSession(g: String): Sess {
    if (g.startsWith("t")) { val min = g.drop(1).toInt(); return Sess("goal", "suave", "$min min", "Corre $min minutos; te aviso al cumplirlos.", 0.0, goal = true, min = min) }
    val m = g.toDouble()
    return Sess("goal", "suave", "${fmtKmS(m)} km", "Corre ${fmtKmS(m)} km; te aviso al llegar.", m, segs = listOf(Seg("Objetivo ${fmtKmS(m)} km", "", "suave", m = m)), goal = true)
}
