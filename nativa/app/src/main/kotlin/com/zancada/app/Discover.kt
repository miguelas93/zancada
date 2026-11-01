package com.zancada.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Descubre: calendario de carreras en España, «Aprende en 1 minuto» (con el consejo de comida del día)
 * y clips de 15 s en bucle para calentar, técnica y fuerza.
 */
object DiscSt {
    var tab by mutableStateOf("carreras")
    var dist by mutableStateOf("all")
    var race by mutableStateOf<Race?>(null)
    var lesson by mutableStateOf<Lesson?>(null)
    var group by mutableStateOf("calentar")
    var wrapFrom by mutableStateOf(0L)
}

fun openDiscover(tab: String) { DiscSt.tab = tab; Nav.go(DISCOVER) }

fun lessonIcon(k: String): ImageVector = when (k) { "heart" -> Ico.heart; "gauge" -> Ico.gauge; "shield" -> Ico.shield; "shoe" -> Ico.shoe; "moon" -> Ico.moon; "steps" -> Ico.pulse; "sun" -> Ico.sun; "cloud" -> Ico.cloud; "drop" -> Ico.rain; else -> Ico.bolt }

@Composable
fun DiscoverScreen() {
    val data by Repo.data.collectAsState()
    val app = remember(data) { Repo.app() }
    Box(Modifier.fillMaxSize()) {
        ScreenCol(withTabs = false, gap = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBtn(Ico.back, { Nav.back() }); H1("Descubre", size = 26.sp)
            }
            TopTabs(listOf("carreras" to "Carreras", "aprende" to "Aprende", "clips" to "Clips"), DiscSt.tab, { DiscSt.tab = it })
            TabBody(DiscSt.tab, listOf("carreras", "aprende", "clips")) { t ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (t) {
                        "aprende" -> LearnTab(app)
                        "clips" -> ClipsTab()
                        else -> RacesTab(app, data.plan)
                    }
                }
            }
        }
        DiscSt.race?.let { r -> RaceSheet(r, app, data.plan) { DiscSt.race = null } }
    }
}

// ---------- Carreras ----------

