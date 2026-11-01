package com.zancada.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

fun closeModal() { St.modal = null }

/** Fondo oscuro y tarjeta abajo (o centrada), como .modal de la web. */
@Composable
private fun Sheet(center: Boolean = false, scrim: Color = Z.c.scrim, dismiss: Boolean = true, maxW: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit) {
    val ins = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize().background(scrim).clickable(remember { MutableInteractionSource() }, null) { if (dismiss) closeModal() }
        .imePadding().padding(start = 16.dp, end = 16.dp, top = ins.calculateTopPadding() + 16.dp, bottom = ins.calculateBottomPadding() + 16.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.BottomCenter) {
        Column(Modifier.widthIn(max = maxW).fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {}, content = content)
    }
}

@Composable
fun Modals() {
    when (St.modal) {
        "stretch" -> StretchModal(St.stretchWarm)
        "coach" -> CoachSheet()
        "medal" -> St.medal?.let { MedalModal(it) }
        "photo" -> PhotoModal()
        "news" -> NewsModal()
        "cinta" -> CintaModal()
        "manual" -> ManualModal()
        "short" -> ShortModal()
    }
}

// ---------- estiramientos y calentamiento guiados ----------

@Composable
private fun StretchModal(warm: Boolean) {
    val list = if (warm) WARMUP else STRETCHES; val sec = if (warm) 30 else 40
    var i by remember { mutableStateOf(0) }; var left by remember { mutableStateOf(sec) }; var paused by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    fun go(k: Int) {
        if (k >= list.size) { closeModal(); Voice.say(if (warm) "Calentamiento hecho. Cuando quieras, empieza." else "Listo. Buen trabajo."); toast(if (warm) "Calentamiento hecho" else "Estiramientos hechos", 2500); return }
        i = k; left = sec; Voice.say("${list[k].first}. ${list[k].second}"); Voice.buzz(longArrayOf(0, 120))
    }
    LaunchedEffect(Unit) { go(0) }
    LaunchedEffect(i, paused) {
        while (isActive && !paused) {
            delay(1000); if (paused) break
            left--
            if (!warm && left == 10 && i == STRETCHES.size - 1) Voice.say("Cambia de lado.")
            if (warm && left == 15 && i == 3) Voice.say("Cambia de pierna.")
            if (left <= 0) { go(i + 1); break }
        }
    }
    Sheet(dismiss = false) {
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("${if (warm) "Calentamiento" else "Estiramiento"} ${i + 1} de ${list.size}"); Underline("Cerrar", ::closeModal) }
            H1(list[i].first, size = 28.sp)
            Sub(list[i].second, size = 15.sp)
            Num("$left", 72.sp, unit = "s", spacing = -0.04)
            Track((sec - left).toDouble() / sec)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (paused) "Seguir" else "Pausa", { paused = !paused }, Modifier.weight(1f), BtnKind.Sec, 48.dp, 15.sp)
                Btn("Siguiente", { go(i + 1) }, Modifier.weight(1f), BtnKind.Sec, 48.dp, 15.sp)
            }
            Sub("Suave y sin rebotes: tensión, nunca dolor.", size = 11.sp)
        }
    }
}

// ---------- Zeta ----------

