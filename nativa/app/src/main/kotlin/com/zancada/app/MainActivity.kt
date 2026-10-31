package com.zancada.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zancada.core.Run
import com.zancada.core.fmtKm
import com.zancada.core.fmtPace
import com.zancada.core.fmtTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sistema "Volt": tinta + un único acento lima.
val Ink = Color(0xFF0B0C0B)
val Card = Color(0xFF1A1C1A)
val Volt = Color(0xFFD4FF3A)
val Paper = Color(0xFFF4F5F0)
val Muted = Color(0xFF9A9D95)

class MainActivity : ComponentActivity() {
    private var afterPermission: (() -> Unit)? = null
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true) afterPermission?.invoke()
        else RunBus.state.value = RunUi(gpsError = "Sin permiso de ubicación no puedo grabar la carrera.")
    }

    private fun withLocation(then: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { then(); return }
        afterPermission = then
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        askPerms.launch(perms.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = RunStore(this)
        setContent {
            val ui by RunBus.state.collectAsStateWithLifecycle()
            var runs by remember { mutableStateOf(store.load()) }
            var counting by remember { mutableStateOf(false) }
            LaunchedEffect(ui.phase) { if (ui.phase == Phase.DONE || ui.phase == Phase.IDLE) runs = store.load() }
            Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
                when {
                    counting -> Countdown { counting = false; RunService.send(this@MainActivity, RunService.START) }
                    ui.phase == Phase.RUNNING -> RunScreen(ui,
                        pause = { RunService.send(this@MainActivity, RunService.PAUSE) },
                        resume = { RunService.send(this@MainActivity, RunService.RESUME) },
                        finish = { RunService.send(this@MainActivity, RunService.FINISH) },
                        saveShort = { RunService.send(this@MainActivity, RunService.SAVE_SHORT) },
                        discard = { RunService.send(this@MainActivity, RunService.DISCARD) })
                    ui.phase == Phase.PREP -> PrepScreen(ui, start = { counting = true }, back = { RunService.send(this@MainActivity, RunService.STOP) })
                    ui.phase == Phase.DONE && ui.result != null -> DoneScreen(ui.result!!) { RunBus.state.value = RunUi() }
                    else -> HomeScreen(runs, ui.gpsError,
                        run = { withLocation { RunService.send(this@MainActivity, RunService.PREP) } },
                        battery = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                        open = { r -> RunBus.state.value = RunUi(phase = Phase.DONE, result = r) })
                }
            }
        }
    }
}

@Composable
fun Big(text: String, size: Int = 96, color: Color = Paper) = Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)

@Composable
fun Label(text: String) = Text(text.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)

