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

    var tab by remember(r.id) { mutableStateOf("res") }
    var sureDel by remember(r.id) { mutableStateOf(false) }
    LaunchedEffect(sureDel) { if (sureDel) { delay(3000); sureDel = false } }
    val paceS = remember(r.id, r.points.size) { paceSeries(r.points) }
    val eleS = remember(r.id, r.points.size) { elevSeries(r.points) }
    val hrS = remember(r.id, r.hrs?.size) { hrSeries(r.hrs) }
    val hasCharts = paceS.size >= 3 || eleS.size >= 3 || hrS.size >= 3
    val tabs = listOfNotNull("res" to "Resumen", if (r.splits.isNotEmpty()) "spl" to "Parciales" else null, if (hasCharts) "gra" to "Gráficas" else null)
    if (tabs.none { it.first == tab }) tab = "res"

    ScreenCol(withTabs = false, gap = 0.dp, pad = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(320.dp).background(c.mapLand)) {
            if (hasPts) ZMap(Modifier.matchParentSize(), data.settings.map, lines, dots, ctl, interactive = false, attrBottom = 36.dp)
            else CompositionLocalProvider(LocalDark provides true) {
                Column(Modifier.matchParentSize().background(c.bar), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    ZMark(64.dp, c.volt); Sub("Añadida a mano", Modifier.padding(top = 12.dp), size = 14.sp)
                }
            }
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
            if (hasPts) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (seg.size >= 3) MapPill(if (byPace) Ico.layers else Ico.bolt, if (byPace) "Color único" else "Por ritmo") { Repo.settings { it.copy(pacemap = if (it.pacemap == "1") "0" else "1") } }
                Spacer(Modifier.weight(1f))
                MapPill(Ico.play, "Repetición", tint = c.volt) { Nav.go(REPLAY, run = r.id) }
            }
            if (byPace) Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 102.dp).shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(c.bar).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("rápido", size = 12.sp, weight = FontWeight.SemiBold, color = c.onBar)
                Box(Modifier.size(64.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFD4FF3A), Color(0xFFFFB020), Color(0xFFFF4D4D)))))
                Txt("lento", size = 12.sp, weight = FontWeight.SemiBold, color = c.onBar)
            }
        }
        Column(Modifier.offset(y = (-36).dp).padding(start = 12.dp, end = 12.dp, bottom = ins.calculateBottomPadding() + 24.dp - 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cabecera: fecha, nombre, km grandes y cuadrícula de cifras.
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
                        }, size = 32.dp, bg = c.surface2, iconSize = 16.dp, label = if (renaming) "Guardar nombre" else "Cambiar nombre")
                    }
                }
                Num(countUp(r.dist) { fmtKm(it) }, 76.sp, unit = "km", unitSize = 22.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
                val ok = r.splits.filter { !it.partial }
                val cells = listOfNotNull(
                    Triple("Tiempo", fmtTime(r.dur), null), Triple("Ritmo", app.pace(r.paceSec()), "/km"),
                    if (ok.isNotEmpty()) Triple("Mejor km", app.pace(ok.minOf { it.split }), "/km") else null,
                    r.gain?.let { Triple("Desnivel", "+$it", "m") }, r.hr?.let { Triple("Pulso", "$it", "ppm") }, r.cad?.let { Triple("Cadencia", "$it", "ppm") },
                )
                cells.chunked(3).forEachIndexed { i, row ->
                    if (i > 0) Divider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (l, v, u) -> Stat(l, v, 24.sp, unit = u, unitSize = 12.sp) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                val info = app.summaryInfo(r).filter { !it.first.startsWith("Desnivel") }
                if (info.isNotEmpty()) Chips(wrap = true) { info.forEach { (t, _) -> InfoChip(t) } }
            }
            if (tabs.size > 1) TopTabs(tabs, tab, { tab = it }, Modifier.padding(horizontal = 8.dp))
            TabBody(tab, tabs.map { it.first }) { t ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (t) {
                        "spl" -> SplitsCard(app, r)
                        "gra" -> ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 20.dp), gap = 26.dp) {
                            if (paceS.size >= 3) ScrubChart("Ritmo", Ico.bolt, paceS, { app.pace(it) }, "/km", "km", invert = true, key = "pace")
                            if (hrS.size >= 3) ScrubChart("Pulso", Ico.heart, hrS, { "${it.toInt()}" }, "ppm", "min", color = Color(0xFFFF4D4D), key = "hr")
                            if (eleS.size >= 3) ScrubChart("Altitud", Ico.trend, eleS, { "${it.toInt()}" }, "m", "km", key = "ele")
                            Sub("Desliza el dedo por la gráfica para ver cada punto.", size = 12.sp)
                        }
                        else -> {
                            app.runInsights(r).takeIf { it.isNotEmpty() }?.let { ins2 ->
                                ZCard(dark = true, pad = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 8.dp) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Ico.bolt, null, Modifier.size(16.dp), tint = c.volt); Lbl("Lectura de la carrera") }
                                    Txt(ins2, size = 15.sp, lineHeight = 1.5)
                                }
                            }
                            app.zones(r)?.let { z -> EffortCard(app, z) }
                            FeelCard(r, note, { note = it }, { f -> upd(f) })
                        }
                    }
                }
            }
            // Acciones: una fila de iconos en vez de cuatro botones apilados.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.points.size > 5 || (r.route != null && !r.route!!.id.startsWith("run-"))) ActionTile(Ico.restart, "Repetir") {
                    val rr = r.route
                    val base = if (rr != null && !rr.id.startsWith("run-")) (app.zr.firstOrNull { it.id == rr.id } ?: data.savedRoutes.firstOrNull { it.id == rr.id }) else null
                    if (base != null) { St.pickedRoute = base; Nav.go(PREP) }
                    else if (r.points.size <= 5) toast("Esta carrera no tiene trazado para repetir")
                    else { St.pickedRoute = Route("run-" + r.id, r.name, r.points.map { listOf(it.lat, it.lon) }, jsRound(r.dist / 100) / 10.0, r.gain, start = LatLon(r.points[0].lat, r.points[0].lon), src = "run"); Nav.go(PREP) }
                }
                ActionTile(Ico.heart, "Estirar") { St.stretchWarm = false; St.modal = "stretch" }
                if (hasPts) ActionTile(Ico.download, "GPX") { Share.file(ctx, "zancada-${isoMs(r.start).take(10)}.gpx", "application/gpx+xml", runGpx(r).toByteArray(), r.name) }
                ActionTile(Ico.trash, if (sureDel) "¿Seguro?" else "Borrar", danger = true) {
                    if (!sureDel) { sureDel = true; return@ActionTile }
                    Repo.update { d -> d.copy(runs = d.runs.filter { it.id != r.id }) }; Repo.mutate { reviewPlan() }; toast("Carrera borrada"); Nav.reset(Scr(HOME))
                }
            }
        }
    }
}