@Composable
private fun CoachSheet() {
    val c = Z.c; val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    var q by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val h = (LocalConfiguration.current.screenHeightDp * 0.82f).coerceAtMost(720f).dp
    LaunchedEffect(data.coach.size, St.coachBusy, St.coachWait) { delay(30); scroll.animateScrollTo(scroll.maxValue) }
    fun ask(text: String) {
        val t = text.trim(); if (t.isEmpty() || St.coachBusy) return
        Repo.update { it.copy(coach = it.coach.filter { m -> !m.err } + CoachMsg("user", t)) }
        St.coachBusy = true
        scope.launch {
            val a = Repo.app(); val body = a.coachBody(a.data.coach)
            suspend fun call(): Pair<String?, Int?> {
                val r1 = Net.post(COACH_URL, body)
                if (r1.code == 402 || r1.code == 429) return null to (r1.retryAfter ?: 30)
                r1.body?.let { coachAnswer(it) }?.let { return it to null }
                val r2 = Net.post(COACH_URL2, body)
                if (r2.code == 402 || r2.code == 429) return null to (r2.retryAfter ?: 30)
                return r2.body?.let { coachClean(it).takeIf { s -> s.isNotEmpty() } } to null
            }
            var (ans, wait) = call()
            if (ans == null && wait != null && wait <= 45) { St.coachWait = wait; delay(wait * 1000L + 500); St.coachWait = null; val r = call(); ans = r.first; wait = r.second }
            St.coachBusy = false
            val msg = if (ans != null) CoachMsg("assistant", ans) else CoachMsg("assistant", if (wait != null) "La IA está saturada. Prueba en un minuto." else "Sin conexión. Prueba en un momento.", err = true)
            Repo.update { it.copy(coach = (it.coach + msg).takeLast(30)) }
        }
    }
    Sheet {
        ZCard(Modifier.height(h), pad = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp), gap = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZetaAv(42.dp, 23)
                Column(Modifier.weight(1f)) { Text("Zeta", style = numStyle(20.sp, -0.02).copy(color = c.ink)); Sub(if (St.coachBusy) "Escribiendo…" else "Tu entrenador IA", size = 12.sp) }
                if (data.coach.isNotEmpty()) Underline("Nueva charla", { if (!St.coachBusy) Repo.update { it.copy(coach = emptyList()) } })
                IconBtn(Ico.close, ::closeModal, size = 40.dp)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bubble("Hola${if (data.profile.name.isNotEmpty()) ", " + data.profile.name else ""}. $COACH_HELLO".replace("Hola. Soy", "Hola. Soy"), me = false)
                data.coach.forEach { m -> Bubble(m.content, m.role == "user", m.err) }
                St.coachWait?.let { Bubble("Hay cola en la IA gratuita. Lo intento de nuevo en $it s…", false, true) }
                if (St.coachBusy) Box(Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(c.surface2).padding(horizontal = 16.dp, vertical = 8.dp)) { TypingDots() }
            }
            Chips { COACH_QS.forEach { (l, qq) -> ChipB(l, false, { ask(qq) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(q, { q = it }, "Pregúntale lo que quieras", Modifier.weight(1f), h = 48.dp, maxLen = 400, onDone = { ask(q); q = "" }, radius = 14.dp)
                Btn("Enviar", { ask(q); q = "" }, height = 48.dp, size = 15.sp, enabled = !St.coachBusy, fill = false)
            }
            Sub("Conoce tu plan y tus carreras. Es una IA: si algo duele, consulta a un profesional.", size = 11.sp)
        }
    }
}

@Composable
private fun ColumnScope.Bubble(text: String, me: Boolean, err: Boolean = false) {
    val c = Z.c
    val shape = RoundedCornerShape(18.dp, 18.dp, if (me) 6.dp else 18.dp, if (me) 18.dp else 6.dp)
    Box(Modifier.align(if (me) Alignment.End else Alignment.Start).fillMaxWidth(0.88f).wrapContentWidth(if (me) Alignment.End else Alignment.Start)
        .clip(shape).background(if (err) Color.Transparent else if (me) c.bar else c.surface2)
        .then(if (err) Modifier.border(1.dp, c.line, shape) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        val fg = if (err) c.muted else if (me) c.onBar else c.ink
        Text(buildAnnotatedString { coachRich(text).forEach { p -> if (p.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(p.text) } else append(p.text) } },
            style = bodyStyle(if (err) 13.sp else 15.sp).copy(color = fg))
    }
}

// ---------- medalla ----------

@Composable
private fun MedalModal(b: Badge) {
    val app = remember { Repo.app() }
    Sheet(center = true, maxW = 360.dp) {
        ZCard(pad = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 22.dp), gap = 10.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Medal(b, 150.dp, pop = b.`when` > 0, shine = b.`when` > 0)
                Lbl(if (b.`when` > 0) TIER_NAME[tierOf(b.id)] ?: "" else "Por conseguir")
                H1(b.name, size = 26.sp)
                Sub(if (b.`when` > 0) "Conseguida el ${app.es.dayMonLongYear(b.`when`)}." else "Aún no la tienes. Te aviso al conseguirla.", size = 14.sp, align = TextAlign.Center)
                Btn("Cerrar", ::closeModal, Modifier.padding(top = 6.dp), height = 48.dp, size = 15.sp)
            }
        }
    }
}

