package com.zancada.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zancada.core.*
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Una pantalla de la app (los mismos nombres que las secciones de la web). */
data class Scr(val id: String, val run: String? = null, val route: Route? = null)

const val HOME = "home"; const val HISTORY = "history"; const val ROUTES = "routes"; const val PROFILE = "profile"; const val CLUBS = "clubs"
const val WELCOME = "welcome"; const val ROUTE = "routeDetail"; const val PLAN = "plan"; const val PLANSETUP = "planSetup"; const val PREP = "prep"
const val COUNT = "count"; const val RUN = "run"; const val CELE = "cele"; const val SUMMARY = "summary"; const val HEAT = "heat"; const val REPLAY = "replay"; const val V3D = "view3d"; const val SPOT = "spot"; const val SETTINGS = "settings"
val TABS = listOf(HOME, HISTORY, ROUTES, PROFILE)
private val TRANSIENT = setOf(WELCOME, COUNT, RUN, CELE)
/** Pantallas oscuras (para el color de los iconos de la barra de estado). */
val DARK_SCREENS = setOf(WELCOME, PLANSETUP, COUNT, CELE, HEAT, REPLAY, RUN, V3D)

/** Navegación con el botón «atrás» de Android, como el historial de la web. */
object Nav {
    val stack = mutableStateListOf(Scr(HOME))
    val cur: Scr get() = stack.last()

    fun go(id: String, run: String? = null, route: Route? = null) {
        val s = Scr(id, run, route)
        if (id in TABS) { stack.clear(); stack.add(Scr(HOME)); if (id != HOME) stack.add(s); return }
        if (cur.id == id && id != ROUTE && id != SUMMARY) { stack[stack.size - 1] = s; return }
        stack.add(s)
    }
    /** Sustituye la pantalla actual (la cuenta atrás por la carrera, la carrera por la celebración…). */
    fun replace(id: String, run: String? = null, route: Route? = null) { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); stack.add(Scr(id, run, route)) }
    fun reset(vararg s: Scr) { stack.clear(); stack.addAll(s.toList().ifEmpty { listOf(Scr(HOME)) }) }

    /** Devuelve false si no queda nada atrás (la app se cierra). Nunca sale de una carrera en marcha. */
    fun back(): Boolean {
        if (cur.id == RUN || cur.id == COUNT) return true
        if (!Repo.data.value.profile.done) return true
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        while (stack.size > 1 && stack.last().id in TRANSIENT) stack.removeAt(stack.size - 1)
        if (stack.last().id in TRANSIENT) stack[0] = Scr(HOME)
        return true
    }
}

/** Estado de la interfaz que sobrevive al cambiar de pantalla (como las variables sueltas de la web). */
object St {
    var modal by mutableStateOf<String?>(null)
    var medal by mutableStateOf<Badge?>(null)
    var photos by mutableStateOf<List<Photo>>(emptyList()); var photoI by mutableStateOf(0)
    var stretchWarm by mutableStateOf(false)
    var manualPrefill by mutableStateOf<Pair<Long, String>?>(null)
    // Actividad
    var histTab by mutableStateOf("prog"); var histFilter by mutableStateOf("all"); var weeksView by mutableStateOf("w"); var histQ by mutableStateOf(""); var calOff by mutableStateOf(0)
    // Rutas
    var rMode by mutableStateOf("rec"); var rView by mutableStateOf("list"); var rZone by mutableStateOf<String?>(null)
    var rDist by mutableStateOf("all"); var rSurf by mutableStateOf("all"); var rSort by mutableStateOf("near"); var rMore by mutableStateOf(false)
    var rSearch by mutableStateOf(false); var rQ by mutableStateOf(""); var rSel by mutableStateOf(0); var rSheet by mutableStateOf(1); var rLayer by mutableStateOf("calles"); var rDiff by mutableStateOf("all")
    var rKm by mutableStateOf(5); var rSug by mutableStateOf<List<Route>>(emptyList()); var rSugBusy by mutableStateOf(false); var rSugErr by mutableStateOf<String?>(null)
    // «Pídesela a Zeta»
    var askQ by mutableStateOf(""); var askList by mutableStateOf<List<Route>>(emptyList()); var askBusy by mutableStateOf<String?>(null); var askErr by mutableStateOf<String?>(null)
    var askSpec by mutableStateOf<AskSpec?>(null); var askAuto by mutableStateOf(false); var askNote by mutableStateOf<String?>(null)
    // Sitios
    var spots by mutableStateOf<List<Spot>>(emptyList()); var spotsKey by mutableStateOf<String?>(null); var spotsBusy by mutableStateOf(false)
    var spot by mutableStateOf<Spot?>(null)
    // Preparar
    var prepMode by mutableStateOf<String?>(null)
    var pickedRoute by mutableStateOf<Route?>(null)
    var tramos by mutableStateOf<Sess?>(null)
    var goal by mutableStateOf("0")
    var light by mutableStateOf(false)
    // Plan
    var planView by mutableStateOf<Int?>(null)
    // Zeta
    var coachBusy by mutableStateOf(false); var coachWait by mutableStateOf<Int?>(null)
    // Aviso arriba
    var toast by mutableStateOf<Pair<Long, String>?>(null); var toastMs = 2600L
}