/** Botón redondo sobre el mapa del resumen. */
@Composable
private fun MapPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Z.c.onBar, onClick: () -> Unit) {
    val c = Z.c; val buzz = rememberBuzz()
    Row(Modifier.height(40.dp).shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.bar).clickable { buzz(); onClick() }
        .padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint); Txt(label, size = 13.sp, weight = FontWeight.SemiBold, color = c.onBar, maxLines = 1)
    }
}

/** Dato secundario en una píldora (plan, ruta, objetivo…). */
@Composable
private fun InfoChip(t: String) {
    val c = Z.c
    Txt(t, Modifier.clip(RoundedCornerShape(50)).background(c.surface2).padding(horizontal = 10.dp, vertical = 5.dp), size = 12.sp, weight = FontWeight.Medium, color = Z.muted, maxLines = 1)
}

/** Icono con texto debajo: acciones del final del resumen. */
@Composable
private fun RowScope.ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Z.c; val buzz = rememberBuzz()
    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.surface).clickable { buzz(); onClick() }.padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (danger) c.danger else Z.text)
        Txt(label, size = 12.sp, weight = FontWeight.SemiBold, color = if (danger) c.danger else Z.text, maxLines = 1)
    }
}

@Composable
private fun SplitsCard(app: App, r: Run) {
    val c = Z.c
    val best = r.splits.filter { !it.partial }.minOfOrNull { it.split }; val maxS = r.splits.maxOfOrNull { it.split } ?: 1.0
    val grow = animatedOnce(1f, 700)
    ZCard(pad = PaddingValues(22.dp), gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Lbl("km", Modifier.width(34.dp)); Lbl("Ritmo", Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.volt)); Sub(" mejor", size = 11.sp)
        }
        r.splits.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sub("${s.km}", Modifier.width(22.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((grow * max(18.0, 100 * (maxS / s.split) * 0.84) / 100).toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(5.dp))
                        .background(if (s.split == best && !s.partial) c.volt else c.ink))
                }
                Txt(app.pace(s.split) + if (s.partial) "*" else "", Modifier.width(52.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
            }
        }
    }
}

@Composable
private fun EffortCard(app: App, z: Zones) {
    val c = Z.c
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Ico.gauge, null, Modifier.size(18.dp), tint = Z.text); H2("Tu esfuerzo") }
            Sub("habitual ${app.pace(z.userPace)}/km", size = 12.sp)
        }
        val tot = z.zones.sumOf { it.sec }.let { if (it == 0.0) 1.0 else it }
        val grow = animatedOnce(1f, 700)
        Row(Modifier.fillMaxWidth(grow.coerceAtLeast(0.01f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.line)) { z.zones.forEach { x -> if (x.sec > 0) Box(Modifier.weight((x.sec / tot).toFloat()).fillMaxHeight().background(Color(x.color))) } }
        z.zones.forEachIndexed { i, x ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(x.color))); Txt(x.name, size = 14.sp, weight = if (i == z.main) FontWeight.SemiBold else FontWeight.Normal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Txt("${x.pct} %", size = 14.sp, weight = FontWeight.SemiBold); Sub(fmtTime(x.sec)) }
            }
        }
    }
}

@Composable
private fun FeelCard(r: Run, note: String, setNote: (String) -> Unit, upd: ((Run) -> Run) -> Unit) {
    val c = Z.c; val buzz = rememberBuzz()
    ZCard(pad = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("¿Cómo fue?"); r.feel?.let { Sub(FEEL_L.getOrNull(it) ?: "", size = 12.sp) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { v ->
                val on = r.feel == v
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.bar else c.surface2).clickable { buzz(); upd { it.copy(feel = if (it.feel == v) null else v) } }, contentAlignment = Alignment.Center) {
                    Txt(FEEL_L[v], size = 12.sp, weight = FontWeight.SemiBold, color = if (on) c.volt else c.ink, maxLines = 1)
                }
            }
        }
        Field(note, { setNote(it); val n = it.trim(); upd { r0 -> r0.copy(note = n.ifEmpty { null }) } }, "Notas", Modifier.fillMaxWidth(), h = 64.dp, size = 14.sp, maxLen = 500, singleLine = false, radius = 14.dp)
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
