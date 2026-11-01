package com.zancada.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Felicitación al terminar: tu recorrido se dibuja como el logo (pastilla A hueca en la salida, B maciza en la meta),
 * caen pastillas de confeti de la marca y te decimos por qué volver: tu serie y lo próximo.
 */
@Composable
fun CeleScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val cele by RunBus.cele.collectAsState()
    val ce = cele ?: run { LaunchedEffect(Unit) { Nav.reset(Scr(HOME)) }; return }
    val r = ce.run; val app = remember { Repo.app() }
    val name = Repo.data.collectAsState().value.profile.name
    val w = remember { app.homeWeek() }
    val plan = remember { app.planCard() }
    val draw = remember { Animatable(0f) }
    val show = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)); }
    LaunchedEffect(Unit) { delay(500); show.animateTo(1f, tween(500)) }
    val ins = WindowInsets.systemBars.asPaddingValues()
    CompositionLocalProvider(LocalDark provides true) {
        Box(Modifier.fillMaxSize().background(c.bar)) {
            Confetti(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp, bottom = ins.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ZLockup(22.dp, c.volt, c.onBar)
                RouteMark(r, draw.value, Modifier.fillMaxWidth().height(170.dp))
                Column(Modifier.alpha(show.value), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lbl("Carrera terminada", color = c.volt)
                    H1(if (name.isNotEmpty()) "¡Enhorabuena, $name!" else "¡Enhorabuena!", size = 32.sp, color = c.onBar)
                    Txt(ce.text, size = 16.sp, color = c.onBarMuted)
                }
                if (ce.badges.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                    ce.badges.take(3).forEachIndexed { i, b ->
                        var on by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(900L + i * 200L); on = true }
                        Box(Modifier.size(92.dp, 110.dp)) { if (on) Medal(b, 92.dp, pop = true, shine = true) }
                    }
                }
                Num(fmtKm(r.dist), 96.sp, unit = "km", unitSize = 24.sp, unitWeight = FontWeight.SemiBold, color = c.volt, spacing = -0.05)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Tiempo", fmtTime(r.dur), 24.sp); Stat("Ritmo", app.pace(r.paceSec()), 24.sp); Stat("Semana", ce.week, 24.sp) }
                // Por qué volver: la serie y lo siguiente.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x14F4F5F0)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).background(c.volt), contentAlignment = Alignment.Center) { Num(max(1, w.streak).toString(), 22.sp, color = c.onVolt) }
                        Column(Modifier.weight(1f)) {
                            Txt(if (w.count >= w.goal) "Semana cumplida" else "${w.count} de ${w.goal} esta semana", size = 16.sp, weight = FontWeight.SemiBold, color = c.onBar)
                            Sub(if (w.count >= w.goal) "Tu serie va por ${max(1, w.streak)} ${pl(max(1, w.streak), "semana")}. La próxima, a por otra." else "Te falta${if (w.goal - w.count == 1) "" else "n"} ${w.goal - w.count} para mantener tu serie.", color = c.onBarMuted)
                        }
                    }
                    // Pastillas de la semana: llenas = carreras hechas (la última, la de hoy, en volt).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(max(w.goal, w.count)) { i -> Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(when { i == w.count - 1 -> c.volt; i < w.count -> c.onBar; else -> Color(0x33F4F5F0) })) }
                    }
                    (plan as? PlanCard.Week)?.title?.let { t -> Sub("Lo próximo: ${(plan as PlanCard.Week).chip?.let { "$it · " } ?: ""}$t", color = c.onBar) }
                }
                Btn("Ver mi carrera", { Nav.replace(SUMMARY, run = r.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Compartir", {
                        scope.launch {
                            val a = Repo.app(); val png = withContext(Dispatchers.Default) { Images.run(ctx, a, r) }
                            Share.file(ctx, "zancada-${isoMs(r.start).take(10)}.png", "image/png", png, "He corrido ${fmtKm(r.dist)} km en ${fmtTime(r.dur)} (${a.pace(r.paceSec())} /km) con Zancada 🏃")
                        }
                    }, Modifier.weight(1f), BtnKind.Ghost, color = c.onBar)
                    Btn("Estirar 5 min", { St.stretchWarm = false; St.modal = "stretch" }, Modifier.weight(1f), BtnKind.Ghost, color = c.onBar)
                }
            }
        }
    }
}

