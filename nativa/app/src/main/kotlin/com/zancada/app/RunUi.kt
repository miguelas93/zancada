package com.zancada.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- Preparar ----------

@Composable
fun PrepScreen() {
    val c = Z.c; val ctx = LocalContext.current; val ins = WindowInsets.systemBars.asPaddingValues()
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val ui by RunBus.state.collectAsState()
    val app = remember(data, minute) { Repo.app() }
    var perm by remember { mutableStateOf(Repo.hasLocation(ctx)) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (RunBus.state.value.phase == Phase.RUNNING) { Nav.replace(RUN); return@LaunchedEffect }
        Perm.location { ok -> perm = ok; denied = !ok; if (ok) RunService.send(ctx, RunService.PREP) }
    }
    DisposableEffect(Unit) { onDispose { if (Nav.cur.id != COUNT && Nav.cur.id != RUN && RunBus.state.value.phase != Phase.RUNNING) RunService.send(ctx, RunService.STOP) } }
    val fix = ui.fix; val acc = ui.acc
    var wx by remember { mutableStateOf<Wx?>(null) }
    LaunchedEffect(fix != null) { fix?.let { wx = Repo.weather(it) } }

    val s0 = app.todaySession(); val s = if (s0 != null && St.light) lightSession(s0) else s0
    val mode = (St.prepMode ?: "plan").let { if (s == null && it == "plan") "libre" else it }
    val tr = if (mode == "tramos") (St.tramos ?: app.tramosSession(data.settings.tramos ?: "6x400", data.settings.tramosCustom) ?: app.tramosSession("6x400")) else null
    val goal = St.goal.takeIf { it != "0" } ?: "0"
    val ss: Sess? = when (mode) { "plan" -> s; "tramos" -> tr; else -> if (goal != "0") goalSession(goal) else null }
    val route = St.pickedRoute

    // mapa
    val ctl = remember { MapCtl() }
    val rl = remember(route) { route?.ll?.toLL() }
    val d0 = if (route != null && fix != null) hav(fix, route.startLL()) else null
    LaunchedEffect(route, fix == null, d0?.let { it > 50 && it <= 2500 }) {
        if (rl != null) ctl.fit(rl + listOfNotNull(fix?.takeIf { d0 != null && d0 > 50 && d0 <= 2500 }), 30f, 90f, 30f, 340f)
        else fix?.let { ctl.center(it, 16.0) }
    }
    LaunchedEffect(fix) { if (rl == null) fix?.let { ctl.center(it) } }
    val lines = buildList {
        rl?.let { add(MLine(it, Ink, 4f, 0.55f, dash = 2f to 9f)) }
        if (route != null && fix != null && d0 != null && d0 > 50 && d0 <= 2500) add(MLine(listOf(fix, route.startLL()), Volt, 4f, 0.9f, dash = 1f to 8f))
    }
    val dots = listOfNotNull(fix?.let { MDot(it, Volt, Ink) })

    Box(Modifier.fillMaxSize().background(c.mapLand)) {
        ZMap(Modifier.fillMaxSize(), "calles", lines, dots, ctl, interactive = false, attribution = false)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.close, { Nav.back() })
            val good = acc != null && acc <= 25
            Row(Modifier.clip(RoundedCornerShape(50)).background(c.bar).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (denied || ui.gpsError != null) c.danger else if (acc == null) c.onBarMuted else if (good) c.volt else if (acc <= 60) c.onBarMuted else c.danger))
                Txt(if (denied) "Sin permiso" else if (acc == null) "Buscando GPS…" else if (good) "GPS listo · ${acc.roundToInt()} m" else "Afinando GPS · ${acc.roundToInt()} m", size = 13.sp, weight = FontWeight.SemiBold, color = c.onBar, lineHeight = 1.2)
            }
            Spacer(Modifier.width(44.dp))
        }
        // hoja inferior
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = (LocalView.current.height / LocalDensity.current.density * 0.82f).dp)
            .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.surface)
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = ins.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
                Seg(listOfNotNull(if (s != null) "plan" to "Plan" else null, "libre" to "Libre", "tramos" to "Tramos"), mode, { St.prepMode = it })
            }
            if (mode == "tramos") TramosPicker(app, tr)
            Column {
                Lbl(if (mode == "plan" && s != null) "Plan · semana ${s.week + 1}" else if (tr != null) "Entreno por tramos" else "Entreno")
                H1(when { mode == "plan" && s != null -> "${s.title} · ${fmtKmS(s.m)} km"; tr != null -> "${tr.title} · ${if (tr.timed) "${tr.min} min" else fmtKmS(tr.m) + " km"}"; goal != "0" && mode == "libre" -> "Carrera libre · ${goalSession(goal).title}"; else -> "Carrera libre" },
                    Modifier.padding(top = 4.dp), size = 30.sp)
                Sub(when {
                    mode == "plan" && s != null -> "${s.desc} Ritmo objetivo ~${app.targetPace(s.type)}/km.${if (s.struct != null) " Te guío por voz en cada tramo." else ""}"
                    tr != null -> "${tr.desc} Ritmo fuerte ~${app.targetPace(tr.type)}/km. Te guío por voz en cada tramo."
                    goal != "0" && mode == "libre" -> "${goalSession(goal).desc} Sin más guía: ritmo y parciales en directo."
                    else -> "Sin objetivo. Distancia, ritmo y parciales en directo."
                }, Modifier.padding(top = 6.dp))
                wx?.let { Sub(wxText(it), Modifier.padding(top = 8.dp)) }
            }
            if (route != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.routes, null, Modifier.size(22.dp), tint = Z.muted)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Txt(route.name, weight = FontWeight.SemiBold, maxLines = 1)
                    val km = "${commaNum(route.km)} km"
                    Sub(if (fix == null || d0 == null) "$km · te guío por el camino" else if (d0 <= 50) "$km · estás en la salida" else if (d0 <= 2500) "$km · salida a ${fmtM(d0)}, te llevo hasta allí" else "$km · salida a ${fmtM(d0)}: mejor ve en transporte (Cómo llegar, en Rutas)", size = 12.sp)
                }
                IconBtn(Ico.close, { St.pickedRoute = null }, size = 36.dp, iconSize = 18.dp)
            }
            if (mode == "libre") Chips(wrap = true) {
                listOf("0" to "Sin objetivo", "3000" to "3 km", "5000" to "5 km", "10000" to "10 km", "t20" to "20 min", "t30" to "30 min", "t60" to "60 min").forEach { (k, l) ->
                    ChipB(l, goal == k, { St.goal = k; Repo.settings { it.copy(goal = k) } })
                }
            }
            if (mode == "plan" && s != null) Btn(if (s.light) "Volver a la sesión completa" else "Hoy no estoy al 100 %", {
                St.light = !St.light; toast(if (St.light) "Sesión recortada un 40 %: menos series o menos km, misma estructura." else "Sesión completa", 3000)
            }, kind = BtnKind.Ghost, height = 40.dp, size = 13.sp)
            Btn("Empezar", {
                RunBus.config = RunConfig(ss, route)
                Perm.runExtras { Nav.go(COUNT) }
            }, enabled = fix != null && perm)
            Repo.data.value.settings.takeIf { it.sosOn == "1" && !it.sosPhone.isNullOrEmpty() }?.let { st ->
                Sub("Modo seguridad: avisaré por SMS a ${st.sosName?.ifEmpty { null } ?: st.sosPhone} cada ${st.sosEvery ?: 10} min y si me quedo parado.", size = 12.sp)
            }
            Sub(when {
                !perm || denied -> "Necesito permiso de ubicación para medir tu carrera. Si lo negaste, actívalo en Ajustes › Apps › Zancada › Permisos."
                ui.gpsError != null -> ui.gpsError!!
                acc == null -> "Sal a cielo abierto y espera unos segundos."
                acc <= 25 -> "Cuando quieras."
                else -> "Puedes empezar igual; el principio puede salir impreciso."
            }, Modifier.fillMaxWidth(), size = 12.sp, align = TextAlign.Center)
            FlowRowCenter {
                Underline("Calentar 3 min, guiado", { St.stretchWarm = true; St.modal = "stretch" })
                Underline("¿En cinta? Sin GPS", { St.modal = "cinta" })
                Underline("Avisar a alguien", { Share.text(ctx, app.tellText(route, ss, fix)) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCenter(content: @Composable () -> Unit) =
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }

@Composable
private fun TramosPicker(app: App, tr: Sess?) {
    val c = Z.c; val s = app.settings
    val cur = tr?.key ?: s.tramos ?: "6x400"
    val cust = s.tramosCustom ?: TramosCustom()
    var unitMin by remember { mutableStateOf(cust.t) }
    var reps by remember { mutableStateOf(cust.reps.toString()) }
    var on by remember { mutableStateOf(if (cust.t) commaNum(cust.on / 60.0) else cust.on.toString()) }
    var off by remember { mutableStateOf(if (cust.t) commaNum(cust.off / 60.0) else cust.off.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chips(wrap = true) {
            TRAMOS.forEach { t -> ChipB(t.title, cur == t.key, { Repo.settings { it.copy(tramos = t.key) }; St.tramos = app.tramosSession(t.key) }) }
            if (cur == "custom") ChipB("A mi gusto", true, {})
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(reps, { reps = it.filter(Char::isDigit) }, "6", Modifier.width(44.dp), h = 38.dp, size = 13.sp, numeric = true, maxLen = 2, align = TextAlign.Center, radius = 10.dp)
            Txt("×", size = 13.sp)
            Field(on, { on = it }, if (unitMin) "1" else "400", Modifier.width(60.dp), h = 38.dp, size = 13.sp, decimal = true, maxLen = 5, align = TextAlign.Center, radius = 10.dp)
            Box(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2).clickable {
                unitMin = !unitMin; if (unitMin) { on = "1"; off = "1" } else { on = "400"; off = "200" }
            }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Txt(if (unitMin) "min ▾" else "m ▾", size = 13.sp) }
            Txt("· rec.", size = 13.sp)
            Field(off, { off = it }, if (unitMin) "1" else "200", Modifier.width(54.dp), h = 38.dp, size = 13.sp, decimal = true, maxLen = 5, align = TextAlign.Center, radius = 10.dp)
            Txt(if (unitMin) "min" else "m", size = 13.sp)
            Spacer(Modifier.weight(1f))
            Btn("Usar", {
                fun num(v: String) = v.replace(',', '.').toDoubleOrNull()
                val r = maxOf(1, minOf(30, reps.toIntOrNull() ?: 6))
                val cc = if (unitMin) TramosCustom(true, r, jsRoundI(maxOf(0.5, num(on) ?: 1.0) * 60), jsRoundI(maxOf(0.0, num(off) ?: 0.0) * 60))
                else TramosCustom(false, r, maxOf(100, (num(on) ?: 400.0).toInt()), maxOf(0, (num(off) ?: 0.0).toInt()))
                Repo.settings { it.copy(tramos = "custom", tramosCustom = cc) }; St.tramos = app.tramosSession("custom", cc)
            }, kind = BtnKind.Sec, height = 38.dp, size = 13.sp, fill = false)
        }
    }
}

// ---------- Cuenta atrás ----------

@Composable
fun CountScreen() {
    val c = Z.c; val ctx = LocalContext.current
    var n by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        Voice.say("Tres, dos, uno. ¡Vamos!")
        repeat(2) { delay(1000); n-- }
        delay(1000)
        RunService.send(ctx, RunService.START)
        Nav.reset(Scr(HOME), Scr(RUN))
    }
    Column(Modifier.fillMaxSize().background(c.bar), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("$n", style = numStyle(180.sp).copy(color = c.volt))
        Sub("Prepárate…", size = 16.sp, color = c.onBarMuted)
    }
}

// ---------- En carrera ----------

@Composable
fun RunScreen() {
    val c = Z.c; val ctx = LocalContext.current; val ins = WindowInsets.systemBars.asPaddingValues(); val view = LocalView.current
    val ui by RunBus.state.collectAsState()
    val track by RunBus.track.collectAsState()
    val cele by RunBus.cele.collectAsState()
    val data by Repo.data.collectAsState()
    val toastEv by RunBus.toast.collectAsState()
    var locked by remember { mutableStateOf(false) }
    val cfg = RunBus.config
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(toastEv) { toastEv?.let { toast(it.second, 3500) } }
    LaunchedEffect(ui.phase, cele) { if (ui.phase == Phase.DONE && cele != null) { St.pickedRoute = null; Nav.replace(CELE) } }
    LaunchedEffect(ui.gpsError, ui.phase) { if (ui.gpsError != null && ui.phase == Phase.IDLE) { toast(ui.gpsError!!); Nav.reset(Scr(HOME)) } }
    LaunchedEffect(ui.tooShort) { if (ui.tooShort != null) St.modal = "short" }
    val ctl = remember { MapCtl() }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(ui.fix) { ui.fix?.let { ctl.center(it, if (!zoomed) 16.0 else null); zoomed = true } }
    val routeLL = remember(cfg.route) { cfg.route?.ll?.toLL() }
    val lines = buildList {
        routeLL?.let { add(MLine(it, Ink, 5f, 0.45f, dash = 2f to 10f)) }
        ui.live.toStart?.let { s -> ui.fix?.let { f -> add(MLine(listOf(f, s), Volt, 4f, 0.9f, dash = 1f to 8f)) } }
        addAll(routeLines(track))
    }
    val dots = listOfNotNull(ui.fix?.let { MDot(it, Volt, Ink) })
    val bigm = data.settings.bigm ?: "dist"
    val el = ui.elapsed
    val avgTxt = if (ui.dist > 20) fmtPace(el / (ui.dist / 1000)) else "–:––"
    val paceTxt = if (ui.paused || !ui.pace.isFinite()) avgTxt else fmtPace(ui.pace)
    val bigVal = when (bigm) { "time" -> fmtTime(el); "pace" -> avgTxt; else -> fmtKm(ui.dist) }
    val secVal = if (bigm == "dist") fmtTime(el) else fmtKm(ui.dist) + " km"
    val stateText = if (ui.paused) (if (ui.autoPaused) "Autopausa · muévete para seguir" else "En pausa") else ui.live.stateText
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = ins.calculateTopPadding() + 14.dp, bottom = ins.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZCard(dark = true, pad = PaddingValues(22.dp), gap = 16.dp, radius = 32.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Lbl(stateText, Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ui.acc != null && ui.acc!! <= 25) c.volt else if (ui.acc != null && ui.acc!! <= 60) c.onBarMuted else c.danger))
                        Sub("GPS" + (ui.acc?.let { " ±${it.roundToInt()} m" } ?: ""))
                    }
                }
                Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {
                    val order = listOf("dist", "time", "pace"); Repo.settings { it.copy(bigm = order[(order.indexOf(bigm) + 1) % 3]) }
                }) {
                    Lbl(when (bigm) { "time" -> "Tiempo"; "pace" -> "Ritmo medio"; else -> "Distancia" })
                    Num(bigVal, if (bigm == "time" && el >= 3600) 76.sp else 100.sp, unit = when (bigm) { "dist" -> "km"; "pace" -> "/km"; else -> null }, unitSize = 22.sp, unitWeight = FontWeight.SemiBold, spacing = -0.05)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Ritmo actual", paceTxt, 40.sp, unit = "/km", color = c.volt)
                    Stat(if (bigm == "dist") "Tiempo" else "Distancia", secVal, 40.sp, end = true)
                }
                val lv = ui.live
                if (lv.guideOn) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Lbl("Ahora"); Sub(lv.gStep) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Num(lv.gLabel, 24.sp, Modifier.weight(1f, fill = false), color = if (lv.gKind == "fuerte") c.volt else c.onBar)
                        Num(lv.gLeft, 24.sp, unit = lv.gUnit.ifEmpty { null })
                    }
                    Track(lv.gPct)
                }
                if (lv.ghostOn) Column {
                    Divider(); Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Lbl("Fantasma · tu mejor ${lv.ghostBest}"); Num(lv.ghostDiff, 24.sp, color = when (lv.ghostAhead) { true -> c.volt; false -> Color(0xFFFF8A65); null -> c.onBar }) }
                        Sub(lv.ghostSub, Modifier.fillMaxWidth(0.45f), size = 12.sp, align = TextAlign.End)
                    }
                }
                if (lv.navOn) Column {
                    Divider(); Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.navArrow, null, Modifier.size(36.dp).rotate(lv.navRot.toFloat()), tint = c.volt)
                        Column { Txt(lv.navMain, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1); Sub(lv.navSub) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Ritmo medio", if (ui.dist > 50) fmtPace(el / (ui.dist / 1000)) else "–:––", 28.sp, unit = "/km")
                Stat("Mejor km", if (ui.bestKm.isFinite()) fmtPace(ui.bestKm) else "–:––", 28.sp, unit = "/km")
                ui.cad?.let { Stat("Cadencia", "$it", 28.sp, unit = "ppm") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) { ZMap(Modifier.matchParentSize(), "calles", lines, dots, ctl) }
            if (!ui.paused) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                IconBtn(Ico.lock, { locked = true }, Modifier.align(Alignment.CenterStart).padding(start = 40.dp), size = 56.dp)
                Box(Modifier.align(Alignment.Center).size(88.dp).clip(CircleShape).background(c.volt).clickable { RunService.send(ctx, RunService.PAUSE) }, contentAlignment = Alignment.Center) {
                    Icon(Ico.pause, "Pausar", Modifier.size(30.dp), tint = c.onVolt)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Seguir", { RunService.send(ctx, RunService.RESUME) })
                SlideToFinish { RunService.send(ctx, RunService.FINISH) }
            }
        }
        if (locked) LockOverlay(ui, paceTxt) { locked = false }
    }
}

