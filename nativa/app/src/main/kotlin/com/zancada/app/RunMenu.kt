package com.zancada.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlin.math.max
import kotlin.math.min

// ---------- Tipos de mapa ----------

/** Miniaturas de los tipos de mapa para elegir (dibujadas, sin descargar nada). */
@Composable
fun MapTypeGrid(cur: String, onPick: (String) -> Unit) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MAP_TYPES.forEach { (k, name) ->
            val on = k == cur
            Column(Modifier.width(72.dp).clickable { onPick(k) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).border(if (on) 3.dp else 1.dp, if (on) c.volt else c.line, RoundedCornerShape(14.dp))) { thumb(k) }
                Txt(name, size = 12.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium, align = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

private fun DrawScope.thumb(k: String) {
    val w = size.width; val h = size.height
    when (k) {
        "sat", "hibrido" -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF2E4A2B), Color(0xFF1E3322), Color(0xFF4B5A3A))))
            drawCircle(Color(0xFF5E6E45), w * 0.22f, Offset(w * 0.28f, h * 0.3f))
            drawCircle(Color(0xFF233E2A), w * 0.3f, Offset(w * 0.8f, h * 0.75f))
            drawRect(Color(0xFF3E5C7A), Offset(0f, h * 0.78f), androidx.compose.ui.geometry.Size(w * 0.45f, h * 0.22f))
        }
        "relieve" -> {
            drawRect(Color(0xFFE6EBD3))
            for (i in 1..5) drawCircle(Color(0xFFB59B6A), w * 0.1f * i, Offset(w * 0.65f, h * 0.35f), style = Stroke(1.2f * density))
            drawRect(Color(0xFFA9CBE0), Offset(0f, h * 0.8f), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.2f))
        }
        "color" -> {
            drawRect(Color(0xFFF2EFE9))
            drawCircle(Color(0xFFC8E6B0), w * 0.28f, Offset(w * 0.25f, h * 0.25f))
            drawRect(Color(0xFFAAD3DF), Offset(0f, h * 0.78f), androidx.compose.ui.geometry.Size(w, h * 0.22f))
            drawLine(Color(0xFFF7C46B), Offset(0f, h * 0.55f), Offset(w, h * 0.45f), 5f * density)
        }
        else -> {
            drawRect(Color(0xFFE9EAE6))
            drawCircle(Color(0xFFDADDD6), w * 0.26f, Offset(w * 0.3f, h * 0.3f))
            drawLine(Color.White, Offset(0f, h * 0.55f), Offset(w, h * 0.45f), 5f * density)
        }
    }
    if (k == "hibrido" || k != "sat") {
        val road = if (k == "hibrido") Color(0xFFFFE082) else Color.White
        drawLine(road, Offset(w * 0.15f, 0f), Offset(w * 0.4f, h), 3f * density)
        drawLine(road, Offset(0f, h * 0.2f), Offset(w, h * 0.3f), 2f * density)
    }
    // un trazo de carrera encima, como en la app
    val p = Path().apply { moveTo(w * 0.2f, h * 0.85f); cubicTo(w * 0.35f, h * 0.4f, w * 0.6f, h * 0.9f, w * 0.85f, h * 0.2f) }
    drawPath(p, Ink, style = Stroke(6f * density, cap = StrokeCap.Round))
    drawPath(p, Volt, style = Stroke(3f * density, cap = StrokeCap.Round))
}

/** Hoja para elegir el tipo de mapa (rutas y repetición). */
@Composable
fun MapTypeSheet(cur: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    BottomSheet(onClose) {
        H2("Tipo de mapa")
        MapTypeGrid(cur) { onPick(it); onClose() }
    }
}

/** Hoja inferior sencilla con fondo oscurecido. */
@Composable
fun BottomSheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Z.c; val ins = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() }) {
        CompositionLocalProvider(LocalDark provides false) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.surface)
                    .clickable(remember { MutableInteractionSource() }, null) {}.verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = ins.calculateBottomPadding() + 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
                content()
            }
        }
    }
}

