package com.zancada.core

import kotlinx.serialization.json.*
import java.io.File
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * La app nativa calcula lo mismo que la web. tests/tools/dump-app.mjs carga la web con
 * tests/fixtures/app-data.json y un reloj fijo (miércoles 21/10/2026 10:30, Madrid) y guarda
 * todo en tests/golden/app.json. Aquí cargamos los mismos datos y comparamos valor a valor.
 * webCompat = true: reproduce también los fallos de la web que la app corrige (ritmo «4:60»…).
 */
class AppParityTest {
    private val dir = File(System.getProperty("zancada.tests") ?: "../../tests")
    private val golden = Json.parseToJsonElement(File(dir, "golden/app.json").readText()).jsonObject
    private val T0 = java.time.OffsetDateTime.parse("2026-10-21T10:30:00+02:00").toInstant().toEpochMilli()
    private val zone = ZoneId.of("Europe/Madrid")
    private val zr = parseRecRoutes(File(dir, "../routes-data.js").readText())
    private val app = App(Store.fromKeys(Store.parseKeys(File(dir, "fixtures/app-data.json").readText())), zr, { T0 }, zone, webCompat = true).also { it.reviewPlan() }
    private val errs = mutableListOf<String>()

    private fun g(path: String): JsonElement = path.split('.').fold(golden as JsonElement) { e, k -> if (e is JsonArray) e[k.toInt()] else e.jsonObject[k] ?: JsonNull }

