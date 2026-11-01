package com.zancada.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

/** Actividad: pestañas fijas arriba (Progreso · Carreras · Retos), como «Tú» de Strava. */
@Composable
fun HistoryScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val app = remember(data, minute) { Repo.app() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = ins.calculateTopPadding() + 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                H1("Actividad", Modifier.weight(1f), size = 28.sp)
                HeadIcon(Ico.plus, "Añadir una carrera a mano") { St.manualPrefill = null; St.modal = "manual" }
                HeadIcon(Ico.map, "Mapa de todas tus carreras") { if (data.runs.none { it.points.size > 1 }) toast("Cuando tengas carreras, aquí verás todas juntas") else Nav.go(HEAT) }
                HeadIcon(Ico.share, "Compartir tu semana") {
                    scope.launch {
                        val a = Repo.app(); val png = withContext(Dispatchers.Default) { Images.week(ctx, a) }
                        if (png == null) { toast("Cuando corras esta semana, podrás compartirla"); return@launch }
                        val ws = a.weekStart(a.now()); val wr = a.runs.filter { it.start >= ws }
                        Share.file(ctx, "zancada-semana-${isoMs(ws).take(10)}.png", "image/png", png, "Mi semana con Zancada: ${fmtKm(wr.sumOf { it.dist })} km en ${wr.size} ${pl(wr.size, "salida")} 🏃")
                    }
                }
            }
        }
        TopTabs(listOf("prog" to "Progreso", "runs" to "Carreras", "retos" to "Retos"), St.histTab, { St.histTab = it }, Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f)) {
            val pad = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = ins.calculateBottomPadding() + 110.dp)
            TabBody(St.histTab, listOf("prog", "runs", "retos")) { t ->
                when (t) {
                    "runs" -> RunsTab(app, data, pad)
                    "retos" -> ScreenCol(pad = pad, gap = 14.dp, scroll = rememberScrollState()) { RetosTab(app) }
                    else -> ScreenCol(pad = pad, gap = 14.dp, scroll = rememberScrollState()) { ProgressTab(app) }
                }
            }
        }
    }
}

@Composable
private fun HeadIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp), tint = Z.text) }
}

@Composable
private fun ProgressTab(app: App) {
    val c = Z.c; val mo = app.month(); val g = app.monthGoal()
    // Mes: km, anillo del objetivo y barras por día. Al tocar: «Tu mes en Zancada» en historias.
    ZCard(pad = PaddingValues(18.dp), gap = 12.dp, onClick = { val m0 = app.monthStart(app.now()); val from = if (app.monthWrap(m0) != null) m0 else app.monthStart(app.now(), 1); DiscSt.wrapFrom = from; Nav.go(MONTH) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lbl(mo.name)
                Num(countUp(mo.km) { toFixed(it / 1000, 1).replace('.', ',') }, 52.sp, unit = "km", unitSize = 18.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
            }
            if (g != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Ring(g.pct / 100, 64.dp, 7.dp) { Num(countUp(min(100.0, g.pct)) { "${it.roundToInt()}%" }, 14.sp) }
                Sub("de ${g.goal} km", size = 11.sp)
            }
        }
        val mx = max(5000.0, mo.perDay.maxOrNull() ?: 0.0)
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            mo.perDay.forEachIndexed { i, m ->
                Box(Modifier.weight(1f).height(max(3, (m / mx * 64).roundToInt()).dp).clip(RoundedCornerShape(3.dp)).background(if (i == mo.today) c.volt else c.ink)
                    .alpha(if (m == 0.0) 0.12f else 1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(1, 8, 15, 22, mo.days).forEach { Sub(it.toString(), size = 11.sp) } }
        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IcoStat(Ico.flag, mo.n.toString(), "carreras"); IcoStat(Ico.clock, fmtTime(mo.dur), "tiempo")
            IcoStat(Ico.gauge, if (mo.km > 0) app.pace(mo.avg) else "–:––", "ritmo medio", "/km")
        }
    }
    // 12 semanas o meses en la misma tarjeta.
    val ms = app.months()
    ZCard(pad = PaddingValues(18.dp), gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.history, null, Modifier.size(20.dp), tint = Z.text); Spacer(Modifier.width(8.dp))
            Txt("Volumen", Modifier.weight(1f), size = 17.sp, weight = FontWeight.SemiBold)
            if (ms != null) Seg(listOf("w" to "12 sem", "m" to "Meses"), St.weeksView, { St.weeksView = it }, fill = false, h = 30.dp, pad = 3.dp)
        }
        if (St.weeksView == "m" && ms != null) MonthsBars(ms) else Weeks12(app)
    }
    CalendarCard(app)
    app.trend()?.let { TrendCard(it, app) }
    app.paces()?.let { p ->
        ZCard(pad = PaddingValues(18.dp), gap = 12.dp) {
            SectionHead("Ritmos y predicciones", icon = Ico.bolt)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.preds.forEach { (l, v) -> Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(12.dp)) { Lbl(l, size = 10.sp); Num(v, 18.sp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { p.train.forEach { (l, v) -> Stat(l, v, 16.sp, unit = "/km", unitSize = 11.sp) } }
            Sub(p.src, size = 11.sp)
        }
    }
    MarksCard(app)
}

/** Mejores marcas y récords juntos (antes en Perfil y en Actividad). */
@Composable
private fun MarksCard(app: App) {
    val c = Z.c; val marks = app.marks(); val recs = app.personalRecords()
    ZCard(pad = PaddingValues(18.dp), gap = 10.dp) {
        SectionHead("Mejores marcas", icon = Ico.trophy)
        marks.forEach { x ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(x.name, Modifier.weight(1f), size = 14.sp, weight = FontWeight.SemiBold)
                if (x.best.isFinite() && x.`when` != null) Sub(app.es.dayMon(x.`when`!!), size = 11.sp)
                Num(if (x.best.isFinite()) fmtTime(x.best) else "—", 18.sp, color = if (x.best.isFinite()) Z.text else Z.muted)
            }
        }
        if (recs.isNotEmpty()) {
            Divider()
            recs.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { x ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface2).clickable { x.run?.let { Nav.go(SUMMARY, run = it.id) } }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Lbl(x.label, size = 10.sp); Num(x.value, 20.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RunsTab(app: App, data: AppData, pad: PaddingValues) {
    val c = Z.c
    val shown = remember(data, St.histFilter, St.histQ) { app.historyList(St.histFilter, St.histQ) }
    // Lista perezosa: solo se dibujan las filas que se ven, aunque haya cientos de carreras.
    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = pad) {
        item(key = "q") { Field(St.histQ, { St.histQ = it }, "Buscar", Modifier.fillMaxWidth(), h = 44.dp, size = 14.sp, radius = 14.dp) }
        item(key = "f") {
            Chips(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                listOf("all" to "Todas", "plan" to "Del plan", "route" to "Con ruta", "long" to "Largas", "note" to "Con notas").forEach { (k, l) -> ChipB(l, St.histFilter == k, { St.histFilter = k }) }
            }
        }
        if (shown.isEmpty()) item(key = "empty") {
            if (data.runs.isNotEmpty()) EmptyState("Nada con ese filtro", action = "Ver todas", onAction = { St.histFilter = "all"; St.histQ = "" })
            else EmptyState("Tu primera carrera te espera", "Aquí verás cada salida con su mapa.", "Salir a correr", { Nav.go(PREP) })
        }
        else itemsIndexed(shown, key = { _, r -> r.id }) { i, r ->
            val top = if (i == 0) 24.dp else 0.dp; val bot = if (i == shown.size - 1) 24.dp else 0.dp
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bot, bot)).background(c.surface).padding(horizontal = 14.dp)) {
                if (i > 0) Divider()
                HistRow(r, app)
            }
        }
    }
}

