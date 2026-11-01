package com.zancada.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

// Lo que pinta cada pantalla, calculado aquí (sin Android) para poder probarlo contra la web.

// ---------- Inicio ----------

data class HomeWeek(val km: String, val perDay: List<Double>, val maxDay: Double, val today: Int, val count: Int, val goal: Int, val dur: Double, val streak: Int, val nudge: String)

fun App.homeWeek(): HomeWeek {
    val w = weekStats(); val ws = weekStart(now())
    val week = runs.filter { it.start >= ws }
    val perDay = DoubleArray(7).also { a -> week.forEach { a[es.dow(it.start)] += it.dist } }.toList()
    val left = max(0, goalOf() - w.count)
    val nudge = if (runs.isEmpty()) "Tu primera carrera: GPS, ritmo en directo y voz en cada km" else if (left == 0) "Semana cumplida. Lo que corras ahora suma." else "Te falta${if (left == 1) "" else "n"} $left ${pl(left, "carrera")} para el objetivo"
    return HomeWeek(toFixed(week.sumOf { it.dist } / 1000, 1).replace('.', ','), perDay, max(5000.0, perDay.max()), w.today, w.count, goalOf(), week.sumOf { it.dur }, w.streak, nudge)
}

data class Recap(val ws: Long, val n: Int, val km: Double, val dur: Double, val text: String)

/** Resumen de la semana pasada, de lunes a miércoles. */
fun App.recap(): Recap? {
    val dow = es.dow(now()); val ws = weekStart(now())
    val last = runs.filter { it.start >= ws - WEEK && it.start < ws }; val prev = runs.filter { it.start >= ws - 2 * WEEK && it.start < ws - WEEK }
    if (dow > 2 || last.isEmpty() || settings.recapSeen == ws) return null
    val km = last.sumOf { it.dist } / 1000; val pkm = prev.sumOf { it.dist } / 1000; val dur = last.sumOf { it.dur }
    val best = last.reduce { a, r -> if (r.dist > a.dist) r else a }
    val cmp = if (pkm > 0) "${if (km >= pkm) "+" else "−"}${jsRound(abs(km - pkm) / pkm * 100)} % respecto a la anterior" else "la semana anterior no corriste"
    val text = "${cap(cmp)}. La más larga: ${best.name}, ${fmtKm(best.dist)} km.${if (last.size >= goalOf()) " Objetivo semanal cumplido." else " ${last.size} de ${goalOf()} carreras del objetivo."}"
    return Recap(ws, last.size, km, dur, text)
}

fun App.showStartCard() = runs.size < 3 && plan == null && settings.startSeen != "1"

sealed interface PlanCard {
    data object None : PlanCard
    data class Finished(val done: Int, val total: Int, val text: String) : PlanCard
    data class Week(val head: String, val doneTxt: String, val chip: String?, val title: String?, val desc: String?, val pct: Double, val review: String?) : PlanCard
}

fun App.planCard(): PlanCard {
    val p = plan ?: return PlanCard.None
    val weeks = planStatus(p); val wi = curWeek(p); val g = GOALS[p.goal]!!
    if (wi >= p.weeks) { val all = weeks.flatten().filter { it.state != "skip" }; val done = all.count { it.run != null }; return PlanCard.Finished(done, all.size, "${g.name} en ${p.weeks} semanas. Toca para verlo o crear otro.") }
    if (wi < 0) return PlanCard.Week("Plan ${g.name} · empieza pronto", "", null, null, null, 0.0, null)
    val ws = weeks[wi]; val done = ws.count { it.run != null }; val total = ws.count { it.state != "skip" }
    val td = ws.firstOrNull { it.state == "today" }; val nx = td ?: nextSession(weeks)
    val whenS = if (nx == null) "" else if (nx === td) "Hoy" else if (dayStart(nx.date) == dayStart(now()) + DAY) "Mañana" else "El " + DAY_N[nx.dayIdx]
    val rv = p.lastReview?.takeIf { it.week == wi - 1 }?.let { "Semana pasada: ${it.done} de ${it.total} sesiones, ${fmtKmS(it.doneKm)} de ${fmtKmS(it.planKm)} km. ${it.text}" }
    val daysLeft = max(0.0, kotlin.math.ceil(((p.raceDate ?: (p.start + p.weeks * WEEK)) - now()) / DAY.toDouble())).toLong()
    val head = "${p.raceName ?: "Plan " + g.name} · semana ${wi + 1} de ${p.weeks}${if (g.race > 0) " · $daysLeft días" else ""}"
    return PlanCard.Week(head, "$done/$total", nx?.let { whenS }, nx?.let { "${it.title} · ${fmtKmS(it.m)} km" }, nx?.desc, if (total > 0) done.toDouble() / total else 0.0, rv)
}

