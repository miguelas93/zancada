package com.zancada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- Bienvenida ----------

@Composable
fun WelcomeScreen() {
    val c = Z.c
    val prof = Repo.data.collectAsState().value.profile
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(prof.name) }
    var goal by remember { mutableStateOf(maxOf(1, if (prof.goal == 0) 3 else prof.goal)) }
    var lvl by remember { mutableStateOf(if (Repo.data.value.settings.startSeen == "1") "corro" else "cero") }
    val n = name.trim()
    fun next() {
        if (step == 0) Repo.update { it.copy(profile = it.profile.copy(name = safeText(name, 20))) }
        if (step == 1) Repo.update { it.copy(profile = it.profile.copy(goal = goal)) }
        if (step < 3) { step++; return }
        Repo.update { it.copy(profile = it.profile.copy(done = true), settings = it.settings.copy(newsSeen = NEWS_VER)) }
        Nav.reset(Scr(HOME))
    }
    ScreenFix(c.bar, dark = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZLockup(26.dp, c.volt, c.onBar)
            Dots(4, step)
        }
        Spacer(Modifier.weight(1f))
        when (step) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("Bienvenida"); H1("Corre.\nMide.\nMejora.", size = 40.sp)
                Sub("Zancada graba tus carreras con el GPS del móvil: distancia, ritmo, parciales y la ruta en el mapa. ¿Cómo te llamas?", size = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
                Field(name, { name = it }, "Tu nombre", Modifier.fillMaxWidth(), h = 56.dp, size = 18.sp, maxLen = 20, dark = true, onDone = ::next, radius = 18.dp)
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("Objetivo semanal"); H1(if (n.isNotEmpty()) "$n, ¿cuántas veces a la semana?" else "¿Cuántas veces a la semana?", size = 32.sp)
                Sub("Lo verás en Inicio. Puedes cambiarlo en Perfil.", modifier = Modifier.padding(bottom = 6.dp))
                Chips(Modifier.padding(bottom = 4.dp)) {
                    ChipB("Empiezo de cero", lvl == "cero", { lvl = "cero"; Repo.settings { it.copy(startSeen = "0") } })
                    ChipB("Ya corro", lvl == "corro", { lvl = "corro"; Repo.settings { it.copy(startSeen = "1") } })
                }
                Opt("2", "Empezar a correr", "2 carreras a la semana", goal == 2) { goal = 2 }
                Opt("3", "Mantener la constancia", "3 carreras a la semana", goal == 3) { goal = 3 }
                Opt("4", "Mejorar mis marcas", "4 carreras a la semana", goal == 4) { goal = 4 }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lbl("Qué hay dentro"); H1("Tres cosas, nada más.", size = 32.sp, modifier = Modifier.padding(bottom = 8.dp))
                Feat(Ico.play, "Correr", "GPS, ritmo en directo, voz en cada km, autopausa y pantalla bloqueada.", first = true)
                Feat(Ico.plan, "Plan", "Un plan de 5K, 10K o media que cada lunes se adapta a lo que corriste.")
                Feat(Ico.routes, "Rutas", "Sitios investigados para correr en Madrid y San Fernando, con trazado, perfil y guía por voz.")
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("Todo listo"); H1(if (n.isNotEmpty()) "Listo, $n." else "Listo.", size = 36.sp)
                Sub("Objetivo: $goal carreras a la semana. Cada semana que lo cumplas suma una semana seguida. Pulsa el botón lima cuando quieras salir; en Perfil tienes insignias, zapatillas y copia de seguridad.", size = 15.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Btn(when (step) { 0 -> "Continuar"; 1 -> "Ese es mi objetivo"; 2 -> "Vale"; else -> "Empezar" }, ::next)
    }
}

@Composable
private fun Feat(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, first: Boolean = false) {
    val c = Z.c
    Column {
        if (!first) Divider()
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14F4F5F0)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = c.volt) }
            Column { Txt(title, size = 15.sp, weight = FontWeight.SemiBold); Sub(text, color = c.onBarMuted) }
        }
    }
}

// ---------- Inicio ----------