@Composable
private fun SlideToFinish(done: () -> Unit) {
    val c = Z.c; val scope = rememberCoroutineScope(); val dn = LocalDensity.current.density
    var w by remember { mutableStateOf(0f) }
    val x = remember { Animatable(0f) }
    val knob = 56 * dn; val max = (w - knob - 8 * dn).coerceAtLeast(1f)
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(c.surface).onSizeChanged { w = it.width.toFloat() }
        .pointerInput(max) {
            var t0 = 0L
            detectHorizontalDragGestures(onDragStart = { t0 = System.currentTimeMillis() }, onDragEnd = {
                val v = x.value / maxOf(1L, System.currentTimeMillis() - t0)
                scope.launch { if (x.value >= max * 0.45f || (v > 0.6f * dn && x.value > 40 * dn)) { x.animateTo(max, tween(120)); done(); x.snapTo(0f) } else x.animateTo(0f, tween(250)) }
            }, onDragCancel = { scope.launch { x.animateTo(0f) } }) { ch, d ->
                ch.consume(); scope.launch {
                    x.snapTo((x.value + d).coerceIn(0f, max))
                    if (x.value >= max * 0.55f) { x.animateTo(max, tween(120)); done(); x.snapTo(0f) }
                }
            }
        }) {
        Box(Modifier.fillMaxHeight().width(((x.value + knob + 4 * dn) / dn).dp).background(c.danger.copy(alpha = 0.18f)))
        Txt("Desliza para terminar →", Modifier.align(Alignment.Center).padding(start = 56.dp), size = 15.sp, weight = FontWeight.SemiBold, color = c.muted)
        Box(Modifier.padding(4.dp).offset { IntOffset(x.value.roundToInt(), 0) }.size(56.dp).clip(CircleShape).background(c.danger), contentAlignment = Alignment.Center) {
            Icon(Ico.arrow, null, Modifier.size(22.dp), tint = Color.White)
        }
    }
}

