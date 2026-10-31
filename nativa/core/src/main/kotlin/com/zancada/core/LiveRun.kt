package com.zancada.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Todo lo que acompaña a una carrera en marcha, además de medirla (eso es [RunSession]):
 * la guía de la sesión por tramos, la navegación por la ruta, el fantasma, los avisos de ritmo,
 * los ánimos y la voz de cada km. Traducido de updateGuide / updateNav / updateGhost / cheer / paceCheck / tick de la web.
 * Vive en el servicio de la carrera, así funciona igual con la pantalla apagada.
 */
class LiveRun(
    private val app: App,
    val sess: Sess?,
    val route: Route?,
    val ghostRef: Run?,
    startPos: LatLon?,
    private val speak: (String) -> Unit,
    private val toast: (String) -> Unit = {},
) {
    private val settings get() = app.settings
    val guide: List<Seg>? = if (sess?.goal == true && sess.min != null) null else buildGuide(sess)
    val nav: Nav? = route?.let { buildNav(it.ll) }?.also { n -> if (startPos != null && hav(startPos, n.pts[0]) > 50) n.phase = "start" }

    private var guideIdx: Int = -1   // -2 = sesión terminada
    private var segAt = 0.0
    private var paceOff: Double? = null
    private var paceSaid = -999.0
    private var warnIdx = -1
    private var heading: Double? = null
    private var off = 0
    private var offSaid = 0L
    private var halfSaid = -1
    private var min5Said = 0
    private var goalSaid = false
    private val cheered = mutableSetOf<String>()

    /** Lo que pinta la pantalla de carrera. */
    data class State(
        val stateText: String = "Carrera libre",
        val guideOn: Boolean = false, val gLabel: String = "—", val gKind: String = "", val gStep: String = "", val gLeft: String = "0", val gUnit: String = "m", val gPct: Double = 0.0,
        val navOn: Boolean = false, val navMain: String = "", val navSub: String = "", val navRot: Double = 0.0,
        val ghostOn: Boolean = false, val ghostBest: String = "", val ghostDiff: String = "—", val ghostAhead: Boolean? = null, val ghostSub: String = "",
        val toStart: LatLon? = null,
    )

    var state = State(
        stateText = sess?.title ?: route?.let { "Ruta: " + it.name } ?: "Carrera libre",
        guideOn = guide != null, navOn = nav != null, navMain = if (nav?.phase == "start") "Hacia la salida" else "Sigue por la ruta",
        ghostOn = ghostRef != null, ghostBest = ghostRef?.let { fmtTime(it.dur) } ?: "", ghostSub = if (ghostRef != null) "te comparo en cuanto arranques" else "",
        toStart = if (nav?.phase == "start") startPos else null,
    ); private set

    private fun pace(s: Double) = app.pace(s)

    fun stateText(s: RunSession) = if (s.paused) (if (s.autoPaused) "Autopausa · muévete para seguir" else "En pausa") else sess?.title ?: route?.let { "Ruta: " + it.name } ?: "Carrera libre"

    // ---------- avisos de cada km ----------
    fun onKm(km: Int, split: Double, s: RunSession, now: Long) {
        var gh = ""
        ghostRef?.let { ref -> val sec = jsRound(ghostTimeAt(ref, s.dist) - s.elapsed(now)); gh = if (abs(sec) < 3) " A la par con tu mejor marca." else " ${abs(sec)} segundos ${if (sec > 0) "por delante" else "por detrás"} de tu mejor marca." }
        if (settings.voice != "min5" && settings.voice != "0") speak("Kilómetro $km. ${floor(split / 60).toInt()} ${jsRound(split % 60)}.$gh")
    }

    // ---------- cada punto ----------
    fun onPoint(s: RunSession, p: Point, now: Long) {
        val pts = s.points
        if (pts.size >= 2) { val q = pts[pts.size - 2]; if (!p.gap && haversine(q.lat, q.lon, p.lat, p.lon) >= 5) heading = bearing(LatLon(q.lat, q.lon), LatLon(p.lat, p.lon)) }
        if (settings.voice == "half" && s.dist >= s.lastKm * 1000.0 + 500 && halfSaid != s.lastKm) { halfSaid = s.lastKm; speak("${s.lastKm} kilómetros y medio. Ritmo ${pace(s.elapsed(now) / (s.dist / 1000)).replace(":", " ")}.") }
        updateGuide(s, now)
        if (nav != null) updateNav(LatLon(p.lat, p.lon), now)
    }

    // ---------- cada medio segundo ----------
    fun tick(s: RunSession, now: Long) {
        val el = s.elapsed(now)
        if (guide != null && guide[0].sec != null) updateGuide(s, now)
        if (settings.voice == "min5" && !s.paused && el >= 300 && floor(el / 300).toInt() > min5Said) { min5Said = floor(el / 300).toInt(); speak("${jsRound(el / 60)} minutos. ${fmtKm(s.dist).replace(",", " coma ")} kilómetros. Ritmo ${if (s.dist > 100) pace(el / (s.dist / 1000)).replace(":", " ") else "sin datos"}.") }
        val ss = sess
        if (ss?.goal == true && ss.min != null) {
            val left = ss.min * 60 - el
            state = state.copy(guideOn = true, gLabel = if (left > 0) "Objetivo ${ss.min} min" else "Objetivo cumplido", gKind = "", gStep = if (left > 0) "tiempo" else "lo que corras ahora suma",
                gLeft = if (left > 0) fmtTime(left) else "0", gUnit = if (left > 0) "" else "m", gPct = min(1.0, el / (ss.min * 60)))
            if (left <= 0 && !goalSaid) { goalSaid = true; speak("Objetivo cumplido. Lo que corras ahora suma.") }
        }
        paceCheck(s, el, now); cheer(s, el); updateGhost(s, now)
        state = state.copy(stateText = stateText(s))
    }

    // ---------- guía de la sesión ----------
    private fun segTarget(seg: Seg?): Double? {
        val ss = sess ?: return null; if (ss.goal || seg == null) return null
        if (ss.segs?.any { Regex("^Anda|andando", RegexOption.IGNORE_CASE).containsMatchIn(it.label) } == true) return null
        val type = if (seg.kind == "fuerte") (if (ss.type == "series") "series" else "tempo") else (if (ss.type == "largo") "largo" else "suave")
        return app.userPace() * (PACE_K[type] ?: 1.0)
    }

    private fun updateGuide(s: RunSession, now: Long) {
        val g = guide ?: return
        val timed = g[0].sec != null; val pos = if (timed) s.elapsed(now) else s.dist
        fun len(x: Seg) = if (timed) (x.sec ?: 0).toDouble() else x.m ?: 0.0
        var acc = 0.0; var i = 0
        while (i < g.size && pos >= acc + len(g[i])) { acc += len(g[i]); i++ }
        if (i >= g.size) {
            if (guideIdx != -2) {
                guideIdx = -2; val isGoal = sess?.goal == true
                state = state.copy(gLabel = if (isGoal) "Objetivo cumplido" else "Sesión completada", gKind = "", gStep = "lo que corras ahora suma", gLeft = "0", gPct = 1.0)
                speak(if (isGoal) "Objetivo cumplido. Lo que corras ahora suma." else "Sesión completada. Enhorabuena.")
            }
            return
        }
        val seg = g[i]; val left = max(0.0, acc + len(seg) - pos)
        if (guideIdx != i) {
            guideIdx = i; segAt = s.elapsed(now); paceOff = null
            val tp = segTarget(seg)
            state = state.copy(gLabel = seg.label, gKind = seg.kind, gStep = "paso ${i + 1} de ${g.size}${if (tp != null) " · ~" + pace(tp) + "/km" else ""}")
            if (s.dist > 0 || i > 0 || timed) speak(seg.say)
        }
        if (timed && left <= 10 && left > 0 && i < g.size - 1 && warnIdx != i) { warnIdx = i; speak("Diez segundos.") }
        state = state.copy(gLeft = if (timed) fmtTime(left) else if (left >= 1000) fmtKmS(left) + " k" else jsNum((jsRound(left / 10) * 10).toDouble()),
            gUnit = if (timed) "" else "m", gPct = (len(seg) - left) / len(seg))
    }

    private fun paceCheck(s: RunSession, el: Double, now: Long) {
        if ((settings.pacealert ?: "1") == "0" || s.paused || guide == null || guideIdx < 0) return
        val seg = guide[guideIdx]; val t = segTarget(seg) ?: return; val cp = s.currentPace(now)
        if (!cp.isFinite() || el - segAt < 30) return
        val offR = (cp - t) / t; val tol = if (seg.kind == "fuerte") 0.06 else 0.10
        if (abs(offR) <= tol) { paceOff = null; return }
        val po = paceOff ?: run { paceOff = el; return }
        if (el - po >= 20 && el - paceSaid >= 60) { paceSaid = el; paceOff = null; speak(if (offR < 0) "Vas rápido. Afloja a ${pace(t).replace(":", " ")}." else "Un poco lento. Sube a ${pace(t).replace(":", " ")}.") }
    }

    private fun cheer(s: RunSession, el: Double) {
        val ss = sess ?: return; if (s.paused || (guide != null && guide.size > 1)) return
        fun say(k: String, t: String) { if (cheered.add(k)) speak(t) }
        val avg = { if (s.dist > 100) " Ritmo medio ${pace(el / (s.dist / 1000)).replace(":", " ")}." else "" }
        if (ss.min != null) { val T = ss.min * 60.0; if (T < 600) return; if (el >= T / 2 + 10) say("half", "Mitad del tiempo.${avg()} Sigue así."); if (el >= T - 60 && el < T) say("last", "Último minuto."); return }
        val T = ss.m; if (!(T >= 2000)) return
        if (s.dist >= T / 2 + 80 && s.dist < T - 1000) say("half", "Mitad hecha.${avg()}")
        if (T >= 3000 && s.dist >= T - 920 && s.dist < T - 200) say("lastkm", "Último kilómetro. A por él.")
        if (s.dist >= T - 200 && s.dist < T) say("200", "Doscientos metros. Aprieta si puedes.")
    }

    // ---------- fantasma ----------
    private fun updateGhost(s: RunSession, now: Long) {
        val ref = ghostRef ?: return
        if (s.dist < 50) { state = state.copy(ghostDiff = "—", ghostAhead = null, ghostSub = "te comparo en cuanto arranques"); return }
        val el = s.elapsed(now); val gd = ghostDistAt(ref, el * 1000); val diff = s.dist - gd; val sec = jsRound(ghostTimeAt(ref, s.dist) - el)
        state = state.copy(ghostDiff = "${if (diff >= 0) "+" else "−"}${fmtM(abs(diff))}", ghostAhead = diff >= 0,
            ghostSub = if (abs(sec) < 3) "a la par con tu mejor marca" else "${if (abs(sec) >= 60) fmtTime(abs(sec).toDouble()) else "${abs(sec)} s"} ${if (sec >= 0) "por delante" else "por detrás"}")
    }

    // ---------- navegación por la ruta ----------
    private fun updateNav(p: LatLon, now: Long) {
        val n = nav ?: return
        var target: LatLon? = null; var main = ""; var sub = ""; var toStart = state.toStart
        if (n.phase == "start") {
            val d = hav(p, n.pts[0])
            val joined = d > 50 && run { n.joinN = if (navProgress(n, p, true).off <= 30) n.joinN + 1 else 0; n.joinN >= 4 }
            if (d <= 50 || joined) { n.phase = "route"; toast("Ya estás en la salida"); speak("Ya estás en la salida. Empieza la ruta."); toStart = null }
            else { target = n.pts[0]; main = "Hacia la salida"; sub = "${fmtM(d)} · calentamiento"; toStart = p }
        }
        if (n.phase == "route") {
            val pr = navProgress(n, p); val here = n.cum[n.idx]; val left = n.total - here
            if (pr.off > 60) {
                target = n.pts[pr.i]; main = "Fuera de la ruta"; sub = "la ruta está a ${fmtM(pr.off)}"
                off++
                if (off >= 2 && now - offSaid > 60000) {
                    offSaid = now
                    val side = heading?.let { h -> if (angDiff(h, bearing(p, target)) > 0) " a tu derecha" else " a tu izquierda" } ?: ""
                    toast("Te has salido de la ruta"); speak("Te has salido de la ruta. Está a ${fmtM(pr.off)}$side.")
                }
            } else {
                if (off >= 2) toast("De vuelta en la ruta"); off = 0
                target = n.at(here + 40)
                val t = n.turns.firstOrNull { it.i > n.idx }; val dt = if (t != null) t.d - here else Double.POSITIVE_INFINITY
                if (left <= 40) { main = "Fin de la ruta"; sub = "lo que corras ahora suma"; if (!n.done) { n.done = true; toast("Ruta completada"); speak("Fin de la ruta. Lo que corras ahora suma.") } }
                else if (t != null && dt <= 120) {
                    main = "${if (t.dir == "derecha") "Derecha" else "Izquierda"} en ${fmtM(dt)}"; sub = "quedan ${fmtKmS(left)} km"
                    if (dt <= 90 && n.turnSaid != t.i && (t.sharp || dt <= 45 || now - n.saidAt > 25000)) { n.turnSaid = t.i; n.saidAt = now; speak("En ${fmtM(dt).replace(" m", " metros")}, ${if (t.sharp) "giro cerrado a la " else "a la "}${t.dir}.") }
                } else { main = if (t != null) "Sigue ${fmtM(dt)}, luego ${t.dir}" else "Sigue por la ruta"; sub = "quedan ${fmtKmS(left)} km" }
            }
        }
        val rot = target?.let { tg -> heading?.let { h -> angDiff(h, bearing(p, tg)) } ?: 0.0 } ?: state.navRot
        state = state.copy(navMain = main, navSub = sub, navRot = rot, toStart = toStart)
    }

    /** Texto de la pantalla bloqueada. */
    fun lockLine(): String = if (nav != null) "${state.navMain} · ${state.navSub}" else if (guide != null) "${state.gLabel} · ${state.gLeft} m" else ""
}

