package com.zancada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zancada.core.*
import kotlinx.coroutines.delay

@Composable
fun PlanScreenUi() {
    val c = Z.c; val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val app = remember(data, minute) { Repo.app() }
    val p = data.plan
    if (p == null) { LaunchedEffect(Unit) { Nav.reset(Scr(HOME)) }; return }
    val ps = app.planScreen(St.planView) ?: return
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(sure) { if (sure) { delay(3000); sure = false } }
    val wkScroll = rememberScrollState()
    LaunchedEffect(ps.view) { wkScroll.animateScrollTo(maxOf(0, (ps.view - 3) * 52 * ctx.resources.displayMetrics.density.toInt())) }
    ScreenCol(withTabs = false, gap = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.back, { Nav.back() }); Chip(ps.chip); Spacer(Modifier.width(44.dp))
        }
        Column {
            Lbl(ps.sub); H1(ps.title, Modifier.padding(top = 4.dp))
            ps.pred?.let { pred ->
                val t = Regex("saldría en ([^ ]+)").find(pred)?.groupValues?.get(1)
                val ink = Z.text
                Text(buildAnnotatedString {
                    if (t == null) append(pred) else { val i = pred.indexOf(t); append(pred.substring(0, i)); withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(t) }; append(pred.substring(i + t.length)) }
                }, Modifier.padding(top = 8.dp), style = bodyStyle(14.sp, FontWeight.Medium).copy(color = Z.muted))
            }
        }
        PhotoCard(height = 240.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl(ps.weekTitle); ps.note?.let { Sub(it, size = 12.sp) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Num(fmtKmS(ps.done), 56.sp, unit = "de ${fmtKmS(ps.km)} km", unitSize = 18.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
                Sub("${ps.sessions.count { it.run != null }}/${ps.sessions.size} sesiones")
            }
            Track(if (ps.km > 0) ps.done / ps.km else 0.0)
        }
        Row(Modifier.horizontalScroll(wkScroll).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ps.weekKms.forEachIndexed { i, km ->
                val on = i == ps.view
                Column(Modifier.size(46.dp, 58.dp).alpha(if (i < ps.cur && !on) 0.55f else 1f).clip(RoundedCornerShape(14.dp)).background(if (on) c.bar else c.surface).clickable { St.planView = i },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Num("${i + 1}", 17.sp, color = if (on) c.volt else c.ink, spacing = 0.0)
                    Txt("$km km", size = 11.sp, color = if (on) c.onBarMuted else c.muted, lineHeight = 1.1)
                }
            }
        }
        WeekCalendar(ps.sessions)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
            ps.sessions.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable { if (s.run != null) Nav.go(SUMMARY, run = s.run!!.id) else if (s.state == "today") Nav.go(PREP) else toast(s.desc, 4000) }.padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SessIcon(s)
                    Column(Modifier.weight(1f)) { Txt(s.title, weight = FontWeight.SemiBold); Sub(app.sessLine(s), size = 12.sp) }
                    Column(horizontalAlignment = Alignment.End) {
                        Num(fmtKmS(s.m), 20.sp, unit = "km", unitSize = 12.sp)
                        app.sessPace(s)?.let { Sub(it, size = 11.sp) }
                    }
                }
            }
        }
        ZCard(gap = 4.dp) {
            H2("Avisos", Modifier.padding(bottom = 6.dp))
            SelRow("Recordarme los días de entreno", listOf("0" to "No", "1" to "Sí"), if (p.remind?.on == true) "1" else "0") { v ->
                val on = v == "1"
                Repo.update { d -> d.copy(plan = d.plan?.let { it.copy(remind = (it.remind ?: Remind()).copy(on = on)) }) }
                if (on) Perm.notifications { ok -> if (!ok) toast("Sin permiso de notificaciones: actívalo en Ajustes del móvil"); Reminders.sync(ctx) } else Reminders.sync(ctx)
            }
            SelRow("Hora del aviso", listOf(6, 7, 8, 9, 10, 12, 14, 17, 18, 19, 20).map { "$it" to "$it:00" }, "${p.remind?.hour ?: 8}") { v ->
                Repo.update { d -> d.copy(plan = d.plan?.let { it.copy(remind = (it.remind ?: Remind()).copy(hour = v.toInt())) }) }; Reminders.sync(ctx)
            }
            Sub("Aviso los días de entreno, aunque la app esté cerrada.", size = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Btn("Añadir el plan a mi calendario", {
                val ics = app.planIcs(p); if (ics == null) toast("No quedan sesiones pendientes en el plan")
                else Share.file(ctx, "zancada-plan.ics", "text/calendar", ics.first.toByteArray(), "Mi plan de Zancada: ${ics.second} sesiones")
            }, Modifier.padding(top = 10.dp), BtnKind.Sec, 46.dp, 14.sp)
        }
        ZCard(pad = PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = { openCoach() }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ZetaAv(44.dp, 24)
                Column(Modifier.weight(1f)) { Txt("Pregúntale a Zeta", weight = FontWeight.SemiBold); Sub("Tu entrenador IA: conoce tu plan y tus carreras") }
                Icon(Ico.next, null, Modifier.size(18.dp), tint = Z.text)
            }
        }
        Btn("Cambiar objetivo, días o nivel", { PlanSetupSt.open(p) }, kind = BtnKind.Sec)
        Btn(if (sure) "Pulsa otra vez para confirmar" else "Eliminar el plan", {
            if (!sure) { sure = true; return@Btn }
            Repo.update { it.copy(plan = null) }; Reminders.sync(ctx); toast("Plan eliminado"); Nav.reset(Scr(HOME))
        }, kind = BtnKind.Ghost, color = c.danger)
        Spacer(Modifier.height(70.dp))
    }
}