data class Near(val r: Route, val d: Double, val times: Int)
fun App.nearRoute(pos: LatLon?): Near? {
    pos ?: return null
    val x = zr.map { it to hav(pos, it.startLL()) }.filter { it.second < 3000 }.minByOrNull { it.second } ?: return null
    return Near(x.first, x.second, runs.count { it.route?.id == x.first.id })
}

// ---------- Actividad ----------

data class Month(val name: String, val km: Double, val n: Int, val dur: Double, val days: Int, val perDay: List<Double>, val today: Int, val avg: Double)

fun App.month(): Month {
    val d = es.z(now()); val y = d.year; val m = d.monthValue
    val mr = runs.filter { es.z(it.start).let { z -> z.year == y && z.monthValue == m } }
    val days = d.toLocalDate().lengthOfMonth()
    val per = DoubleArray(days).also { a -> mr.forEach { a[es.z(it.start).dayOfMonth - 1] += it.dist } }
    val dist = mr.sumOf { it.dist }; val dur = mr.sumOf { it.dur }
    return Month(es.monYear(now()), dist, mr.size, dur, days, per.toList(), d.dayOfMonth - 1, if (dist > 0) dur / (dist / 1000) else Double.NaN)
}

data class WeekBar(val from: Long, val km: Double, val n: Int)
data class Weeks(val list: List<WeekBar>, val max: Double, val axis: List<String>, val sub: String)

fun App.weeks12(): Weeks {
    val ws0 = weekStart(now())
    val weeks = (0 until 12).map { i -> val from = ws0 - (11 - i) * WEEK; val rs = runs.filter { it.start >= from && it.start < from + WEEK }; WeekBar(from, rs.sumOf { it.dist } / 1000, rs.size) }
    val last4 = weeks.takeLast(4).sumOf { it.km } / 4; val prev4 = weeks.subList(4, 8).sumOf { it.km } / 4
    val sub = if (last4 > 0) "media ${toFixed(last4, 1).replace('.', ',')} km/sem${if (prev4 >= 3) " · ${if (last4 >= prev4) "+" else "−"}${min(200L, jsRound(abs(last4 - prev4) / prev4 * 100))} %" else ""}" else "sin carreras aún"
    return Weeks(weeks, max(5.0, weeks.maxOf { it.km }), listOf(0, 4, 8, 11).map { es.dayMon(weeks[it].from) }, sub)
}

data class CalDay(val day: Int, val km: Double, val runs: List<Run>, val size: Int, val today: Boolean)
data class Calendar(val title: String, val lead: Int, val days: List<CalDay>, val sub: String, val canNext: Boolean)