/** El recorrido de la carrera dibujado con el trazo del logo; sin GPS (carrera a mano), el propio logo. */
@Composable
private fun RouteMark(r: Run, t: Float, modifier: Modifier) {
    val c = Z.c
    val pts = r.points
    if (pts.size < 2) { Box(modifier, contentAlignment = Alignment.Center) { Canvas(Modifier.size(150.dp * (MARK_W / MARK_H), 150.dp)) { drawZMark(c.volt, z = t, a = min(1f, t * 4), b = if (t > 0.92f) (t - 0.92f) / 0.08f else 0f) } }; return }
    Canvas(modifier) {
        val k = cos(Math.toRadians(pts[0].lat)); val xs = pts.map { it.lon * k }; val ys = pts.map { -it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val span = max(maxX - minX, maxY - minY).let { if (it == 0.0) 1e-6 else it }
        val sw = 14.dp.toPx(); val pad = sw * 2.4f
        val s = min((size.width - 2 * pad) / max(1e-9, maxX - minX), (size.height - 2 * pad) / max(1e-9, maxY - minY)).let { if (it.isInfinite()) (size.height - 2 * pad) / span else it }
        val ox = (size.width - (maxX - minX) * s) / 2; val oy = (size.height - (maxY - minY) * s) / 2
        fun P(i: Int) = Offset(((xs[i] - minX) * s + ox).toFloat(), ((ys[i] - minY) * s + oy).toFloat())
        val path = Path().apply { pts.indices.forEach { i -> val p = P(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val pm = PathMeasure().apply { setPath(path, false) }
        val part = Path(); pm.getSegment(0f, pm.length * t, part, true)
        drawPath(part, c.volt, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Pastillas A (hueca) y B (maciza), del tamaño del trazo, como en el logo.
        val pl = sw * 2f; val ph = sw
        val a = P(0)
        drawRoundRect(c.volt, Offset(a.x - pl / 2 + ph * 0.14f, a.y - ph / 2 + ph * 0.14f), Size(pl - ph * 0.28f, ph - ph * 0.28f), CornerRadius(ph * 0.36f), style = Stroke(ph * 0.28f))
        if (t >= 0.98f) { val b = P(pts.size - 1); drawRoundRect(c.volt, Offset(b.x - pl / 2, b.y - ph / 2), Size(pl, ph), CornerRadius(ph / 2)) }
    }
}

/** Confeti de la marca: pastillas volt y blancas que caen y giran durante unos segundos. */
@Composable
private fun Confetti(modifier: Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(1300); t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val bits = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextInt(3)) } }
    if (t.value <= 0f || t.value >= 1f) return
    Canvas(modifier) {
        bits.forEachIndexed { i, (x0, d, kind) ->
            val p = (t.value * 1.35f - d * 0.35f).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEachIndexed
            val x = size.width * x0 + kotlin.math.sin((p * 6 + i).toDouble()).toFloat() * 18.dp.toPx()
            val y = -30f + (size.height + 60f) * p
            val w = 16.dp.toPx(); val h = 8.dp.toPx()
            val col = when (kind) { 0 -> Volt; 1 -> Color(0xFFF4F5F0); else -> Volt.copy(alpha = 0.6f) }
            rotate(p * 540f + i * 37f, Offset(x, y)) {
                if (kind == 2) drawRoundRect(col, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(h / 2), style = Stroke(h * 0.3f))
                else drawRoundRect(col, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(h / 2))
            }
        }
    }
}
