package com.zancada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun SummaryScreen(id: String?) {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    val app = remember(data) { Repo.app() }
    val r = data.runs.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { Nav.reset(Scr(HOME)) }; return }
    val hasPts = r.points.size > 1
    val ll = remember(r.id, r.points.size) { r.points.map { LatLon(it.lat, it.lon) } }
    val seg = remember(r.id, r.points.size) { if (r.points.size > 5) paceSegments(r.points) else emptyList() }
    val byPace = data.settings.pacemap == "1" && seg.size >= 3
    val ctl = remember { MapCtl() }
    LaunchedEffect(r.id) { if (hasPts) ctl.fit(ll, 32f, 80f, 32f, 70f) }
    val lines = if (!hasPts) emptyList() else if (byPace) {
        val ps = seg.map { it.pace }.sorted(); val lo = ps[(ps.size * 0.15).toInt()]; val hi = ps[(ps.size * 0.85).toInt()]
        listOf(MLine(ll, Ink, 9f)) + seg.map { x -> MLine(x.ll.toLL(), Color(paceColor(if (hi > lo) (x.pace - lo) / (hi - lo) else 0.5)), 5f) }
    } else routeLines(ll)
    val dots = if (hasPts) listOf(startDot(ll.first()), endDot(ll.last())) else emptyList()
    var renaming by remember { mutableStateOf(false) }
    var name by remember(r.id) { mutableStateOf(r.name) }
    var note by remember(r.id) { mutableStateOf(r.note ?: "") }
    fun upd(f: (Run) -> Run) = Repo.update { d -> d.copy(runs = d.runs.map { if (it.id == r.id) f(it) else it }) }

    ScreenCol(withTabs = false, gap = 0.dp, pad = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(340.dp).background(c.mapLand)) {
            if (hasPts) ZMap(Modifier.matchParentSize(), data.settings.map, lines, dots, ctl, interactive = false, attrBottom = 36.dp)
            else CompositionLocalProvider(LocalDark provides true) { Box(Modifier.matchParentSize().background(c.bar), contentAlignment = Alignment.Center) { Sub("Carrera añadida a mano: sin mapa", size = 14.sp) } }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconBtn(Ico.back, { Nav.back() })
                IconBtn(Ico.share, {
                    scope.launch {
                        val a = Repo.app(); val png = withContext(Dispatchers.Default) { Images.run(ctx, a, r) }
                        val recs = a.personalRecords().filter { it.run?.id == r.id }.map { it.label }
                        Share.file(ctx, "zancada-${isoMs(r.start).take(10)}.png", "image/png", png, "He corrido ${fmtKm(r.dist)} km en ${fmtTime(r.dur)} (${a.pace(r.paceSec())} /km) con Zancada 🏃${if (recs.isNotEmpty()) " · récord personal: " + recs.joinToString(", ").lowercase() else ""}")
                    }
                })
            }
            if (seg.size >= 3) {
                if (byPace) Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 108.dp).shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(c.bar).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("rápido", size = 12.sp, weight = FontWeight.SemiBold, color = c.onBar)
                    Box(Modifier.size(64.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFD4FF3A), Color(0xFFFFB020), Color(0xFFFF4D4D)))))
                    Txt("lento", size = 12.sp, weight = FontWeight.SemiBold, color = c.onBar)
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 56.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.bar)
                    .clickable { Repo.settings { it.copy(pacemap = if (it.pacemap == "1") "0" else "1") } }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Txt(if (byPace) "Color único" else "Color por ritmo", size = 13.sp, weight = FontWeight.SemiBold, color = c.onBar)
                }
            }
            if (hasPts) Row(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 56.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.bar).clickable { Nav.go(REPLAY, run = r.id) }
                .padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ico.play, null, Modifier.size(18.dp), tint = c.volt); Txt("Repetición", size = 13.sp, weight = FontWeight.SemiBold, color = c.onBar)
            }
        }
        Column(Modifier.offset(y = (-36).dp).padding(start = 12.dp, end = 12.dp, bottom = ins.calculateBottomPadding() + 24.dp - 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZCard(pad = PaddingValues(22.dp), gap = 18.dp) {
                Column {
                    Sub(app.es.fmtDate(r.start))
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (renaming) Field(name, { name = it }, "Nombre", Modifier.weight(1f), h = 40.dp, size = 18.sp, maxLen = 40, onDone = {
                            val v = safeText(name, 40); if (v.isNotEmpty()) upd { it.copy(name = v) }; renaming = false
                        })
                        else H1(r.name, Modifier.weight(1f, fill = false), size = 28.sp)
                        IconBtn(if (renaming) Ico.check else Ico.pencil, {
                            if (renaming) { val v = safeText(name, 40); if (v.isNotEmpty()) upd { it.copy(name = v) }; renaming = false } else { name = r.name; renaming = true }
                        }, size = 32.dp, bg = c.surface2, iconSize = 16.dp)
                    }
                }
                Num(fmtKm(r.dist), 76.sp, unit = "km", unitSize = 22.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
                val ok = r.splits.filter { !it.partial }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Tiempo", fmtTime(r.dur), 26.sp); Stat("Ritmo", app.pace(r.paceSec()), 26.sp, unit = "/km")
                    Stat("Mejor km", if (ok.isNotEmpty()) app.pace(ok.minOf { it.split }) else "–:––", 26.sp, unit = "/km")
                }
                val info = app.summaryInfo(r) + listOfNotNull(cadenceTip(r.cad)?.let { it to false })
                if (info.isNotEmpty()) Column { Divider(); Spacer(Modifier.height(12.dp)); info.forEach { (t, b) -> Txt(t, size = 13.sp, weight = if (b) FontWeight.SemiBold else FontWeight.Medium, color = if (b) c.ink else c.muted, lineHeight = 1.6) } }
            }
            app.runInsights(r).takeIf { it.isNotEmpty() }?.let { ins2 ->
                ZCard(dark = true, pad = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 8.dp) { Lbl("Lectura de la carrera"); Txt(ins2, size = 15.sp, lineHeight = 1.5) }
            }
            app.zones(r)?.let { z ->
                ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Tu esfuerzo"); Sub("ritmo habitual ${app.pace(z.userPace)}/km", size = 12.sp) }
                    val tot = z.zones.sumOf { it.sec }.let { if (it == 0.0) 1.0 else it }
                    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.line)) { z.zones.forEach { x -> if (x.sec > 0) Box(Modifier.weight((x.sec / tot).toFloat()).fillMaxHeight().background(Color(x.color))) } }
                    z.zones.forEachIndexed { i, x ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(x.color))); Txt(x.name, size = 14.sp)
                                if (i == z.main) Sub("· la mayor parte", size = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Txt("${x.pct} %", size = 14.sp, weight = FontWeight.SemiBold); Sub(fmtTime(x.sec)) }
                        }
                    }
                }
            }
            ZCard(pad = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("¿Cómo fue?"); Sub(r.feel?.let { FEEL_L.getOrNull(it) } ?: "sensación y notas", size = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { v ->
                        val on = r.feel == v
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.bar else c.surface2).clickable { upd { it.copy(feel = if (it.feel == v) null else v) } }, contentAlignment = Alignment.Center) {
                            Txt(FEEL_L[v], size = 12.sp, weight = FontWeight.SemiBold, color = if (on) c.volt else c.ink, maxLines = 1)
                        }
                    }
                }
                Field(note, { note = it; val n = it.trim(); upd { r0 -> r0.copy(note = n.ifEmpty { null }) } }, "Notas: dónde, con quién, cómo te sentiste…", Modifier.fillMaxWidth(), h = 64.dp, size = 14.sp, maxLen = 500, singleLine = false, radius = 14.dp)
            }
            val ele = r.points.mapNotNull { it.a }
            if (ele.size >= 10) ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Perfil"); Sub("${ele.min()} – ${ele.max()} m${r.gain?.let { " · +$it m" } ?: ""}") }
                ElevProfile(ele)
            }
            ZCard(pad = PaddingValues(22.dp), gap = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Parciales"); Sub("Mejor km en lima", size = 12.sp) }
                if (r.splits.isEmpty()) Sub(if (r.manual) "Carrera añadida a mano: sin parciales." else "Menos de un kilómetro, sin parciales.")
                val best = r.splits.filter { !it.partial }.minOfOrNull { it.split }; val maxS = r.splits.maxOfOrNull { it.split } ?: 1.0
                r.splits.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Sub("${s.km}", Modifier.width(22.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((max(18.0, 100 * (maxS / s.split) * 0.84) / 100).toFloat().coerceAtMost(1f)).clip(RoundedCornerShape(5.dp))
                                .background(if (s.split == best && !s.partial) c.volt else c.ink))
                        }
                        Txt(app.pace(s.split) + if (s.partial) "*" else "", Modifier.width(52.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
                    }
                }
            }
            if (r.points.size > 5 || (r.route != null && !r.route!!.id.startsWith("run-"))) Btn("Volver a correr este recorrido", {
                val rr = r.route
                val base = if (rr != null && !rr.id.startsWith("run-")) (app.zr.firstOrNull { it.id == rr.id } ?: data.savedRoutes.firstOrNull { it.id == rr.id }) else null
                if (base != null) { St.pickedRoute = base; Nav.go(PREP) }
                else if (r.points.size <= 5) toast("Esta carrera no tiene trazado para repetir")
                else { St.pickedRoute = Route("run-" + r.id, r.name, r.points.map { listOf(it.lat, it.lon) }, jsRound(r.dist / 100) / 10.0, r.gain, start = LatLon(r.points[0].lat, r.points[0].lon), src = "run"); Nav.go(PREP) }
            }, kind = BtnKind.Sec, height = 48.dp, size = 15.sp)
            Btn("Estirar 5 min, guiado", { St.stretchWarm = false; St.modal = "stretch" }, kind = BtnKind.Ghost)
            if (hasPts) Btn("Exportar GPX (Strava, Garmin…)", { Share.file(ctx, "zancada-${isoMs(r.start).take(10)}.gpx", "application/gpx+xml", runGpx(r).toByteArray(), r.name) }, kind = BtnKind.Ghost)
            Btn("Borrar esta carrera", { Repo.update { d -> d.copy(runs = d.runs.filter { it.id != r.id }) }; Repo.mutate { reviewPlan() }; toast("Carrera borrada"); Nav.reset(Scr(HOME)) }, kind = BtnKind.Ghost, color = c.danger)
        }
    }
}

