package com.zancada.app

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val APP_VER = "Zancada nativa 1.0"
const val APP_DATE = "31 oct 2026"

@Composable
fun ProfileScreen() {
    val c = Z.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val data by Repo.data.collectAsState()
    val app = remember(data) { Repo.app() }
    val s = data.settings
    val w = app.weekStats()
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val txt = withContext(Dispatchers.IO) { Share.readText(ctx, uri) }
            if (txt == null) { toast("No he podido leer ese archivo", 4000); return@launch }
            runCatching { Store.import(Repo.data.value, txt) }.onSuccess { im ->
                Repo.update { im.data }; Repo.mutate { reviewPlan() }; Repo.saveNow(); Reminders.sync(ctx)
                toast("Copia importada: ${im.added} ${pl(im.added, "carrera nueva", "carreras nuevas")}${if (im.had > 0) " · ${im.had} ya estaba${if (im.had == 1) "" else "n"}" else ""}", 4000)
            }.onFailure { toast("No he podido leer esa copia: ${it.message}", 4000) }
        }
    }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var ok = 0; var dup = 0; var bad = 0
            val texts = withContext(Dispatchers.IO) { uris.map { Share.readText(ctx, it) } }
            Repo.mutate {
                texts.forEach { t ->
                    val r = t?.let { runCatching { parseGpx(it) }.getOrNull() }
                    if (r == null) bad++ else if (runs.any { kotlin.math.abs(it.start - r.start) < 60_000 }) dup++ else { this.data = this.data.copy(runs = (this.data.runs + r).sortedByDescending { it.start }); ok++ }
                }
                reviewPlan()
            }
            toast("$ok ${pl(ok, "carrera importada", "carreras importadas")}${if (dup > 0) " · $dup ya estaba${if (dup == 1) "" else "n"}" else ""}${if (bad > 0) " · $bad sin leer" else ""}", 4000)
            if (ok > 0) Nav.go(HISTORY)
        }
    }
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(sure) { if (sure) { delay(3000); sure = false } }

    ScreenCol(gap = 18.dp) {
        H1("Perfil")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.bar), contentAlignment = Alignment.Center) { Num(data.profile.name.ifEmpty { "Z" }.take(1).uppercase(), 28.sp, color = c.volt, spacing = 0.0) }
            Column { Num(data.profile.name.ifEmpty { "Tú" }, 26.sp); Sub("Objetivo: ${app.goalOf()} carreras a la semana · ${w.streak} sem seguidas") }
        }
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
            val total = data.runs.sumOf { it.dist }; val yr = app.es.z(app.now()).year
            val y = data.runs.filter { app.es.z(it.start).year == yr }.sumOf { it.dist }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("Carreras", data.runs.size.toString(), 26.sp); Stat("Total", jsRound(total / 1000).toString(), 26.sp, unit = "km"); Stat("Este año", jsRound(y / 1000).toString(), 26.sp, unit = "km") }
        }
        // Mejores marcas
        ZCard(pad = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp), gap = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { H2("Mejores marcas"); Sub("en esta app") }
            val marks = app.marks(); val newest = marks.filter { it.best.isFinite() }.maxByOrNull { it.`when` ?: 0 }
            marks.forEachIndexed { i, x ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { Txt(x.name, weight = FontWeight.SemiBold); Sub(if (x.best.isFinite()) app.es.dayMonYear(x.`when`!!) else "Aún sin marca", size = 12.sp) }
                    if (newest === x && data.runs.size > 1) Chip("Nueva", size = 9.sp)
                    Num(if (x.best.isFinite()) fmtTime(x.best) else "—", 22.sp, color = if (x.best.isFinite()) Z.text else Z.muted)
                }
            }
        }
        // Insignias
        val B = app.badges(); val got = B.filter { it.`when` > 0 }
        val seen = remember { data.badgeSeen }
        LaunchedEffect(got.size) { if (got.map { it.id }.toSet() != data.badgeSeen.toSet()) Repo.update { it.copy(badgeSeen = got.map { b -> b.id }) } }
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 12.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Insignias"); Sub("${got.size} de ${B.size}") }
            val order = got + B.filter { it.`when` == 0L }
            order.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        val new = b.`when` > 0 && b.id !in seen
                        Box(Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface2).clickable { St.medal = b; St.modal = "medal"; if (b.`when` > 0) Voice.buzz(longArrayOf(0, 30, 60, 30)) }
                                .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Medal(b, 58.dp, pop = new)
                                if (b.`when` > 0) { Txt(b.name, size = 11.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 1.25); Sub(app.es.dayMon(b.`when`), size = 11.sp, align = TextAlign.Center) }
                                else Sub(b.name, size = 11.sp, align = TextAlign.Center)
                            }
                            if (new) Chip("Nueva", size = 9.sp, modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-6).dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // Ajustes
        ZCard(gap = 4.dp) {
            H2("Ajustes", Modifier.padding(bottom = 6.dp))
            SelRow("Objetivo de km al mes", listOf("0" to "Sin objetivo") + listOf(30, 50, 80, 100, 150, 200).map { "$it" to "$it km" }, s.monthkm) { v -> Repo.settings { it.copy(monthkm = v) } }
            SelRow("Avisos de ritmo en sesiones", listOf("1" to "Sí", "0" to "No"), s.pacealert ?: "1") { v -> Repo.settings { it.copy(pacealert = v) } }
            SelRow("Vibrar en los avisos", listOf("1" to "Sí", "0" to "No"), s.vibrate ?: "1") { v -> Repo.settings { it.copy(vibrate = v) } }
            SelRow("Avisos de voz", listOf("1" to "Cada km", "half" to "Cada 500 m", "min5" to "Cada 5 min", "0" to "Sin voz"), s.voice) { v -> Repo.settings { it.copy(voice = v) } }
            SelRow("Texto más grande", listOf("0" to "Normal", "1" to "Grande"), s.bigtext) { v -> Repo.settings { it.copy(bigtext = v) } }
            SelRow("Modo noche", listOf("auto" to "Automático", "on" to "Siempre", "off" to "Nunca"), s.night) { v -> Repo.settings { it.copy(night = v) } }
            SelRow("Mapa del resumen", listOf("calles" to "Calles", "sat" to "Satélite"), s.map) { v -> Repo.settings { it.copy(map = v) } }
            SelRow("Autopausa al pararte", listOf("1" to "Sí", "0" to "No"), s.autopause) { v -> Repo.settings { it.copy(autopause = v) } }
        }
        ShoesCard(app)
        SosCard()
        HcCard()
        // Tus datos
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
            H2("Tus datos")
            Sub("Todo se guarda solo en este móvil. Haz una copia de vez en cuando y guárdala en Drive o Telegram; con ella recuperas carreras, plan y rutas en otro teléfono.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Exportar copia", { val a = Repo.app(); Share.file(ctx, "zancada-copia-${isoMs(a.now()).take(10)}.json", "application/json", Store.backup(a.data, a.now()).toByteArray(), "Copia de seguridad de Zancada") }, Modifier.weight(1f), BtnKind.Sec, 46.dp, 14.sp)
                Btn("CSV", {
                    val a = Repo.app(); if (a.runs.isEmpty()) toast("Aún no hay carreras que exportar")
                    else Share.file(ctx, "zancada-carreras-${isoMs(a.now()).take(10)}.csv", "text/csv", a.runsCsv().toByteArray(), "Carreras de Zancada")
                }, kind = BtnKind.Sec, height = 46.dp, size = 14.sp, fill = false)
                Btn("Importar copia", { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, Modifier.weight(1f), BtnKind.Sec, 46.dp, 14.sp)
            }
            Btn("Importar carreras GPX (Strava, Garmin…)", { gpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) }, kind = BtnKind.Ghost)
        }
        // Clubs se oculta hasta que Zancada tenga cuentas (ClubsScreen se queda para entonces).
        Btn("Cambiar mi nombre u objetivo", { Nav.go(WELCOME) }, kind = BtnKind.Sec)
        Btn("Cargar una carrera de ejemplo", { Repo.mutate { addRun(demoRun()) }; toast("Carrera de ejemplo añadida"); Nav.go(HOME) }, kind = BtnKind.Ghost)
        ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Zancada"); Sub(APP_VER, size = 12.sp) }
            Sub("Estás probando la app nativa. Si algo falla o se te ocurre algo, cuéntalo: cada aviso mejora la app.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Invitar a un amigo", { Share.text(ctx, INVITE_TEXT, "Zancada") }, Modifier.weight(1f), BtnKind.Sec, 44.dp, 14.sp)
                Btn("Avisar de un fallo", {
                    val env = "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}".take(120)
                    val body = Uri.encode("**Qué ha pasado**\n\n\n**Qué esperaba**\n\n\n**Pasos**\n1. \n\n---\n$APP_VER · $env")
                    Share.open(ctx, "https://github.com/miguelas93/zancada/issues/new?title=${Uri.encode("[nativa] ")}&body=$body")
                }, Modifier.weight(1f), BtnKind.Sec, 44.dp, 14.sp)
            }
            Underline("Ver las novedades de cada versión", { Share.open(ctx, "https://github.com/miguelas93/zancada/blob/main/LEEME.md#novedades") }, Modifier.fillMaxWidth(), size = 12.sp)
        }
        Btn(if (sure) "Pulsa otra vez para confirmar" else "Borrar todos los datos de este móvil", {
            if (!sure) { sure = true; return@Btn }
            Repo.update { it.copy(runs = emptyList(), plan = null, savedRoutes = emptyList(), shoes = emptyList()) }; Reminders.sync(ctx); toast("Datos borrados"); Nav.go(HOME)
        }, kind = BtnKind.Ghost, color = c.danger)
        Sub("$APP_VER · $APP_DATE", size = 12.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Health Connect: guardar cada carrera como ejercicio para Google Fit, Samsung Health… */
@Composable
private fun HcCard() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val st = Repo.data.collectAsState().value.settings
    val avail = remember { Hc.available(ctx) }
    fun enable() {
        Repo.settings { it.copy(hc = "1") }
        Hc.push(ctx) { n -> scope.launch { toast(if (n > 0) "Enviadas $n ${pl(n, "carrera", "carreras")} a Health Connect" else "Health Connect activado", 3500) } }
    }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got: Set<String> ->
        if (got.containsAll(Hc.PERMS)) enable() else toast("Sin permiso de Health Connect", 3000)
    }
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            H2("Health Connect"); if (st.hc == "1") Chip("Activado")
        }
        Sub(if (avail) "Guarda cada carrera (hora, duración y distancia) en Health Connect para verla en Google Fit, Samsung Health, Fitbit… Zancada solo escribe: no lee datos de tu salud."
            else "Este móvil no tiene Health Connect instalado o actualizado. Instálalo desde Google Play para enviar ahí tus carreras.")
        if (avail) {
            if (st.hc == "1") {
                Btn("Enviar todas mis carreras", { Hc.push(ctx, all = true) { n -> scope.launch { toast(if (n > 0) "Enviadas $n ${pl(n, "carrera", "carreras")}" else "Ya estaban todas", 3000) } } }, kind = BtnKind.Sec, height = 44.dp, size = 14.sp)
                Btn("Desactivar", { Repo.settings { it.copy(hc = "0") }; toast("Health Connect desactivado") }, kind = BtnKind.Ghost, height = 40.dp, size = 13.sp)
            } else Btn("Activar Health Connect", {
                scope.launch { if (Hc.granted(ctx)) enable() else ask.launch(Hc.PERMS) }
            }, height = 46.dp, size = 15.sp)
        } else Btn("Abrir Health Connect en Google Play", { Share.open(ctx, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata") }, kind = BtnKind.Sec, height = 44.dp, size = 14.sp)
    }
}

/** Modo seguridad: un contacto recibe tu ubicación por SMS mientras corres. */
@Composable
private fun SosCard() {
    val st = Repo.data.collectAsState().value.settings
    var phone by remember { mutableStateOf(st.sosPhone ?: "") }
    var name by remember { mutableStateOf(st.sosName ?: "") }
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 8.dp) {
        H2("Modo seguridad")
        Sub("Al empezar a correr, Zancada manda un SMS a tu contacto con tu ubicación, otro cada pocos minutos, uno si te quedas parado mucho rato y otro al terminar. Los SMS los paga tu tarifa como cualquier otro.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "Nombre (Ana)", Modifier.weight(1f), maxLen = 20)
            Field(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } }, "Teléfono", Modifier.weight(1.3f), numeric = true, maxLen = 18)
        }
        SelRow("Avisar cada", listOf("5" to "5 min", "10" to "10 min", "15" to "15 min", "30" to "30 min"), "${st.sosEvery ?: 10}") { v -> Repo.settings { it.copy(sosEvery = v.toInt()) } }
        if (st.sosOn == "1") {
            Btn("Guardar contacto", {
                if (!Sos.validPhone(phone)) { toast("Revisa el número de teléfono"); return@Btn }
                Repo.settings { it.copy(sosPhone = Sos.cleanPhone(phone), sosName = safeText(name, 20)) }; toast("Contacto guardado")
            }, kind = BtnKind.Sec, height = 44.dp, size = 14.sp)
            Btn("Desactivar modo seguridad", { Repo.settings { it.copy(sosOn = "0") }; toast("Modo seguridad desactivado") }, kind = BtnKind.Ghost, height = 40.dp, size = 13.sp)
        } else Btn("Activar modo seguridad", {
            if (!Sos.validPhone(phone)) { toast("Escribe el teléfono de tu contacto"); return@Btn }
            Perm.sms { ok ->
                if (!ok) { toast("Sin permiso para enviar SMS: actívalo en Ajustes del móvil", 3500); return@sms }
                Repo.settings { it.copy(sosOn = "1", sosPhone = Sos.cleanPhone(phone), sosName = safeText(name, 20)) }
                toast("Modo seguridad activado")
            }
        }, height = 46.dp, size = 15.sp)
    }
}