@Composable
private fun LockOverlay(ui: RunUi, paceTxt: String, unlock: () -> Unit) {
    val c = Z.c; val scope = rememberCoroutineScope()
    val p = remember { Animatable(0f) }
    CompositionLocalProvider(LocalDark provides true) {
        Column(Modifier.fillMaxSize().background(c.bar).clickable(remember { MutableInteractionSource() }, null) {}.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Lbl("Pantalla bloqueada")
            Text(fmtKm(ui.dist), style = numStyle(88.sp).copy(color = c.volt)); Sub("km")
            Row(Modifier.widthIn(max = 300.dp).padding(top = 8.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Lbl("Tiempo"); Num(fmtTime(ui.elapsed), 30.sp) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Lbl("Ritmo"); Num(paceTxt, 30.sp) }
            }
            Txt(ui.lockLine, Modifier.padding(top = 6.dp).heightIn(min = 22.dp), size = 15.sp, color = c.onBar, align = TextAlign.Center)
            Box(Modifier.padding(top = 30.dp).height(58.dp).clip(RoundedCornerShape(50)).background(c.surface2)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val job = scope.launch { p.snapTo(0f); p.animateTo(1f, tween(1000)); unlock() }
                        do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed })
                        job.cancel(); scope.launch { p.snapTo(0f) }
                    }
                }, contentAlignment = Alignment.CenterStart) {
                Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(c.volt.copy(alpha = 0.35f))) }
                Txt("Mantén para desbloquear", Modifier.padding(horizontal = 28.dp), size = 16.sp, weight = FontWeight.SemiBold, color = c.ink)
            }
        }
    }
}