// ---------- foto ----------

@Composable
private fun PhotoModal() {
    val ph = St.photos; if (ph.isEmpty()) { closeModal(); return }
    val i = ((St.photoI % ph.size) + ph.size) % ph.size; val x = ph[i]
    val h = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    Sheet(center = true, scrim = Color(0xEB000000), maxW = 720.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            key(x.big) { NetImage(x.big, Modifier.fillMaxWidth().heightIn(max = h).height(h).clip(RoundedCornerShape(12.dp)), fallback = x.src, fit = ContentScale.Fit) }
            Txt("${i + 1} de ${ph.size} · ${if (x.by.isNotEmpty()) "Foto: " + x.by else "Wikimedia Commons"}${if (x.lic.isNotEmpty()) " · " + x.lic else ""}", Modifier.fillMaxWidth(), size = 12.sp, color = Color(0xFFCCCCCC), align = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Anterior", { St.photoI = i - 1 }, Modifier.weight(1f), BtnKind.Sec, 46.dp, 14.sp)
                Btn("Cerrar", ::closeModal, Modifier.weight(1f), height = 46.dp, size = 14.sp)
                Btn("Siguiente", { St.photoI = i + 1 }, Modifier.weight(1f), BtnKind.Sec, 46.dp, 14.sp)
            }
        }
    }
}

// ---------- novedades ----------

@Composable
private fun NewsModal() {
    val ctx = LocalContext.current
    fun done() { Repo.settings { it.copy(newsSeen = NEWS_VER) }; closeModal() }
    Sheet(dismiss = false) {
        ZCard(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.86f).dp), pad = PaddingValues(horizontal = 20.dp, vertical = 24.dp), gap = 12.dp) {
            Lbl("Novedades · app nativa")
            H1("Zancada ya es una app de Android", size = 26.sp)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                NEWS.forEach { (t, d) ->
                    Text(buildAnnotatedString { append("•  "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(t) }; append(": $d") }, style = bodyStyle(15.sp).copy(color = Z.c.ink, lineHeight = 21.sp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Ver todo", { done(); Share.open(ctx, "https://github.com/miguelas93/zancada/blob/main/LEEME.md#novedades") }, Modifier.weight(1f), BtnKind.Sec, 48.dp, 14.sp)
                Btn("A correr", ::done, Modifier.weight(1f), height = 48.dp, size = 15.sp)
            }
        }
    }
}

// ---------- cinta (sin GPS) ----------

@Composable
private fun CintaModal() {
    val t0 = remember { System.currentTimeMillis() }
    var pausedMs by remember { mutableStateOf(0L) }; var pauseAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var said by remember { mutableStateOf(0) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    fun sec() = ((now - t0 - pausedMs - (if (pauseAt > 0) now - pauseAt else 0)) / 1000).toInt()
    LaunchedEffect(Unit) { Voice.say("Cronómetro en marcha."); while (isActive) { delay(250); now = System.currentTimeMillis(); val s = sec(); if (s > 0 && s % 300 == 0 && said != s) { said = s; Voice.say("${s / 60} minutos.") } } }
    Sheet(dismiss = false) {
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("Cinta · sin GPS"); Underline("Cancelar", ::closeModal) }
            Text(fmtTime(sec().toDouble()), Modifier.fillMaxWidth(), style = numStyle(84.sp, -0.04).copy(color = Z.c.ink, textAlign = TextAlign.Center))
            Sub("Aviso por voz cada 5 min. Al final, apunta los km de la cinta.", Modifier.fillMaxWidth(), size = 14.sp, align = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (pauseAt > 0) "Seguir" else "Pausa", { if (pauseAt > 0) { pausedMs += System.currentTimeMillis() - pauseAt; pauseAt = 0 } else pauseAt = System.currentTimeMillis() }, Modifier.weight(1f), BtnKind.Sec, 52.dp, 15.sp)
                Btn("Terminar", {
                    val s = sec(); closeModal()
                    if (s < 30) { toast("Menos de 30 segundos: no lo guardo"); return@Btn }
                    Nav.go(HOME); St.manualPrefill = t0 to fmtTime(s.toDouble()); St.modal = "manual"; Voice.say("Apunta los kilómetros que marca la cinta.")
                }, Modifier.weight(1f), height = 52.dp, size = 15.sp)
            }
        }
    }
}