@Composable
fun HomeScreen() {
    val c = Z.c; val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val here by Repo.here.collectAsState()
    val app = remember(data, minute) { Repo.app() }
    LaunchedEffect(Unit) { Repo.locate(ctx) }
    var wx by remember { mutableStateOf(Repo.wxFresh(20 * 60_000)) }
    LaunchedEffect(here) { here?.let { p -> Repo.weather(p)?.let { wx = it } } }
    val w = app.homeWeek()
    ScreenCol {
        // Cabecera de marca: logo siempre a la izquierda y accesos rápidos a la derecha.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ZLockup(24.dp, if (c.night) c.volt else c.ink, c.ink, Modifier.weight(1f))
            HeadBtn(Ico.plus, "Añadir una carrera a mano") { St.manualPrefill = null; St.modal = "manual" }
            HeadBtn(Ico.chat, "Hablar con Zeta", ::openCoach)
            HeadBtn(Ico.search, "Buscar rutas") { St.rSearch = true; Nav.go(ROUTES) }
            HeadBtn(Ico.bell, "Novedades") { St.modal = "news" }
        }
        // Saludo en una línea y el tiempo como chip con icono.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Sub(cap(app.es.wdLongDayMonLong(app.now())), size = 12.sp, maxLines = 1)
                H1(if (data.profile.name.isNotEmpty()) "Hola, ${data.profile.name}" else "Hola", size = 26.sp)
            }
            wx?.let { WxChip(it) }
        }
        if (app.showStartCard()) StartCard()
        StreakStrip(app, w)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalTile(w, Modifier.weight(1f).fillMaxHeight())
            KmTile(w, Modifier.weight(1f).fillMaxHeight())
        }
        app.loadWarn()?.let { lw ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).clickable(onClick = ::openCoach).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IcoBox(Ico.gauge, 34.dp, bg = c.danger.copy(alpha = 0.14f), tint = c.danger)
                Txt(lw.text, Modifier.weight(1f), size = 13.sp, maxLines = 2)
                Icon(Ico.chevron, null, Modifier.size(20.dp), tint = Z.muted)
            }
        }
        PlanCardUi(app)
        app.recap()?.let { r -> RecapCard(r) }
        data.runs.firstOrNull()?.let { LastRunCard(it, app) }
        app.nearRoute(here)?.let { n -> NearCard(n) }
    }
}

@Composable
private fun HeadBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(23.dp), tint = Z.c.ink) }
}

/** Chip del tiempo: icono, grados y la mejor hora para salir. */
@Composable
private fun WxChip(w: Wx) {
    val c = Z.c
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(wxIcon(w.code), null, Modifier.size(18.dp), tint = c.ink)
        Num("${w.t}°", 16.sp)
        w.best?.let { b -> Sub("· ${b.h}:00", size = 12.sp, maxLines = 1) }
    }
}

/** «En racha»: semanas seguidas cumpliendo el objetivo, en una sola fila, con el sello Z en cada día corrido. */
@Composable
private fun StreakStrip(app: App, w: HomeWeek) {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val ws = app.weekStart(app.now()); val ran = app.runs.filter { it.start >= ws }.map { app.es.dow(it.start) }.toSet()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).clickable { Nav.go(HISTORY) }.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Ico.flame, null, Modifier.size(18.dp), tint = if (w.streak > 0) c.ink else c.muted)
                Num(w.streak.toString(), 22.sp)
            }
            Sub("En racha", size = 11.sp, maxLines = 1)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            "LMXJVSD".forEachIndexed { i, l ->
                val done = i in ran; val today = i == w.today
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) c.bar else Color.Transparent).then(if (today && !done) Modifier.border(1.5.dp, c.ink, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    if (done) ZMark(9.dp, c.volt) else Txt(l.toString(), size = 11.sp, weight = if (today) FontWeight.SemiBold else FontWeight.Normal, color = if (today) c.ink else c.muted)
                }
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Compartir tu semana") {
            scope.launch {
                val a = Repo.app(); val png = withContext(Dispatchers.Default) { Images.week(ctx, a) }
                if (png == null) { toast("Cuando corras esta semana, podrás compartirla"); return@launch }
                val wr = a.runs.filter { it.start >= ws }
                Share.file(ctx, "zancada-semana-${isoMs(ws).take(10)}.png", "image/png", png, "Mi semana con Zancada: ${fmtKm(wr.sumOf { it.dist })} km en ${wr.size} ${pl(wr.size, "salida")} 🏃")
            }
        }, contentAlignment = Alignment.Center) { Icon(Ico.share, "Compartir", Modifier.size(20.dp), tint = c.ink) }
    }
}

/** Mosaico oscuro: anillo del objetivo semanal en %. */
@Composable
private fun GoalTile(w: HomeWeek, modifier: Modifier) {
    val c = Z.c; val f = if (w.goal > 0) w.count.toDouble() / w.goal else 0.0
    ZCard(modifier, dark = true, pad = PaddingValues(16.dp), gap = 10.dp, radius = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Ico.target, null, Modifier.size(16.dp), tint = c.onBarMuted); Lbl("Objetivo") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ring(f, 62.dp, 7.dp) { Num("${min(100, (f * 100).roundToInt())}%", 15.sp, color = c.onBar) }
            Column { Num("${w.count}/${w.goal}", 22.sp, color = c.onBar); Sub("carreras", size = 11.sp, color = c.onBarMuted) }
        }
    }
}