@Composable
private fun ShoesCard(app: App) {
    val c = Z.c; val s = app.settings
    var name by remember { mutableStateOf("") }; var km0 by remember { mutableStateOf("") }
    ZCard(pad = PaddingValues(horizontal = 20.dp, vertical = 18.dp), gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2("Zapatillas"); Sub("km por par") }
        if (app.shoes.isEmpty()) Sub("Añade tus zapatillas y cada carrera sumará km al par en uso. A los 700 km te aviso.")
        app.shoes.forEach { sh ->
            val km = app.shoeKm(sh); val on = s.shoe == sh.id
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) c.surface2 else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { Repo.settings { it.copy(shoe = if (on) null else sh.id) } }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(sh.name, Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold, maxLines = 1)
                        if (on) Chip("En uso"); if (km >= SHOE_LIMIT) Chip("Toca cambiar", "hard")
                    }
                    Num(jsRound(km).toString(), 18.sp, unit = "km")
                    IconBtn(Ico.close, { Repo.update { d -> d.copy(shoes = d.shoes.filter { it.id != sh.id }, settings = if (d.settings.shoe == sh.id) d.settings.copy(shoe = null) else d.settings) } }, size = 32.dp, bg = Color.Transparent, iconSize = 16.dp)
                }
                Track(min(1.0, km / SHOE_LIMIT), h = 4.dp, color = if (km >= SHOE_LIMIT) c.danger else c.volt)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(name, { name = it }, "Nuevo par (p. ej. Pegasus 41)", Modifier.weight(1f), maxLen = 40)
            Field(km0, { km0 = it.filter(Char::isDigit) }, "km ya", Modifier.width(76.dp), numeric = true, maxLen = 5)
            Btn("Añadir", {
                val n = safeText(name, 40); if (n.isEmpty()) return@Btn
                val sh = Shoe("s" + System.currentTimeMillis(), n, km0.toDoubleOrNull() ?: 0.0, System.currentTimeMillis())
                Repo.update { it.copy(shoes = listOf(sh) + it.shoes, settings = it.settings.copy(shoe = sh.id)) }
                name = ""; km0 = ""; toast("Par añadido y en uso")
            }, kind = BtnKind.Sec, height = 44.dp, size = 14.sp, fill = false)
        }
    }
}