@Composable
private fun RacesTab(app: App, plan: Plan?) {
    Seg(listOf("all" to "Todas", "10k" to "10K", "media" to "Media", "maraton" to "Maratón"), DiscSt.dist, { DiscSt.dist = it })
    val list = app.upcomingRaces(DiscSt.dist.takeIf { it != "all" })
    if (list.isEmpty()) EmptyState("Nada a la vista", "No tengo carreras próximas de esta distancia.")
    var lastMonth = ""
    list.forEach { r ->
        val m = cap(app.es.monYear(r.day(app.zone)))
        if (m != lastMonth) { lastMonth = m; Lbl(m, Modifier.padding(top = 6.dp)) }
        RaceRow(r, app, plan?.raceId == r.id) { DiscSt.race = r }
    }
    Sub("Fechas revisadas el 1 nov 2026. Confírmalas siempre en la web oficial antes de inscribirte.", size = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RaceRow(r: Race, app: App, mine: Boolean, onClick: () -> Unit) {
    val c = Z.c; val d = r.day(app.zone)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(if (mine) c.volt else c.bar), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Num("${app.es.z(d).dayOfMonth}", 24.sp, color = if (mine) c.onVolt else c.onBar)
            Txt(app.es.dayMon(d).substringAfter(' ').uppercase(), size = 10.sp, weight = FontWeight.SemiBold, color = if (mine) c.onVolt else c.onBarMuted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(r.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 1.2)
            Sub("${r.city} · ${r.distLabel()}", size = 12.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val n = app.daysTo(r)
                Sub(if (n == 0) "Hoy" else if (n == 1) "Mañana" else "Faltan $n días", size = 12.sp, color = Z.text)
                if (mine) Chip("Tu plan") else if (r.tag.isNotEmpty()) Chip(r.tag, kind = "mid", size = 10.sp)
            }
        }
        Icon(Ico.chevron, null, Modifier.size(20.dp), tint = Z.muted)
    }
}

@Composable
private fun RaceSheet(r: Race, app: App, plan: Plan?, onClose: () -> Unit) {
    val ctx = LocalContext.current; val d = r.day(app.zone)
    BottomSheet(onClose) {
        Lbl(cap(app.es.wdLongDayMonLong(d)) + " · " + app.es.z(d).year + if (r.unsure) " · por confirmar" else "")
        H1(r.name, size = 28.sp)
        Sub("${r.city} · ${r.distLabel()}${if (r.tag.isNotEmpty()) " · " + r.tag else ""}", size = 14.sp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Faltan", "${app.daysTo(r)}", unit = "días")
            Stat("Semanas", "${app.weeksTo(r)}")
            Stat("Distancia", fmtKmS(r.dist), unit = "km")
        }
        val why = app.raceReady(r)
        when {
            plan?.raceId == r.id -> Sub("Ya te estás preparando para esta. Tu plan termina el día de la carrera.", size = 13.sp, color = Z.text)
            why == null -> Btn("Prepárame para esta", { onClose(); PlanSetupSt.openRace(r) }, icon = Ico.target)
            else -> Sub(why, size = 13.sp, color = Z.text)
        }
        Btn("Web oficial e inscripción", { Share.open(ctx, r.link()) }, kind = BtnKind.Sec, height = 48.dp, size = 14.sp, icon = Ico.share)
        Sub("Fecha y recorrido pueden cambiar: confírmalos en la web oficial.", size = 11.sp)
    }
}

// ---------- Aprende ----------

@Composable
private fun LearnTab(app: App) {
    val c = Z.c
    val tip = remember(app) { app.fuelTip(Repo.wxFresh(3 * 3600_000L)?.t) }
    ZCard(dark = true, gap = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IcoBox(lessonIcon(tip.icon), 34.dp, bg = c.volt, tint = c.onVolt)
            Column { Lbl("Comer y beber · hoy"); Txt(tip.title, size = 16.sp, weight = FontWeight.SemiBold) }
        }
        Sub(tip.text, size = 13.sp)
    }
    Lbl("Aprende en 1 minuto", Modifier.padding(top = 4.dp))
    LESSONS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { l ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(c.surface).clickable { DiscSt.lesson = l; Nav.go(LESSON) }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IcoBox(lessonIcon(l.icon), 36.dp)
                    Txt(l.title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 1.2)
                    Sub("${l.sub} · ${l.cards.size} tarjetas", size = 12.sp, maxLines = 2)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Historias a pantalla completa: toca a la derecha para avanzar y a la izquierda para volver. */
@Composable
fun StoryScreen(n: Int, onClose: () -> Unit, bottom: (@Composable (Int) -> Unit)? = null, page: @Composable ColumnScope.(Int) -> Unit) {
    val c = Z.c; var i by remember { mutableStateOf(0) }; val buzz = rememberBuzz(Buzz.Tick)
    ScreenFix(c.bar, dark = true, gap = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(n) { k -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (k <= i) c.volt else Color(0x33F4F5F0))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Lbl("${i + 1} de $n", color = c.onBarMuted)
            IconBtn(Ico.close, onClose, bg = Color(0x14F4F5F0), tint = c.onBar)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) { page(i) }
            Row(Modifier.matchParentSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = "Anterior") { if (i > 0) { i--; buzz() } })
                Box(Modifier.weight(2f).fillMaxHeight().clickable(onClickLabel = "Siguiente") { if (i < n - 1) { i++; buzz() } else onClose() })
            }
        }
        bottom?.invoke(i)
    }
}

@Composable
fun LessonScreen() {
    val l = DiscSt.lesson ?: run { LaunchedEffect(Unit) { Nav.back() }; return }
    val c = Z.c
    StoryScreen(l.cards.size, { Nav.back() }) { i ->
        val k = l.cards[i]
        Lbl(l.title, color = c.volt)
        Text2(k.big, 92)
        H1(k.title, size = 30.sp, color = c.onBar)
        Sub(k.text, size = 17.sp, color = c.onBarMuted)
        when (k.viz) {
            "zones" -> ZonesViz(k.big)
            "pace" -> PaceViz()
            "cadence" -> Metronome()
            "shoe", "body" -> Icon(if (k.viz == "shoe") Ico.shoe else Ico.shield, null, Modifier.size(72.dp), tint = c.volt)
        }
    }
}

@Composable
private fun Text2(s: String, size: Int) = Num(s, size.sp, color = Z.c.volt, spacing = -0.05)

private val ZONE_C = listOf(Color(0xFF8A8E87), Color(0xFF3D8BFF), Color(0xFFD4FF3A), Color(0xFFFFB020), Color(0xFFFF5A47))