// ---------- Repetición ----------

@Composable
fun ReplayScreen(id: String?) {
    val c = Z.c; val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    val r = data.runs.firstOrNull { it.id == id }?.takeIf { it.points.size > 1 } ?: run { LaunchedEffect(Unit) { Nav.back() }; return }
    val ll = remember(r.id) { r.points.map { LatLon(it.lat, it.lon) } }
    var layer by remember { mutableStateOf("sat") }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(2) }
    var pos by remember { mutableStateOf(0.0) }
    val ctl = remember { MapCtl() }
    LaunchedEffect(r.id) { ctl.fit(ll, 40f, 90f, 40f, 240f) }
    LaunchedEffect(playing, speed) {
        var last = System.nanoTime()
        while (isActive && playing) {
            delay(16); val now = System.nanoTime(); val dt = (now - last) / 1e9; last = now
            pos = min(1.0, pos + dt / (15.0 / speed))
            if (pos >= 1) playing = false
        }
    }
    val (p, d, pace) = replayAt(r, pos)
    val tNow = pos * r.dur
    var i = 1; while (i < r.points.size - 1 && r.points[i].t < tNow * 1000) i++
    val trail = ll.take(i) + p
    LaunchedEffect(p) { if (playing && pos < 1) ctl.center(p) }
    val lines = listOf(MLine(ll, Color.White, 4f, 0.4f, dash = 2f to 8f)) + routeLines(trail)
    val dots = listOf(startDot(ll.first()), endDot(ll.last()), MDot(p, Volt, Ink, 24f, 4f, halo = Volt.copy(alpha = 0.3f), haloW = 10f))
    Box(Modifier.fillMaxSize().background(c.bar)) {
        ZMap(Modifier.fillMaxSize(), layer, lines, dots, ctl, onTouch = { })
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.close, { Nav.back() }, bg = c.bar, tint = c.onBar)
            Seg(listOf("sat" to "Satélite", "calles" to "Calles"), layer, { layer = it }, dark = true, bg = c.bar, fill = false)
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.bar).clickable { speed = if (speed == 1) 2 else if (speed == 2) 4 else 1 }, contentAlignment = Alignment.Center) {
                Text("$speed×", style = numStyle(15.sp, 0.0).copy(color = c.onBar))
            }
        }
        ZCard(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = ins.calculateBottomPadding() + 16.dp), dark = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("km", fmtKm(d), 30.sp, color = c.volt); Stat("Tiempo", fmtTime(tNow), 30.sp); Stat("Ritmo", if (pace.isFinite()) fmtPace(pace) else "–:––", 30.sp)
            }
            Slider(pos.toFloat(), { pos = it.toDouble(); playing = false }, Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = Volt, activeTrackColor = Volt, inactiveTrackColor = Color(0x33F4F5F0)))
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.Center).offset(x = (-68).dp).size(48.dp).clip(CircleShape).background(Color(0x1AF4F5F0)).clickable { pos = 0.0; playing = true }, contentAlignment = Alignment.Center) {
                    Icon(Ico.restart, "Volver al inicio", Modifier.size(20.dp), tint = c.onBar)
                }
                Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(c.volt).clickable { if (pos >= 1) pos = 0.0; playing = !playing }, contentAlignment = Alignment.Center) {
                    Icon(if (playing) Ico.pause else Ico.play, if (playing) "Pausar" else "Reproducir", Modifier.size(26.dp), tint = c.onVolt)
                }
            }
        }
    }
}