private val METAL = mapOf(
    "b" to listOf(0xFFFBD3B0, 0xFFCD8450, 0xFF7E4420, 0xFF4A230C),
    "s" to listOf(0xFFFFFFFF, 0xFFB9C2CB, 0xFF6C7680, 0xFF2B333B),
    "g" to listOf(0xFFFFF4B0, 0xFFF4C531, 0xFFA3740A, 0xFF4F3600),
)
private val RIBBON = mapOf("b" to (0xFFE5533D to 0xFFA8301F), "s" to (0xFF3B82F6 to 0xFF1E4FA8), "g" to (0xFFD4FF3A to 0xFF0B0C0B))

/** La medalla de la web (medalSvg): cinta, disco metálico con brillo y el texto corto. Sin conseguir: contorno discontinuo. */
@Composable
fun Medal(b: Badge, size: Dp, pop: Boolean = false, shine: Boolean = false) {
    val c = Z.c; val tm = rememberTextMeasurer()
    val t = tierOf(b.id); val m = METAL[t]!!.map { Color(it) }; val (r1, r2) = RIBBON[t]!!.let { Color(it.first) to Color(it.second) }
    val fs = when { b.short.length <= 2 -> 26; b.short.length <= 3 -> 22; b.short.length <= 4 -> 18; else -> 14 }
    val sc = remember { Animatable(if (pop) 0.3f else 1f) }
    LaunchedEffect(pop) { if (pop) sc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val shineX = if (shine) rememberInfiniteTransition(label = "s").animateFloat(-40f, 140f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing)), label = "x").value else -100f
    Canvas(Modifier.size(size, size * 1.2f).scale(sc.value)) {
        val k = this.size.width / 100f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        if (b.`when` == 0L) {
            drawPath(Path().apply { moveTo(32 * k, 0f); lineTo(48 * k, 0f); lineTo(56 * k, 40 * k); lineTo(42 * k, 46 * k); close(); moveTo(68 * k, 0f); lineTo(52 * k, 0f); lineTo(44 * k, 40 * k); lineTo(58 * k, 46 * k); close() }, c.line)
            drawCircle(c.surface, 34 * k, o(50f, 78f))
            drawCircle(c.line, 34 * k, o(50f, 78f), style = Stroke(3 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * k, 5 * k))))
            medalText(tm, b.short, fs, k, c.muted, FontWeight.Bold)
            return@Canvas
        }
        drawPath(Path().apply { moveTo(30 * k, 0f); lineTo(48 * k, 0f); lineTo(56 * k, 42 * k); lineTo(40 * k, 49 * k); close() }, r1)
        drawPath(Path().apply { moveTo(70 * k, 0f); lineTo(52 * k, 0f); lineTo(44 * k, 42 * k); lineTo(60 * k, 49 * k); close() }, r2)
        drawPath(Path().apply { moveTo(39 * k, 0f); lineTo(43 * k, 0f); lineTo(50 * k, 40 * k); lineTo(46 * k, 42 * k); close() }, Color.White.copy(alpha = 0.35f))
        drawCircle(m[2], 36 * k, o(50f, 78f))
        drawCircle(Brush.linearGradient(0f to m[0], 0.45f to m[1], 1f to m[2], start = o(16f, 44f), end = o(84f, 112f)), 34 * k, o(50f, 78f))
        drawCircle(Brush.linearGradient(0f to m[0], 0.5f to m[2], 1f to m[1], start = o(24f, 104f), end = o(76f, 52f)), 26 * k, o(50f, 78f), style = Stroke(3 * k))
        repeat(12) { i -> val a = i * Math.PI / 6; drawCircle(m[0].copy(alpha = 0.8f), 1.3f * k, o((50 + cos(a) * 30.5).toFloat(), (78 + sin(a) * 30.5).toFloat())) }
        medalText(tm, b.short, fs, k, m[3], FontWeight.ExtraBold)
        rotate(-30f, o(38f, 62f)) { drawOval(Color.White.copy(alpha = 0.35f), o(24f, 54f), Size(28 * k, 16 * k)) }
        if (shine) clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(o(16f, 44f), Size(68 * k, 68 * k))) }) {
            withTransform({ translate(shineX * k, 0f) }) {
                drawPath(Path().apply { moveTo(0f, 30 * k); lineTo(14 * k, 30 * k); lineTo(-22 * k, 130 * k); lineTo(-36 * k, 130 * k); close() }, Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.medalText(tm: TextMeasurer, s: String, fs: Int, k: Float, color: Color, w: FontWeight) {
    val st = TextStyle(fontFamily = Body, fontWeight = w, fontSize = (fs * k / density).sp, color = color, letterSpacing = (-0.5 * k / density).sp)
    val l = tm.measure(s, st)
    drawText(l, topLeft = Offset(50 * k - l.size.width / 2f, 78 * k - l.size.height / 2f))
}
