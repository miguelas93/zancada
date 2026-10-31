package com.zancada.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun HistoryScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val app = remember(data, minute) { Repo.app() }
    val mo = app.month()
    ScreenCol(gap = 22.dp) {
        H1("Actividad")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Lbl(mo.name)
            Num(toFixed(mo.km / 1000, 1).replace('.', ','), 80.sp, unit = "km", unitSize = 22.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val mx = max(5000.0, mo.perDay.maxOrNull() ?: 0.0)
            Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                mo.perDay.forEachIndexed { i, m ->
                    Box(Modifier.weight(1f).height(max(3, (m / mx * 100).roundToInt()).dp).clip(RoundedCornerShape(3.dp)).background(c.ink)
                        .alpha(if (m == 0.0) 0.12f else if (i == mo.today) 1f else 0.8f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(1, 8, 15, 22, mo.days).forEach { Sub(it.toString(), size = 11.sp) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Carreras", mo.n.toString(), 26.sp); Stat("Tiempo", fmtTime(mo.dur), 26.sp)
            Stat("Ritmo medio", if (mo.km > 0) app.pace(mo.avg) else "–:––", 26.sp, unit = "/km")
        }
        CalendarCard(app)
        Weeks12Card(app)
        app.months()?.let { MonthsCard(it) }
        app.monthGoal()?.let { g ->
            ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 10.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Objetivo del mes"); Sub("${jsRound(g.km)} de ${g.goal} km") }
                Track(g.pct / 100); Sub(g.text)
            }
        }
        val ch = app.challenges()
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 12.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Retos del mes"); Sub(ch.head) }
            ch.list.forEach { x ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.weight(1f)) { Txt((if (x.done) "✓ " else "") + x.title, size = 14.sp, weight = FontWeight.SemiBold); Sub(" · " + x.desc) }
                        Sub(if (x.done) "Hecho" else x.fmt(x.v) + " / " + x.fmt(x.goal), size = 12.sp, maxLines = 1)
                    }
                    Track(x.v / x.goal)
                }
            }
        }
        app.trend()?.let { TrendCard(it, app) }
        app.paces()?.let { p ->
            ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Tus ritmos"); Sub(p.src, size = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { p.preds.forEach { (l, v) -> Stat(l, v, 22.sp) } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { p.train.forEach { (l, v) -> Stat(l, v, 18.sp, unit = "/km", unitSize = 11.sp) } }
            }
        }
        val recs = app.personalRecords()
        if (recs.isNotEmpty()) ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 12.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Tus récords"); Sub("toca uno para ver la carrera", size = 12.sp) }
            recs.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { x ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(c.surface2).clickable { x.run?.let { Nav.go(SUMMARY, run = it.id) } }.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Lbl(x.label, size = 10.sp); Num(x.value, 22.sp); Sub(x.run?.let { app.es.fmtDate(it.start).split(" · ")[0] } ?: "objetivo semanal", size = 11.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Btn("Añadir una carrera a mano (cinta, reloj…)", { St.manualPrefill = null; St.modal = "manual" }, kind = BtnKind.Ghost)
        LinkCard("Compartir tu semana", "Una imagen con tus km, salidas y días de esta semana") {
            scope.launch {
                val a = Repo.app(); val png = withContext(Dispatchers.Default) { Images.week(ctx, a) }
                if (png == null) { toast("Cuando corras esta semana, podrás compartirla"); return@launch }
                val ws = a.weekStart(a.now()); val wr = a.runs.filter { it.start >= ws }
                Share.file(ctx, "zancada-semana-${isoMs(ws).take(10)}.png", "image/png", png, "Mi semana con Zancada: ${fmtKm(wr.sumOf { it.dist })} km en ${wr.size} ${pl(wr.size, "salida")} 🏃")
            }
        }
        LinkCard("Mapa de todas tus carreras", "Tus recorridos superpuestos: donde más corres") {
            if (data.runs.none { it.points.size > 1 }) toast("Cuando tengas carreras, aquí verás todas juntas") else Nav.go(HEAT)
        }
        Chips(wrap = true) {
            listOf("all" to "Todas", "plan" to "Del plan", "route" to "Con ruta", "long" to "Largas", "note" to "Con notas").forEach { (k, l) -> ChipB(l, St.histFilter == k, { St.histFilter = k }) }
        }
        if (data.runs.size >= 6 || St.histQ.isNotEmpty()) Field(St.histQ, { St.histQ = it }, "Buscar por nombre, ruta o notas", Modifier.fillMaxWidth(), h = 44.dp, size = 14.sp, radius = 14.dp)
        val shown = app.historyList(St.histFilter, St.histQ)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (shown.isEmpty()) Sub(if (data.runs.isNotEmpty()) "Ninguna carrera con ese filtro." else "Sin carreras todavía.", align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 4.dp))
            shown.forEachIndexed { i, r -> if (i > 0) Divider(); HistRow(r, app) }
        }
    }
}