@Composable
private fun ZonesViz(sel: String) {
    val names = listOf("Muy suave", "Suave", "Moderado", "Umbral", "Máximo")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { i, n ->
            val on = sel == "5" || sel == "Z${i + 1}"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("Z${i + 1}", Modifier.width(28.dp), size = 13.sp, weight = FontWeight.SemiBold, color = if (on) Z.c.onBar else Z.c.onBarMuted)
                Box(Modifier.fillMaxWidth(0.35f + i * 0.13f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(ZONE_C[i].copy(alpha = if (on) 1f else 0.3f)))
                Sub(n, size = 12.sp, color = if (on) Z.c.onBar else Z.c.onBarMuted)
            }
        }
    }
}

@Composable
private fun PaceViz() {
    val c = Z.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("4:30" to "Series", "5:15" to "Tempo", "6:15" to "Suave").forEachIndexed { i, (p, l) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(listOf(ZONE_C[4], ZONE_C[3], ZONE_C[2])[i]), contentAlignment = Alignment.Center) { Txt(p, size = 14.sp, weight = FontWeight.Bold, color = c.onVolt) }
                Sub(l, size = 12.sp, color = c.onBarMuted)
            }
        }
    }
}

/** Metrónomo a 180 pasos por minuto: un punto que salta de pie a pie. */
@Composable
private fun Metronome() {
    val c = Z.c
    val t = if (Motion.reduced) 0f else rememberInfiniteTransition(label = "met").animateFloat(0f, 2f, infiniteRepeatable(tween(667, easing = LinearEasing)), label = "m").value
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { k -> Box(Modifier.size(34.dp).clip(CircleShape).background(if (t.toInt() == k) c.volt else Color(0x33F4F5F0))) }
        Sub("180 pasos/min", size = 13.sp, color = c.onBarMuted)
    }
}

// ---------- Clips ----------

@Composable
private fun ClipsTab() {
    Seg(DRILL_GROUPS, DiscSt.group, { DiscSt.group = it })
    Sub(when (DiscSt.group) { "calentar" -> "5 minutos antes de correr. Hazlos seguidos."; "tecnica" -> "Después del calentamiento, 2 días por semana."; else -> "Dos días por semana, 15 minutos. Previene lesiones." }, size = 13.sp)
    DRILLS.filter { it.group == DiscSt.group }.forEach { d -> DrillCard(d) }
}

@Composable
private fun DrillCard(d: Drill) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 112.dp, height = 132.dp).clip(RoundedCornerShape(18.dp)).background(c.bar)) { DrillAnim(d.anim, Modifier.fillMaxSize().semantics { contentDescription = "Animación: ${d.name}" }) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(d.name, size = 16.sp, weight = FontWeight.SemiBold)
            Chip(d.dose, kind = "mid", size = 10.sp)
            Sub(d.cue, size = 13.sp)
        }
    }
}

/** Postura del muñeco (grados): tronco, muslo y rodilla de cada pierna, brazo y codo de cada brazo, y cuánto se eleva. */
private data class Pose(val t: Float, val tl: Float, val kl: Float, val tr: Float, val kr: Float, val al: Float, val el: Float, val ar: Float, val er: Float, val lift: Float = 0f)

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun mix(a: Pose, b: Pose, f: Float) = Pose(lerp(a.t, b.t, f), lerp(a.tl, b.tl, f), lerp(a.kl, b.kl, f), lerp(a.tr, b.tr, f), lerp(a.kr, b.kr, f),
    lerp(a.al, b.al, f), lerp(a.el, b.el, f), lerp(a.ar, b.ar, f), lerp(a.er, b.er, f), lerp(a.lift, b.lift, f))