// ---------- En carrera: vista de solo datos ----------

/** Números grandes, como un reloj de carrera: tiempo, ritmo del km, distancia y parciales. */
@Composable
fun RunDataView(ui: RunUi, paceTxt: String, modifier: Modifier = Modifier) {
    val c = Z.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        BigNum(fmtTime(ui.elapsed), "Tiempo", if (ui.elapsed >= 3600) 76.sp else 92.sp)
        BigNum(if (ui.kmPace.isFinite()) fmtPace(ui.kmPace) else paceTxt, "Ritmo del km (/km)", 112.sp, c.volt)
        BigNum(fmtKm(ui.dist), "Distancia (km)", 92.sp)
        if (ui.hr != null || ui.cad != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.hr?.let { Stat("Pulso", "$it", 30.sp, unit = "ppm") }
            ui.cad?.let { Stat("Cadencia", "$it", 30.sp, unit = "ppm") }
        }
        SplitBars(ui.splits, ui.kmPace, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
}

@Composable
private fun BigNum(v: String, label: String, size: androidx.compose.ui.unit.TextUnit, color: Color = Z.c.onBar) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = numStyle(size).copy(color = color), maxLines = 1)
        Sub(label, size = 14.sp, color = Z.c.onBarMuted)
    }
}

/** Barras de parciales: las 4 últimas cerradas y la del km en curso. Más alta = más rápido. */
@Composable
fun SplitBars(splits: List<Double>, cur: Double, modifier: Modifier = Modifier) {
    val c = Z.c
    val shown = splits.takeLast(4).map { it to false } + listOf((if (cur.isFinite()) cur else Double.NaN) to true)
    val ok = shown.map { it.first }.filter { it.isFinite() && it > 0 }
    if (ok.isEmpty()) { Sub("Los parciales salen al terminar el primer km", size = 13.sp, color = c.onBarMuted); return }
    val fast = ok.min(); val slow = ok.max()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            shown.forEach { (s, live) ->
                val frac = if (!s.isFinite() || s <= 0) 0.15f else if (slow - fast < 1) 0.8f else (0.35f + 0.6f * ((slow - s) / (slow - fast))).toFloat()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (s.isFinite() && s > 0) fmtPace(s) else "–", style = numStyle(13.sp, 0.0).copy(color = c.onBar))
                    Box(Modifier.fillMaxWidth().height((80 * min(1f, max(0.1f, frac))).dp).clip(RoundedCornerShape(10.dp))
                        .background(if (live) c.volt else c.onBarMuted.copy(alpha = 0.45f)))
                }
            }
        }
        Sub("Parciales (/km)", size = 13.sp, color = c.onBarMuted)
    }
}

// ---------- En carrera: menú ----------