fun App.calendar(off: Int): Calendar {
    val base = es.z(now()).toLocalDate().withDayOfMonth(1).plusMonths(off.toLong())
    val y = base.year; val m = base.monthValue; val days = base.lengthOfMonth(); val lead = base.dayOfWeek.value - 1
    val per = runs.filter { es.z(it.start).let { z -> z.year == y && z.monthValue == m } }.groupBy { es.z(it.start).dayOfMonth }
    val mx = max(5000.0, per.values.maxOfOrNull { rs -> rs.sumOf { it.dist } } ?: 0.0)
    val today = es.z(now()); val isCur = today.year == y && today.monthValue == m
    val list = (1..days).map { d -> val rs = per[d] ?: emptyList(); val km = rs.sumOf { it.dist }; CalDay(d, km, rs, if (rs.isNotEmpty()) jsRoundI(14 + 20 * min(1.0, km / mx)) else 0, isCur && d == today.dayOfMonth) }
    val n = per.size
    val sub = if (n > 0) "$n ${pl(n, "día")} corriendo · ${fmtKmS(per.values.flatten().sumOf { it.dist })} km" else "Sin carreras este mes"
    val ts = base.atStartOfDay(zone).toInstant().toEpochMilli()
    return Calendar(cap(es.monYear(ts)), lead, list, sub, off < 0)
}

data class MonthRow(val label: String, val km: Double, val n: Int, val dur: Double)
data class Months(val rows: List<MonthRow>, val max: Double, val sub: String)

fun App.months(): Months? {
    val by = runs.groupBy { es.z(it.start).let { z -> z.year * 100 + (z.monthValue - 1) } }
    val ms = by.entries.sortedByDescending { it.key }.take(6)
    if (ms.size < 2) return null
    val yr = es.z(now()).year
    val rows = ms.map { (k, rs) ->
        val y = k / 100; val mi = k % 100
        MonthRow(cap(EsDate.MON_LONG[mi] + if (y != yr) " $y" else ""), rs.sumOf { it.dist } / 1000, rs.size, rs.sumOf { it.dur })
    }
    return Months(rows, rows.maxOf { it.km }, "${rows.sumOf { it.n }} carreras · ${jsRound(rows.sumOf { it.km })} km")
}

data class Challenge(val title: String, val desc: String, val v: Double, val goal: Double, val fmt: (Double) -> String) { val done get() = v >= goal }
data class Challenges(val list: List<Challenge>, val head: String)

fun App.challenges(): Challenges {
    val now = es.z(now()); val days = now.toLocalDate().lengthOfMonth()
    val mr = runs.filter { es.z(it.start).let { z -> z.year == now.year && z.monthValue == now.monthValue } }
    val easy = settings.startSeen != "1"; val longKm = if (easy) 3.0 else 5.0
    val routes = mr.mapNotNull { it.route?.id }.filter { !it.startsWith("run-") }.toSet()
    val L = listOf(
        Challenge("Constancia", "${if (easy) 6 else 8} salidas este mes", mr.size.toDouble(), if (easy) 6.0 else 8.0) { jsNum(it) },
        Challenge("Tirada larga", "Una carrera de ${jsNum(longKm)} km o más", max(0.0, mr.maxOfOrNull { it.dist / 1000 } ?: 0.0), longKm) { toFixed(it, 1).replace('.', ',') + " km" },
        Challenge("Explorador", "2 rutas recomendadas distintas", routes.size.toDouble(), 2.0) { jsNum(it) },
        Challenge("Cuestas", "${if (easy) 100 else 200} m de desnivel acumulado", mr.sumOf { (it.gain ?: 0).toDouble() }, if (easy) 100.0 else 200.0) { "${jsRound(it)} m" },
    )
    val ok = L.count { it.done }; val left = days - now.dayOfMonth
    return Challenges(L, "$ok de ${L.size}${if (left > 0) " · quedan $left ${pl(left, "día")}" else " · último día"}")
}

data class MonthGoal(val goal: Int, val km: Double, val pct: Double, val text: String)

fun App.monthGoal(): MonthGoal? {
    val goal = settings.monthkm.toIntOrNull() ?: 0; if (goal == 0) return null
    val mo = month(); val now = es.z(now())
    val km = mo.km / 1000; val left = max(0.0, goal - km); val daysLeft = mo.days - now.dayOfMonth + 1
    val perWeek = left / max(1.0, daysLeft / 7.0); val onTrack = km / goal >= (now.dayOfMonth - 1).toDouble() / mo.days
    val text = if (left <= 0) "¡Objetivo del mes cumplido! Lo que corras ahora es extra." else "Faltan ${toFixed(left, 1).replace('.', ',')} km: unos ${toFixed(perWeek, 1).replace('.', ',')} km por semana.${if (onTrack) " Vas bien." else " Vas algo por detrás."}"
    return MonthGoal(goal, km, min(100.0, km / goal * 100), text)
}