/** Fotogramas clave de cada ejercicio y lo que dura un ciclo (ms). Ida y vuelta en bucle. */
private val ANIMS: Map<String, Pair<Int, List<Pose>>> = mapOf(
    "knees" to (620 to listOf(Pose(4f, 88f, 92f, 0f, 4f, -35f, 75f, 50f, 70f, .02f), Pose(4f, 0f, 4f, 88f, 92f, 50f, 70f, -35f, 75f, .02f))),
    "heels" to (620 to listOf(Pose(6f, -6f, 150f, 4f, 4f, -30f, 80f, 40f, 80f, .02f), Pose(6f, 4f, 4f, -6f, 150f, 40f, 80f, -30f, 80f, .02f))),
    "swing" to (1300 to listOf(Pose(0f, 55f, 5f, 0f, 2f, 8f, 10f, 75f, -15f), Pose(4f, -35f, 20f, 0f, 2f, 8f, 10f, 75f, -15f))),
    "skip" to (460 to listOf(Pose(6f, 50f, 70f, -5f, 10f, -40f, 80f, 45f, 80f, .035f), Pose(6f, -5f, 10f, 50f, 70f, 45f, 80f, -40f, 80f, .035f))),
    "arms" to (720 to listOf(Pose(3f, 3f, 2f, -3f, 2f, 45f, 80f, -35f, 80f), Pose(3f, 3f, 2f, -3f, 2f, -35f, 80f, 45f, 80f))),
    "run" to (520 to listOf(Pose(8f, 40f, 30f, -30f, 95f, -40f, 80f, 45f, 80f, .03f), Pose(8f, -30f, 95f, 40f, 30f, 45f, 80f, -40f, 80f, .03f))),
    "squat" to (1700 to listOf(Pose(0f, 0f, 0f, 0f, 0f, 80f, 0f, 80f, 0f), Pose(38f, 82f, 112f, 82f, 112f, 95f, 0f, 95f, 0f))),
    "lunge" to (1700 to listOf(Pose(0f, 25f, 2f, -20f, 2f, 5f, 5f, 5f, 5f), Pose(2f, 82f, 82f, -12f, 78f, 5f, 5f, 5f, 5f))),
    "bridge" to (1900 to listOf(Pose(-88f, 128f, 108f, 128f, 108f, 90f, 0f, 90f, 0f), Pose(-112f, 100f, 100f, 100f, 100f, 90f, 0f, 90f, 0f))),
    "calf" to (1400 to listOf(Pose(0f, 1f, 0f, -1f, 0f, 6f, 6f, -4f, 6f), Pose(0f, 1f, 0f, -1f, 0f, 6f, 6f, -4f, 6f, .045f))),
)

/** El muñeco del ejercicio en bucle: pierna y brazo izquierdos en lima, los derechos en blanco. */
@Composable
fun DrillAnim(anim: String, modifier: Modifier = Modifier) {
    val (ms, keys) = ANIMS[anim] ?: ANIMS.getValue("run")
    val f = if (Motion.reduced) 0.5f else rememberInfiniteTransition(label = "drill").animateFloat(0f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "f").value
    val e = (1 - cos(f * Math.PI.toFloat())) / 2   // suave en los extremos
    val p = mix(keys[0], keys[1], e)
    val volt = Z.c.volt; val fg = Z.c.onBar; val ground = Color(0x33F4F5F0)
    Canvas(modifier) {
        val u = size.height * 0.62f                     // 1 unidad del muñeco en píxeles
        fun dirDown(a: Float) = Offset(sin(Math.toRadians(a.toDouble())).toFloat(), cos(Math.toRadians(a.toDouble())).toFloat())
        fun dirUp(a: Float) = Offset(sin(Math.toRadians(a.toDouble())).toFloat(), -cos(Math.toRadians(a.toDouble())).toFloat())
        val hip = Offset(0f, 0f)
        val sh = hip + dirUp(p.t) * 0.32f; val head = hip + dirUp(p.t) * 0.42f
        val kL = hip + dirDown(p.tl) * 0.24f; val fL = kL + dirDown(p.tl - p.kl) * 0.24f
        val kR = hip + dirDown(p.tr) * 0.24f; val fR = kR + dirDown(p.tr - p.kr) * 0.24f
        val eL = sh + dirDown(p.al) * 0.17f; val hL = eL + dirDown(p.al + p.el) * 0.16f
        val eR = sh + dirDown(p.ar) * 0.17f; val hR = eR + dirDown(p.ar + p.er) * 0.16f
        val all = listOf(fL, fR, kL, kR, hip, sh, head + Offset(0f, 0.06f), hL, hR)
        val low = all.maxOf { it.y }
        val gy = size.height * 0.9f
        val cx = size.width * (if (anim == "bridge") 0.62f else 0.5f)
        fun P(o: Offset) = Offset(cx + o.x * u, gy - (low - o.y) * u - p.lift * u)
        drawLine(ground, Offset(size.width * 0.12f, gy + 2f), Offset(size.width * 0.88f, gy + 2f), 3f, StrokeCap.Round)
        val w = u * 0.07f
        fun limb(a: Offset, b: Offset, c2: Offset, col: Color) { drawLine(col, P(a), P(b), w, StrokeCap.Round); drawLine(col, P(b), P(c2), w, StrokeCap.Round) }
        limb(hip, kR, fR, fg.copy(alpha = 0.55f)); limb(sh, eR, hR, fg.copy(alpha = 0.55f))
        drawLine(fg, P(hip), P(sh), w * 1.15f, StrokeCap.Round)
        drawCircle(fg, u * 0.065f, P(head))
        limb(hip, kL, fL, volt); limb(sh, eL, hL, volt)
    }
}