/** Color de cada tipo de sesión, como en el calendario de Runna: suave lima, largo tinta, ritmo ámbar, series rojo. */
@Composable
fun sessColor(type: String): Pair<Color, Color> {
    val c = Z.c
    return when (type) {
        "suave" -> c.volt to c.onVolt
        "largo" -> (if (c.night) Color(0xFF3A3E39) else c.bar) to c.onBar
        "tempo" -> Color(0xFFFFB020) to Ink
        "series", "fartlek" -> Color(0xFFFF5A47) to Color.White
        "carrera" -> c.volt to c.onVolt
        else -> c.surface2 to c.ink
    }
}
private val SESS_L = mapOf("suave" to "Suave", "largo" to "Largo", "tempo" to "Ritmo", "series" to "Series", "fartlek" to "Series", "carrera" to "Carrera")

/** La semana como calendario: un bloque de color por sesión, con sus km; hecho = check; hoy = borde. */
@Composable
private fun WeekCalendar(list: List<Sess>) {
    val c = Z.c; val buzz = rememberBuzz()
    val byDay = list.associateBy { it.dayIdx }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..6).forEach { d ->
                val s = byDay[d]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(DAY_L[d], size = 12.sp, weight = FontWeight.SemiBold, color = if (s?.state == "today") Z.text else Z.muted)
                    val (bg, fg) = if (s == null) Color.Transparent to Z.muted else sessColor(s.type)
                    val faded = s != null && (s.state == "miss" || s.state == "skip")
                    Box(Modifier.fillMaxWidth().height(62.dp).alpha(if (faded) 0.35f else 1f).clip(RoundedCornerShape(14.dp))
                        .background(if (s == null) c.surface2.copy(alpha = 0.5f) else bg)
                        .then(if (s?.state == "today") Modifier.border(2.dp, Z.text, RoundedCornerShape(14.dp)) else Modifier)
                        .then(if (s != null) Modifier.clickable { buzz(); if (s.run != null) Nav.go(SUMMARY, run = s.run!!.id) else if (s.state == "today") Nav.go(PREP) else toast(s.desc, 4000) } else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (s != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (s.state == "ok") Icon(Ico.check, null, Modifier.size(16.dp), tint = fg)
                            Text(fmtKmS(s.m), style = numStyle(15.sp, 0.0).copy(color = fg))
                            Txt("km", size = 10.sp, color = fg.copy(alpha = 0.7f), lineHeight = 1.0)
                        }
                    }
                }
            }
        }
        val types = list.map { it.type }.distinctBy { SESS_L[it] ?: it }
        if (types.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            types.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(sessColor(t).first)); Sub(SESS_L[t] ?: t, size = 11.sp)
            } }
        }
    }
}