@Composable
fun Btn(text: String, primary: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(60.dp).clip(RoundedCornerShape(30.dp)).background(if (primary) Volt else Card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (primary) Ink else Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HomeScreen(runs: List<Run>, error: String?, run: () -> Unit, battery: () -> Unit, open: (Run) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Zancada", color = Paper, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Versión nativa · fase 1", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        val week = runs.filter { it.start > System.currentTimeMillis() - 7 * 864e5.toLong() }.sumOf { it.dist }
        Label("Últimos 7 días"); Big(fmtKm(week), 64); Text("km", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (error != null) { Text(error, color = Volt, fontSize = 15.sp); Spacer(Modifier.height(12.dp)) }
        Btn("Correr", modifier = Modifier.fillMaxWidth(), onClick = run)
        Spacer(Modifier.height(10.dp))
        Text("Si la carrera se corta con la pantalla apagada, toca aquí y quita la optimización de batería a «Zancada N».", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = battery).padding(vertical = 6.dp))
        Spacer(Modifier.height(16.dp))
        Label("Tus carreras")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(runs, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable { open(r) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(SimpleDateFormat("EEE d MMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(r.start)), color = Muted, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${fmtKm(r.dist)} km", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${fmtTime(r.dur)} · ${fmtPace(r.dur / (r.dist / 1000))}/km", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            if (runs.isEmpty()) item { Text("Aún no hay carreras en la versión nativa.", color = Muted, modifier = Modifier.padding(vertical = 12.dp)) }
        }
    }
}

@Composable
fun PrepScreen(ui: RunUi, start: () -> Unit, back: () -> Unit) {
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val acc = ui.acc
        val good = acc != null && acc <= 25
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (good) Volt else if (acc != null) Color(0xFFFFB020) else Muted))
        Spacer(Modifier.height(12.dp))
        Text(if (acc == null) "Buscando GPS…" else if (good) "GPS listo · ${Math.round(acc)} m" else "Afinando GPS · ${Math.round(acc)} m", color = Paper, fontSize = 20.sp)
        Text(if (acc == null) "Sal a cielo abierto y espera unos segundos." else if (good) "Cuando quieras." else "Puedes empezar igual; el principio puede salir impreciso.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Btn("Empezar", modifier = Modifier.fillMaxWidth(), onClick = { if (acc != null) start() })
        Spacer(Modifier.height(10.dp))
        Btn("Volver", primary = false, modifier = Modifier.fillMaxWidth(), onClick = back)
        Spacer(Modifier.height(20.dp))
        Text("Puedes bloquear el móvil: la carrera sigue grabando y verás la notificación de Zancada.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Countdown(done: () -> Unit) {
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) { while (n > 0) { delay(1000); n-- }; done() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Big(if (n > 0) "$n" else "¡Ya!", 160, Volt) }
}

@Composable
fun RunScreen(ui: RunUi, pause: () -> Unit, resume: () -> Unit, finish: () -> Unit, saveShort: () -> Unit, discard: () -> Unit) {
    BackHandler { }   // el botón atrás no corta la carrera
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (ui.autoPaused) "Autopausa · muévete para seguir" else if (ui.paused) "En pausa" else "Corriendo", color = if (ui.paused) Volt else Muted, fontSize = 15.sp)
        ui.acc?.let { Text("±${Math.round(it)} m", color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.weight(1f))
        Label("Distancia"); Big(fmtKm(ui.dist), 110); Text("km", color = Muted)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Tiempo", fmtTime(ui.elapsed))
            Stat("Ritmo", if (ui.paused || !ui.pace.isFinite()) (if (ui.dist > 20) fmtPace(ui.elapsed / (ui.dist / 1000)) else "–:––") else fmtPace(ui.pace))
            Stat("Medio", if (ui.avg.isFinite()) fmtPace(ui.avg) else "–:––")
        }
        Spacer(Modifier.weight(1f))
        if (ui.tooShort != null) {
            Text("Has recorrido ${Math.round(ui.tooShort)} m. ¿La guardo igualmente?", color = Paper, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Guardar", modifier = Modifier.weight(1f), onClick = saveShort); Btn("Descartar", primary = false, modifier = Modifier.weight(1f), onClick = discard)
            }
        } else if (ui.paused) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Seguir", modifier = Modifier.weight(1f), onClick = resume)
                HoldBtn("Mantén para terminar", Modifier.weight(1f), finish)
            }
        } else {
            Btn("Pausa", primary = false, modifier = Modifier.fillMaxWidth(), onClick = pause)
            Spacer(Modifier.height(10.dp))
            HoldBtn("Mantén pulsado para terminar", Modifier.fillMaxWidth(), finish)
        }
    }
}

@Composable
fun Stat(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Label(label); Text(value, color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold) }

/** Terminar exige mantener pulsado 1 s: así no se acaba la carrera por un toque sin querer. */
@Composable
fun HoldBtn(text: String, modifier: Modifier, done: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (holding) { val t0 = System.currentTimeMillis(); while (holding) { progress = ((System.currentTimeMillis() - t0) / 1000f).coerceAtMost(1f); if (progress >= 1f) { holding = false; done(); break }; delay(16) } }
        progress = 0f
    }
    Box(modifier.height(60.dp).clip(RoundedCornerShape(30.dp)).background(Card).pointerInput(Unit) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) }) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Volt))
        Text(text, color = if (progress > 0.5f) Ink else Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun DoneScreen(r: Run, close: () -> Unit) {
    BackHandler(onBack = close)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text(r.name, color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(r.start)), color = Muted)
            Spacer(Modifier.height(20.dp))
            Label("Distancia"); Big(fmtKm(r.dist), 96, Volt); Text("km", color = Muted)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Tiempo", fmtTime(r.dur)); Stat("Ritmo", fmtPace(r.dur / (r.dist / 1000))); Stat("Desnivel", r.gain?.let { "+$it m" } ?: "–")
            }
            Spacer(Modifier.height(24.dp))
            if (r.splits.isNotEmpty()) Label("Parciales")
        }
        items(r.splits) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (s.partial) "Km ${s.km} (parcial)" else "Km ${s.km}", color = Paper, fontSize = 16.sp)
                Text("${fmtPace(s.split)} /km", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Spacer(Modifier.height(20.dp)); Btn("Listo", modifier = Modifier.fillMaxWidth(), onClick = close) }
    }
}