// ---------- Tu mes en Zancada ----------

@Composable
fun MonthScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    val app = remember(data) { Repo.app() }
    val w = remember(app, DiscSt.wrapFrom) { app.monthWrap(DiscSt.wrapFrom) } ?: run { LaunchedEffect(Unit) { Nav.back(); toast("Ese mes no hay carreras") }; return }
    StoryScreen(5, { Nav.back() }, bottom = { i ->
        if (i == 4) Btn("Compartir mi mes", {
            scope.launch {
                val png = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Images.month(ctx, app, w) }
                Share.file(ctx, "zancada-mes-${app.es.z(w.from).year}-${app.es.z(w.from).monthValue}.png", "image/png", png, "Mi ${app.es.monLong(w.from)} con Zancada: ${fmtKm(w.km * 1000)} km 🏃")
            }
        }, icon = Ico.share)
    }) { i ->
        Lbl("Tu ${app.es.monLong(w.from)} en Zancada", color = c.volt)
        when (i) {
            0 -> { Num(countUp(w.km) { fmtKmS(it * 1000) }, 110.sp, unit = "km", unitSize = 28.sp, color = c.onBar, spacing = -0.05); H1("Eso has corrido este mes.", size = 26.sp, color = c.onBar) }
            1 -> {
                Num("${w.runs}", 110.sp, unit = pl(w.runs, "salida"), unitSize = 28.sp, color = c.volt, spacing = -0.05)
                Sub("${fmtTime(w.dur)} corriendo · ${w.days} ${pl(w.days, "día")} activos" + if (w.gain > 0) " · +${w.gain} m de desnivel" else "", size = 17.sp, color = c.onBarMuted)
                MonthDots(w)
            }
            2 -> {
                w.longest?.let { r -> Lbl("La más larga", color = c.onBarMuted); Num(fmtKm(r.dist), 72.sp, unit = "km", unitSize = 22.sp, color = c.onBar); Sub(app.es.wdLongDayMonLong(r.start), size = 15.sp, color = c.onBarMuted) }
                w.fastest?.let { r -> Lbl("La más rápida", Modifier.padding(top = 10.dp), color = c.onBarMuted); Num(app.pace(r.paceSec()), 72.sp, unit = "/km", unitSize = 22.sp, color = c.volt); Sub("${fmtKm(r.dist)} km · ${app.es.wdLongDayMonLong(r.start)}", size = 15.sp, color = c.onBarMuted) }
            }
            3 -> {
                H1(w.bestDow?.let { "Tu día: el ${DAY_N[it]}" } ?: "Sin día fijo", size = 34.sp, color = c.onBar)
                Sub(w.bestHour?.let { h -> "Y sueles salir ${if (h < 12) "por la mañana" else if (h < 20) "por la tarde" else "por la noche"}, hacia las $h:00." } ?: "Corres cuando te cuadra. También vale.", size = 17.sp, color = c.onBarMuted)
            }
            else -> {
                H1(w.verdict(), size = 34.sp, color = c.onBar)
                if (w.prevKm > 0) Sub("Mes anterior: ${fmtKmS(w.prevKm * 1000)} km", size = 15.sp, color = c.onBarMuted)
            }
        }
    }
}

/** Un punto por día del mes: lleno los días que corriste. */
@Composable
private fun MonthDots(w: MonthWrap) {
    val c = Z.c
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        w.dayKm.toList().chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { km -> Box(Modifier.size(22.dp).clip(CircleShape).background(if (km > 0) c.volt else Color(0x1FF4F5F0))) }
            }
        }
    }
}