/** Panel de la carrera: compartir ubicación, cargar ruta, pulsómetro, tipo de mapa y ajustes. */
@Composable
fun RunMenu(ui: RunUi, start: String = "main", onClose: () -> Unit) {
    val c = Z.c; val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    val st = data.settings
    var page by remember { mutableStateOf(start) }
    BackHandler { if (page != "main" && start == "main") page = "main" else onClose() }
    BottomSheet(onClose) {
        if (page != "main") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBtn(Ico.back2, { page = "main" }, size = 36.dp, bg = c.surface2, iconSize = 18.dp)
            H2(when (page) { "route" -> "Cargar ruta"; "sensor" -> "Añadir sensor"; "map" -> "Tipo de mapa"; else -> "Ajustes de la carrera" })
        }
        when (page) {
            "main" -> {
                MenuRow(Ico.pin, "Compartir mi ubicación", if (ui.sos) "Modo seguridad activo: tu contacto ya recibe SMS" else "Manda dónde estás por WhatsApp, SMS…") {
                    val f = ui.fix
                    if (f == null) toast("Aún no tengo tu posición") else Share.text(ctx, "Estoy corriendo con Zancada: llevo ${fmtKm(ui.dist)} km en ${fmtTime(ui.elapsed)}. Estoy aquí: ${Sos.mapUrl(f.lat, f.lon)}", "Mi ubicación")
                }
                if (!ui.live.guideOn) MenuRow(Ico.routes, "Cargar ruta", RunBus.config.route?.name ?: "Sigue una ruta guardada o recomendada") { page = "route" }
                MenuRow(Ico.heart, "Añadir sensor", st.hrName?.let { "Pulsómetro: $it" } ?: "Pulsómetro por Bluetooth") { page = "sensor" }
                MenuRow(Ico.layers, "Tipo de mapa", MAP_TYPES.firstOrNull { it.first == (st.runMap ?: "calles") }?.second ?: "Claro") { page = "map" }
                MenuRow(Ico.sliders, "Ajustes", "Avisos de voz, autopausa y vibración") { page = "settings" }
            }
            "route" -> RoutePick(ui) { r ->
                RunBus.loadRoute = r; RunService.send(ctx, RunService.LOAD_ROUTE); toast("Ruta cargada: ${r.name}"); onClose()
            }
            "sensor" -> HrCard { RunService.send(ctx, RunService.HR_ON); toast("Conectando con el pulsómetro…") }
            "map" -> MapTypeGrid(st.runMap ?: "calles") { k -> Repo.settings { it.copy(runMap = k) } }
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelRow("Avisos de voz", listOf("1" to "Cada km", "half" to "Cada 500 m", "min5" to "Cada 5 min", "0" to "Sin voz"), st.voice) { v -> Repo.settings { it.copy(voice = v) } }
                SelRow("Autopausa al pararte", listOf("1" to "Sí", "0" to "No"), st.autopause) { v -> Repo.settings { it.copy(autopause = v) }; RunService.send(ctx, RunService.SETTINGS) }
                SelRow("Vibrar en los avisos", listOf("1" to "Sí", "0" to "No"), st.vibrate ?: "1") { v -> Repo.settings { it.copy(vibrate = v) } }
                SelRow("Avisos de ritmo en sesiones", listOf("1" to "Sí", "0" to "No"), st.pacealert ?: "1") { v -> Repo.settings { it.copy(pacealert = v) } }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Z.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.ink)
        Column(Modifier.weight(1f)) { Txt(title, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1); Sub(sub, size = 12.sp, maxLines = 2) }
        Icon(Ico.chevron, null, Modifier.size(18.dp), tint = Z.muted)
    }
}

/** Rutas para cargar en mitad de la carrera: las tuyas y las recomendadas cerca, por cercanía a su salida. */
@Composable
private fun RoutePick(ui: RunUi, onPick: (Route) -> Unit) {
    val app = remember { Repo.app() }
    val here = ui.fix
    val list = remember(here == null) {
        val all = app.myRoutes() + app.zr.filter { here == null || hav(here, it.startLL()) < 15000 }
        all.distinctBy { it.id }.sortedBy { r -> here?.let { hav(it, r.startLL()) } ?: 0.0 }.take(15)
    }
    if (list.isEmpty()) Sub("No hay rutas guardadas ni recomendadas cerca. Guarda una en Rutas para poder cargarla.")
    list.forEach { r ->
        val away = here?.let { hav(it, r.startLL()) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Z.c.surface2).clickable { onPick(r) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(r.name, weight = FontWeight.SemiBold, maxLines = 1)
                Sub("${commaNum(r.km)} km" + (away?.let { " · salida a ${fmtM(it)}" } ?: ""), size = 12.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(18.dp), tint = Z.muted)
        }
    }
}

/** Botón redondo flotante sobre el mapa. */
@Composable
fun MapFab(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconBtn(icon, onClick, modifier.shadow6(), size = 44.dp, bg = Z.c.surface, tint = Z.c.ink, iconSize = 20.dp)

private fun Modifier.shadow6() = this.then(Modifier.clip(CircleShape))