data class Trend(val pc: List<Double>, val ma: List<Double>, val lo: Double, val hi: Double, val sub: String, val note: String)

/** Evolución del ritmo: cada punto una carrera, la línea la media de las últimas 5. */
fun App.trend(): Trend? {
    val L = runs.filter { it.dist >= 1000 && it.dur > 0 }.sortedBy { it.start }.takeLast(20)
    if (L.size < 4) return null
    val pc = L.map { it.paceSec() }
    val ma = pc.indices.map { i -> val w = pc.subList(max(0, i - 4), i + 1); w.sum() / w.size }
    val n = min(5, L.size / 2); val nowP = pc.takeLast(n).sum() / n; val before = pc.take(n).sum() / n; val d = jsRound(before - nowP)
    val note = if (abs(d) < 5) "Ritmo estable: unos ${pace(nowP)}/km de media. Si quieres mejorar, mete una sesión de series o tempo a la semana."
    else if (d > 0) "Vas $d s/km más rápido que al principio (${pace(before)} → ${pace(nowP)}/km). Buena señal: mantén la mayoría de salidas en suave."
    else "Vas ${-d} s/km más lento que al principio (${pace(before)} → ${pace(nowP)}/km). Puede ser cansancio, calor o rutas con más cuestas: no pasa nada si las salidas son suaves."
    return Trend(pc, ma, pc.min(), pc.max(), "últimas ${L.size} carreras", note)
}

data class Paces(val src: String, val preds: List<Pair<String, String>>, val train: List<Pair<String, String>>)

fun App.paces(): Paces? {
    if (runs.size < 2) return null
    val b = raceBase()
    return Paces(b.srcTxt, listOf("5 km" to 5000.0, "10 km" to 10000.0, "Media" to 21097.0).map { (l, d) -> l to fmtTime(b.predict(d)) },
        listOf("Suave" to "suave", "Largo" to "largo", "Tempo" to "tempo", "Series" to "series").map { (l, k) -> l to targetPace(k) })
}

/** Filtros y buscador de la lista de carreras. */
fun App.historyList(filter: String, q: String): List<Run> {
    val qq = q.trim().lowercase()
    val f: (Run) -> Boolean = when (filter) { "plan" -> { r -> r.session != null }; "route" -> { r -> r.route != null }; "long" -> { r -> r.dist >= 8000 }; "note" -> { r -> r.note != null || r.feel != null }; else -> { _ -> true } }
    return runs.filter(f).filter { r -> qq.isEmpty() || listOf(r.name, r.note, r.route?.name, r.session?.title, r.goal).any { it != null && it.lowercase().contains(qq) } }
}

// ---------- Perfil ----------

data class Mark(val name: String, val best: Double, val `when`: Long?)

fun App.marks(): List<Mark> = listOf("1 km" to 1000.0, "5K" to 5000.0, "10K" to 10000.0, "Media maratón" to 21097.5, "Maratón" to 42195.0).map { (name, m) ->
    var best = Double.POSITIVE_INFINITY; var w: Long? = null
    runs.forEach { r -> val t = bestEffort(r, m); if (t < best) { best = t; w = r.start } }
    Mark(name, best, w)
}

// ---------- Plan ----------

data class PlanScreen(val chip: String, val sub: String, val title: String, val pred: String?, val view: Int, val cur: Int, val weekTitle: String, val note: String?,
                      val done: Double, val km: Double, val sessions: List<Sess>, val weekKms: List<Long>)