@Composable
private fun SessIcon(s: Sess) {
    val c = Z.c
    val tc = sessColor(s.type)
    val (bg, fg) = when (s.state) { "ok" -> c.volt to c.onVolt; "today" -> c.bar to c.onBar; "miss" -> Color.Transparent to c.muted; else -> tc.first.copy(alpha = 0.22f) to Z.text }
    Box(Modifier.size(40.dp).alpha(if (s.state == "skip") 0.4f else 1f).clip(CircleShape).background(bg)
        .then(if (s.state == "miss") Modifier.border(1.5.dp, c.line, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (s.state == "ok") Icon(Ico.check, null, Modifier.size(18.dp), tint = fg) else Num(DAY_L[s.dayIdx], 13.sp, color = fg, spacing = 0.0)
    }
}

/** Estado del asistente para crear o cambiar el plan. */
object PlanSetupSt {
    var goal by mutableStateOf("10k"); var weeks by mutableStateOf(8); var days by mutableStateOf(listOf(1, 3, 5)); var level by mutableStateOf("medio")
    var step by mutableStateOf(0); var hint = ""
    /** Plan para una carrera del calendario: objetivo y semanas fijos, se empieza en «días». */
    var race by mutableStateOf<Race?>(null)
    fun openRace(r: Race) {
        open(null); race = r
        val a = Repo.app(); goal = r.goal() ?: "10k"; weeks = a.weeksTo(r).coerceIn(1, RACE_MAX_WEEKS)
        if (goal == "maraton" && level == "inicio") level = "medio"
        step = 1
    }
    fun open(prev: Plan? = null) {
        race = null
        val a = Repo.app(); val h = a.histWeeklyKm()
        val lvl = if (h.n >= 2) (if (h.km < 10) "inicio" else if (h.km < 25) "medio" else "avanzado") else "medio"
        val g = a.goalOf(); val def = if (g <= 2) listOf(1, 5) else if (g == 3) listOf(1, 3, 5) else listOf(0, 2, 4, 6)
        if (prev != null) { goal = prev.goal; weeks = prev.weeks; days = prev.days; level = prev.level } else { goal = "10k"; weeks = 8; days = def; level = lvl }
        step = 0
        hint = if (h.n >= 2) "Según tus últimas 4 semanas corres unos ${toFixed(h.km, 0)} km por semana." else "Sin carreras recientes en Zancada: elige a ojo, se puede cambiar."
        Nav.go(PLANSETUP)
    }
}

@Composable
fun PlanSetupScreen() {
    val c = Z.c; val ctx = LocalContext.current; val s = PlanSetupSt
    val data by Repo.data.collectAsState()
    val nd = s.days.size
    ScreenFix(c.bar, dark = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.close, { if (data.plan != null || s.race != null) Nav.back() else Nav.reset(Scr(HOME)) }, bg = Color(0x14F4F5F0), tint = c.onBar)
            Dots(4, s.step); Spacer(Modifier.width(44.dp))
        }
        Spacer(Modifier.weight(1f))
        when (s.step) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("Objetivo"); H1("¿Para qué entrenas?", size = 32.sp)
                listOf(Triple("5k", "5", "Correr 5 km" to "Terminar o mejorar tu 5K"), Triple("10k", "10", "Correr 10 km" to "El clásico. Con 8 semanas suele bastar"),
                    Triple("media", "21", "Media maratón" to "21,1 km. Mejor con 12 semanas"), Triple("constancia", "∞", "Coger el hábito" to "Sin carrera objetivo, solo constancia")).forEach { (k, n, t) ->
                    Opt(n, t.first, t.second, s.goal == k) { s.goal = k; if (k == "media" && s.weeks < 10) s.weeks = 12 }
                }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Lbl("Días y duración"); H1("¿Qué días corres?", size = 32.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_L.forEachIndexed { i, l ->
                        val on = i in s.days
                        Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.volt else Color(0x14F4F5F0))
                            .clickable { s.days = if (on) s.days - i else (s.days + i).sorted() }, contentAlignment = Alignment.Center) {
                            Txt(l, size = 15.sp, weight = FontWeight.SemiBold, color = if (on) c.onVolt else c.onBar)
                        }
                    }
                }
                Sub(if (nd < 2) "Elige al menos 2 días." else if (nd > 5) "Más de 5 días no es buena idea por ahora." else "$nd días: ${listEs(s.days.map { DAY_N[it] })}. El rodaje largo cae en ${DAY_N[s.days.last()]}.", color = c.onBarMuted)
                val r = s.race
                if (r != null) Sub("${s.weeks} semanas hasta ${r.name} (${Repo.app().es.dayMon(r.day(Repo.app().zone))}). La carrera cae ese día aunque no sea uno de los tuyos.", color = c.onBarMuted)
                else {
                    Lbl("Semanas", Modifier.padding(top = 6.dp))
                    Seg(listOf("6", "8", "10", "12").map { it to it }, "${s.weeks}", { s.weeks = it.toInt() }, dark = true)
                }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lbl("Nivel"); H1("¿Cuánto corres ahora?", size = 32.sp)
                Sub(s.hint, modifier = Modifier.padding(bottom = 4.dp), color = c.onBarMuted)
                Opt("A", "Estoy empezando", "Menos de 10 km a la semana", s.level == "inicio") { s.level = "inicio" }
                Opt("B", "Corro con regularidad", "Entre 10 y 25 km a la semana", s.level == "medio") { s.level = "medio" }
                Opt("C", "Ya entreno en serio", "Más de 25 km a la semana", s.level == "avanzado") { s.level = "avanzado" }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (t, txt) = Repo.app().planSetupText(s.goal, s.weeks, s.days, s.level, true)
                Lbl("Tu plan"); H1(s.race?.name ?: t, size = 32.sp); Sub((if (s.race != null) "$t. " else "") + txt, size = 15.sp, color = c.onBarMuted)
                if (s.goal == "maraton" && s.level == "inicio") Sub("Ojo: un maratón empezando de cero es mucho. Si puedes, elige una media antes.", size = 13.sp, color = c.volt)
            }
        }
        Spacer(Modifier.weight(1f))
        Btn(if (s.step < 3) "Continuar" else if (data.plan != null) "Sustituir mi plan" else "Crear el plan", {
            if (s.step < 3) { s.step++; return@Btn }
            val plan = s.race?.let { Repo.app().newRacePlan(it, s.days, s.level, true) } ?: Repo.app().newPlan(s.goal, s.weeks, s.days, s.level, true)
            Repo.update { it.copy(plan = plan, profile = it.profile.copy(goal = s.days.size)) }
            St.planView = null
            Perm.notifications { ok -> if (!ok) Repo.update { d -> d.copy(plan = d.plan?.let { it.copy(remind = (it.remind ?: Remind()).copy(on = false)) }) }; Reminders.sync(ctx) }
            toast("Plan creado"); Nav.replace(PLAN)
        }, enabled = !(s.step == 1 && (nd < 2 || nd > 5)))
    }
}