/** Completa una carrera recién terminada con lo que la rodea (sesión, ruta, fantasma, zapatillas, tiempo), como finishRun de la web. */
fun App.decorateRun(r0: Run, sess: Sess?, route: Route?, ghostRef: Run?, wx: RunWx?): Run {
    var r = r0
    if (sess?.goal == true) r = r.copy(goal = if (sess.min != null) "${sess.min} min" else "${fmtKmS(sess.m)} km")
    if (sess != null && !sess.goal) r = r.copy(session = sess.ref())
    if (route != null) r = r.copy(route = RouteRef(route.id, route.name), ghost = ghostRef?.let { GhostRef(it.id, it.dur) })
    else detectRoute(r)?.let { r = r.copy(route = RouteRef(it.id, it.name, auto = true)) }
    settings.shoe?.let { s -> if (shoes.any { it.id == s }) r = r.copy(shoe = s) }
    if (wx != null) r = r.copy(wx = wx)
    return r
}

/** Guarda una carrera nueva (ordenadas de la más reciente a la más antigua), revisa el plan y devuelve las insignias nuevas. */
fun App.addRun(r: Run): List<Badge> {
    val before = badges().filter { it.`when` > 0 }.map { it.id }.toSet()
    data = data.copy(runs = (data.runs.filter { it.id != r.id } + r).sortedByDescending { it.start })
    reviewPlan()
    return badges().filter { it.`when` > 0 && it.id !in before }
}