@Composable
private fun RetosTab(app: App) {
    val c = Z.c
    app.monthGoal()?.let { g ->
        ZCard(dark = true, pad = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ring(g.pct / 100, 76.dp, 8.dp) { Num("${min(100.0, g.pct).roundToInt()}%", 16.sp, color = c.onBar) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Lbl("Objetivo del mes"); Num("${jsRound(g.km)} / ${g.goal} km", 24.sp, color = c.onBar) }
            }
        }
    }
    val ch = app.challenges()
    ZCard(pad = PaddingValues(18.dp), gap = 4.dp) {
        SectionHead("Retos del mes", icon = Ico.target)
        Sub(ch.head, size = 12.sp)
        ch.list.forEach { x ->
            val f = (x.v / x.goal).coerceIn(0.0, 1.0)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ring(f, 44.dp, 5.dp) { if (x.done) Icon(Ico.check, null, Modifier.size(18.dp), tint = Z.text) else Txt("${(f * 100).roundToInt()}%", size = 11.sp, weight = FontWeight.SemiBold) }
                Column(Modifier.weight(1f)) { Txt(x.title, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1); Sub(x.desc, size = 12.sp, maxLines = 1) }
                Sub(if (x.done) "Hecho" else x.fmt(x.v) + "/" + x.fmt(x.goal), size = 12.sp, maxLines = 1)
            }
        }
    }
    ZCard(pad = PaddingValues(18.dp), gap = 12.dp) { BadgeGrid(app) }
}

@Composable
fun HistRow(r: Run, app: App) {
    val c = Z.c; val ll = remember(r.id, r.points.size) { r.points.map { LatLon(it.lat, it.lon) } }
    Row(Modifier.fillMaxWidth().clickable { Nav.go(SUMMARY, run = r.id) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(c.mapLand)) { RunThumb(ll, Modifier.matchParentSize(), 5f) }
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
private fun Weeks12(app: App) {
    val c = Z.c; val w = app.weeks12()
    Sub(w.sub, size = 12.sp)
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        w.list.forEachIndexed { i, x ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                if (x.km > 0) Txt(jsRound(x.km).toString(), size = 10.sp, weight = FontWeight.SemiBold, color = Z.muted)
                Box(Modifier.fillMaxWidth().height(max(3, (x.km / w.max * 64).roundToInt()).dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i == 11) c.volt else c.ink).alpha(if (x.km == 0.0) 0.12f else 1f))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { w.axis.forEach { Sub(it, size = 11.sp) } }
}

@Composable
private fun MonthsBars(m: Months) {
    val c = Z.c
    Sub(m.sub, size = 12.sp)
    m.rows.forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(r.label, Modifier.width(52.dp), size = 12.sp, weight = FontWeight.SemiBold, maxLines = 1)
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(max(0.03, r.km / m.max).toFloat().coerceAtMost(1f)).clip(RoundedCornerShape(5.dp)).background(c.ink))
            }
            Num(toFixed(r.km, 1).replace('.', ','), 14.sp, Modifier.width(44.dp))
        }
    }
}

@Composable
private fun TrendCard(t: Trend, app: App) {
    val c = Z.c; val ink = Z.text; val muted = Z.muted
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Ico.trend, null, Modifier.size(20.dp), tint = Z.text); H2("Ritmo") }; Sub(t.sub, size = 12.sp) }
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
        Sub(t.note, size = 12.sp)
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
            Sub("Más intenso = más veces. Pellizca para acercar.", size = 12.sp)
        }
    }
}