@Composable
fun HistRow(r: Run, app: App) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().clickable { Nav.go(SUMMARY, run = r.id) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(c.mapLand)) { RunThumb(r.points.map { LatLon(it.lat, it.lon) }, Modifier.matchParentSize(), 5f) }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(r.name, Modifier.weight(1f, fill = false), size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                if (app.isLongest(r)) Chip("Récord", size = 9.sp)
            }
            Sub(app.histSub(r), size = 12.sp, maxLines = 1)
        }
        Num(fmtKm(r.dist), 22.sp, unit = "km", unitSize = 12.sp)
    }
}

@Composable
fun LinkCard(title: String, sub: String, chip: String? = null, onClick: () -> Unit) {
    ZCard(pad = PaddingValues(start = 20.dp, top = 16.dp, end = 18.dp, bottom = 16.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (chip != null) Chip(chip, size = 9.sp, modifier = Modifier.padding(bottom = 6.dp))
                Txt(title, size = 16.sp, weight = FontWeight.SemiBold); Sub(sub, size = 12.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(22.dp), tint = Z.muted)
        }
    }
}

@Composable
private fun CalendarCard(app: App) {
    val c = Z.c; val cal = app.calendar(St.calOff)
    ZCard(pad = PaddingValues(horizontal = 18.dp, vertical = 16.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.back, { St.calOff-- }, size = 36.dp)
            Txt(cal.title, size = 15.sp, weight = FontWeight.SemiBold)
            IconBtn(Ico.next, { if (St.calOff < 0) St.calOff++ }, Modifier.alpha(if (cal.canNext) 1f else 0f), size = 36.dp)
        }
        val cells: List<CalDay?> = List(cal.lead) { null } + cal.days
        Row { "LMXJVSD".forEach { Sub(it.toString(), size = 11.sp, align = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        cells.chunked(7).forEach { wk ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { k ->
                    val d = wk.getOrNull(k)
                    Box(Modifier.weight(1f).height(40.dp).then(if (d != null && d.runs.isNotEmpty()) Modifier.clickable { Nav.go(SUMMARY, run = d.runs.maxBy { it.dist }.id) } else Modifier), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            if (d.runs.isNotEmpty()) Box(Modifier.size(d.size.dp).clip(CircleShape).background(c.volt).then(if (d.today) Modifier.border(2.dp, c.ink, CircleShape) else Modifier))
                            else if (d.today) Box(Modifier.size(28.dp).border(1.5.dp, c.ink, CircleShape))
                            Txt(d.day.toString(), Modifier.alpha(if (d.runs.isNotEmpty()) 1f else 0.55f), size = 12.sp, weight = if (d.runs.isNotEmpty() || d.today) FontWeight.SemiBold else FontWeight.Normal, color = if (d.runs.isNotEmpty()) c.onVolt else c.ink)
                        }
                    }
                }
            }
        }
        Sub(cal.sub, size = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Weeks12Card(app: App) {
    val c = Z.c; val w = app.weeks12()
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Últimas 12 semanas"); Sub(w.sub) }
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            w.list.forEachIndexed { i, x ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (x.km > 0) Txt(jsRound(x.km).toString(), size = 10.sp, weight = FontWeight.SemiBold, color = Z.muted)
                    Box(Modifier.fillMaxWidth().height(max(3, (x.km / w.max * 64).roundToInt()).dp).clip(RoundedCornerShape(4.dp))
                        .background(if (i == 11) c.volt else c.ink).alpha(if (x.km == 0.0) 0.12f else if (i == 11) 1f else 0.8f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { w.axis.forEach { Sub(it, size = 11.sp) } }
    }
}

@Composable
private fun MonthsCard(m: Months) {
    val c = Z.c
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Por meses"); Sub(m.sub, size = 12.sp) }
        m.rows.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Txt(r.label, size = 13.sp, weight = FontWeight.SemiBold); Sub("${r.n} ${pl(r.n, "carrera")} · ${fmtTime(r.dur)}") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(max(0.03, r.km / m.max).toFloat().coerceAtMost(1f)).clip(RoundedCornerShape(5.dp)).background(c.ink))
                    }
                    Num(toFixed(r.km, 1).replace('.', ',') + " km", 14.sp, Modifier.width(66.dp))
                }
            }
        }
    }
}