fun App.planScreen(view: Int?): PlanScreen? {
    val p = plan ?: return null
    val weeks = planStatus(p); val wi = min(p.weeks - 1, max(0, curWeek(p))); val g = GOALS[p.goal]!!
    val w = if (view == null || view < 0) wi else min(view, p.weeks - 1)
    val ss = weeks[w]; val km = ss.sumOf { it.m }; val done = ss.sumOf { it.run?.dist ?: 0.0 }
    val pred = if (g.race > 0 && runs.size >= 2) { val b = raceBase(); val t = b.predict(g.race); "Con tu forma de hoy, tu ${g.name} saldría en ${fmtTime(t)} (${pace(t / (g.race / 1000))}/km), ${b.srcTxt}. Se irá ajustando con cada carrera." } else null
    val f = p.factor?.getOrNull(w)
    val note = if (f != null && abs(f - 1) > 0.01) "${if (f > 1) "Subida" else "Bajada"} del ${jsRound(abs(f - 1) * 100)} % tras la revisión"
        else if (g.race > 0 && w == p.weeks - 1) "Semana de carrera: afinar" else if (w % 4 == 3 && w < p.weeks - 1) "Semana de descarga" else null
    return PlanScreen(if (g.name == "Constancia") "Hábito" else g.name, (p.raceName?.let { "$it · " + es.dayMon(p.raceDate ?: 0) } ?: "${p.weeks} semanas · ${listEs(p.days.map { DAY_N[it] })}"),
        if (curWeek(p) >= p.weeks) "Plan terminado" else "Semana ${wi + 1} de ${p.weeks}", pred, w, wi, if (w == wi) "Esta semana" else "Semana ${w + 1}", note,
        done, km, ss, weeks.map { x -> jsRound(x.sumOf { it.m } / 1000) })
}

fun App.sessLine(s: Sess): String = es.wdLongDay(s.date) + when { s.run != null -> " · hecha${if (s.moved) " (otro día)" else ""}: ${fmtKm(s.run.dist)} km"; s.state == "miss" -> " · no hecha"; s.state == "skip" -> " · antes de crear el plan"; s.state == "today" -> " · hoy"; else -> "" }

/** Resumen del asistente antes de crear el plan. */
fun App.planSetupText(goal: String, weeks: Int, days: List<Int>, level: String, reminders: Boolean): Pair<String, String> {
    val tmp = Plan(goal, weeks, days, level, weekStart(now()), now(), emptyList())
    val kms = (0 until weeks).map { planWeekKm(tmp, it) }; val g = GOALS[goal]!!
    return (if (g.race > 0) "${g.name} en $weeks semanas" else "$weeks semanas de hábito") to
        "${days.size} sesiones a la semana (${listEs(days.map { DAY_N[it] })}), de ${jsRound(kms.min())} a ${jsRound(kms.max())} km semanales. Cada lunes reviso lo que has corrido y ajusto la carga.${if (reminders) " Te avisaré a las 8:00 los días de entreno." else ""} Tu objetivo semanal pasa a ${days.size} carreras."
}

// ---------- Resumen de una carrera ----------

fun App.summaryInfo(r: Run): List<Pair<String, Boolean>> {
    val info = mutableListOf<Pair<String, Boolean>>()
    plan?.let { p -> planStatus(p).flatten().firstOrNull { it.run?.id == r.id }?.let { ps -> info.add("Plan · semana ${ps.week + 1} · ${ps.title} ${fmtKmS(ps.m)} km · ${if (r.dist >= ps.m * 0.9) "cumplida" else "parcial"}${if (ps.moved) " (otro día)" else ""}" to false) } }
    if (r.session?.custom == true) info.add("Entreno por tramos: ${r.session.title}" to false)
    r.goal?.let { info.add("Objetivo: $it" to false) }
    r.route?.let { info.add(("Ruta: " + it.name + if (it.auto) " (detectada por el trazado)" else "") to false) }
    r.gain?.let { info.add("Desnivel: +$it m" to false) }
    if (r.manual) info.add("Añadida a mano (cinta o reloj)" to false)
    if (r.imported) info.add("Importada de un GPX" to false)
    r.shoe?.let { id -> shoes.firstOrNull { it.id == id }?.let { info.add("Zapatillas: " + it.name to false) } }
    r.wx?.let { info.add("Tiempo: ${it.t} °C, viento ${it.wind} km/h${if (it.rain != 0) ", lluvia" else ""}" to false) }
    val recHere = personalRecords().filter { it.run?.id == r.id }.map { it.label.lowercase() }
    if (recHere.isNotEmpty()) info.add(0, "Récord personal: ${listEs(recHere)}" to true)
    compareRun(r)?.let { info.add(it to false) }
    return info
}