// ---------- Carrera terminada ----------

@Composable
fun CeleScreen() {
    val c = Z.c
    val cele by RunBus.cele.collectAsState()
    val ce = cele ?: run { LaunchedEffect(Unit) { Nav.reset(Scr(HOME)) }; return }
    val r = ce.run; val app = remember { Repo.app() }
    ScreenFix(c.bar, gap = 20.dp, dark = true) {
        Lbl("Carrera terminada")
        if (ce.badges.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            ce.badges.take(3).forEachIndexed { i, b ->
                var show by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(250L + i * 200L); show = true }
                Box(Modifier.size(if (ce.badges.size > 1) 96.dp else 140.dp, (if (ce.badges.size > 1) 96 else 140).dp * 1.2f)) { if (show) Medal(b, if (ce.badges.size > 1) 96.dp else 140.dp, pop = true, shine = true) }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Num(fmtKm(r.dist), 112.sp, unit = "km", unitSize = 26.sp, unitWeight = FontWeight.SemiBold, color = c.volt, spacing = -0.05)
            Txt(ce.text, size = 17.sp, weight = FontWeight.Medium)
        }
        Column { Divider(); Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Tiempo", fmtTime(r.dur), 26.sp); Stat("Ritmo", app.pace(r.paceSec()), 26.sp); Stat("Semana", ce.week, 26.sp) }
        }
        Spacer(Modifier.weight(1f))
        Btn("Ver mi carrera", { Nav.replace(SUMMARY, run = r.id) })
        Btn("Estirar 5 min, guiado", { St.stretchWarm = false; St.modal = "stretch" }, Modifier.alpha(0.8f), BtnKind.Ghost, color = c.onBar)
    }
}