@Composable
private fun TrendCard(t: Trend, app: App) {
    val c = Z.c; val ink = Z.text; val muted = Z.muted
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Evolución del ritmo"); Sub(t.sub, size = 12.sp) }
        Box(Modifier.fillMaxWidth().aspectRatio(320f / 110f)) {
            Canvas(Modifier.matchParentSize()) {
                val k = size.width / 320f
                val pad = max(10.0, (t.hi - t.lo) * 0.15); val y0 = t.lo - pad; val y1 = t.hi + pad; val H = 110f
                fun X(i: Int) = (40 + i * (320 - 48f) / (t.pc.size - 1)) * k
                fun Y(v: Double) = (8 + ((v - y0) / (y1 - y0) * (H - 16)).toFloat()) * k
                t.pc.forEachIndexed { i, v -> drawCircle(muted.copy(alpha = 0.55f), 3.2f * k, Offset(X(i), Y(v))) }
                val p = Path().apply { t.ma.forEachIndexed { i, v -> if (i == 0) moveTo(X(i), Y(v)) else lineTo(X(i), Y(v)) } }
                drawPath(p, ink, style = Stroke(2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val e = Offset(X(t.ma.size - 1), Y(t.ma.last())); drawCircle(c.volt, 5f * k, e); drawCircle(ink, 5f * k, e, style = Stroke(2f * k))
            }
            Sub(app.pace(t.lo), size = 10.sp, modifier = Modifier.align(Alignment.TopStart))
            Sub(app.pace(t.hi), size = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
        }
        Sub(t.note)
    }
}

// ---------- Clubs ----------

@Composable
fun ClubsScreen() {
    val c = Z.c
    val data by Repo.data.collectAsState()
    val app = remember(data) { Repo.app() }
    val w = app.weekStats(); val ws = app.weekStart(app.now()); val km = data.runs.filter { it.start >= ws }.sumOf { it.dist }
    ScreenCol {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { H1("Clubs"); IconBtn(Ico.close, { Nav.go(PROFILE) }) }
        ZCard(dark = true, pad = PaddingValues(24.dp), gap = 12.dp) {
            Chip("Muy pronto"); Num("Corre con tu gente", 30.sp)
            Sub("Clubs, clasificación semanal y quedadas llegarán cuando Zancada tenga cuentas. En esta beta todo vive en tu móvil, así que todavía no hay nada que compartir con otros.", size = 14.sp)
        }
        ZCard(gap = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { H2("Clasificación semanal"); Sub("km", size = 12.sp) }
            CompositionLocalProvider(LocalDark provides true) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bar).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Num("1", 16.sp, color = c.onBarMuted)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(c.volt), contentAlignment = Alignment.Center) { Txt(data.profile.name.ifEmpty { "Tú" }.take(2).uppercase(), size = 13.sp, weight = FontWeight.SemiBold, color = c.onVolt) }
                        Txt("Tú", weight = FontWeight.SemiBold)
                    }
                    Num(toFixed(km / 1000, 1).replace('.', ','), 20.sp, color = c.volt)
                }
            }
            Sub("Así se verá tu fila cuando haya más gente en tu club. ${w.count} ${pl(w.count, "carrera")} esta semana.", size = 12.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp))
        }
    }
}

// ---------- Mapa de todas las carreras ----------

@Composable
fun HeatScreen() {
    val c = Z.c; val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    var layer by remember { mutableStateOf("calles") }
    val ctl = remember { MapCtl() }
    val rs = remember(data.runs) { data.runs.filter { it.points.size > 1 } }
    val lines = remember(rs) { rs.flatMap { r -> val ll = r.points.filterIndexed { i, _ -> i % 2 == 0 }.map { LatLon(it.lat, it.lon) }; listOf(MLine(ll, Ink, 8f, 0.12f), MLine(ll, Volt, 3.5f, 0.45f)) } }
    LaunchedEffect(rs) { val all = rs.flatMap { r -> r.points.filterIndexed { i, _ -> i % 10 == 0 }.map { LatLon(it.lat, it.lon) } }; if (all.isNotEmpty()) ctl.fit(all, 40f, 40f, 40f, 40f) }
    val app = remember(data) { Repo.app() }
    Box(Modifier.fillMaxSize().background(c.bar)) {
        ZMap(Modifier.fillMaxSize(), layer, lines, ctl = ctl, attrBottom = 0.dp)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.back, { Nav.back() })
            Seg(listOf("calles" to "Calles", "sat" to "Satélite"), layer, { layer = it }, dark = true, bg = c.bar, fill = false)
            Spacer(Modifier.width(44.dp))
        }
        ZCard(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = ins.calculateBottomPadding() + 16.dp), dark = true) {
            Lbl("Todas tus carreras")
            val first = rs.minOfOrNull { it.start } ?: app.now()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Carreras", rs.size.toString(), 26.sp); Stat("Km", jsRound(rs.sumOf { it.dist } / 1000).toString(), 26.sp); Stat("Desde", app.es.monShortYY(first), 26.sp) }
            Sub("Cuanto más repites un camino, más intenso se ve. Pellizca para acercarte.", size = 12.sp)
        }
    }
}