/** Carrera añadida a mano (cinta, reloj). Null si los datos no valen. */
fun App.manualRun(start: Long, km: String, time: String, name: String): Run? {
    val dist = jsRound((km.replace(',', '.').toDoubleOrNull() ?: return null) * 1000).toDouble()
    val parts = time.trim().split(":").map { it.toIntOrNull() ?: return null }
    val dur = when (parts.size) { 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; 2 -> parts[0] * 60 + parts[1]; else -> return null }.toDouble()
    if (!(dist > 0) || !(dur > 0)) return null
    var r = Run("m$start", start, safeText(name, 40).ifEmpty { runName(start, zone) }, emptyList(), dist, dur, emptyList(), manual = true)
    settings.shoe?.let { s -> if (shoes.any { it.id == s }) r = r.copy(shoe = s) }
    return r
}

/** Ritmo de los últimos metros en la repetición (como drawRep de la web). */
fun replayAt(r: Run, pos: Double): Triple<LatLon, Double, Double> {
    val pts = r.points; val tNow = pos * r.dur * 1000
    var i = 1; while (i < pts.size - 1 && pts[i].t < tNow) i++
    val a = pts[i - 1]; val b = pts[i]; val f = min(1.0, max(0.0, (tNow - a.t) / max(1.0, (b.t - a.t).toDouble())))
    val d = a.d + (b.d - a.d) * f
    val j = max(1, i - 6); val dd = d - pts[j].d; val dt = (tNow - pts[j].t) / 1000
    return Triple(LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f), d, if (dd > 5) dt / (dd / 1000) else Double.NaN)
}