fun toast(msg: String, ms: Long = 2600) { St.toastMs = ms; St.toast = System.nanoTime() to msg }

/** Contenedor de pantalla con scroll: márgenes de la web (.screen, .withtabs). */
@Composable
fun ScreenCol(withTabs: Boolean = true, gap: Dp = 20.dp, pad: PaddingValues? = null, scroll: ScrollState = rememberScrollState(), bg: Color = Z.c.bg, content: @Composable ColumnScope.() -> Unit) {
    val ins = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier.fillMaxSize().background(bg).verticalScroll(scroll)
            .padding(pad ?: PaddingValues(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp, bottom = ins.calculateBottomPadding() + if (withTabs) 110.dp else 16.dp)),
        verticalArrangement = Arrangement.spacedBy(gap), content = content,
    )
}

/** Pantalla fija (sin scroll) con los márgenes de la web. */
@Composable
fun ScreenFix(bg: Color = Z.c.bg, gap: Dp = 18.dp, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ins = WindowInsets.systemBars.asPaddingValues()
    CompositionLocalProvider(LocalDark provides dark) {
        Column(Modifier.fillMaxSize().background(bg).imePadding().padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 14.dp, bottom = ins.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}

@Composable
fun ZRoot() {
    val data by Repo.data.collectAsState()
    val minute by Repo.minute.collectAsState()
    val sysDark = isSystemInDarkTheme()
    val night = remember(data.settings.night, minute, sysDark) { Repo.isNight(data.settings, sysDark) }
    val colors = if (night) NightColors else DayColors
    CompositionLocalProvider(LocalZ provides colors, LocalBig provides (data.settings.bigtext == "1"), LocalDark provides false) {
        val scr = Nav.cur
        Box(Modifier.fillMaxSize().background(colors.bg).swipeNav(scr.id)) {
            key(scr) {
                when (scr.id) {
                    WELCOME -> WelcomeScreen()
                    HOME -> HomeScreen()
                    HISTORY -> HistoryScreen()
                    CLUBS -> ClubsScreen()
                    PROFILE -> ProfileScreen()
                    SETTINGS -> SettingsScreen()
                    ROUTES -> RoutesScreen()
                    ROUTE -> scr.route?.let { RouteDetailScreen(it) }
                    PLAN -> PlanScreenUi()
                    PLANSETUP -> PlanSetupScreen()
                    PREP -> PrepScreen()
                    COUNT -> CountScreen()
                    RUN -> RunScreen()
                    CELE -> CeleScreen()
                    SUMMARY -> SummaryScreen(scr.run)
                    HEAT -> HeatScreen()
                    REPLAY -> ReplayScreen(scr.run)
                    V3D -> Map3dScreen(scr.route, scr.run)
                    SPOT -> SpotScreen()
                }
            }
            if (scr.id in TABS || scr.id == CLUBS) Tabs(scr.id, Modifier.align(Alignment.BottomCenter))
            val showZeta = data.profile.done && scr.id in setOf(PLAN, CLUBS)
            if (showZeta) ZetaFab(scr.id == PLAN, data.settings.zetaSeen == null, Modifier.align(Alignment.BottomEnd))
            Modals()
            ToastHost(Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Zonas que se deslizan de lado por sí mismas (carruseles): ahí el gesto no cambia de pestaña. */
object SwipeBlock { val rects = mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
fun Modifier.noSwipeNav(key: String): Modifier = composed {
    DisposableEffect(key) { onDispose { SwipeBlock.rects.remove(key) } }
    onGloballyPositioned { SwipeBlock.rects[key] = it.boundsInRoot() }
}

/** Deslizar entre pestañas (Inicio ↔ Actividad ↔ Rutas ↔ Perfil); hacia la derecha vuelve a Inicio desde Preparar y Resumen. */
private fun Modifier.swipeNav(cur: String) = pointerInput(cur) {
    if ((cur !in TABS && cur != CLUBS && cur != PREP && cur != SUMMARY) || cur == ROUTES) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val t0 = System.currentTimeMillis(); val start = down.position; var end = start; var multi = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size > 1) multi = true
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            end = ch.position
            if (!ch.pressed) break
        }
        if (multi || St.modal != null || SwipeBlock.rects.values.any { it.contains(start) }) return@awaitEachGesture
        val dx = (end.x - start.x) / density; val dy = (end.y - start.y) / density; val dt = System.currentTimeMillis() - t0
        if (abs(dx) < 60 || abs(dx) < 1.6 * abs(dy) || dt > 700) return@awaitEachGesture
        // En Preparar y Resumen el mapa ocupa la parte de arriba: solo cuenta el gesto en la hoja de abajo.
        if ((cur == PREP || cur == SUMMARY) && start.y < size.height * (if (cur == SUMMARY) 0.42f else 0.45f)) return@awaitEachGesture
        val i = TABS.indexOf(cur)
        if (i >= 0) { TABS.getOrNull(i + if (dx < 0) 1 else -1)?.let { Nav.go(it) }; return@awaitEachGesture }
        if (cur == CLUBS) { if (dx > 0) Nav.go(PROFILE); return@awaitEachGesture }
        if (dx > 0) Nav.go(HOME)
    }
}

@Composable
private fun Tabs(cur: String, modifier: Modifier) {
    val c = Z.c; val ins = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier.padding(start = 16.dp, end = 16.dp, bottom = ins.calculateBottomPadding() + 14.dp).fillMaxWidth().height(68.dp)
            .shadow(18.dp, RoundedCornerShape(34.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(RoundedCornerShape(34.dp)).background(c.bar).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable fun tab(id: String, label: String, ico: androidx.compose.ui.graphics.vector.ImageVector) {
            val on = cur == id
            Column(Modifier.width(58.dp).clip(RoundedCornerShape(14.dp)).clickable { Nav.go(id) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(ico, label, Modifier.size(22.dp), tint = if (on) c.onBar else c.onBarMuted)
                Text(label, style = bodyStyle(if (Z.big) 12.sp else 11.sp, if (on) FontWeight.SemiBold else FontWeight.Medium).copy(color = if (on) c.onBar else c.onBarMuted, lineHeight = 13.sp), maxLines = 1)
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) c.volt else Color.Transparent))
            }
        }
        tab(HOME, "Inicio", Ico.home); tab(HISTORY, "Actividad", Ico.history)
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.volt).clickable { Nav.go(PREP) }, contentAlignment = Alignment.Center) { Icon(Ico.play, "Correr", Modifier.size(20.dp), tint = c.onVolt) }
        tab(ROUTES, "Rutas", Ico.routes); tab(PROFILE, "Perfil", Ico.profile)
    }
}

@Composable
fun ZetaAv(size: Dp = 36.dp, font: Int = 20, ring: Boolean = false) {
    val c = Z.c
    // El avatar de Zeta es el símbolo de Zancada: la misma Z en todas partes.
    Box(Modifier.size(size).clip(CircleShape).background(c.volt).then(if (ring) Modifier.border(3.dp, c.bar, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        ZMark(size * 0.40f, Ink)
    }
}

@Composable
private fun ZetaFab(noTabs: Boolean, dot: Boolean, modifier: Modifier) {
    val ins = WindowInsets.navigationBars.asPaddingValues()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, modifier.padding(end = 18.dp, bottom = ins.calculateBottomPadding() + if (noTabs) 22.dp else 96.dp), enter = fadeIn() + slideInVertically { it / 2 }) {
        Box(Modifier.size(60.dp).shadow(14.dp, CircleShape).clickable(remember { MutableInteractionSource() }, null) { openCoach() }) {
            ZetaAv(60.dp, 32, ring = true)
            if (dot) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp).clip(CircleShape).background(Color(0xFFE5533D)).border(2.dp, Z.c.bg, CircleShape))
        }
    }
}

fun openCoach() { St.modal = "coach"; if (Repo.data.value.settings.zetaSeen == null) Repo.settings { it.copy(zetaSeen = 1) } }

@Composable
private fun ToastHost(modifier: Modifier) {
    val t = St.toast; val c = Z.c; val ins = WindowInsets.statusBars.asPaddingValues()
    var vis by remember { mutableStateOf(false) }
    LaunchedEffect(t) { if (t != null) { vis = true; delay(St.toastMs); vis = false } }
    AnimatedVisibility(vis && t != null, modifier.padding(start = 20.dp, end = 20.dp, top = ins.calculateTopPadding() + 12.dp), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        Text(t?.second ?: "", Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.bar).clickable { vis = false }.padding(horizontal = 16.dp, vertical = 12.dp),
            style = bodyStyle(15.sp, FontWeight.SemiBold).copy(color = c.onBar))
    }
}