    private fun js(v: Any?): JsonElement = when (v) {
        null -> JsonNull; is JsonElement -> v; is String -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v)
        is Double -> if (v.isFinite()) JsonPrimitive(v) else JsonNull; is Number -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to js(it.value) })
        is Iterable<*> -> JsonArray(v.map { js(it) }); is Array<*> -> JsonArray(v.map { js(it) })
        is Pair<*, *> -> JsonArray(listOf(js(v.first), js(v.second)))
        else -> JsonPrimitive(v.toString())
    }

    private fun same(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a[it]!!, b[it]!!) }
        a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString && a.doubleOrNull != null && b.doubleOrNull != null ->
            abs(a.double - b.double) <= 1e-6 * max(1.0, abs(a.double))
        else -> a == b
    }

    private fun chk(path: String, actual: Any?) {
        val exp = g(path); val act = js(actual)
        if (!same(exp, act)) errs += "$path\n   web:    ${exp.toString().take(600)}\n   nativa: ${act.toString().take(600)}"
    }

    private fun r3(x: Double) = jsRound(x * 1000) / 1000.0
    private fun r2(x: Double) = jsRound(x * 100) / 100.0

    @Test
    fun calculaLoMismoQueLaWeb() {
        val R = app.runs
        // formatos
        chk("fmt.pace", listOf(0.0, 299.6, 300.0, 359.5, 359.4, 3600.0, 2401.0, 2399.6).map { fmtPace(it, web = true) })
        chk("fmt.time", listOf(0.0, 59.5, 61.0, 3599.5, 3600.0, 7322.0).map(::fmtTime))
        chk("fmt.km", listOf(0.0, 5.0, 994.9, 1005.0, 21097.5).map(::fmtKm))
        chk("fmt.kmS", listOf(0.0, 500.0, 1000.0, 1500.0, 21097.5, 3333.0).map(::fmtKmS))
        chk("fmt.m", listOf(5.0, 44.0, 950.0, 949.0, 1234.0, 12000.0).map(::fmtM))
        chk("fmt.dur", listOf(59.0, 600.0, 3599.0, 3600.0, 5000.0).map(::fmtDur))
        chk("fmt.away", listOf(0.2, 0.95, 1.0, 3.33, 12.6).map(::fmtAway))
        chk("fmt.min", listOf(60, 90, 150, 300).map(::fmtMin))
        chk("fmt.list", listOf(listOf(), listOf("a"), listOf("a", "b"), listOf("a", "b", "c")).map(::listEs))
        chk("fmt.date", R.take(4).map { app.es.fmtDate(it.start) })
        chk("fmt.runName", listOf(7, 12, 19, 20, 23).map { runName(java.time.ZonedDateTime.of(2026, 10, 21, it, 0, 0, 0, zone).toInstant().toEpochMilli(), zone) })
        // estadísticas
        chk("weekStart", listOf(T0, R[0].start, R.last().start).map { app.weekStart(it) })
        app.weekStats().let { chk("weekStats", mapOf("count" to it.count, "daysDone" to it.daysDone.sorted(), "streak" to it.streak, "today" to it.today)) }
        chk("records", app.personalRecords().map { mapOf("label" to it.label, "val" to it.value, "run" to it.run?.id) })
        chk("bestStreak", app.bestStreak())
        app.raceBase().let { chk("raceBase", mapOf("base" to mapOf("d" to it.d, "t" to it.t), "srcTxt" to it.srcTxt)) }
        chk("userPace", app.userPace())
        chk("targetPace", listOf("suave", "largo", "tempo", "series", "carrera", "otro").map { app.targetPace(it) })
        chk("badges", app.badges().map { mapOf("id" to it.id, "when" to it.`when`) })
        chk("shoeKm", app.shoes.map { app.shoeKm(it) })
        R.forEachIndexed { i, r ->
            chk("perRun.$i", mapOf("id" to r.id, "bestFor5" to bestFor(r, 5000), "bestFor10" to bestFor(r, 10000), "bestFor1" to bestFor(r, 1000),
                "effort1" to bestEffort(r, 1000.0), "effort5" to bestEffort(r, 5000.0), "compare" to app.compareRun(r), "insights" to app.runInsights(r),
                "segs" to (if (r.points.size > 5) paceSegments(r.points).map { listOf(it.ll.size, r3(it.dist), r3(it.pace)) } else emptyList()),
                "detect" to app.detectRoute(r)?.let { mapOf("id" to it.id, "name" to it.name, "score" to it.score) }, "sessDone" to if (r.session != null) sessDone(r) else null))
        }
        chk("bestOnRoute", zr.take(3).map { app.bestOnRoute(it.id)?.id })
        val ref = R.first { it.route != null }
        chk("ghost", listOf(0.0, 30000.0, 120000.0, 9e9).map { ghostDistAt(ref, it) } + listOf(0.0, 500.0, 1000.0, 1e9).map { ghostTimeAt(ref, it) })
        // pantallas
        app.weeks12().let { w -> chk("history.weeksSub", w.sub); chk("history.weekAxis", w.axis.joinToString("")) }
        app.calendar(0).let { c -> chk("history.calTitle", c.title); chk("history.calSub", c.sub) }
        app.month().let { m -> chk("history.monthKm", toFixed(m.km / 1000, 1).replace('.', ',')); chk("history.monthStats", "Carreras${m.n} Tiempo${fmtTime(m.dur)} Ritmo medio${app.pace(m.avg)}/km") }
        app.monthGoal()?.let { m -> chk("history.monthGoal", "Objetivo del mes${jsRound(m.km)} de ${m.goal} km${m.text}") }
        app.challenges().let { c -> chk("history.challenges", "Retos del mes${c.head}" + c.list.joinToString("") { x -> "${if (x.done) "✓ " else ""}${x.title} · ${x.desc}${if (x.done) "Hecho" else x.fmt(x.v) + " / " + x.fmt(x.goal)}" }) }
        app.trend()?.let { t -> chk("history.trendNote", t.note); chk("history.trendSub", t.sub) }
        chk("history.months", app.months()?.let { "x" } ?: "")
        app.paces()?.let { p -> chk("history.paces", "Tus ritmos${p.src}" + p.preds.joinToString("") { it.first + it.second } + p.train.joinToString("") { it.first + it.second + "/km" }) }
        chk("history.records", app.personalRecords().joinToString("") { it.label + it.value + (it.run?.let { r -> app.es.wdDayMon(r.start) } ?: "") })
        chk("history.list", app.historyList("all", "").map { r -> "${r.name}${if (app.isLongest(r)) "Récord" else ""} ${app.histSub(r)} ${fmtKm(r.dist)}km" })
        app.homeWeek().let { h -> chk("home.weekKm", h.km); chk("home.nudge", h.nudge); chk("home.weekStats", "Carreras${h.count}de ${h.goal} Tiempo${fmtTime(h.dur)} Seguidas${h.streak}sem") }
        chk("home.date", cap(app.es.wdLongDayMonLong(T0)))
        (app.planCard() as? PlanCard.Week)?.let { c -> chk("home.plan", "${c.head}${c.doneTxt} ${c.chip}${c.title}${c.desc} ${c.review}") }
        app.recap()?.let { r -> chk("home.recap", "La semana pasada Carreras${r.n}Km${toFixed(r.km, 1).replace('.', ',')}Tiempo${fmtTime(r.dur)} ${r.text}") }
        chk("profile.prs", app.marks().joinToString("") { m -> m.name + (m.`when`?.let { app.es.dayMonYear(it) + (if (R.isNotEmpty() && m.`when` == R[0].start) "Nueva" else "") + fmtTime(m.best) } ?: "Aún sin marca—") })
        // plan
        val P = app.plan!!
        chk("plan.factor", P.factor); chk("plan.reviewed", P.reviewed)
        P.lastReview?.let { chk("plan.lastReview", mapOf("week" to it.week, "done" to it.done, "total" to it.total, "doneKm" to it.doneKm, "planKm" to it.planKm, "text" to it.text)) }
        chk("plan.weekKm", (0 until P.weeks).map { planWeekKm(P, it) })
        app.histWeeklyKm().let { chk("plan.hist", mapOf("km" to it.km, "n" to it.n)) }
        chk("plan.sessions", app.planStatus(P).map { w -> w.map { s -> mapOf("key" to s.key, "type" to s.type, "title" to s.title, "desc" to s.desc, "m" to s.m, "dayIdx" to s.dayIdx, "date" to s.date, "state" to s.state,
            "run" to s.run?.id, "moved" to s.moved, "struct" to s.struct?.let { st -> mapOf("warm" to st.warm, "reps" to st.reps, "on" to st.on, "off" to st.off, "cool" to st.cool) }) } })
        chk("plan.today", app.todaySession()?.key); chk("plan.next", app.nextSession(app.planStatus(P))?.key)
        chk("plan.light", planSessions(P).flatten().take(8).map { lightSession(it).let { l -> listOf(l.title, l.m) } })
        app.planScreen(null)!!.let { s -> chk("planScreen.title", s.title); chk("planScreen.sub", s.sub); chk("planScreen.pred", s.pred)
            chk("planScreen.weeks", s.weekKms.mapIndexed { i, k -> "${i + 1}$k km" }.joinToString(""))
            chk("planScreen.week", "${s.weekTitle}${s.note ?: ""} ${fmtKmS(s.done)}de ${fmtKmS(s.km)} km${s.sessions.count { it.run != null }}/${s.sessions.size} sesiones")
            chk("planScreen.sessions", s.sessions.map { x -> "${if (x.state == "ok") "" else DAY_L[x.dayIdx] + " "}${x.title}${app.sessLine(x)} ${fmtKmS(x.m)}km${app.sessPace(x) ?: ""}" }) }
        // guía, tramos, objetivos
        fun seg(s: Seg) = mapOf("label" to s.label, "say" to s.say, "kind" to s.kind) + (s.m?.let { mapOf("m" to it) } ?: emptyMap()) + (s.sec?.let { mapOf("sec" to it) } ?: emptyMap())
        chk("guide", planSessions(P)[3].map { buildGuide(it)?.map(::seg) })
        fun sess(s: Sess?) = s?.let { x -> x }
        chk("routes.n", zr.size)
        chk("routes.meta", zr.map { r -> app.routeMeta(r).let { listOf(r.id, r3(it.time), it.diff) } })
        chk("routes.poly", zr.take(2).map { it.ll.take(5) })
        listOf(Triple(null as LatLon?, null as String?, Triple("all", "all", "near")), Triple(null, "sanfernando", Triple("all", "all", "near")), Triple(LatLon(40.4153, -3.6845), null, Triple("all", "all", "near")),
            Triple(LatLon(40.4153, -3.6845), "cerca", Triple("t30", "all", "near")), Triple(null, "madrid", Triple("all", "tierra", "long")), Triple(null, "madrid", Triple("t60", "all", "flat")),
            Triple(null, "madrid", Triple("all", "all", "new")), Triple(null, "madrid", Triple("all", "asfalto", "short"))).forEach { (here, zn, t) ->
            val l = app.recList(RouteFilter(here, zn, t.first, t.second, t.third))
            val key = "${here?.let { "{\"lat\":${jsNum(it.lat)},\"lon\":${jsNum(it.lon)}}" } ?: "null"}|${zn ?: ""}|${t.first}|${t.second}|${t.third}"
            val exp = g("routes.lists").jsonObject[key]!!; val act = js(l.map { listOf(it.id, it.away?.let { a -> jsRound(a * 1e6) / 1e6 }) })
            if (!same(exp, act)) errs += "routes.lists[$key]\n   web:    $exp\n   nativa: $act"
            if (here != null && zn == null) app.pickToday(l).let { td -> val e = g("routes.lists").jsonObject["today"]!!; val a = js(td?.let { mapOf("id" to it.r.id, "why" to it.why) }); if (!same(e, a)) errs += "routes.today\n   web:    $e\n   nativa: $a" }
        }
        chk("routes.nearCity", nearCity(LatLon(36.47, -6.19)))
        chk("routes.mine", app.myRoutes().map { listOf(it.id, it.name, it.km, it.gain, it.src, it.sub) })
        chk("nav", zr.take(4).map { r -> buildNav(r.ll).let { n -> mapOf("pts" to n.pts.size, "total" to r3(n.total), "turns" to n.turns.map { t -> listOf(t.i, r2(t.d), r2(t.dv), t.dir, t.sharp) }) } })
        chk("geo", mapOf("bearing" to bearing(LatLon(40.41, -3.7), LatLon(40.42, -3.69)), "dest" to dest(LatLon(40.41, -3.7), 75.0, 1500.0).let { mapOf("lat" to it.lat, "lon" to it.lon) },
            "compass" to listOf(0.0, 44.0, 46.0, 180.0, 359.0, -30.0).map(::compass), "ang" to listOf(10.0 to 350.0, 350.0 to 10.0, 0.0 to 180.0).map { angDiff(it.first, it.second) }))
        // GPX
        val pts = (0 until 60).joinToString("") { i -> "<trkpt lat=\"${jsNum(40.41 + i * 0.00025)}\" lon=\"${jsNum(-3.68 + kotlin.math.sin(i / 5.0) * 0.0002)}\"><ele>${650 + jsRound(kotlin.math.sin(i / 8.0) * 9)}</ele><time>${isoMs(java.time.Instant.parse("2026-10-10T07:00:00Z").toEpochMilli() + i * 9000L)}</time></trkpt>" }
        val gpx = "<?xml version=\"1.0\"?><gpx><metadata><name>Mi <b>carrera</b> &amp; más</name></metadata><trk><trkseg>$pts</trkseg></trk></gpx>"
        app.parseGpx(gpx).let { r -> chk("gpx.run", r?.let { mapOf("id" to it.id, "start" to it.start, "name" to it.name, "dist" to it.dist, "dur" to it.dur,
            "splits" to it.splits.map { s -> mapOf("km" to s.km, "split" to s.split) + (if (s.partial) mapOf("partial" to true) else emptyMap()) }, "gain" to it.gain, "n" to it.points.size) }) }
        app.parseRouteGpx(gpx).let { r -> chk("gpx.route", r?.let { mapOf("name" to it.name, "km" to it.km, "gain" to it.gain, "n" to it.ll.size) }) }
        chk("gpx.out", runGpx(R.first { it.points.size in 2..899 }))
        chk("wx", listOf(Wx(3, 1, 30, 60, false, null, null, null, null), Wx(15, 15, 8, 10, false, null, null, null, null), Wx(27, 29, 5, 0, true, null, null, null, null), Wx(20, 21, 12, 50, false, null, null, null, null)).map(::wxText))
        val hours = (0 until 24)
        fun hj(pp: List<Int>, tt: List<Double>, daily: JsonObject) = buildJsonObject { put("hourly", buildJsonObject { put("precipitation_probability", JsonArray(pp.map { JsonPrimitive(it) })); put("temperature_2m", JsonArray(tt.map { JsonPrimitive(it) })) }); put("daily", daily) }
        chk("bestHour", listOf(bestHour(hj(hours.map { if (it < 15) 70 else 5 }, hours.map { 8 + it * 0.4 }, buildJsonObject { put("sunrise", JsonArray(listOf(JsonPrimitive("2026-10-21T08:10")))); put("sunset", JsonArray(listOf(JsonPrimitive("2026-10-21T19:05")))) }), 10),
            bestHour(hj(hours.map { 0 }, hours.map { 12.0 }, buildJsonObject {}), 10)).map { b -> b?.let { mapOf("h" to it.h, "t" to it.t, "rain" to it.rain) } })
        chk("coach.ctx", app.coachCtx()); chk("coach.clean", coachClean("Hola **tú**.\n\n---\nPowered by Pollinations 🌸 sponsor"))
        chk("misc.feel", FEEL_L)
        app.demoRun().let { d -> chk("misc.demo.0", d.points.size); chk("misc.demo.1", r3(d.dist)) }
        // tramos y objetivos (sesiones completas)
        fun sj(s: Sess?) = s?.let { x -> mapOf("key" to x.key, "title" to x.title, "desc" to x.desc) }
        val tr = g("tramos").jsonArray; val mine = TRAMOS.map { app.tramosSession(it.key) } + listOf(app.tramosSession("custom", TramosCustom(reps = 5, on = 600, off = 300)), app.tramosSession("custom", TramosCustom(t = true, reps = 6, on = 90, off = 60)))
        mine.forEachIndexed { i, s -> cmpSess("tramos.$i", tr[i], s) }
        val gl = g("goals").jsonArray; listOf("3000", "5000", "10000", "t20", "t30", "t60").forEachIndexed { i, k -> cmpSess("goals.$i", gl[i], goalSession(k)) }
        if (errs.isNotEmpty()) fail("${errs.size} diferencias con la web:\n" + errs.joinToString("\n"))
    }

    /** Compara una sesión con la de la web solo en los campos que la web tiene. */
    private fun cmpSess(path: String, exp: JsonElement, s: Sess?) {
        if (s == null) { if (exp !is JsonNull) errs += "$path: nativa null"; return }
        val segs = s.segs?.map { x -> mapOf("label" to x.label, "say" to x.say, "kind" to x.kind) + (x.m?.let { mapOf("m" to it) } ?: emptyMap()) + (x.sec?.let { mapOf("sec" to it) } ?: emptyMap()) }
        val all = mapOf("key" to s.key, "title" to s.title, "desc" to s.desc, "type" to s.type, "m" to s.m, "custom" to s.custom, "timed" to s.timed, "min" to s.min, "goal" to s.goal, "light" to s.light,
            "segs" to segs, "struct" to s.struct?.let { st -> mapOf("warm" to st.warm, "reps" to st.reps, "on" to st.on, "off" to st.off, "cool" to st.cool) })
        exp.jsonObject.forEach { (k, v) -> val a = js(all[k]); if (k in all && !same(v, a)) errs += "$path.$k\n   web:    $v\n   nativa: $a" else if (k !in all) errs += "$path.$k: campo que falta en la nativa" }
    }
}