/** Mosaico: km de la semana con mini barras por día. */
@Composable
private fun KmTile(w: HomeWeek, modifier: Modifier) {
    val c = Z.c
    ZCard(modifier, pad = PaddingValues(16.dp), gap = 8.dp, radius = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Ico.history, null, Modifier.size(16.dp), tint = Z.muted); Lbl("Semana") }
        Num(w.km, 26.sp, unit = "km", unitSize = 12.sp)
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            w.perDay.forEachIndexed { i, m ->
                Box(Modifier.weight(1f).height(max(3, (m / max(1.0, w.maxDay) * 30).roundToInt()).dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i == w.today) c.volt else c.ink).alpha(if (m == 0.0) 0.12f else 1f))
            }
        }
    }
}

@Composable
private fun StartCard() {
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("¿Empiezas de cero?"); Underline("Ya corro", { Repo.settings { it.copy(startSeen = "1") } }, size = 12.sp) }
        Txt("Corre 1 min · anda 1,5 min · 8 veces", size = 19.sp, weight = FontWeight.SemiBold, lineHeight = 1.15)
        Sub("29 minutos guiados por voz: la forma más segura de empezar. Cuando te salga fácil, pasa a \"Corre 3 min\".")
        Btn("Probar hoy", { St.prepMode = "tramos"; Repo.settings { it.copy(tramos = "anda1") }; St.tramos = Repo.app().tramosSession("anda1"); Nav.go(PREP) }, height = 46.dp, size = 15.sp)
    }
}

@Composable
private fun RecapCard(r: Recap) {
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Lbl("La semana pasada"); IconBtn(Ico.close, { Repo.settings { it.copy(recapSeen = r.ws) } }, size = 32.dp, bg = Z.c.surface2, iconSize = 16.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IcoStat(Ico.flag, r.n.toString(), "carreras"); IcoStat(Ico.routes, toFixed(r.km, 1).replace('.', ','), "km"); IcoStat(Ico.clock, fmtTime(r.dur), "tiempo")
        }
    }
}

@Composable
fun PlanCardUi(app: App) {
    when (val p = app.planCard()) {
        PlanCard.None -> PhotoCard(height = 250.dp, onClick = { PlanSetupSt.open() }) {
            Chip("Nuevo"); Num("Entrena con un plan", 26.sp); Sub("Objetivo, días y semanas. Se adapta cada semana a lo que corres.")
        }
        is PlanCard.Finished -> ZCard(dark = true, gap = 10.dp, onClick = { St.planView = null; Nav.go(PLAN) }) {
            Chip("Plan terminado"); Num("${p.done} de ${p.total} sesiones", 26.sp); Sub(p.text)
        }
        is PlanCard.Week -> PhotoCard(onClick = { St.planView = null; Nav.go(PLAN) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl(p.head, Modifier.weight(1f)); Sub(p.doneTxt) }
            if (p.title != null) Column {
                Chip(p.chip ?: "", modifier = Modifier.padding(bottom = 8.dp)); Num(p.title ?: "", 26.sp); p.desc?.let { Sub(it, modifier = Modifier.padding(top = 4.dp)) }
            } else if (p.chip == null && p.head.contains("semana")) Num("Semana cumplida", 24.sp)
            Track(p.pct)
            p.review?.let { Divider(); Sub(it, size = 12.sp) }
        }
    }
}

@Composable
private fun NearCard(n: Near) {
    ZCard(pad = PaddingValues(start = 18.dp, top = 14.dp, end = 16.dp, bottom = 14.dp), onClick = { Nav.go(ROUTE, route = n.r) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePhotoThumb(n.r, w = 64.dp, h = 48.dp)
            Column(Modifier.weight(1f)) {
                Lbl("Ruta cerca · a ${fmtM(n.d)}")
                Txt(n.r.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Sub("${jsNum(n.r.km).replace('.', ',')} km · ${n.r.surface ?: ""}${if (n.times > 0) " · la has corrido ${if (n.times == 1) "una vez" else "${n.times} veces"}" else ""}", size = 12.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(22.dp), tint = Z.muted)
        }
    }
}

@Composable
private fun LastRunCard(r: Run, app: App) {
    val c = Z.c
    ZCard(pad = PaddingValues(16.dp), onClick = { Nav.go(SUMMARY, run = r.id) }) {
        if (r.points.size > 1) Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(c.mapLand)) { RunThumb(r.points.map { LatLon(it.lat, it.lon) }, Modifier.matchParentSize(), 7f, crop = false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lbl("Última carrera" + if (r.manual) " · añadida a mano" else "", Modifier.padding(bottom = 4.dp))
                Txt(r.name, size = 16.sp, weight = FontWeight.SemiBold)
                Sub(app.es.fmtDate(r.start), size = 12.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(22.dp), tint = Z.muted)
        }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCols(r, app, 24) }
    }
}

/** Distancia, ritmo y tiempo de una carrera (statCols de la web). */
@Composable
fun RowScope.StatCols(r: Run, app: App, size: Int) {
    Stat("Distancia", fmtKm(r.dist), size.sp, unit = "km")
    Stat("Ritmo", app.pace(r.paceSec()), size.sp, unit = "/km")
    Stat("Tiempo", fmtTime(r.dur), size.sp)
}
