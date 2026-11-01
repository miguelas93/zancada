package com.zancada.app

import androidx.compose.foundation.background
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
            Row { Num("zancada", 22.sp, spacing = -0.04); Num(".", 22.sp, color = c.volt, spacing = -0.04) }
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
        Column {
            Sub(cap(app.es.wdLongDayMonLong(app.now())))
            H1(if (data.profile.name.isNotEmpty()) "Hola, ${data.profile.name}" else "Hola")
            wx?.let { Sub(wxText(it).removeSuffix(".") + (it.best?.let { b -> " · Mejor hora hoy: ${b.h}:00 (${b.t}°, ${b.rain} % de lluvia)" } ?: ""), size = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (app.showStartCard()) StartCard()
        // Esta semana
        ZCard(dark = true, gap = 18.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("Esta semana"); Sub("Objetivo ${w.goal} carreras") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Num(w.km, 72.sp, unit = "km", unitSize = 20.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
                WeekBarsDay(w.perDay, w.maxDay, w.today)
            }
            Track(min(1.0, w.count.toDouble() / w.goal))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Carreras", w.count.toString(), 22.sp, unit = "de ${w.goal}")
                Stat("Tiempo", fmtTime(w.dur), 22.sp)
                Stat("Seguidas", w.streak.toString(), 22.sp, unit = "sem")
            }
        }
        app.recap()?.let { r -> RecapCard(r) }
        app.loadWarn()?.let { lw ->
            ZCard(gap = 8.dp, onClick = ::openCoach) {
                Chip("Cuidado con la carga", "hard")
                Sub(lw.text, size = 14.sp, color = c.ink)
                Underline("Pregúntale a Zeta cómo seguir", ::openCoach)
            }
        }
        PlanCardUi(app)
        app.nearRoute(here)?.let { n -> NearCard(n) }
        // Salir a correr
        ZCard(Modifier, pad = PaddingValues(start = 22.dp, top = 18.dp, end = 18.dp, bottom = 18.dp), onClick = { Nav.go(PREP) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Chip("Carrera libre"); Num("Salir a correr", 26.sp); Sub(w.nudge) }
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.volt), contentAlignment = Alignment.Center) { Icon(Ico.play, null, Modifier.size(22.dp), tint = c.onVolt) }
            }
        }
        data.runs.firstOrNull()?.let { LastRunCard(it, app) }
        Sub("Beta · los datos se guardan solo en este móvil", size = 12.sp, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeekBarsDay(perDay: List<Double>, maxDay: Double, today: Int) {
    val c = Z.c
    Row(Modifier.height(84.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        "LMXJVSD".forEachIndexed { i, l ->
            val t = i == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val h = max(4, (perDay[i] / maxDay * 60).roundToInt())
                Box(Modifier.width(10.dp).height(h.dp).clip(RoundedCornerShape(5.dp)).background(if (t) c.volt else c.onBar).alpha(if (t) 1f else if (perDay[i] > 0) 0.35f else 0.35f * 0.18f))
                Txt(l.toString(), size = 11.sp, weight = if (t) FontWeight.SemiBold else FontWeight.Medium, color = if (t) c.onBar else c.onBarMuted)
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
            Stat("Carreras", r.n.toString(), 24.sp); Stat("Km", toFixed(r.km, 1).replace('.', ','), 24.sp); Stat("Tiempo", fmtTime(r.dur), 24.sp)
        }
        Sub(r.text)
    }
}

@Composable
fun PlanCardUi(app: App) {
    when (val p = app.planCard()) {
        PlanCard.None -> ZCard(dark = true, gap = 10.dp, onClick = { PlanSetupSt.open() }) {
            Chip("Nuevo"); Num("Entrena con un plan", 26.sp); Sub("Objetivo, días y semanas. Se adapta cada semana a lo que corres.")
        }
        is PlanCard.Finished -> ZCard(dark = true, gap = 10.dp, onClick = { St.planView = null; Nav.go(PLAN) }) {
            Chip("Plan terminado"); Num("${p.done} de ${p.total} sesiones", 26.sp); Sub(p.text)
        }
        is PlanCard.Week -> ZCard(dark = true, gap = 12.dp, onClick = { St.planView = null; Nav.go(PLAN) }) {
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