// ---------- carrera a mano ----------

@Composable
private fun ManualModal() {
    val c = Z.c; val ctx = LocalContext.current; val app = remember { Repo.app() }
    val pre = St.manualPrefill
    var start by remember { mutableStateOf(pre?.first ?: (System.currentTimeMillis() - 3600_000)) }
    var km by remember { mutableStateOf("") }; var time by remember { mutableStateOf(pre?.second ?: "") }; var name by remember { mutableStateOf(if (pre != null) "Cinta" else "") }
    val z = ZoneId.systemDefault()
    fun pick() {
        val d = Instant.ofEpochMilli(start).atZone(z)
        DatePickerDialog(ctx, { _, y, m, dd ->
            TimePickerDialog(ctx, { _, hh, mm -> start = d.withYear(y).withMonth(m + 1).withDayOfMonth(dd).withHour(hh).withMinute(mm).withSecond(0).toInstant().toEpochMilli() }, d.hour, d.minute, true).show()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
    Sheet {
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 24.dp), gap = 12.dp) {
            H2("Carrera a mano")
            Sub("Cinta o reloj: cuenta para la semana, el plan y las insignias.")
            @Composable fun row(label: String, content: @Composable () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(label, Modifier.weight(1f), size = 16.sp, weight = FontWeight.Medium); content()
            }
            row("Fecha y hora") { Box(Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2).clickable { pick() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Txt(app.es.numeric(start), size = 14.sp) } }
            row("Distancia (km)") { Field(km, { km = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } }, "5,0", Modifier.width(110.dp), h = 40.dp, decimal = true, maxLen = 6, radius = 10.dp) }
            row("Tiempo (h:mm:ss o mm:ss)") { Field(time, { time = it.filter { ch -> ch.isDigit() || ch == ':' } }, "28:30", Modifier.width(110.dp), h = 40.dp, maxLen = 8, radius = 10.dp) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Nombre", size = 16.sp, weight = FontWeight.Medium); Field(name, { name = it }, "Cinta en el gimnasio", Modifier.weight(1f), h = 40.dp, maxLen = 40, radius = 10.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancelar", ::closeModal, Modifier.weight(1f), BtnKind.Sec, 46.dp)
                Btn("Guardar", {
                    val r = Repo.app().manualRun(start, km, time, name)
                    if (r == null) { toast("Revisa la fecha, la distancia y el tiempo"); return@Btn }
                    Repo.mutate { addRun(r) }; Reminders.sync(ctx); Hc.push(ctx); St.manualPrefill = null; closeModal(); toast("Carrera añadida")
                }, Modifier.weight(1f), height = 46.dp)
            }
        }
    }
}

// ---------- carrera muy corta ----------

@Composable
private fun ShortModal() {
    val c = Z.c; val ctx = LocalContext.current
    val ui by RunBus.state.collectAsState()
    Sheet(dismiss = false) {
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 24.dp), gap = 12.dp) {
            H1("Carrera muy corta", size = 24.sp)
            Sub("Has recorrido ${jsRound(ui.tooShort ?: 0.0)} m. ¿La guardo igualmente?")
            Btn("Guardar igualmente", { closeModal(); RunBus.state.value = RunBus.state.value.copy(tooShort = null); RunService.send(ctx, RunService.SAVE_SHORT) })
            Btn("Descartar", { closeModal(); RunService.send(ctx, RunService.DISCARD); toast("Carrera descartada"); Nav.reset(Scr(HOME)) }, kind = BtnKind.Ghost, color = c.danger)
        }
    }
}