/** Texto de "Avisar a alguien" antes de salir. */
fun App.tellText(route: Route?, ss: Sess?, pos: LatLon?): String {
    val min = if (ss?.min != null) ss.min.toDouble() else if (ss != null && ss.m > 0) ss.m / 1000 * userPace() / 60 else if (route != null) routeMeta(route).time / 60 else 45.0
    val back = es.hm(now() + ((min + 10) * 60_000).toLong())
    val p = route?.startLL() ?: pos
    val map = p?.let { " https://maps.google.com/?q=${toFixed(it.lat, 5)},${toFixed(it.lon, 5)}" } ?: ""
    return "Salgo a correr${route?.let { " por ${it.name}" } ?: ""}${ss?.let { " (${it.title.ifEmpty { "entreno" }})" } ?: ""}. Calculo volver sobre las $back.${if (p != null) " ${if (route != null) "Salida" else "Salgo de aquí"}:$map" else ""}"
}

/** Mensaje al terminar una carrera. */
fun App.celeText(r: Run, newBadges: List<Badge>, prevBest: Double): String {
    val w = weekStats()
    return if (newBadges.isNotEmpty()) "Nueva insignia: ${listEs(newBadges.map { it.name })}." else if (r.route?.auto == true) "Has corrido ${r.route.name}: la próxima vez te comparo con este tiempo."
    else if (r.session != null && sessDone(r)) (if (r.session.custom) "Entreno por tramos completado." else "Sesión del plan cumplida.") else if (runs.size == 1) "Tu primera carrera con Zancada."
    else if (r.dist > prevBest) "Tu carrera más larga hasta ahora." else if (w.count == goalOf()) "Objetivo de la semana cumplido." else "Carrera guardada."
}

fun App.finishSpeech(r: Run, newBadges: List<Badge>): String {
    val ok = r.splits.filter { !it.partial }; val bestS = ok.minByOrNull { it.split }
    val t = fmtTime(r.dur).let { s -> Regex("^(\\d+):(\\d+)$").find(s)?.let { "${it.groupValues[1]} minutos ${it.groupValues[2]}" } ?: Regex("^(\\d+):(\\d+):(\\d+)$").find(s)?.let { "${it.groupValues[1]} horas ${it.groupValues[2]} minutos" } ?: s }
    return "Carrera terminada. ${fmtKm(r.dist).replace(",", " coma ")} kilómetros en $t. Ritmo ${pace(r.paceSec()).replace(":", " ")}.${if (bestS != null) " Tu mejor kilómetro, el ${bestS.km}." else ""}${if (newBadges.isNotEmpty()) " Nueva insignia: ${listEs(newBadges.map { it.name })}." else ""}"
}

/** Segunda línea de una carrera en la lista: fecha, ritmo, sensación y nota. */
fun App.histSub(r: Run) = "${es.fmtDate(r.start)} · ${pace(r.paceSec())} /km${r.feel?.takeIf { it in 1..5 }?.let { " · " + FEEL_L[it].lowercase() } ?: ""}${if (r.note != null) " · 📝" else ""}"
/** La carrera más larga lleva la etiqueta «Récord» en la lista. */
fun App.isLongest(r: Run) = runs.size > 1 && r.dist == runs.maxOf { it.dist }
/** Ritmo objetivo que se muestra en las sesiones pendientes del plan. */
fun App.sessPace(s: Sess) = if (s.type != "carrera" && s.run == null) "~${targetPace(s.type)}/km" else null
